package com.asqi.scholia_kantin_be.controller.konfigurasi;

import com.asqi.scholia_kantin_be.dto.konfigurasi.KejadianOfflineResponse;
import com.asqi.scholia_kantin_be.dto.konfigurasi.LaporInsidenRequest;
import com.asqi.scholia_kantin_be.dto.konfigurasi.AkunBukuKasResponse;
import com.asqi.scholia_kantin_be.dto.konfigurasi.ProfilKantinResponse;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.PerluPeran;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.konfigurasi.KejadianOfflineService;
import com.asqi.scholia_kantin_be.service.konfigurasi.AkunBukuKasService;
import com.asqi.scholia_kantin_be.service.konfigurasi.ProfilKantinService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Konfigurasi kantin &amp; solusi demo untuk pertanyaan terblokir
 * (issues <b>#21, #23, #25</b>).
 *
 * <p>Karena kantin-be belum rilis penuh, endpoint ini memberi solusi "seadanya"
 * agar modul tetap jalan tanpa menunggu keputusan tim lain:
 * <ul>
 *   <li><b>#21 / Q8</b> — pos Buku Kas dibuat otomatis per sekolah
 *       (fallback lokal sampai admin-be mengonfirmasi).</li>
 *   <li><b>#23 / Q14</b> — pencatatan insiden offline (prosedur darurat
 *       ADR-0006) sebagai dasar keputusan memajukan mode offline.</li>
 *   <li><b>#22/Q17 &amp; #24/Q15</b> — profil reader RFID &amp; postur regulasi
 *       (asumsi demo untuk tim RFID &amp; legal).</li>
 * </ul>
 *
 * <p><b>Audit #401 (2026-11-23):</b> dua endpoint demo yang <b>duplikat</b> sudah
 * dihapus — {@code GET /api/konfigurasi/pos-buku-kas} (respons
 * {@code POST .../aktivasi} sudah memuat daftar pos) dan
 * {@code GET}/{@code PUT /api/konfigurasi/kebijakan} (kebijakan saldo mengendap
 * sudah dilayani {@code GET}/{@code PUT /api/pengaturan-kantin}, lihat
 * {@link PengaturanKantinController}). Endpoint yang tersisa dipertahankan
 * karena belum punya padanan dan menopang pertanyaan yang masih terbuka
 * (#197). Rincian keputusan: {@code architecture/API-ENDPOINTS.md} §9.
 *
 * <p>Controller tipis (AGENTS.md §10): tenant dari {@link TenantContext}, RBAC
 * {@code @PerluPeran}, logika di service.
 */
@RestController
@RequestMapping("api/konfigurasi")
@RequiredArgsConstructor
public class KonfigurasiKantinController {

    private final AkunBukuKasService posBukuKasService;
    private final KejadianOfflineService insidenOfflineService;
    private final ProfilKantinService profilKantinService;

    // ────────────────────────────────────────────────────────────────
    // #21 / Q8 — POS BUKU KAS
    // ────────────────────────────────────────────────────────────────

    /**
     * Aktifkan modul kantin: pastikan pos Buku Kas standar dibuat otomatis
     * (idempoten). Aman dipanggil berkali-kali.
     */
    @PerluPeran({AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN, AktorKantin.TU_SEKOLAH})
    @PostMapping("pos-buku-kas/aktivasi")
    public ResponseEntity<Response<List<AkunBukuKasResponse>>> aktivasiPos(
            @AuthenticationPrincipal IdentitasKantin identitas) {

        List<AkunBukuKasResponse> pos = posBukuKasService.pastikanPosStandar(
                TenantContext.sekolahIdWajib(), identitas.aktorIdWajib());
        return CommonResponse.data(pos, "Pos Buku Kas kantin siap");
    }

    // ────────────────────────────────────────────────────────────────
    // #23 / Q14 — INSIDEN OFFLINE (prosedur darurat)
    // ────────────────────────────────────────────────────────────────

    /**
     * Laporkan insiden offline (internet/server mati) — prosedur darurat
     * ADR-0006. Append-only; dicatat oleh petugas saat gangguan terjadi.
     */
    @PerluPeran({AktorKantin.PETUGAS_KANTIN, AktorKantin.PENGELOLA_KANTIN,
            AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @PostMapping("insiden-offline")
    public ResponseEntity<Response<KejadianOfflineResponse>> laporInsiden(
            @Valid @RequestBody LaporInsidenRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        KejadianOfflineResponse hasil = insidenOfflineService.lapor(
                TenantContext.sekolahIdWajib(), request.getTitikKasirId(),
                request.getKeterangan(), identitas.aktorIdWajib());
        return CommonResponse.data(hasil, "Insiden offline dicatat");
    }

    /** Daftar insiden offline satu sekolah, terbaru dulu (tenant-scoped). */
    @PerluPeran({AktorKantin.PENGELOLA_KANTIN, AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @GetMapping("insiden-offline")
    public ResponseEntity<Response<List<KejadianOfflineResponse>>> daftarInsiden() {
        return CommonResponse.data(
                insidenOfflineService.daftar(TenantContext.sekolahIdWajib()));
    }

    // ────────────────────────────────────────────────────────────────
    // #22 / Q17 & #24 / Q15 — PROFIL PERANGKAT & POSTUR REGULASI
    // ────────────────────────────────────────────────────────────────

    /**
     * Profil perangkat &amp; postur kepatuhan kantin (asumsi demo):
     * reader RFID USB (#22/Q17) dan model dana titipan (#24/Q15).
     * Dipakai tim RFID/legal &amp; FE untuk melihat asumsi aktif sekarang.
     */
    @PerluPeran({AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN,
            AktorKantin.TU_SEKOLAH, AktorKantin.PETUGAS_KANTIN})
    @GetMapping("profil")
    public ResponseEntity<Response<ProfilKantinResponse>> profil() {
        return CommonResponse.data(profilKantinService.ambil());
    }
}
