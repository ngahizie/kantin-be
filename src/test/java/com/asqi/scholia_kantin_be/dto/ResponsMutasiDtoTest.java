package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.dto.saldo.HasilMutasiSaldoResponse;
import com.asqi.scholia_kantin_be.dto.stok.HasilMutasiStokResponse;
import com.asqi.scholia_kantin_be.enums.ArahMutasi;
import com.asqi.scholia_kantin_be.enums.ArahStok;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;
import com.asqi.scholia_kantin_be.model.MutasiSaldo;
import com.asqi.scholia_kantin_be.model.RiwayatStok;
import com.asqi.scholia_kantin_be.service.kasir.HasilMutasiSaldo;
import com.asqi.scholia_kantin_be.service.stok.HasilMutasiStok;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bukti bahwa konversi hasil mutasi ke DTO tidak mengubah nilai yang dipakai
 * klien dan tidak membawa entity JPA ke payload.
 */
@DisplayName("DTO hasil mutasi — proyeksi tanpa entity")
class ResponsMutasiDtoTest {
    /** Waktu tetap supaya assert deterministik. */
    private static final OffsetDateTime WAKTU = OffsetDateTime.parse("2026-11-25T08:00:00Z");

    /**
     * Memeriksa proyeksi mutasi saldo ke respons API.
     */
    @Test
    @DisplayName("saldo: field ledger tersalin, entity tidak ikut")
    void saldo() {
        MutasiSaldo mutasi = MutasiSaldo.builder()
                .id(11L)
                .sekolahId(7L)
                .siswaId(100L)
                .arah(ArahMutasi.KREDIT)
                .jenis(JenisMutasiSaldo.TOPUP_TUNAI)
                .nominal(50_000L)
                .saldoSetelah(150_000L)
                .waktu(WAKTU)
                .build();

        HasilMutasiSaldoResponse respons =
                HasilMutasiSaldoResponse.dari(HasilMutasiSaldo.baru(mutasi, 150_000L));

        assertThat(respons.getMutasi().getId()).isEqualTo(11L);
        assertThat(respons.getMutasi().getSiswaId()).isEqualTo(100L);
        assertThat(respons.getSaldoSebelum()).isEqualTo(100_000L);
        assertThat(respons.getSaldoSetelah()).isEqualTo(150_000L);
        assertThat(respons.getWaktu()).isEqualTo(WAKTU);
        assertThat(respons.isIdempotentReplay()).isFalse();
    }

    /**
     * Memeriksa proyeksi mutasi stok ke respons API.
     */
    @Test
    @DisplayName("stok: field ledger tersalin, entity tidak ikut")
    void stok() {
        RiwayatStok mutasi = RiwayatStok.builder()
                .id(12L)
                .sekolahId(7L)
                .menuId(10L)
                .arah(ArahStok.MASUK)
                .jenis(JenisMutasiStok.BARANG_MASUK)
                .qty(20)
                .stokSetelah(20)
                .hppSnapshot(5_000L)
                .waktu(WAKTU)
                .build();

        HasilMutasiStokResponse respons =
                HasilMutasiStokResponse.dari(HasilMutasiStok.baru(mutasi, 20, 5_000L));

        assertThat(respons.getMutasi().getId()).isEqualTo(12L);
        assertThat(respons.getMutasi().getMenuId()).isEqualTo(10L);
        assertThat(respons.getMutasi().getQty()).isEqualTo(20);
        assertThat(respons.getStokSetelah()).isEqualTo(20);
        assertThat(respons.getHppSetelah()).isEqualTo(5_000L);
    }
}
