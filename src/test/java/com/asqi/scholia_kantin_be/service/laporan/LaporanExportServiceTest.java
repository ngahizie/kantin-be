package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.dto.laporan.RingkasanPenjualan;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.enums.JenisLaporan;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Isi dan label berkas ekspor Excel (issue #450).
 *
 * <p>Writer di-mock supaya test memeriksa kontrak isi berkas: metadata, header,
 * baris data, baris total, dan catatan kaki — bukan styling POI.
 */
class LaporanExportServiceTest {

    private final LaporanPenjualanService penjualan = mock(LaporanPenjualanService.class);
    private final LaporanSaldoService saldo = mock(LaporanSaldoService.class);
    private final LaporanStokService stok = mock(LaporanStokService.class);
    private final LaporanPembatalanService pembatalan = mock(LaporanPembatalanService.class);
    private final LaporanSiswaService siswa = mock(LaporanSiswaService.class);
    private final LaporanSetoranService setoran = mock(LaporanSetoranService.class);
    private final ExcelWriter excel = mock(ExcelWriter.class);
    private final JamKantin jam = mock(JamKantin.class);

    private LaporanExportService service;

    @BeforeEach
    void siap() {
        service = new LaporanExportService(penjualan, saldo, stok, pembatalan, siswa,
                setoran, excel, jam, null);
        when(jam.hariIni()).thenReturn(LocalDate.of(2026, 11, 27));
        when(jam.sekarang()).thenReturn(OffsetDateTime.parse("2026-11-27T10:15:00+07:00"));
    }

    @AfterEach
    void bersih() {
        TenantContext.clear();
    }

    private void masuk(AktorKantin peran) {
        TenantContext.set(IdentitasKantin.builder()
                .userId("reviewer").sekolahId(7L).peran(peran).build());
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<String>> tangkapMetadata() {
        return ArgumentCaptor.forClass(List.class);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<String>> tangkapHeader() {
        return ArgumentCaptor.forClass(List.class);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<List<Object>>> tangkapBaris() {
        return ArgumentCaptor.forClass(List.class);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<Object>> tangkapTotal() {
        return ArgumentCaptor.forClass(List.class);
    }

    @Test
    @DisplayName("#450 (revisi): ekspor PENJUALAN memakai label FE, metadata dua baris, baris total dua sel, dan catatan kaki")
    void eksporPenjualanLengkap() {
        masuk(AktorKantin.TU_SEKOLAH);
        when(penjualan.ringkasanPenjualan(eq(7L), isNull(), isNull(), isNull()))
                .thenReturn(new RingkasanPenjualan(null, null, 10, 100_000, 1, 5_000,
                        95_000, 40_000L, 55_000L, 57.89));

        service.ekspor(7L, JenisLaporan.PENJUALAN, null, null, null);

        var metadata = tangkapMetadata();
        var header = tangkapHeader();
        var baris = tangkapBaris();
        var total = tangkapTotal();
        var catatan = ArgumentCaptor.forClass(String.class);
        verify(excel).tulis(eq("Penjualan & Keuntungan"), eq("Penjualan & Keuntungan"),
                anyString(), anyString(), metadata.capture(), header.capture(),
                baris.capture(), total.capture(), catatan.capture());

        assertThat(metadata.getValue()).containsExactly(
                "Tanggal Cetak: 2026-11-27 10:15",
                "Periode Laporan: 27-11-2026");
        assertThat(header.getValue()).containsExactly("Metrik", "Nilai");
        assertThat(baris.getValue().toString())
                .contains("Total Penjualan (Sebelum Batal/Refund)")
                .contains("Total Penjualan Akhir")
                .contains("Keuntungan")
                .contains("Persen Untung (%)")
                .doesNotContain("Penjualan bruto", "Penjualan bersih", "Laba", "Margin (%)");
        // Revisi #450: header PENJUALAN dua kolom, jadi baris total hanya label + nilai akhir.
        assertThat(total.getValue()).containsExactly("Total Penjualan Akhir", 95_000L);
        assertThat(catatan.getValue())
                .isEqualTo("Angka keuntungan hanya ditampilkan untuk peran yang berhak.");
    }

    @Test
    @DisplayName("#450 (revisi): ADMIN_SEKOLAH menerima nilai modal dan keuntungan kosong; baris total tetap dua sel")
    void eksporPenjualanDisamarkan() {
        masuk(AktorKantin.ADMIN_SEKOLAH);
        when(penjualan.ringkasanPenjualan(eq(7L), isNull(), isNull(), isNull()))
                .thenReturn(new RingkasanPenjualan(null, null, 10, 100_000, 1, 5_000,
                        95_000, 40_000L, 55_000L, 57.89));

        service.ekspor(7L, JenisLaporan.PENJUALAN, null, null, null);

        var baris = tangkapBaris();
        var total = tangkapTotal();
        verify(excel).tulis(anyString(), anyString(), anyString(), anyString(),
                tangkapMetadata().capture(), tangkapHeader().capture(), baris.capture(),
                total.capture(), org.mockito.ArgumentMatchers.nullable(String.class));

        assertThat(baris.getValue()).anySatisfy(row -> {
            assertThat(row.get(0)).isEqualTo("Total modal");
            assertThat(row.get(1)).isNull();
        });
        assertThat(baris.getValue()).anySatisfy(row -> {
            assertThat(row.get(0)).isEqualTo("Keuntungan");
            assertThat(row.get(1)).isNull();
        });
        assertThat(total.getValue()).containsExactly("Total Penjualan Akhir", 95_000L);
    }

    @Test
    @DisplayName("#450: header STOK dan PEMBATALAN memakai label baru")
    void headerStokDanPembatalanMemakaiLabelBaru() {
        masuk(AktorKantin.TU_SEKOLAH);
        when(stok.laporanStok(eq(7L), eq(false), eq(0), eq(com.asqi.scholia_kantin_be.helper.UkuranHalaman.UKURAN_MAKS)))
                .thenReturn(Page.empty());
        when(pembatalan.pembatalanKasir(eq(7L), isNull(), isNull(), isNull(), any()))
                .thenReturn(List.of());

        service.ekspor(7L, JenisLaporan.STOK, null, null, null);
        service.ekspor(7L, JenisLaporan.PEMBATALAN, null, null, null);

        var header = tangkapHeader();
        verify(excel, org.mockito.Mockito.times(2)).tulis(anyString(), anyString(), anyString(),
                anyString(), tangkapMetadata().capture(), header.capture(),
                org.mockito.ArgumentMatchers.<List<List<Object>>>any(),
                org.mockito.ArgumentMatchers.nullable(List.class),
                org.mockito.ArgumentMatchers.nullable(String.class));

        assertThat(header.getAllValues().get(0)).contains("Harga Modal", "Total Nilai Stok")
                .doesNotContain("HPP", "Nilai Persediaan");
        assertThat(header.getAllValues().get(1)).contains("Alasan Batal/Refund")
                .doesNotContain("Alasan Void");
    }
}
