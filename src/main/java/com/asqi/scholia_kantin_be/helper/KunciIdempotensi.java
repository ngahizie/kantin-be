package com.asqi.scholia_kantin_be.helper;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;

/**
 * Penyusun kunci idempotency ledger (issue #382).
 *
 * <p>Kolom {@code mutasi_saldo.idempotency_key} hanya <b>64 karakter</b>
 * ({@code V28__SkemaV2.sql:400}, sama dengan {@code @Column(length = 64)} di
 * entity). Beberapa jalur menambahkan awalan pada nomor referensi dari klien —
 * {@code KOREKSI-}, {@code TOPUP-TUNAI-}, {@code TOPUP-ONLINE-}, {@code REFUND-},
 * {@code TRANSFER-} (plus akhiran {@code -MASUK}) — sehingga referensi yang
 * masih lolos validasi DTO bisa menghasilkan kunci &gt; 64 karakter dan berakhir
 * {@code DataIntegrityViolationException} (HTTP 500) alih-alih 400.
 *
 * <p>Helper ini menjadi satu-satunya tempat penyusunan kunci: panjang diperiksa
 * sebelum menyentuh DB, dan pesan galatnya menyebut sisa karakter yang tersedia
 * untuk awalan tersebut. Menambah jalur baru cukup memakai {@link #gabung}.
 */
public final class KunciIdempotensi {

    /** Panjang maksimum kolom {@code idempotency_key} (DB &amp; entity). */
    public static final int PANJANG_MAKS = 64;

    private KunciIdempotensi() {
    }

    /**
     * Susun kunci {@code awalan + referensiId} dan pastikan tidak melebihi
     * {@value #PANJANG_MAKS} karakter.
     *
     * @param awalan      awalan tetap jalur ini (mis. {@code "KOREKSI-"})
     * @param referensiId nomor referensi/berita acara dari klien
     * @return kunci siap pakai untuk {@code PerintahMutasiSaldo}
     * @throws InvalidOperationException (HTTP 400) bila hasilnya kepanjangan
     */
    public static String gabung(String awalan, String referensiId) {
        String depan = awalan == null ? "" : awalan;
        String kunci = depan + (referensiId == null ? "" : referensiId);
        if (kunci.length() > PANJANG_MAKS) {
            int sisa = PANJANG_MAKS - depan.length();
            throw new InvalidOperationException(
                    "Nomor referensi terlalu panjang: maksimal " + sisa + " karakter untuk kunci \""
                            + depan + "\" (idempotency_key dibatasi " + PANJANG_MAKS + " karakter)");
        }
        return kunci;
    }
}
