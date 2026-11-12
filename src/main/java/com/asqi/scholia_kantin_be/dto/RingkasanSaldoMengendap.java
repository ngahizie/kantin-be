package com.asqi.scholia_kantin_be.dto;

/**
 * Ringkasan dana titipan (kewajiban sekolah) — laporan "Saldo mengendap"
 * (PRD §9.5): total saldo siswa yang belum dibelanjakan.
 *
 * @param saldoSiswa  Σ saldo semua siswa (uang titipan ortu)
 * @param total       kewajiban sekolah (= {@code saldoSiswa})
 * @param jumlahSiswa banyak siswa yang punya baris saldo
 */
public record RingkasanSaldoMengendap(
        long saldoSiswa,
        long total,
        long jumlahSiswa) {
}
