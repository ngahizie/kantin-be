package com.asqi.scholia_kantin_be.dev;

/**
 * Permintaan login shim dev (bentuknya meniru payload FE
 * {@code user-auth-form.tsx}: email/username + password).
 *
 * <p>Kredensial <b>wajib cocok</b> dengan akun di tabel {@code dev_pengguna}
 * (lihat {@link DevLoginValidator}) — akun asal-asalan ditolak 401.
 *
 * <p>{@code sekolah_id} sengaja tidak dibaca lagi: sekolah, role, dan
 * {@code userId} diambil dari akun, jadi klien tidak bisa memilih identitas
 * sendiri. Field itu tetap boleh dikirim (diabaikan) agar payload FE lama
 * tidak perlu diubah.
 */
public record DevLoginRequest(String email, String username, String password) {
}
