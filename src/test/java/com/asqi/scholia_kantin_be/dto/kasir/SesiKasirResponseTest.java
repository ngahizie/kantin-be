package com.asqi.scholia_kantin_be.dto.kasir;

import com.asqi.scholia_kantin_be.enums.StatusSesiKasir;
import com.asqi.scholia_kantin_be.model.SesiKasir;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** Bukti bahwa proyeksi DTO mengawetkan seluruh field yang dulu dikirim entity. */
@DisplayName("SesiKasirResponse — proyeksi entity ke response")
class SesiKasirResponseTest {

    @Test
    @DisplayName("semua field entity tersalin ke DTO (kontrak JSON tidak berubah)")
    void dariMenyalinSemuaField() {
        OffsetDateTime buka = OffsetDateTime.of(2026, 11, 24, 7, 0, 0, 0, ZoneOffset.ofHours(7));
        OffsetDateTime tutup = buka.plusHours(7);
        SesiKasir sesi = SesiKasir.builder()
                .id(42L)
                .sekolahId(7L)
                .titikKasirId(3L)
                .tanggal(LocalDate.of(2026, 11, 24))
                .status(StatusSesiKasir.DITUTUP)
                .totalBruto(500_000L)
                .totalVoid(10_000L)
                .totalBersih(490_000L)
                .dibukaAt(buka)
                .dibukaOleh(11L)
                .ditutupAt(tutup)
                .ditutupOleh(12L)
                .uangFisik(485_000L)
                .selisih(-5_000L)
                .catatan("selisih kas")
                .autoTutup(false)
                .postingBukuKas(true)
                .referensiBukuKas("BK-42")
                .createdAt(buka)
                .updatedAt(tutup)
                .build();

        SesiKasirResponse hasil = SesiKasirResponse.dari(sesi);

        assertThat(hasil.id()).isEqualTo(42L);
        assertThat(hasil.sekolahId()).isEqualTo(7L);
        assertThat(hasil.titikKasirId()).isEqualTo(3L);
        assertThat(hasil.tanggal()).isEqualTo(LocalDate.of(2026, 11, 24));
        assertThat(hasil.status()).isEqualTo(StatusSesiKasir.DITUTUP);
        assertThat(hasil.totalBruto()).isEqualTo(500_000L);
        assertThat(hasil.totalVoid()).isEqualTo(10_000L);
        assertThat(hasil.totalBersih()).isEqualTo(490_000L);
        assertThat(hasil.dibukaAt()).isEqualTo(buka);
        assertThat(hasil.dibukaOleh()).isEqualTo(11L);
        assertThat(hasil.ditutupAt()).isEqualTo(tutup);
        assertThat(hasil.ditutupOleh()).isEqualTo(12L);
        assertThat(hasil.uangFisik()).isEqualTo(485_000L);
        assertThat(hasil.selisih()).isEqualTo(-5_000L);
        assertThat(hasil.catatan()).isEqualTo("selisih kas");
        assertThat(hasil.autoTutup()).isFalse();
        assertThat(hasil.postingBukuKas()).isTrue();
        assertThat(hasil.referensiBukuKas()).isEqualTo("BK-42");
        assertThat(hasil.createdAt()).isEqualTo(buka);
        assertThat(hasil.updatedAt()).isEqualTo(tutup);
    }
}
