package com.asqi.scholia_kantin_be.repository.projection;

/**
 * Baris penjualan per dimensi — dipakai laporan per titik kasir dan per
 * petugas (PRD §9.5).
 *
 * <p>Projection menggantikan {@code List<Object[]>} agar urutan kolom query
 * tidak bisa ketukar diam-diam.
 */
public interface PenjualanDimensi {

    /** Id dimensi (id titik kasir atau id petugas, tergantung query). */
    Long getDimensiId();

    /** Banyaknya transaksi SUKSES pada dimensi ini. */
    Long getJumlah();

    /** Σ total nominal SUKSES. */
    Long getNilai();

    /** Σ HPP snapshot transaksi SUKSES. */
    Long getHpp();
}