package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.component.exception.ForbiddenException;
import com.asqi.scholia_kantin_be.dto.BarisKerugianStok;
import com.asqi.scholia_kantin_be.dto.BarisStok;
import com.asqi.scholia_kantin_be.dto.RingkasanKerugianStok;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.enums.JenisLaporan;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("LaporanController — Kerugian Stok & Ringkasan")
class LaporanControllerTest {

    private LaporanStokService laporanStokService;
    private LaporanExportService exportService;
    private LaporanController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        laporanStokService = mock(LaporanStokService.class);
        exportService = mock(LaporanExportService.class);
        controller = new LaporanController(
                mock(LaporanPenjualanService.class),
                mock(LaporanSaldoService.class),
                laporanStokService,
                mock(LaporanPembatalanService.class),
                mock(LaporanSiswaService.class),
                mock(LaporanSetoranService.class),
                exportService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
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

        when(laporanStokService.kerugianStok(eq(7L), any(), any(), any(), eq(0), eq(200)))
                .thenReturn(new PageImpl<>(List.of(baris), PageRequest.of(0, 200), 1));

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
    @DisplayName("SEC-03 (#391): kerugian stok berhalaman — data tetap array + metadata halaman")
    void kerugianStokBerhalamanMengisiMetadata() throws Exception {
        BarisKerugianStok baris = new BarisKerugianStok(5L, OffsetDateTime.now(), 10L,
                "Nasi Goreng", "Makanan", "BARANG_RUSAK", 2, 5_000L, 10_000L,
                "rusak/basi", "OP-9", "Budi");

        when(laporanStokService.kerugianStok(eq(7L), any(), any(), any(), eq(1), eq(2)))
                .thenReturn(new PageImpl<>(List.of(baris), PageRequest.of(1, 2), 5));

        mockMvc.perform(get("/api/laporan/kerugian-stok")
                        .param("halaman", "1")
                        .param("ukuran", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(5))
                .andExpect(jsonPath("$.data[0].jenis").value("BARANG_RUSAK"))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.currentPage").value(1));
    }

    @Test
    @DisplayName("SEC-04 (#391): Admin Sekolah ditolak mengekspor KERUGIAN_STOK (sama seperti API data)")
    void eksporKerugianStokDitolakUntukAdminSekolah() {
        TenantContext.set(IdentitasKantin.builder()
                .userId("42").sekolahId(7L).peran(AktorKantin.ADMIN_SEKOLAH).build());

        assertThatThrownBy(() -> controller.ekspor(JenisLaporan.KERUGIAN_STOK, null, null,
                null, null, null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("KERUGIAN_STOK");
        verifyNoInteractions(exportService);
    }

    @Test
    @DisplayName("SEC-04 (#391): Admin Sekolah tetap boleh mengekspor PENJUALAN (PRD §9.5)")
    void eksporPenjualanTetapBolehUntukAdminSekolah() {
        TenantContext.set(IdentitasKantin.builder()
                .userId("42").sekolahId(7L).peran(AktorKantin.ADMIN_SEKOLAH).build());
        when(exportService.ekspor(eq(7L), eq(JenisLaporan.PENJUALAN), any(), any(), any(),
                any(), any()))
                .thenReturn(new LaporanExportService.HasilEkspor("penjualan.xlsx", new byte[]{1}));

        assertThatNoException().isThrownBy(() -> controller.ekspor(JenisLaporan.PENJUALAN,
                null, null, null, null, null));
    }

    @Test
    @DisplayName("SEC-04 (#391): TU/Bendahara tetap boleh mengekspor KERUGIAN_STOK")
    void eksporKerugianStokTetapBolehUntukBendahara() {
        TenantContext.set(IdentitasKantin.builder()
                .userId("42").sekolahId(7L).peran(AktorKantin.TU_SEKOLAH).build());
        when(exportService.ekspor(eq(7L), eq(JenisLaporan.KERUGIAN_STOK), any(), any(), any(),
                any(), any()))
                .thenReturn(new LaporanExportService.HasilEkspor("kerugian.xlsx", new byte[]{1}));

        assertThatNoException().isThrownBy(() -> controller.ekspor(JenisLaporan.KERUGIAN_STOK,
                null, null, null, null, null));
    }

    @Test
    void ringkasanKerugianStokMengembalikanAgregat() throws Exception {
        RingkasanKerugianStok ringkasan = new RingkasanKerugianStok(
                30_000L, 3L, 1L, 30_000L, 3L, 0L, 0L);

        when(laporanStokService.ringkasanKerugianStok(eq(7L), any(), any(), any()))
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

    @Test
    @DisplayName("#375: laporan stok berhalaman — data tetap array + metadata halaman")
    void laporanStokBerhalamanMengisiMetadata() throws Exception {
        BarisStok baris = new BarisStok(10L, "Nasi Uduk", 1L, 10, 2, 4_000L, 40_000L,
                false, "Nasi Uduk", 10, null);
        var halaman = new PageImpl<>(List.of(baris), PageRequest.of(0, 2), 3);

        when(laporanStokService.laporanStok(eq(7L), eq(false), eq(0), eq(2)))
                .thenReturn(halaman);

        mockMvc.perform(get("/api/laporan/stok").param("halaman", "0").param("ukuran", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].menuId").value(10))
                .andExpect(jsonPath("$.data[0].namaMenu").value("Nasi Uduk"))
                .andExpect(jsonPath("$.data[0].stokBerjalan").value(10))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.showing").value(1))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.currentPage").value(0));
    }
}
