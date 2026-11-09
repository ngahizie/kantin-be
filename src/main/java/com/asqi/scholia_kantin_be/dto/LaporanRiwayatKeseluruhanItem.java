package com.asqi.scholia_kantin_be.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Satu baris riwayat transaksi keseluruhan (laporan transaksi gabungan seluruh siswa).
 */
public record LaporanRiwayatKeseluruhanItem(
        String id,
        OffsetDateTime waktu,
        String jenis,
        String arah,
        long nominal,
        long saldoSetelah,
        String titikKasir,
        String petugas,
        String referensiId,
        String keterangan,
        List<LaporanRiwayatSiswa.Item> items,
        Long siswaId,
        String siswaNama,
        String siswaNis,
        String siswaKelas) {
}
