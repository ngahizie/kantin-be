package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.PasangKartuSiswaRequest;
import com.asqi.scholia_kantin_be.dto.SimpanSiswaAdminRequest;
import com.asqi.scholia_kantin_be.dto.SiswaManajemenResponse;
import com.asqi.scholia_kantin_be.dto.UbahStatusSiswaRequest;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.PerluPeran;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.siswa.SiswaManajemenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint sementara Admin Sekolah untuk mendaftarkan/mengubah Siswa, Wali, &amp;
 * Kartu RFID sebelum integrasi penuh ke {@code admin-be} selesai.
 *
 * <p>RBAC ketat: hanya {@link AktorKantin#ADMIN_SEKOLAH} yang diizinkan mengakses
 * endpoint ini (memisahkan akses manajemen master &amp; UID RFID dari endpoint
 * lookup umum {@code GET /api/siswa}).
 */
@RestController
@RequestMapping("api/admin/siswa")
@RequiredArgsConstructor
@PerluPeran({AktorKantin.ADMIN_SEKOLAH})
public class SiswaManajemenController {

    private final SiswaManajemenService service;

    @PerluPeran({AktorKantin.ADMIN_SEKOLAH})
    @GetMapping
    public ResponseEntity<Response<List<SiswaManajemenResponse>>> daftar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean aktif,
            @RequestParam(required = false) Boolean punyaKartu,
            @RequestParam(defaultValue = "100") int limit) {

        List<SiswaManajemenResponse> hasil = service.daftar(
                TenantContext.sekolahIdWajib(), q, aktif, punyaKartu, limit);
        return CommonResponse.data(hasil);
    }

    @PerluPeran({AktorKantin.ADMIN_SEKOLAH})
    @GetMapping("{siswaId}")
    public ResponseEntity<Response<SiswaManajemenResponse>> detail(
            @PathVariable Long siswaId) {

        return CommonResponse.data(service.detail(TenantContext.sekolahIdWajib(), siswaId));
    }

    @PerluPeran({AktorKantin.ADMIN_SEKOLAH})
    @PostMapping
    public ResponseEntity<Response<SiswaManajemenResponse>> buat(
            @Valid @RequestBody SimpanSiswaAdminRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        SiswaManajemenResponse hasil = service.buat(
                TenantContext.sekolahIdWajib(), request, identitas.aktorIdWajib());
        return CommonResponse.data(hasil, "Siswa baru berhasil ditambahkan");
    }

    @PerluPeran({AktorKantin.ADMIN_SEKOLAH})
    @PutMapping("{siswaId}")
    public ResponseEntity<Response<SiswaManajemenResponse>> perbarui(
            @PathVariable Long siswaId,
            @Valid @RequestBody SimpanSiswaAdminRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        SiswaManajemenResponse hasil = service.perbarui(
                TenantContext.sekolahIdWajib(), siswaId, request, identitas.aktorIdWajib());
        return CommonResponse.data(hasil, "Data siswa berhasil diperbarui");
    }

    @PerluPeran({AktorKantin.ADMIN_SEKOLAH})
    @PutMapping("{siswaId}/kartu")
    public ResponseEntity<Response<SiswaManajemenResponse>> aturKartu(
            @PathVariable Long siswaId,
            @Valid @RequestBody PasangKartuSiswaRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        SiswaManajemenResponse hasil = service.aturKartu(
                TenantContext.sekolahIdWajib(), siswaId, request, identitas.aktorIdWajib());
        String pesan = Boolean.TRUE.equals(request.getNonaktifkanKartu())
                ? "Kartu RFID siswa dinonaktifkan"
                : "Kartu RFID siswa berhasil diperbarui";
        return CommonResponse.data(hasil, pesan);
    }

    @PerluPeran({AktorKantin.ADMIN_SEKOLAH})
    @PatchMapping("{siswaId}/status")
    public ResponseEntity<Response<SiswaManajemenResponse>> ubahStatus(
            @PathVariable Long siswaId,
            @Valid @RequestBody UbahStatusSiswaRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        boolean aktif = Boolean.TRUE.equals(request.getAktif());
        SiswaManajemenResponse hasil = service.ubahStatus(
                TenantContext.sekolahIdWajib(), siswaId, aktif, identitas.aktorIdWajib());
        return CommonResponse.data(hasil, aktif ? "Siswa diaktifkan" : "Siswa dinonaktifkan");
    }
}
