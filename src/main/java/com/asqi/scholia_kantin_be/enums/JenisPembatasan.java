package com.asqi.scholia_kantin_be.enums;

/**
 * Jenis baris {@code pembatasan} — satu tabel untuk semua pembatasan siswa
 * (ERD v2). Nilai harus sama dengan CHECK {@code ck_pembatasan_jenis}.
 */
public enum JenisPembatasan {

    /** Kartu/siswa diblokir: tap ditolak. */
    BLOKIR_SISWA,

    /** Satu menu atau satu kategori diblokir untuk siswa. */
    BLOKIR_ITEM,

    /** Batas belanja harian (rupiah). {@code nominal} wajib terisi. */
    LIMIT_HARIAN
}
