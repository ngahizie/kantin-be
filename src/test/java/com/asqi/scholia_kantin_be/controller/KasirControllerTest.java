package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.CekKartuResponse;
import com.asqi.scholia_kantin_be.dto.HalamanResponse;
import com.asqi.scholia_kantin_be.dto.TapRequest;
import com.asqi.scholia_kantin_be.dto.TapResponse;
import com.asqi.scholia_kantin_be.dto.TransaksiKasirItem;
import com.asqi.scholia_kantin_be.dto.TutupSesiRequest;
import com.asqi.scholia_kantin_be.dto.VoidRequest;
import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.SesiKasir;
import com.asqi.scholia_kantin_be.model.Transaksi;
import com.asqi.scholia_kantin_be.model.TransaksiItem;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.integrasi.BukuKasPostingService;
import com.asqi.scholia_kantin_be.service.kasir.CekKartuService;
import com.asqi.scholia_kantin_be.service.kasir.SesiKasirService;
import com.asqi.scholia_kantin_be.service.kasir.TapService;
import com.asqi.scholia_kantin_be.service.kasir.TransaksiKasirService;
import com.asqi.scholia_kantin_be.service.kasir.VoidService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uji wiring {@link KasirController} (temuan: endpoint tap sebelumnya stub).
 *
 * <p>Memakai MockMvc standalone + service tiruan (tanpa Spring context / DB).
 * Karena standalone tidak punya {@code SecurityContextArgumentResolver}, kita
 * daftarkan resolver sederhana yang mengembalikan identitas dari konten
 * {@link TenantContext} — meniru perilaku produksi.
 */
@DisplayName("KasirController — wiring tap/void")
class KasirControllerTest {

    private TapService tapService;
    private CekKartuService cekKartuService;
    private VoidService voidService;
    private SesiKasirService sesiKasirService;
    private BukuKasPostingService bukuKasPosting;
    private TransaksiKasirService transaksiKasirService;
    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Resolver tiruan untuk @AuthenticationPrincipal: ambil dari TenantContext. */
    private static class PrincipalResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return TenantContext.get();
        }
    }

    @BeforeEach
    void setUp() {
        tapService = mock(TapService.class);
        cekKartuService = mock(CekKartuService.class);
        voidService = mock(VoidService.class);
        sesiKasirService = mock(SesiKasirService.class);
        bukuKasPosting = mock(BukuKasPostingService.class);
        transaksiKasirService = mock(TransaksiKasirService.class);

        KasirController controller = new KasirController(
                tapService, cekKartuService, voidService, sesiKasirService, bukuKasPosting,
                transaksiKasirService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PrincipalResolver())
                .build();

        // TenantContext diisi seperti yang dilakukan JwtAuthTokenFilter.
        TenantContext.set(IdentitasKantin.builder()
                .userId("42")
                .sekolahId(7L)
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void tapMemanggilServiceDenganTenantDariKonteks() throws Exception {
        TapResponse hasil = TapResponse.builder()
                .transaksiId(1001L)
                .nama("Budi")
                .total(15000L)
                .saldoSisa(35000L)
                .namaItem(List.of("Nasi Goreng"))
                .build();
        when(tapService.tap(eq(7L), any(IdentitasKantin.class), any(TapRequest.class)))
                .thenReturn(hasil);

        TapRequest req = new TapRequest();
        req.setRfidUid("A1B2C3D4");
        req.setTitikKasirId(1L);
        req.setIdempotencyKey("tap-001");
        TapRequest.ItemTap item = new TapRequest.ItemTap();
        item.setMenuId(10L);
        item.setQty(2);
        req.setItems(List.of(item));

        mockMvc.perform(post("/api/kasir/tap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transaksiId").value(1001))
                .andExpect(jsonPath("$.data.saldoSisa").value(35000));

        // Tenant WAJIB dari TenantContext (7), bukan dari body.
        verify(tapService).tap(eq(7L), any(IdentitasKantin.class), any(TapRequest.class));
    }

    @Test
    @DisplayName("cek kartu: identitas + saldo, tanpa memotong saldo")
    void cekKartuMengembalikanIdentitas() throws Exception {
        when(cekKartuService.cek(eq(7L), eq("04AA000201"))).thenReturn(new CekKartuResponse(
                "04AA000201", SubjekTipe.SISWA, 201L, "Rangga Pratama", "7A", null,
                46_000L, 0L, null, null, false, true, null));

        mockMvc.perform(get("/api/kasir/kartu/04AA000201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nama").value("Rangga Pratama"))
                .andExpect(jsonPath("$.data.kelas").value("7A"))
                .andExpect(jsonPath("$.data.saldo").value(46000))
                .andExpect(jsonPath("$.data.bisaBelanja").value(true));

        verify(cekKartuService).cek(eq(7L), eq("04AA000201"));
    }

    @Test
    void refundMeneruskanAktorIdDariToken() throws Exception {
        VoidRequest req = new VoidRequest();
        req.setAlasan("salah input");
        req.setKodeStruk("TRX-20261109-55");

        mockMvc.perform(post("/api/kasir/transaksi/55/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // aktorId = 42 (dari token), sekolahId = 7 (tenant), kode struk ikut diteruskan.
        verify(voidService).refundTransaksi(
                eq(7L), eq(55L), eq(42L), eq("salah input"), eq("TRX-20261109-55"));
    }

    @Test
    @DisplayName("refund: respons berupa DTO — siklus items↔transaksi tidak boleh masuk JSON")
    void refundMengembalikanDtoTanpaSiklus() throws Exception {
        VoidRequest req = new VoidRequest();
        req.setAlasan("salah input");
        req.setKodeStruk("TRX-20261109-55");

        // Entity dengan relasi dua arah: item menunjuk balik ke transaksinya.
        // Sebelum diperbaiki, Jackson menelusuri siklus ini tanpa henti dan
        // berhenti di "Document nesting depth (501)" -> klien menerima 500
        // padahal refund sudah terjadi di server.
        Transaksi trx = Transaksi.builder()
                .id(55L)
                .waktu(OffsetDateTime.parse("2026-11-09T10:15:00+07:00"))
                .siswaId(201L)
                .kartuUid("04AA000201")
                .total(15000L)
                .status(StatusTransaksi.VOID)
                .alasanVoid("salah input")
                .voidOleh(42L)
                .build();
        trx.tambahItem(TransaksiItem.builder()
                .id(551L)
                .transaksi(trx)
                .menuId(10L)
                .namaMenu("Nasi Goreng")
                .kategoriId(100L)
                .hargaJual(15000L)
                .qty(1)
                .hppSnapshot(9000L)
                .subtotal(15000L)
                .build());

        when(voidService.refundTransaksi(eq(7L), eq(55L), eq(42L), eq("salah input"),
                eq("TRX-20261109-55"))).thenReturn(trx);

        mockMvc.perform(post("/api/kasir/transaksi/55/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Transaksi berhasil di-refund"))
                .andExpect(jsonPath("$.data.id").value(55))
                .andExpect(jsonPath("$.data.nomorStruk").value("TRX-20261109-55"))
                .andExpect(jsonPath("$.data.status").value("VOID"))
                .andExpect(jsonPath("$.data.total").value(15000))
                .andExpect(jsonPath("$.data.subjekTipe").value("SISWA"))
                .andExpect(jsonPath("$.data.voidAlasan").value("salah input"))
                // Entity tidak boleh ikut terserialisasi.
                .andExpect(jsonPath("$.data.items").doesNotExist())
                .andExpect(jsonPath("$.data.transaksi").doesNotExist());
    }

    @Test
    @DisplayName("koreksi bendahara: respons juga memakai DTO (tanpa siklus entity)")
    void koreksiMengembalikanDto() throws Exception {
        VoidRequest req = new VoidRequest();
        req.setAlasan("salah input");
        req.setKodeStruk("TRX-20261109-55");

        Transaksi trx = Transaksi.builder()
                .id(55L)
                .waktu(OffsetDateTime.parse("2026-11-09T10:15:00+07:00"))
                .siswaId(9L)
                .total(7000L)
                .status(StatusTransaksi.VOID)
                .alasanVoid("salah input")
                .voidOleh(42L)
                .build();
        trx.tambahItem(TransaksiItem.builder()
                .id(552L)
                .transaksi(trx)
                .menuId(11L)
                .namaMenu("Es Teh Manis")
                .hargaJual(7000L)
                .qty(1)
                .hppSnapshot(2000L)
                .subtotal(7000L)
                .build());

        when(voidService.koreksiTransaksiSesiTertutup(eq(7L), eq(55L), eq(42L), eq("salah input")))
                .thenReturn(trx);

        mockMvc.perform(post("/api/kasir/transaksi/55/koreksi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Transaksi sesi tertutup berhasil dikoreksi"))
                .andExpect(jsonPath("$.data.nomorStruk").value("TRX-20261109-55"))
                .andExpect(jsonPath("$.data.subjekTipe").value("SISWA"))
                .andExpect(jsonPath("$.data.items").doesNotExist());
    }

    @Test
    @DisplayName("endpoint lama /void tetap jalan (kompatibilitas) tapi wajib kode struk")
    void endpointLamaVoidMasihJalan() throws Exception {
        VoidRequest req = new VoidRequest();
        req.setAlasan("salah input");
        req.setKodeStruk("TRX-20261109-55");

        mockMvc.perform(post("/api/kasir/transaksi/55/void")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(voidService).refundTransaksi(
                eq(7L), eq(55L), eq(42L), eq("salah input"), eq("TRX-20261109-55"));
    }

    @Test
    void refundTanpaAlasanDitolakValidasi() throws Exception {
        VoidRequest req = new VoidRequest(); // alasan & kode struk null
        req.setKodeStruk("TRX-20261109-55");

        mockMvc.perform(post("/api/kasir/transaksi/55/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("refund tanpa kode struk ditolak validasi (PRD §6.3)")
    void refundTanpaKodeStrukDitolakValidasi() throws Exception {
        VoidRequest req = new VoidRequest();
        req.setAlasan("salah input"); // kode struk null

        mockMvc.perform(post("/api/kasir/transaksi/55/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("daftar transaksi (#212): tenant dari konteks + filter diteruskan ke service")
    void daftarTransaksiMeneruskanFilter() throws Exception {
        when(transaksiKasirService.daftar(eq(7L), eq(3L), eq(LocalDate.parse("2026-10-16")),
                eq(StatusTransaksi.VOID), eq(0), eq(20)))
                .thenReturn(HalamanResponse.<TransaksiKasirItem>builder()
                        .items(List.of())
                        .total(0L)
                        .halaman(0)
                        .ukuran(20)
                        .totalHalaman(0)
                        .build());

        mockMvc.perform(get("/api/kasir/transaksi")
                        .param("sesiId", "3")
                        .param("tanggal", "2026-10-16")
                        .param("status", "VOID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        // Tenant WAJIB dari TenantContext (7), bukan dari parameter klien.
        verify(transaksiKasirService).daftar(eq(7L), eq(3L), eq(LocalDate.parse("2026-10-16")),
                eq(StatusTransaksi.VOID), eq(0), eq(20));
    }

    // ────────────────────────────────────────────────────────────────
    // GET sesi/aktif (issue #222)
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sesi aktif (#222): 200 + data sesi; tenant dari konteks, bukan klien")
    void sesiAktifMeneruskanTenantDariKonteks() throws Exception {
        when(sesiKasirService.ambilSesiAktif(eq(7L), eq(1L)))
                .thenReturn(new com.asqi.scholia_kantin_be.dto.SesiKasirAktifData(
                        900L, 7L, 1L, "Kasir 1", LocalDate.parse("2026-10-21"),
                        com.asqi.scholia_kantin_be.enums.StatusSesiKasir.TERBUKA,
                        16_000L, 8_000L, 8_000L, 1L, null, null, null, null, false));

        mockMvc.perform(get("/api/kasir/sesi/aktif").param("titikKasirId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(900))
                .andExpect(jsonPath("$.data.status").value("TERBUKA"))
                .andExpect(jsonPath("$.data.titikKasirNama").value("Kasir 1"))
                .andExpect(jsonPath("$.data.totalBersih").value(8000));

        // Tenant WAJIB dari TenantContext (7), bukan dari parameter klien.
        verify(sesiKasirService).ambilSesiAktif(eq(7L), eq(1L));
    }

    /**
     * Perilaku kosong yang disepakati (#222 poin 3): belum ada sesi terbuka →
     * HTTP 200 + {@code data: null} (bukan 404), persis yang diharapkan FE
     * sebelum lanjut memanggil {@code sesi/buka}.
     */
    @Test
    @DisplayName("sesi aktif (#222): tanpa sesi terbuka → 200 + data null")
    void sesiAktifTanpaSesiDiberiNull() throws Exception {
        when(sesiKasirService.ambilSesiAktif(eq(7L), eq(1L))).thenReturn(null);

        mockMvc.perform(get("/api/kasir/sesi/aktif").param("titikKasirId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.message").value("Tidak ada sesi kasir terbuka"));

        verify(sesiKasirService).ambilSesiAktif(eq(7L), eq(1L));
    }

    // ────────────────────────────────────────────────────────────────
    // POST sesi/buka (issue #345)
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sesi buka (#345): 200 + titikKasirNama, petugas pembuka diteruskan ke service")
    void bukaSesiMengembalikanNamaTitikDanMencatatPembuka() throws Exception {
        when(sesiKasirService.ambilSesiAktif(eq(7L), eq(1L)))
                .thenReturn(new com.asqi.scholia_kantin_be.dto.SesiKasirAktifData(
                        900L, 7L, 1L, "Kasir Kelas 7", LocalDate.parse("2026-11-16"),
                        com.asqi.scholia_kantin_be.enums.StatusSesiKasir.TERBUKA,
                        0L, 0L, 0L, 0L, OffsetDateTime.parse("2026-11-16T07:20:00+07:00"),
                        null, 42L, null, false));

        mockMvc.perform(post("/api/kasir/sesi/buka")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titikKasirId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.titikKasirNama").value("Kasir Kelas 7"))
                .andExpect(jsonPath("$.data.dibukaOleh").value(42))
                .andExpect(jsonPath("$.message").value("Sesi kasir dibuka"));

        // Aktor dari token (userId "42"), tenant dari TenantContext (7) — bukan dari body.
        verify(sesiKasirService).bukaSesi(eq(7L), eq(1L), eq(42L));
    }

    /**
     * {@code titikKasirId} wajib di level wiring Spring (standalone MockMvc
     * menolak param hilang dengan 400). Catatan: di aplikasi penuh,
     * {@code GlobalExceptionHandler.handleMissingParam} mengubah ini menjadi
     * HTTP 200 + pesan (warisan admin-be, lihat komentar CommonResponse) —
     * tes ini hanya membuktikan param wajib, bukan bentuk HTTP produksinya.
     */
    @Test
    @DisplayName("sesi aktif (#222): tanpa titikKasirId ditolak (params wajib)")
    void sesiAktifTanpaParamDitolak() throws Exception {
        mockMvc.perform(get("/api/kasir/sesi/aktif"))
                .andExpect(status().isBadRequest());
    }

    // ────────────────────────────────────────────────────────────────
    // POST sesi/{id}/tutup — hitungan kas fisik (C.3)
    // ────────────────────────────────────────────────────────────────

    /**
     * Body tutup sesi diteruskan apa adanya ke service: {@code uangFisik} dan
     * {@code catatan} dari kasir, tanpa tenant dari body (PRD §11.4).
     */
    @Test
    @DisplayName("tutup sesi (C.3): uang fisik & catatan diteruskan ke service")
    void tutupSesiMeneruskanKasFisik() throws Exception {
        SesiKasir hasil = SesiKasir.builder()
                .id(42L)
                .sekolahId(7L)
                .uangFisik(15_000L)
                .selisih(-1_000L)
                .catatan("kurang 1.000")
                .build();
        when(sesiKasirService.tutupSesi(eq(7L), eq(42L), eq(42L), eq(false),
                any(TutupSesiRequest.class))).thenReturn(hasil);

        mockMvc.perform(post("/api/kasir/sesi/42/tutup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uangFisik\":15000,\"catatan\":\"kurang 1.000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uangFisik").value(15_000))
                .andExpect(jsonPath("$.data.selisih").value(-1_000))
                .andExpect(jsonPath("$.data.catatan").value("kurang 1.000"));

        ArgumentCaptor<TutupSesiRequest> kiriman = ArgumentCaptor.forClass(TutupSesiRequest.class);
        verify(sesiKasirService).tutupSesi(eq(7L), eq(42L), eq(42L), eq(false), kiriman.capture());
        assertThat(kiriman.getValue().getUangFisik()).isEqualTo(15_000L);
        assertThat(kiriman.getValue().getCatatan()).isEqualTo("kurang 1.000");
    }

    /** {@code uangFisik} wajib: body kosong ditolak Bean Validation (400). */
    @Test
    @DisplayName("tutup sesi (C.3): tanpa uangFisik ditolak 400")
    void tutupSesiTanpaUangFisikDitolak() throws Exception {
        mockMvc.perform(post("/api/kasir/sesi/42/tutup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"catatan\":\"tanpa kas\"}"))
                .andExpect(status().isBadRequest());
    }

    /** Uang fisik negatif tidak masuk akal untuk hitungan laci → 400. */
    @Test
    @DisplayName("tutup sesi (C.3): uangFisik negatif ditolak 400")
    void tutupSesiUangFisikNegatifDitolak() throws Exception {
        mockMvc.perform(post("/api/kasir/sesi/42/tutup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uangFisik\":-1}"))
                .andExpect(status().isBadRequest());
    }

    /** Detail sesi memakai DTO, bukan entity — field lama tetap terbaca klien. */
    @Test
    @DisplayName("detail sesi: response DTO mengawetkan field entity")
    void ambilSesiMengembalikanDto() throws Exception {
        SesiKasir sesi = SesiKasir.builder()
                .id(42L)
                .sekolahId(7L)
                .uangFisik(150_000L)
                .postingBukuKas(true)
                .referensiBukuKas("BK-42")
                .build();
        when(sesiKasirService.ambil(7L, 42L)).thenReturn(sesi);

        mockMvc.perform(get("/api/kasir/sesi/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.sekolahId").value(7))
                .andExpect(jsonPath("$.data.uangFisik").value(150_000))
                .andExpect(jsonPath("$.data.postingBukuKas").value(true))
                .andExpect(jsonPath("$.data.referensiBukuKas").value("BK-42"));
    }
}
