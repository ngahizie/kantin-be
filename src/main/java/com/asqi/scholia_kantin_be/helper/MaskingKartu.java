package com.asqi.scholia_kantin_be.helper;

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
}
