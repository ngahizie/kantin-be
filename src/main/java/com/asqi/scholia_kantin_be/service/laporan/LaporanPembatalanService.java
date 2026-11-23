package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.dto.BarisPembatalanKasir;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.Siswa;
import com.asqi.scholia_kantin_be.model.TitikKasir;
import com.asqi.scholia_kantin_be.model.Transaksi;
import com.asqi.scholia_kantin_be.repository.SiswaRepository;
import com.asqi.scholia_kantin_be.repository.TitikKasirRepository;
import com.asqi.scholia_kantin_be.repository.TransaksiRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Laporan pembatalan kasir (PRD §9.5, issue #114): daftar transaksi berstatus
 * {@code VOID} pada periode, terbaru dulu.
 *
 * <p><b>Periode memakai waktu pembatalan</b> ({@code void_at}), bukan waktu
 * transaksi (SEC-BATAL-03, issue #395): koreksi hari ini atas transaksi kemarin
 * harus muncul di laporan hari ini, kalau tidak rekonsiliasi kas harian
 * kehilangan jejaknya.
 *
 * <p><b>Dibatasi {@value #MAKS_BARIS} baris</b> (SEC-BATAL-02): query lama
 * menarik seluruh transaksi VOID pada rentang ke memori tanpa LIMIT.
 *
 * <p>Tenant-scoped dan baca-saja.
 */
@Service
@RequiredArgsConstructor
public class LaporanPembatalanService {

    /** Batas baris per permintaan — 500 pembatalan terbaru (SEC-BATAL-02). */
    public static final int MAKS_BARIS = 500;

    private final TransaksiRepository transaksiRepo;
    private final SiswaRepository siswaRepo;
    private final TitikKasirRepository titikKasirRepo;
    private final JamKantin jam;

    /**
     * Daftar transaksi yang dibatalkan (status {@code VOID}) pada periode,
     * terbaru dulu. Mencakup alasan void, waktu, petugas, &amp; subjek — untuk
     * audit sengketa "Kartu dipakai bukan pemiliknya".
     *
     * @param sekolahId tenant pemanggil
     * @param tanggal   tanggal tunggal (opsional)
     * @param dari      awal periode eksplisit (opsional)
     * @param sampai    akhir periode eksplisit, eksklusif (opsional)
     * @return maksimal {@value #MAKS_BARIS} baris pembatalan terbaru lebih dulu
     */
    @Transactional(readOnly = true)
    public List<BarisPembatalanKasir> pembatalanKasir(Long sekolahId, LocalDate tanggal,
                                                      OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = LaporanHelper.rentang(jam, tanggal, dari, sampai);
        List<Transaksi> transaksi = transaksiRepo.pembatalanRentang(
                sekolahId, r[0], r[1], PageRequest.of(0, MAKS_BARIS));
        if (transaksi.isEmpty()) {
            return List.of();
        }

        // Enrichment (SEC-BATAL-04): satu lookup batch per tabel, bukan per baris.
        Set<Long> siswaIds = transaksi.stream()
                .map(Transaksi::getSiswaId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Siswa> siswaById = siswaIds.isEmpty() ? Map.of()
                : siswaRepo.findByIdInAndSekolahId(siswaIds, sekolahId).stream()
                        .collect(Collectors.toMap(Siswa::getId, Function.identity()));
        Map<Long, String> namaTitik = titikKasirRepo.findBySekolahId(sekolahId).stream()
                .collect(Collectors.toMap(TitikKasir::getId, TitikKasir::getNama));

        return transaksi.stream()
                .map(t -> BarisPembatalanKasir.dari(t, siswaById, namaTitik))
                .toList();
    }
}
