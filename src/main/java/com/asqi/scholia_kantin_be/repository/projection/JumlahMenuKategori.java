package com.asqi.scholia_kantin_be.repository.projection;

/**
 * Jumlah menu per kategori dalam satu sekolah (PRD §7.1).
 *
 * <p>Projection menggantikan {@code List<Object[]>} agar urutan kolom query
 * tidak bisa ketukar diam-diam. Satu query untuk melengkapi {@code jumlahItem}
 * di daftar kategori.
 */
public interface JumlahMenuKategori {

    /** Id kategori menu. */
    Long getKategoriId();

    /** Banyaknya menu (belum dihapus) pada kategori ini. */
    Long getJumlah();
}