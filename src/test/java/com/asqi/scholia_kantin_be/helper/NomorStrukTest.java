package com.asqi.scholia_kantin_be.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class NomorStrukTest {

    private static final OffsetDateTime WAKTU =
            OffsetDateTime.of(2026, 11, 9, 13, 51, 8, 0, ZoneOffset.ofHours(7));

    @Test
    @DisplayName("Nomor struk memakai tanggal transaksi dan id penuh")
    void formatLengkap() {
        assertThat(NomorStruk.dari(1794207068224823L, WAKTU))
                .isEqualTo("TRX-20261109-1794207068224823");
    }

    @Test
    @DisplayName("Tanpa id → null (tidak mencetak nomor palsu)")
    void tanpaId() {
        assertThat(NomorStruk.dari(null, WAKTU)).isNull();
    }

    @Test
    @DisplayName("Tanpa waktu → tanggal dikosongkan, id tetap")
    void tanpaWaktu() {
        assertThat(NomorStruk.dari(42L, null)).isEqualTo("TRX-42");
    }
}
