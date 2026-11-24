package com.asqi.scholia_kantin_be.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UidKartuValidator — terima hex, desimal USB reader panjang ganjil/genap, dan tolak format tak aman")
class UidKartuValidatorTest {

    private final UidKartuValidator validator = new UidKartuValidator();

    @Test
    @DisplayName("menerima UID hex, pemisah standar, desimal 9–10 digit, kode kartu, dan null/kosong")
    void nilaiSahDiterima() {
        String[] sah = {
                null,
                "",
                "   ",
                "04A1B2C3D4",
                "04:A1:B2:C3",
                "04-A1-B2-C3",
                "123456789",
                "1831574427",
                "ITUID001",
                "KT_002"
        };
        for (String v : sah) {
            assertThat(validator.isValid(v, null)).as("UID '%s' harus valid", v).isTrue();
        }
    }

    @Test
    @DisplayName("menolak UID terlalu pendek, terlalu panjang, ber-spasi, atau karakter berbahaya")
    void nilaiTakSahDitolak() {
        String[] tidakSah = {
                "123",
                "-1234",
                "1234-",
                "1234 5678",
                "<script>",
                "abc/def",
                "A".repeat(65)
        };
        for (String v : tidakSah) {
            assertThat(validator.isValid(v, null)).as("UID '%s' harus ditolak", v).isFalse();
        }
    }
}
