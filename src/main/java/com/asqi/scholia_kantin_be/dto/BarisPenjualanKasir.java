package com.asqi.scholia_kantin_be.dto;

/**
 * Baris laporan <b>penjualan per kasir</b> — grouping per titik kasir +
 * petugas (PRD §9.5, issue #227). Kontrak FE {@code BarisPenjualanKasir}.
 *
 * <p>Semantik mengikuti <b>rekap sesi kasir</b> ({@code SesiKasirService.hitungRekap})
 * agar Σ {@code penjualanBersih} pada periode yang sama konsisten dengan
 * {@code totalBersih} sesi:
 * <ul>
 *   <li>{@code penjualanBruto} = Σ total transaksi SUKSES + VOID (uang yang
 *       menyentuh kasir, sama seperti {@code totalBruto} rekap sesi),</li>
 *   <li>{@code nilaiVoid} = Σ total transaksi VOID,</li>
 *   <li>{@code penjualanBersih} = {@code penjualanBruto} − {@code nilaiVoid}
 *       = Σ total SUKSES.</li>
 * </ul>
 * {@code jumlahTransaksi} = jumlah transaksi SUKSES (VOID dihitung terpisah di
 * {@code nilaiVoid}, bukan sebagai transaksi penjualan).
 *
 * <p>{@code petugas} memakai id sebagai pengganti nama — nama petugas tersimpan
 * di admin-be; laporan dimensi lain (#116) juga mengembalikan {@code null} nama
 * petugas. FE menampilkan string ini apa adanya sampai port nama petugas dibuat.
 */
public record BarisPenjualanKasir(
        Long titikKasirId,
        String namaTitikKasir,
        Long petugasId,
        String petugas,
        long jumlahTransaksi,
        long penjualanBruto,
        long nilaiVoid,
        long penjualanBersih) {
}
