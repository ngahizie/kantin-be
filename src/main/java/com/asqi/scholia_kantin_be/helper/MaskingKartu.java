package com.asqi.scholia_kantin_be.helper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Penyamaran kredensial kartu pada respons laporan (SEC-BATAL-05, issue #395).
 *
 * <p>UID RFID adalah <i>possession credential</i>: siapa pun yang tahu nilainya
 * bisa menempelkan kartu lain atau memalsukan tap. Laporan pembatalan hanya
 * perlu membedakan kartu satu dengan yang lain, bukan menampilkan UID utuh —
 * jadi empat karakter terakhir saja yang dikirim.
 */
public final class MaskingKartu {

    /** Penanda bagian yang disamarkan. */
    public static final String TOPENG = "****";

    private MaskingKartu() {
    }

    /**
     * Samarkan UID: {@code "04A1B2C3D4"} → {@code "****C3D4"}.
     *
     * @param uid UID kartu mentah (boleh null/kosong)
     * @return UID tersamar, atau {@code null} bila input kosong; UID ≤ 4 karakter
     *         disamarkan seluruhnya agar tidak ada bagian yang terbaca
     */
    public static String uid(String uid) {
        if (uid == null || uid.isBlank()) {
            return null;
        }
        String bersih = uid.trim();
        if (bersih.length() <= 4) {
            return TOPENG;
        }
        return TOPENG + bersih.substring(bersih.length() - 4);
    }

    /**
     * Pseudonim stabil ID siswa untuk log: {@code 1001} → {@code S#a3f9}.
     *
     * <p>ID siswa berupa angka dengan ruang nilai kecil, jadi menyamarkan digit
     * (mis. {@code ****01}) tetap membocorkan nilainya. Yang dipakai adalah empat
     * heksadesimal pertama SHA-256 dengan awalan domain: baris log untuk siswa
     * yang sama tetap bisa dikorelasikan tanpa menuliskan identitasnya.
     *
     * <p>Pseudonim, <b>bukan rahasia</b>: ruang ID kecil sehingga bisa
     * di-brute-force. Tujuannya mencegah identitas terbaca langsung oleh staf
     * non-berwenang atau agregator log pihak ketiga (issue #437).
     *
     * @param siswaId ID siswa (boleh null)
     * @return pseudonim, atau {@code null} bila input null
     */
    public static String idSiswa(Long siswaId) {
        if (siswaId == null) {
            return null;
        }
        byte[] hash = sha256("siswa:" + siswaId);
        return "S#" + String.format("%04x", ((hash[0] & 0xff) << 8) | (hash[1] & 0xff));
    }

    private static byte[] sha256(String teks) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(teks.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 wajib ada di setiap JRE; jangan sampai pencatatan log
            // menggagalkan alur transaksi.
            throw new IllegalStateException("SHA-256 tidak tersedia", e);
        }
    }
}
