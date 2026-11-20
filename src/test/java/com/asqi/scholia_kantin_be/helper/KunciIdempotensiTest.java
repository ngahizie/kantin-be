package com.asqi.scholia_kantin_be.helper;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji penyusun kunci idempotency (issue #382).
 *
 * <p>Kolom {@code mutasi_saldo.idempotency_key} hanya 64 karakter; setiap awalan
 * jalur memakan sebagian kuota itu, jadi batas referensi berbeda per jalur.
 * Uji ini mengunci tabel tersebut: panjang tepat 64 harus lolos, satu karakter
 * lebih harus ditolak 400 (bukan 500 dari DB).
 */
@DisplayName("KunciIdempotensi — batas 64 karakter per awalan (#382)")
class KunciIdempotensiTest {

    /** Panjang maksimum referensi per awalan jalur (64 − panjang awalan). */
    @ParameterizedTest(name = "{0} → maksimal {1} karakter referensi")
    @CsvSource({
            "KOREKSI-, 56",
            "TOPUP-TUNAI-, 52",
            "TOPUP-ONLINE-, 51",
            "REFUND-, 57",
            "TRANSFER-, 55"
    })
    void batasPerAwalan(String awalan, int maksReferensi) {
        String pas = "R".repeat(maksReferensi);
        String kunci = KunciIdempotensi.gabung(awalan, pas);

        assertThat(kunci).hasSize(KunciIdempotensi.PANJANG_MAKS);
        assertThat(kunci).startsWith(awalan);

        String kelebihan = "R".repeat(maksReferensi + 1);
        assertThatThrownBy(() -> KunciIdempotensi.gabung(awalan, kelebihan))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("maksimal " + maksReferensi + " karakter");
    }

    @Test
    @DisplayName("akhiran -MASUK memotong kuota: pindah saldo maksimal 49 karakter")
    void akhiranMasukIkutDihitung() {
        String keySumber = KunciIdempotensi.gabung("TRANSFER-", "R".repeat(49));
        String keyTujuan = KunciIdempotensi.gabung(keySumber, "-MASUK");

        // Sumber 58 karakter, tujuan tepat 64 — batas DTO (49) ditentukan sisi tujuan.
        assertThat(keySumber).hasSize(58);
        assertThat(keyTujuan).hasSize(KunciIdempotensi.PANJANG_MAKS).endsWith("-MASUK");

        // 50 karakter → kunci sumber 59, sisa kuota 5 < panjang "-MASUK" (6) → ditolak.
        String lebih = KunciIdempotensi.gabung("TRANSFER-", "R".repeat(50));
        assertThat(lebih).hasSize(59);
        assertThatThrownBy(() -> KunciIdempotensi.gabung(lebih, "-MASUK"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("terlalu panjang")
                .hasMessageContaining("maksimal 5 karakter");
    }

    @Test
    @DisplayName("referensi null diperlakukan sebagai string kosong, bukan NPE")
    void referensiNullAman() {
        assertThat(KunciIdempotensi.gabung("KOREKSI-", null)).isEqualTo("KOREKSI-");
    }
}
