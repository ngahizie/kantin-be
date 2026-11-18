package com.asqi.scholia_kantin_be.repository.projection;

/**
 * Baris penjualan per kategori — laporan penjualan per kategori (PRD §9.5).
 *
 * <p>Projection menggantikan {@code List<Object[]>} agar urutan kolom query
 * tidak bisa ketukar diam-diam.
 */
public interface PenjualanKategori {

    /** Id kategori menu. */
    Long getKategoriId();

    /** Σ qty terjual. */
    Long getQty();

    /** Σ subtotal nominal. */
    Long getSubtotal();

    /** Σ HPP snapshot (qty × hpp); boleh {@code null} bila snapshot kosong. */
    Long getModal();
}