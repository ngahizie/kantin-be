package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.dto.BarisPembatalanKasir;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.repository.TransaksiRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Laporan pembatalan kasir (PRD §9.5, issue #114): daftar transaksi berstatus
 * {@code VOID} pada periode, terbaru dulu.
 *
 * <p>Tenant-scoped dan baca-saja.
 */
@Service
@RequiredArgsConstructor
public class LaporanPembatalanService {

    private final TransaksiRepository transaksiRepo;
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
     * @return baris pembatalan terbaru lebih dulu
     */
    @Transactional(readOnly = true)
    public List<BarisPembatalanKasir> pembatalanKasir(Long sekolahId, LocalDate tanggal,
                                                      OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = LaporanHelper.rentang(jam, tanggal, dari, sampai);
        return transaksiRepo.pembatalanRentang(sekolahId, r[0], r[1])
                .stream()
                .map(BarisPembatalanKasir::dari)
                .toList();
    }
}