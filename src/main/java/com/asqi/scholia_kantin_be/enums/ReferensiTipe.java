package com.asqi.scholia_kantin_be.enums;

/**
 * Tipe sumber sebuah mutasi (kolom {@code referensi_tipe} pada
 * {@code saldo_ledger} &amp; {@code mutasi_stok}).
 *
 * <p>Dipakai untuk menautkan mutasi ke dokumen asalnya (transaksi, barang
 * masuk, opname, top-up, refund, transfer) sehingga rekonsiliasi &amp; audit
 * mudah. Nilai enum adalah <b>kontrak data</b>: yang disimpan ke kolom
 * {@code referensi_tipe} adalah {@link #name()}, jadi jangan mengganti nama
 * konstanta tanpa migrasi data.
 */
public enum ReferensiTipe {
    /** Mutasi berasal dari sebuah transaksi kasir (debit penjualan / kredit void). */
    TRANSAKSI,
    /** Mutasi berasal dari top-up tunai. */
    TOPUP,
    /** Mutasi berasal dari top-up online (kanal pembayaran digital). */
    TOPUP_ONLINE,
    /** Mutasi berasal dari perpindahan saldo antar-siswa. */
    TRANSFER,
    /** Mutasi berasal dari refund saldo siswa keluar. */
    REFUND,
    /** Mutasi koreksi bendahara. */
    KOREKSI,
    /** Mutasi berasal dari dokumen barang masuk. */
    BARANG_MASUK,
    /** Mutasi pembalik dokumen barang masuk (koreksi salah input). */
    BARANG_MASUK_PEMBALIK,
    /** Mutasi berasal dari opname stok tunggal. */
    OPNAME,
    /** Mutasi berasal dari opname stok batch. */
    OPNAME_BATCH
}
