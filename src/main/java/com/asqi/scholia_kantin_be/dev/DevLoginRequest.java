package com.asqi.scholia_kantin_be.dev;

/**
 * Permintaan login shim dev (bentuknya meniru payload FE
 * {@code user-auth-form.tsx}: email, password, sekolah_id).
 *
 * <p>Shim ini <b>tidak</b> memverifikasi password — ia hanya alat uji lokal
 * agar FE mendapat token RS256 nyata. Jangan dipakai sebagai autentikasi nyata.
 *
 * <p>{@code user_id} & {@code nama} opsional: dipakai agar token bisa mewakili
 * pengguna seed dev tertentu (1=kepsek, 2=TU/bendahara, 3=pengelola, 4=kasir)
 * sehingga {@code aktor_id}/{@code petugas_id} pada data seed cocok dengan
 * identitas yang tampil di FE.
 */
public record DevLoginRequest(String email, String username, String password, Long sekolah_id,
                              Long user_id, String nama) {
}
