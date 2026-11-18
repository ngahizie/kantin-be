package com.asqi.scholia_kantin_be.repository.projection;

/**
 * Baris item terlaris — laporan penjualan per item (PRD §9.5).
 *
 * <p>Projection menggantikan {@code List<Object[]>} agar urutan kolom query
 * tidak bisa ketukar diam-diam.
 */
public interface ItemTerlaris {

    /** Id menu. */
    Long getMenuId();

    /** Nama menu snapshot saat transaksi. */
    String getNamaMenu();

    /** Σ qty terjual. */
    Long getQty();

    /** Σ subtotal nominal. */
    Long getSubtotal();

    /** Σ HPP snapshot (qty × hpp); boleh {@code null} bila snapshot kosong. */
    Long getModal();
}