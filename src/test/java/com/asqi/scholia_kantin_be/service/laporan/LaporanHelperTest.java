package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji validasi rentang tanggal laporan (SEC-03, issue #388) — batas bawah/atas
 * dan durasi maksimum, tanpa DB.
 */
class LaporanHelperTest {

    private final JamKantin jam = new JamKantin("Asia/Jakarta");

    @Test
    @DisplayName("rentang normal — 'dari'/'sampai' dipakai apa adanya")
    void rentangNormal() {
        OffsetDateTime dari = OffsetDateTime.parse("2026-11-01T00:00:00+07:00");
        OffsetDateTime sampai = OffsetDateTime.parse("2026-11-08T00:00:00+07:00");

        OffsetDateTime[] r = LaporanHelper.rentang(jam, null, dari, sampai);

        assertThat(r[0]).isEqualTo(dari);
        assertThat(r[1]).isEqualTo(sampai);
    }

    @Test
    @DisplayName("rentang terbalik atau nol hari ditolak 400")
    void rentangTerbalikDitolak() {
        OffsetDateTime dari = OffsetDateTime.parse("2026-11-08T00:00:00+07:00");
        OffsetDateTime sampai = OffsetDateTime.parse("2026-11-01T00:00:00+07:00");

        assertThatThrownBy(() -> LaporanHelper.rentang(jam, null, dari, sampai))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("'dari' harus sebelum 'sampai'");

        // Sampai == dari juga ditolak (periode kosong).
        assertThatThrownBy(() -> LaporanHelper.rentang(jam, null, dari, dari))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("rentang lebih dari 366 hari ditolak (cegah query tak terbatas)")
    void rentangTerlaluPanjangDitolak() {
        OffsetDateTime dari = OffsetDateTime.parse("2020-01-01T00:00:00+07:00");
        OffsetDateTime sampai = OffsetDateTime.parse("2026-01-01T00:00:00+07:00");

        assertThatThrownBy(() -> LaporanHelper.rentang(jam, null, dari, sampai))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("maksimal 366 hari");
    }

    @Test
    @DisplayName("tepat 366 hari masih diterima")
    void rentangBatasDiterima() {
        OffsetDateTime dari = OffsetDateTime.parse("2026-01-01T00:00:00+07:00");
        OffsetDateTime sampai = dari.plusDays(LaporanHelper.MAKS_HARI);

        OffsetDateTime[] r = LaporanHelper.rentang(jam, null, dari, sampai);

        assertThat(r[0]).isEqualTo(dari);
        assertThat(r[1]).isEqualTo(sampai);
    }

    @Test
    @DisplayName("default tanpa parameter = hari ini (zona sekolah)")
    void defaultHariIni() {
        LocalDate hariIni = jam.hariIni();

        OffsetDateTime[] r = LaporanHelper.rentang(jam, null, null, null);

        assertThat(r[0].toLocalDate()).isEqualTo(hariIni);
        assertThat(r[1]).isEqualTo(r[0].plusDays(1));
    }
}
