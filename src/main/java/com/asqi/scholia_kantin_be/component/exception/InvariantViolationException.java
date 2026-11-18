package com.asqi.scholia_kantin_be.component.exception;

/**
 * 500 — pelanggaran invarian internal (kontrak antar-lapisan dilanggar).
 *
 * <p>Dipakai untuk penjagaan di dalam service yang <b>tidak bisa</b> dipicu
 * pengguna lewat masukan yang sah: nilai negatif, qty ≤ 0, perintah tanpa
 * {@code sekolahId} atau {@code jenis mutasi}, dan sejenisnya. Pengguna akhir
 * tetap menerima jawaban generik 500 yang sama seperti sebelumnya (tidak ada
 * informasi internal yang bocor), tetapi log &amp; stack trace menyebut
 * invarian yang dilanggar sehingga bug pemanggil cepat ditemukan.
 *
 * <p>Bedanya dengan exception domain lain:
 * <ul>
 *   <li>{@link InvalidOperationException} → 400, operasi tidak sah pada state
 *       bisnis yang <i>dapat</i> terjadi (mis. void transaksi yang sudah void).</li>
 *   <li>{@link ConflictException} → 409, bentrok aturan bisnis (saldo kurang,
 *       stok tidak cukup).</li>
 *   <li>{@code InvariantViolationException} → 500, kode pemanggil yang salah:
 *       bukan kesalahan pengguna, jadi tidak boleh dijawab 4xx.</li>
 * </ul>
 */
public class InvariantViolationException extends RuntimeException {
    public InvariantViolationException(String message) {
        super(message);
    }
}
