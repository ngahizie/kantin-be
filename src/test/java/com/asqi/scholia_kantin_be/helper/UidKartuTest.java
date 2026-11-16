package com.asqi.scholia_kantin_be.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Bentuk kanonik UID kartu RFID (issue #346). */
class UidKartuTest {

    @Test
    @DisplayName("huruf kecil & campur → huruf kapital")
    void keHurufKapital() {
        assertThat(UidKartu.normalisasi("04a1b2c3d4")).isEqualTo("04A1B2C3D4");
        assertThat(UidKartu.normalisasi("04A1b2C3D4")).isEqualTo("04A1B2C3D4");
        assertThat(UidKartu.normalisasi("04A1B2C3D4")).isEqualTo("04A1B2C3D4");
    }

    @Test
    @DisplayName("spasi di ujung dibuang")
    void buangSpasi() {
        assertThat(UidKartu.normalisasi("  04a1b2c3d4  ")).isEqualTo("04A1B2C3D4");
        assertThat(UidKartu.normalisasi("\t1831574427\n")).isEqualTo("1831574427");
    }

    @Test
    @DisplayName("null & kosong tidak melempar")
    void nullDanKosong() {
        assertThat(UidKartu.normalisasi(null)).isNull();
        assertThat(UidKartu.normalisasi("")).isEmpty();
        assertThat(UidKartu.normalisasi("   ")).isEmpty();
    }
}
