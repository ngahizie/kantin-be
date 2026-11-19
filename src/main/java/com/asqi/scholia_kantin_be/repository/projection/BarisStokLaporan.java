package com.asqi.scholia_kantin_be.repository.projection;

/**
 * Satu baris laporan stok (PRD §9.5): stok berjalan × katalog menu.
 *
 * <p><b>Kenapa ada (issue #375).</b> Laporan stok sebelumnya memuat seluruh menu
 * dan seluruh baris {@code stok_cache} tenant ke memori lalu memfilternya di Java
 * — <i>uncapped result set</i> (OWASP A06:2025, NIST SC-5). Proyeksi ini dipakai
 * query berhalaman: hanya satu halaman (≤ 200 baris) yang diambil dari DB.
 *
 * <p>Ambang "menipis" memakai {@code menu.stok_minimum} (sumber kebenaran
 * katalog), bukan {@code stok.stok_minimum}.
 */
public interface BarisStokLaporan {

    /** Menu pemilik baris stok. */
    Long getMenuId();

    /** Nama menu (katalog). */
    String getNama();

    /** Kategori menu (boleh null). */
    Long getKategoriId();

    /** Stok berjalan saat ini. */
    Integer getStok();

    /** Stok minimum dari katalog menu. */
    Integer getStokMinimum();

    /** HPP berjalan per unit. */
    Long getHpp();

    /** Barcode menu (boleh null). */
    String getKodeBarcode();
}
