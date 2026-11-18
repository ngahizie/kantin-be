package com.asqi.scholia_kantin_be.repository.projection;

import com.asqi.scholia_kantin_be.enums.StatusTransaksi;

/**
 * Rekap jumlah &amp; total transaksi per status (PRD §6.4, §9.5).
 *
 * <p>Projection menggantikan {@code List<Object[]>} agar urutan kolom query
 * tidak bisa ketukar diam-diam. Dipakai rekap sesi kasir dan ringkasan
 * penjualan.
 */
public interface RekapStatus {

    /** Status transaksi yang direkap. */
    StatusTransaksi getStatus();

    /** Banyaknya transaksi pada status ini. */
    Long getJumlah();

    /** Σ total nominal pada status ini. */
    Long getTotal();
}