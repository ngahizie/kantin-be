package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.dto.BarisPenjualan;
import com.asqi.scholia_kantin_be.dto.BarisPenjualanDimensi;
import com.asqi.scholia_kantin_be.dto.LaporanPerSiswa;
import com.asqi.scholia_kantin_be.dto.RingkasanPenjualan;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji penyamaran atribut finansial laporan penjualan (SEC-02, issue #388).
 *
 * <p>PRD §9.5: baris "Laba kotor" hanya untuk Bendahara (TU), Kepsek, dan
 * Pengelola — Admin Sekolah tidak termasuk.
 */
class LaporanMaskingTest {

    @Test
    @DisplayName("hanya Bendahara, Pengelola, dan Kepsek yang boleh melihat laba")
    void hakLihatLaba() {
        assertThat(LaporanMasking.bolehLihatLaba(AktorKantin.TU_SEKOLAH)).isTrue();
        assertThat(LaporanMasking.bolehLihatLaba(AktorKantin.PENGELOLA_KANTIN)).isTrue();
        assertThat(LaporanMasking.bolehLihatLaba(AktorKantin.KEPSEK)).isTrue();

        assertThat(LaporanMasking.bolehLihatLaba(AktorKantin.ADMIN_SEKOLAH)).isFalse();
        assertThat(LaporanMasking.bolehLihatLaba(AktorKantin.PETUGAS_KANTIN)).isFalse();
        assertThat(LaporanMasking.bolehLihatLaba(null)).isFalse();
    }

    @Test
    @DisplayName("ringkasan tanpa laba — angka penjualan tetap, modal/laba/margin null")
    void ringkasanDisamarkan() {
        RingkasanPenjualan asli = new RingkasanPenjualan(
                OffsetDateTime.parse("2026-11-01T00:00:00+07:00"),
                OffsetDateTime.parse("2026-11-02T00:00:00+07:00"),
                2, 29_000, 0, 0, 29_000, 14_000L, 15_000L, 51.72);

        RingkasanPenjualan samar = LaporanMasking.tanpaLaba(asli);

        assertThat(samar.penjualanBersih()).isEqualTo(29_000);
        assertThat(samar.jumlahTransaksi()).isEqualTo(2);
        assertThat(samar.totalModal()).isNull();
        assertThat(samar.laba()).isNull();
        assertThat(samar.marginPersen()).isNull();
        // Objek asli tidak dimutasi (record immutable).
        assertThat(asli.laba()).isEqualTo(15_000L);
    }

    @Test
    @DisplayName("baris per item/kategori tanpa laba — modal & margin null")
    void barisDisamarkan() {
        List<BarisPenjualan> asli = List.of(
                new BarisPenjualan(10L, "Nasi Uduk", 2, 16_000, 8_000L, 8_000L, 50.0),
                new BarisPenjualan(20L, "Es Jeruk", 1, 5_000, 5_000L, 2_000L, 40.0));

        List<BarisPenjualan> samar = LaporanMasking.tanpaLaba(asli);

        assertThat(samar).hasSize(2);
        assertThat(samar.get(0).total()).isEqualTo(16_000);
        assertThat(samar.get(0).modal()).isNull();
        assertThat(samar.get(0).marginPersen()).isNull();
        assertThat(samar.get(1).modal()).isNull();
    }

    @Test
    @DisplayName("baris dimensi (titik/petugas) tanpa laba — HPP & laba kotor null")
    void dimensiDisamarkan() {
        List<BarisPenjualanDimensi> asli = List.of(
                new BarisPenjualanDimensi(1L, "Kasir 1", 5, 21_000, 10_000L, 11_000L));

        List<BarisPenjualanDimensi> samar = LaporanMasking.tanpaLabaDimensi(asli);

        assertThat(samar.get(0).nilai()).isEqualTo(21_000);
        assertThat(samar.get(0).hpp()).isNull();
        assertThat(samar.get(0).labaKotor()).isNull();
    }

    @Test
    @DisplayName("#402: laporan per siswa tanpa HPP — angka lain utuh, objek asli tidak dimutasi")
    void perSiswaTanpaHpp() {
        var asli = new LaporanPerSiswa(SubjekTipe.SISWA, 9L, 50_000L, "2026-11-01",
                new LaporanPerSiswa.Ringkasan(2, 29_000, 1, 16_000, 50_000, 14_000L),
                List.of(new LaporanPerSiswa.BarisTransaksi(1L, StatusTransaksi.SUKSES, 21_000,
                        10_000L, 5L, 1L, null, OffsetDateTime.parse("2026-11-01T08:00:00Z"))),
                List.of());

        LaporanPerSiswa samar = LaporanMasking.tanpaHpp(asli);

        assertThat(samar.siswaId()).isEqualTo(9L);
        assertThat(samar.saldo()).isEqualTo(50_000L);
        assertThat(samar.ringkasan().nilaiBelanjaSukses()).isEqualTo(29_000);
        assertThat(samar.ringkasan().totalTopup()).isEqualTo(50_000);
        assertThat(samar.ringkasan().totalHpp()).isNull();
        assertThat(samar.transaksi().get(0).total()).isEqualTo(21_000);
        assertThat(samar.transaksi().get(0).totalHpp()).isNull();
        // Ringkasan/baris asli tetap memuat HPP (record immutable, salinan dibuat).
        assertThat(asli.ringkasan().totalHpp()).isEqualTo(14_000L);
        assertThat(asli.transaksi().get(0).totalHpp()).isEqualTo(10_000L);
    }
}
