package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.BarisKerugianStok;
import com.asqi.scholia_kantin_be.dto.RingkasanKerugianStok;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.laporan.LaporanExportService;
import com.asqi.scholia_kantin_be.service.laporan.LaporanService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("LaporanController — Kerugian Stok & Ringkasan")
class LaporanControllerTest {

    private LaporanService laporanService;
    private LaporanExportService exportService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        laporanService = mock(LaporanService.class);
        exportService = mock(LaporanExportService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new LaporanController(laporanService, exportService))
                .build();
        TenantContext.set(IdentitasKantin.builder().userId("42").sekolahId(7L).build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void kerugianStokMengembalikanRincianLengkap() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        BarisKerugianStok baris = new BarisKerugianStok(
                1L,
                now,
                10L,
                "Nasi Goreng",
                "Makanan",
                "OPNAME_KELUAR",
                3,
                10_000L,
                30_000L,
                "Selisih fisik opname",
                "OPN-20261026-001",
                "Siti Aminah");

        when(laporanService.kerugianStok(eq(7L), any(), any(), any()))
                .thenReturn(List.of(baris));

        mockMvc.perform(get("/api/laporan/kerugian-stok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].namaMenu").value("Nasi Goreng"))
                .andExpect(jsonPath("$.data[0].kategoriNama").value("Makanan"))
                .andExpect(jsonPath("$.data[0].jenis").value("OPNAME_KELUAR"))
                .andExpect(jsonPath("$.data[0].qty").value(3))
                .andExpect(jsonPath("$.data[0].hppSnapshot").value(10000))
                .andExpect(jsonPath("$.data[0].totalNilai").value(30000))
                .andExpect(jsonPath("$.data[0].alasan").value("Selisih fisik opname"))
                .andExpect(jsonPath("$.data[0].beritaAcaraId").value("OPN-20261026-001"))
                .andExpect(jsonPath("$.data[0].petugas").value("Siti Aminah"));
    }

    @Test
    void ringkasanKerugianStokMengembalikanAgregat() throws Exception {
        RingkasanKerugianStok ringkasan = new RingkasanKerugianStok(
                30_000L, 3L, 1L, 30_000L, 3L, 0L, 0L);

        when(laporanService.ringkasanKerugianStok(eq(7L), any(), any(), any()))
                .thenReturn(ringkasan);

        mockMvc.perform(get("/api/laporan/kerugian-stok/ringkasan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalKerugian").value(30000))
                .andExpect(jsonPath("$.data.totalQtyHilang").value(3))
                .andExpect(jsonPath("$.data.jumlahKejadian").value(1))
                .andExpect(jsonPath("$.data.kerugianOpname").value(30000))
                .andExpect(jsonPath("$.data.qtyOpname").value(3))
                .andExpect(jsonPath("$.data.kerugianBarangRusak").value(0))
                .andExpect(jsonPath("$.data.qtyBarangRusak").value(0));
    }
}
