package com.asqi.scholia_kantin_be.dto;

/**
 * Baris laporan penjualan per item atau per kategori (PRD §9.5).
 *
 * <p>{@code kunciId} = {@code menuId} (laporan per item) atau {@code kategoriId}
 * (laporan per kategori).
 *
 * @param total        total uang dari baris ini (Σ subtotal jual)
 * @param hargaSatuan  harga jual per item ({@code total ÷ qty}); {@code null}
 *                     untuk laporan per kategori karena kategori tidak punya
 *                     satu harga
 * @param modal        modal barang yang terjual pada baris ini
 * @param marginPersen (total − modal) ÷ total × 100, 2 desimal
 */
public record BarisPenjualan(
        Long kunciId,
        String nama,
        long qty,
        long total,
        Long hargaSatuan,
        long modal,
        double marginPersen) {
}
