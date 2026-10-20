package com.asqi.scholia_kantin_be.dto;

/** Agregat kerugian stok pada periode terpilih (#226). */
public record RingkasanKerugianStok(
        long totalKerugian,
        long totalQtyHilang,
        long jumlahKejadian,
        long kerugianOpname,
        long qtyOpname,
        long kerugianBarangRusak,
        long qtyBarangRusak) {
}
