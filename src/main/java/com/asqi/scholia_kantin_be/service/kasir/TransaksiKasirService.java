package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.dto.HalamanResponse;
import com.asqi.scholia_kantin_be.dto.TransaksiKasirItem;
import com.asqi.scholia_kantin_be.dto.TransaksiSesiItem;
import com.asqi.scholia_kantin_be.enums.StatusSesiKasir;
import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.helper.UkuranHalaman;
import com.asqi.scholia_kantin_be.model.KartuTamu;
import com.asqi.scholia_kantin_be.model.SesiKasir;
import com.asqi.scholia_kantin_be.model.TitikKasir;
import com.asqi.scholia_kantin_be.model.Transaksi;
import com.asqi.scholia_kantin_be.model.TransaksiItem;
import com.asqi.scholia_kantin_be.repository.KartuTamuRepository;
import com.asqi.scholia_kantin_be.repository.SesiKasirRepository;
import com.asqi.scholia_kantin_be.repository.TitikKasirRepository;
import com.asqi.scholia_kantin_be.repository.TransaksiItemRepository;
import com.asqi.scholia_kantin_be.repository.TransaksiRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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
 * Sisi <b>baca</b> transaksi kasir — daftar transaksi untuk koreksi bendahara
 * (PRD §6.3, §9.2; issue <b>#212</b>).
 *
 * <p><b>Latar.</b> Aksi koreksi sudah ada
 * ({@code POST /api/kasir/transaksi/{transaksiId}/koreksi}), tetapi bendahara
 * tidak punya cara menemukan {@code transaksiId}: FE memanggil endpoint daftar
 * yang belum ada ({@code GET /api/transaksi/sesi-tutup},
 * {@code GET /api/v1/kasir/transaksi-lampau}) dan menerima <b>404</b>. Kelas ini
 * menutup celah tersebut.
 *
 * <p><b>Tenant-scoped</b> — selalu difilter {@code sekolah_id} dari token
 * (PRD §11.4), bukan dari parameter klien.
 *
 * <p><b>Filter dinamis via Criteria API</b>, bukan JPQL
 * {@code :x IS NULL OR ...}: pola terakhir membuat Hibernate mengirim parameter
 * {@code null} tanpa tipe dan PostgreSQL menolaknya
 * ({@code could not determine data type of parameter}) — akar bug #198
 * (endpoint daftar selalu 500). Lihat juga {@code RiwayatStokIT}.
 */
@Service
@RequiredArgsConstructor
public class TransaksiKasirService {

    /** Batas atas ukuran halaman — sama dengan pola {@code /api/stok/riwayat}. */
    private static final int UKURAN_MAKS = 200;

    private final TransaksiRepository transaksiRepo;
    private final TitikKasirRepository titikKasirRepo;
    private final KartuTamuRepository kartuTamuRepo;
    private final TransaksiItemRepository transaksiItemRepo;
    private final SesiKasirRepository sesiKasirRepo;
    private final JamKantin jam;

    /**
     * Daftar transaksi satu sekolah, terbaru dulu, berhalaman.
     *
     * @param sekolahId tenant dari token (wajib)
     * @param sesiId    filter sesi kasir (opsional)
     * @param tanggal   filter satu hari menurut zona sekolah (opsional)
     * @param status    filter {@code SUKSES}/{@code VOID} (opsional)
     */
    @Transactional(readOnly = true)
    public HalamanResponse<TransaksiKasirItem> daftar(Long sekolahId, Long sesiId, LocalDate tanggal,
                                                      StatusTransaksi status, int halaman, int ukuran) {
        int ukuranAman = UkuranHalaman.validasi(ukuran, UKURAN_MAKS);
        int halamanAman = Math.max(0, halaman);
        Pageable pageable = PageRequest.of(halamanAman, ukuranAman,
                Sort.by(Sort.Direction.DESC, "waktu").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<Transaksi> page = transaksiRepo.findAll(
                filter(sekolahId, sesiId, tanggal, status), pageable);

        return keHalaman(sekolahId, page);
    }

    /**
     * Daftar transaksi <b>kandidat koreksi bendahara</b> (issue <b>#230</b>) —
     * hanya transaksi pada sesi kasir berstatus {@code DITUTUP} milik tenant,
     * sesuai aksi {@code POST /api/kasir/transaksi/{transaksiId}/koreksi}
     * (PRD §6.3, §9.2).
     *
     * <p>Bedanya dengan {@link #daftar}: transaksi pada sesi yang masih
     * <b>TERBUKA</b> tidak muncul — bukan kandidat koreksi (koreksi pada sesi
     * terbuka ditolak {@code VoidService}; harus lewat void biasa). FE
     * {@code KoreksiBendaharaPage} sebelumnya memanggil
     * {@code GET /api/transaksi/sesi-tutup} yang tidak ada dan diam-diam jatuh
     * ke data mock; endpoint ini menutup celahnya dengan path konsisten
     * {@code GET /api/kasir/transaksi/sesi-tutup}.
     *
     * <p>Filter & paginasi sama dengan {@link #daftar}. Bila {@code sesiId}
     * diberikan, sesi divalidasi eksplisit: harus milik tenant <b>dan</b> sudah
     * ditutup — 404 lebih jelas daripada halaman kosong diam-diam
     * (pola soft-404 dihindari).
     */
    @Transactional(readOnly = true)
    public HalamanResponse<TransaksiKasirItem> daftarSesiTutup(Long sekolahId, Long sesiId,
                                                               LocalDate tanggal,
                                                               StatusTransaksi status,
                                                               int halaman, int ukuran) {
        if (sesiId != null) {
            SesiKasir sesi = sesiKasirRepo.findById(sesiId)
                    .filter(s -> sekolahId.equals(s.getSekolahId()))
                    .orElseThrow(() -> new NotFoundEntity(
                            "Sesi kasir " + sesiId + " tidak ditemukan"));
            if (sesi.terbuka()) {
                throw new InvalidOperationException(
                        "Sesi kasir " + sesiId + " masih terbuka — koreksi hanya untuk sesi tertutup");
            }
        }
        int ukuranAman = UkuranHalaman.validasi(ukuran, UKURAN_MAKS);
        int halamanAman = Math.max(0, halaman);
        Pageable pageable = PageRequest.of(halamanAman, ukuranAman,
                Sort.by(Sort.Direction.DESC, "waktu").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<Transaksi> page = transaksiRepo.findAll(
                filter(sekolahId, sesiId, tanggal, status).and(sesiTertutup(sekolahId)), pageable);

        return keHalaman(sekolahId, page);
    }

    /** Halaman {@code Transaksi} → {@code HalamanResponse<TransaksiKasirItem>} (nama batch). */
    private HalamanResponse<TransaksiKasirItem> keHalaman(Long sekolahId, Page<Transaksi> page) {
        // Nama untuk tampilan — diambil BATCH (satu query per jenis), bukan per baris.
        Map<Long, String> namaTitik = namaTitikKasir(sekolahId, page.getContent());
        Map<Long, String> namaSubjek = namaPemegangKartu(sekolahId, page.getContent());

        return HalamanResponse.<Transaksi, TransaksiKasirItem>dari(page, t -> {
            TransaksiKasirItem item = TransaksiKasirItem.dari(t);
            item.setTitikKasirNama(namaTitik.get(t.getTitikKasirId()));
            if (t.getSubjekTipe() == SubjekTipe.KARTU_TAMU) {
                item.setSubjekNama(namaSubjek.get(t.getSubjekId()));
            }
            return item;
        });
    }

    /**
     * Riwayat transaksi satu <b>sesi kasir</b> (issue #229) — dipakai panel
     * riwayat di layar POS. Tanpa paginasi (terikat satu sesi), terbaru dulu.
     *
     * <p>Rincian item diambil dalam <b>satu query</b> untuk seluruh sesi
     * ({@code findByTransaksiIdIn}), bukan per transaksi — tidak ada N+1.
     * Nama/kelas/NIS siswa tidak tersedia (ADR-0002), lihat
     * {@link TransaksiSesiItem}.
     *
     * @param sesiId sesi kasir; harus milik tenant pemanggil (jika tidak 404)
     */
    @Transactional(readOnly = true)
    public List<TransaksiSesiItem> riwayatSesi(Long sekolahId, Long sesiId) {
        sesiKasirRepo.findById(sesiId)
                .filter(s -> sekolahId.equals(s.getSekolahId()))
                .orElseThrow(() -> new NotFoundEntity("Sesi kasir " + sesiId + " tidak ditemukan"));

        List<Transaksi> trx = transaksiRepo.findAll(filter(sekolahId, sesiId, null, null),
                Sort.by(Sort.Direction.DESC, "waktu").and(Sort.by(Sort.Direction.DESC, "id")));

        Map<Long, String> namaTitik = namaTitikKasir(sekolahId, trx);
        Map<Long, String> namaSubjek = namaPemegangKartu(sekolahId, trx);

        Map<Long, List<TransaksiItem>> itemByTrx = trx.isEmpty() ? Map.of()
                : transaksiItemRepo.findByTransaksiIdIn(
                        trx.stream().map(Transaksi::getId).toList()).stream()
                .collect(Collectors.groupingBy(i -> i.getTransaksi().getId()));

        return trx.stream()
                .map(t -> TransaksiSesiItem.dari(t,
                        namaTitik.get(t.getTitikKasirId()),
                        namaSubjek.get(t.getSubjekId()),
                        itemByTrx.getOrDefault(t.getId(), List.of())))
                .toList();
    }

    // ────────────────────────────────────────────────────────────────
    // HELPER
    // ────────────────────────────────────────────────────────────────

    /**
     * Predicate filter daftar transaksi (Criteria API) — <b>hanya</b> menambahkan
     * syarat untuk filter yang benar-benar terisi.
     *
     * <p>Kenapa bukan JPQL {@code :x IS NULL OR ...}: lihat javadoc kelas
     * (bug #198 — PostgreSQL menolak parameter {@code null} tanpa tipe).
     */
    private Specification<Transaksi> filter(Long sekolahId, Long sesiId,
                                            LocalDate tanggal, StatusTransaksi status) {
        return (root, query, cb) -> {
            List<Predicate> syarat = new ArrayList<>();
            syarat.add(cb.equal(root.get("sekolahId"), sekolahId));   // tenant-scoped
            if (sesiId != null) {
                syarat.add(cb.equal(root.get("sesiKasirId"), sesiId));
            }
            if (status != null) {
                syarat.add(cb.equal(root.get("status"), status));
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

    /**
     * Predicate: transaksi milik sesi kasir berstatus {@code DITUTUP}
     * (issue #230) — via <b>subquery</b> ke {@code sesi_kasir} karena
     * {@code Transaksi} hanya menyimpan {@code sesiKasirId} (tanpa asosiasi).
     * Tenant juga ditegakkan di subquery agar transaksi tenant lain dengan
     * sesiId sama (id lokal) tidak bisa menyelundup.
     */
    private Specification<Transaksi> sesiTertutup(Long sekolahId) {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<SesiKasir> sesi = sub.from(SesiKasir.class);
            sub.select(sesi.get("id")).where(
                    cb.equal(sesi.get("sekolahId"), sekolahId),
                    cb.equal(sesi.get("status"), StatusSesiKasir.DITUTUP));
            return root.get("sesiKasirId").in(sub);
        };
    }

    /** Peta {@code titikKasirId → nama} untuk baris di halaman ini (satu query). */
    private Map<Long, String> namaTitikKasir(Long sekolahId, List<Transaksi> baris) {
        Set<Long> ids = baris.stream()
                .map(Transaksi::getTitikKasirId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return titikKasirRepo.findAllById(ids).stream()
                .filter(t -> t.getSekolahId().equals(sekolahId))
                .collect(Collectors.toMap(TitikKasir::getId, TitikKasir::getNama, (a, b) -> a));
    }

    /**
     * Peta {@code kartuTamuId → label pemegang} untuk subjek {@code KARTU_TAMU}
     * di halaman ini (satu query). Label kosong jatuh ke nomor kartu.
     */
    private Map<Long, String> namaPemegangKartu(Long sekolahId, List<Transaksi> baris) {
        Set<Long> ids = baris.stream()
                .filter(t -> t.getSubjekTipe() == SubjekTipe.KARTU_TAMU)
                .map(Transaksi::getSubjekId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return kartuTamuRepo.findAllById(ids).stream()
                .filter(k -> k.getSekolahId().equals(sekolahId))
                .collect(Collectors.toMap(KartuTamu::getId, TransaksiKasirService::labelKartu, (a, b) -> a));
    }

    private static String labelKartu(KartuTamu k) {
        String label = k.getLabelPemegang();
        return (label == null || label.isBlank()) ? k.getNomorKartu() : label;
    }
}
