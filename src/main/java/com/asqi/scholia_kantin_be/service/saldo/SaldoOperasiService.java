package com.asqi.scholia_kantin_be.service.saldo;

import com.asqi.scholia_kantin_be.dto.HalamanResponse;
import com.asqi.scholia_kantin_be.dto.MutasiKoreksiItem;
import com.asqi.scholia_kantin_be.dto.MutasiSaldoItem;
import com.asqi.scholia_kantin_be.dto.SaldoResponse;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.helper.UkuranHalaman;
import com.asqi.scholia_kantin_be.model.MutasiSaldo;
import com.asqi.scholia_kantin_be.repository.MutasiSaldoRepository;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Facade baca saldo untuk HTTP: menggabungkan saldo berjalan, belanja hari ini,
 * dan mutasi terbaru menjadi satu {@link SaldoResponse} (PRD §9.3), serta
 * menyediakan <b>daftar koreksi bendahara</b> (PRD §9.2; issue #213).
 *
 * <p>Baca-saja; tidak memodifikasi ledger. Tetap tenant-scoped lewat
 * {@link LedgerSaldoService}.
 */
@Service
@RequiredArgsConstructor
public class SaldoOperasiService {

    private static final int MAKS_MUTASI = 50;
    /** Batas atas ukuran halaman — sama dengan pola daftar lain. */
    private static final int UKURAN_MAKS = 200;

    private final LedgerSaldoService ledgerSaldo;
    private final MutasiSaldoRepository ledgerRepo;
    private final JamKantin jam;

    /** Ringkasan saldo + belanja hari ini + mutasi terbaru. */
    @Transactional(readOnly = true)
    public SaldoResponse lihat(Long sekolahId, Long siswaId, int batasMutasi) {
        int batas = (batasMutasi <= 0 || batasMutasi > MAKS_MUTASI) ? 20 : batasMutasi;
        long saldo = ledgerSaldo.saldo(sekolahId, siswaId);
        long belanjaHariIni = ledgerSaldo.belanjaHariIni(sekolahId, siswaId);

        List<MutasiSaldoItem> mutasi = ledgerSaldo
                .riwayatTerbaru(sekolahId, siswaId, batas)
                .stream()
                .map(MutasiSaldoItem::dari)
                .toList();

        return SaldoResponse.builder()
                .siswaId(siswaId)
                .saldo(saldo)
                .belanjaHariIni(belanjaHariIni)
                .mutasiTerbaru(mutasi)
                .build();
    }

    /** Hitung ulang saldo dari ledger — verifikasi/rekonsiliasi cache. */
    @Transactional(readOnly = true)
    public long hitungUlangDariLedger(Long sekolahId, Long siswaId) {
        return ledgerSaldo.hitungUlangDariLedger(sekolahId, siswaId);
    }

    /**
     * <b>Daftar mutasi koreksi saldo</b> (PRD §9.2; issue #213) — bendahara
     * perlu <i>membaca kembali</i> koreksi yang pernah dilakukan. Hanya mutasi
     * berjenis {@link JenisMutasiSaldo#KOREKSI}, terbaru dulu, berhalaman.
     *
     * <p>Data koreksi tidak pernah diedit/dihapus (PRD §9.2), jadi daftar ini
     * murni proyeksi ledger.
     *
     * @param sekolahId  tenant dari token (wajib)
     * @param tanggal    filter satu hari menurut zona sekolah (opsional)
     * @param siswaId   filter siswa tertentu (opsional)
     */
    @Transactional(readOnly = true)
    public HalamanResponse<MutasiKoreksiItem> daftarKoreksi(Long sekolahId, LocalDate tanggal,
                                                            Long siswaId,
                                                            int halaman, int ukuran) {
        int ukuranAman = UkuranHalaman.validasi(ukuran, UKURAN_MAKS);
        int halamanAman = Math.max(0, halaman);
        Pageable pageable = PageRequest.of(halamanAman, ukuranAman,
                Sort.by(Sort.Direction.DESC, "waktu").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<MutasiSaldo> page = ledgerRepo.findAll(
                filterKoreksi(sekolahId, tanggal, siswaId), pageable);

        return HalamanResponse.<MutasiSaldo, MutasiKoreksiItem>dari(page, l -> {
            MutasiKoreksiItem item = MutasiKoreksiItem.dari(l);
            return item;
        });
    }

    // ────────────────────────────────────────────────────────────────
    // HELPER
    // ────────────────────────────────────────────────────────────────

    /**
     * Predicate daftar koreksi (Criteria API) — <b>hanya</b> menambahkan syarat
     * untuk filter yang terisi.
     *
     * <p>Kenapa bukan JPQL {@code :x IS NULL OR ...}: pola itu membuat Hibernate
     * mengirim parameter {@code null} tanpa tipe dan PostgreSQL menolaknya
     * ({@code could not determine data type of parameter}) — akar bug #198
     * (endpoint daftar selalu 500).
     */
    private Specification<MutasiSaldo> filterKoreksi(Long sekolahId, LocalDate tanggal,
                                                     Long siswaId) {
        return (root, query, cb) -> {
            List<Predicate> syarat = new ArrayList<>();
            syarat.add(cb.equal(root.get("sekolahId"), sekolahId));              // tenant-scoped
            syarat.add(cb.equal(root.get("jenis"), JenisMutasiSaldo.KOREKSI));   // inti endpoint
            if (siswaId != null) {
                syarat.add(cb.equal(root.get("siswaId"), siswaId));
            }
            if (tanggal != null) {
                // Batas hari menurut zona sekolah (JamKantin), bukan zona server.
                OffsetDateTime awal = tanggal.atStartOfDay(jam.zona()).toOffsetDateTime();
                syarat.add(cb.greaterThanOrEqualTo(root.get("waktu"), awal));
                syarat.add(cb.lessThan(root.get("waktu"), awal.plusDays(1)));
            }
            return cb.and(syarat.toArray(new Predicate[0]));
        };
    }

}
