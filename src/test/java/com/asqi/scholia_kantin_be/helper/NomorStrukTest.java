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

    @Test
    @DisplayName("Instant yang sama dengan offset berbeda → nomor yang sama (tanggal WIB)")
    void offsetBerbedaNomorSama() {
        // 01:30 WIB tanggal 16 Nov = 18:30 UTC tanggal 15 Nov.
        OffsetDateTime wib = OffsetDateTime.of(2026, 11, 16, 1, 30, 0, 0, ZoneOffset.ofHours(7));
        OffsetDateTime utc = wib.withOffsetSameInstant(ZoneOffset.UTC);

        assertThat(NomorStruk.dari(7L, utc)).isEqualTo("TRX-20261116-7");
        assertThat(NomorStruk.dari(7L, wib)).isEqualTo("TRX-20261116-7");
    }

    @Test
    @DisplayName("Jatuh sebelum tengah malam WIB → tetap tanggal WIB, bukan tanggal UTC")
    void lewatTengahMalamWib() {
        // 23:59:59 UTC tanggal 15 Nov = 06:59:59 WIB tanggal 16 Nov.
        OffsetDateTime utc = OffsetDateTime.of(2026, 11, 15, 23, 59, 59, 0, ZoneOffset.UTC);
        assertThat(NomorStruk.dari(9L, utc)).isEqualTo("TRX-20261116-9");

        // 16:59:59 UTC tanggal 15 Nov = 23:59:59 WIB tanggal 15 Nov (masih hari yang sama).
        OffsetDateTime sebelum = OffsetDateTime.of(2026, 11, 15, 16, 59, 59, 0, ZoneOffset.UTC);
        assertThat(NomorStruk.dari(9L, sebelum)).isEqualTo("TRX-20261115-9");
    }
}
