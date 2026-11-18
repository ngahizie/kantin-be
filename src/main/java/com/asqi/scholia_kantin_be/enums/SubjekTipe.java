package com.asqi.scholia_kantin_be.enums;

/**
 * Jenis pemilik saldo di ledger kantin (lihat skema {@code saldo_ledger}).
 *
 * <p><b>Kenapa hanya SISWA (C.2b).</b> Skema v2 (V28) menghapus tabel
 * {@code kartu_tamu} dan mewajibkan setiap {@code kartu} menunjuk
 * {@code siswa_id}, sehingga subjek saldo satu-satunya adalah siswa. Kartu Tamu
 * diganti <b>pembayaran tunai</b> (KEPUTUSAN 13 Nov) yang tidak memakai saldo —
 * lihat {@code MetodeBayar#TUNAI}. Nilai {@code KARTU_TAMU} dihapus agar tidak
 * ada lagi jalur kode yang bisa menulis subjek yang tidak ada di skema.
 *
 * <p>Catatan: CHECK constraint lama di DB masih menyebut {@code 'KARTU_TAMU'}
 * pada sebagian tabel. Itu tidak berbahaya — tidak ada baris maupun kode yang
 * menulis nilai tersebut lagi.
 */
public enum SubjekTipe {
    /** Siswa (identitas dari admin-be). */
    SISWA
}
