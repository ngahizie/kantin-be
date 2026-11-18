package com.asqi.scholia_kantin_be.enums;

/**
 * Cara pembayaran satu transaksi kasir.
 *
 * <p><b>SALDO</b> (default) — nominal dipotong dari saldo siswa lewat
 * {@code mutasi_saldo} (PRD §6.1, §11.2). Wajib punya {@code siswa_id}.
 *
 * <p><b>TUNAI</b> — uang tunai diterima kasir; <b>tidak</b> menyentuh saldo
 * (KEPUTUSAN 13 Nov: Kartu Tamu dibayar tunai, tanpa topup/prabayar). Boleh
 * dipakai <b>tanpa kartu</b> sehingga {@code siswa_id} boleh kosong — kasir
 * cukup mengisi uang diterima dan (opsional) nama pembeli untuk struk.
 */
public enum MetodeBayar {
    /** Potong saldo siswa (perilaku awal PRD §6.1). */
    SALDO,
    /** Bayar tunai di kasir — tanpa potong saldo. */
    TUNAI
}