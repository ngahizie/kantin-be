package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.BarisPenjualan;
import com.asqi.scholia_kantin_be.dto.BarisPenjualanDimensi;
import com.asqi.scholia_kantin_be.dto.RingkasanPenjualan;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.laporan.LaporanExportService;
import com.asqi.scholia_kantin_be.service.laporan.LaporanPembatalanService;
import com.asqi.scholia_kantin_be.service.laporan.LaporanPenjualanService;
import com.asqi.scholia_kantin_be.service.laporan.LaporanSaldoService;
import com.asqi.scholia_kantin_be.service.laporan.LaporanSetoranService;
import com.asqi.scholia_kantin_be.service.laporan.LaporanSiswaService;
import com.asqi.scholia_kantin_be.service.laporan.LaporanStokService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uji penyamaran atribut finansial laporan penjualan per peran (SEC-02, issue #388).
 *
 * <p>PRD §9.5: Admin Sekolah boleh membaca laporan penjualan, tetapi bukan baris
 * "Laba kotor" — jadi {@code modal}/{@code laba}/{@code margin} dijawab {@code null}.
 */
@DisplayName("LaporanController — masking laba per peran (SEC-02)")
class LaporanMaskingControllerTest {

    private LaporanPenjualanService penjualan;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        penjualan = mock(LaporanPenjualanService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new LaporanController(
                        penjualan,
                        mock(LaporanSaldoService.class),
                        mock(LaporanStokService.class),
                        mock(LaporanPembatalanService.class),
                        mock(LaporanSiswaService.class),
                        mock(LaporanSetoranService.class),
                        mock(LaporanExportService.class)))
                .build();

        when(penjualan.ringkasanPenjualan(any(), any(), any(), any())).thenReturn(new RingkasanPenjualan(
                OffsetDateTime.parse("2026-11-01T00:00:00+07:00"),
                OffsetDateTime.parse("2026-11-02T00:00:00+07:00"),
                2, 29_000, 0, 0, 29_000, 14_000L, 15_000L, 51.72));
        when(penjualan.penjualanPerItem(any(), any(), any(), any())).thenReturn(List.of(
                new BarisPenjualan(10L, "Nasi Uduk", 2, 16_000, 8_000L, 8_000L, 50.0)));
        when(penjualan.penjualanPerKategori(any(), any(), any(), any())).thenReturn(List.of(
                new BarisPenjualan(100L, "Makanan", 2, 16_000, null, 8_000L, 50.0)));
        when(penjualan.penjualanPerTitik(any(), any(), any(), any())).thenReturn(List.of(
                new BarisPenjualanDimensi(1L, "Kasir 1", 5, 21_000, 10_000L, 11_000L)));
        when(penjualan.penjualanPerPetugas(any(), any(), any(), any())).thenReturn(List.of(
                new BarisPenjualanDimensi(5L, "Petugas A", 5, 21_000, 10_000L, 11_000L)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void masukSebagai(AktorKantin peran) {
        TenantContext.set(IdentitasKantin.builder()
                .userId("77").sekolahId(7L).peran(peran).build());
    }

    @Test
    @DisplayName("Admin Sekolah: angka penjualan tampil, modal/laba/margin null")
    void adminTidakMelihatLaba() throws Exception {
        masukSebagai(AktorKantin.ADMIN_SEKOLAH);

        mockMvc.perform(get("/api/laporan/penjualan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.penjualanBersih").value(29_000))
                .andExpect(jsonPath("$.data.totalModal").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.laba").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.marginPersen").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/api/laporan/penjualan/item"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].total").value(16_000))
                .andExpect(jsonPath("$.data[0].modal").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data[0].marginPersen").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/api/laporan/penjualan/kategori"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].modal").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/api/laporan/penjualan/titik"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nilai").value(21_000))
                .andExpect(jsonPath("$.data[0].hpp").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data[0].labaKotor").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/api/laporan/penjualan/petugas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].hpp").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data[0].labaKotor").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("Pengelola: modal/laba/margin tetap tampil")
    void pengelolaMelihatLaba() throws Exception {
        masukSebagai(AktorKantin.PENGELOLA_KANTIN);

        mockMvc.perform(get("/api/laporan/penjualan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalModal").value(14_000))
                .andExpect(jsonPath("$.data.laba").value(15_000))
                .andExpect(jsonPath("$.data.marginPersen").value(51.72));

        mockMvc.perform(get("/api/laporan/penjualan/item"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].modal").value(8_000))
                .andExpect(jsonPath("$.data[0].marginPersen").value(50.0));

        mockMvc.perform(get("/api/laporan/penjualan/titik"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].hpp").value(10_000))
                .andExpect(jsonPath("$.data[0].labaKotor").value(11_000));
    }
}
