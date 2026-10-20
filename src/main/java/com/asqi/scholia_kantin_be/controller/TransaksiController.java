package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.TransaksiSesiTutupItem;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.security.PerluPeran;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.kasir.TransaksiKasirService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Endpoint baca <b>transaksi</b> lintas sesi (PRD §6.3, §9.2).
 *
 * <p>Hanya berisi jalur yang memang diminta klien: daftar transaksi pada
 * <b>sesi yang sudah DITUTUP</b> — kandidat koreksi bendahara (issue <b>#230</b>).
 * Daftar transaksi berhalaman (semua status, untuk koreksi) tetap di
 * {@code GET /api/kasir/transaksi} ({@link KasirController}).
 *
 * <p>Controller tipis (AGENTS.md §10): RBAC {@code @PerluPeran}, tenant dari
 * {@link TenantContext}, logika di {@link TransaksiKasirService}.
 */
@RestController
@RequestMapping("api/transaksi")
@RequiredArgsConstructor
public class TransaksiController {

    private final TransaksiKasirService transaksiKasirService;

    /**
     * Daftar transaksi pada sesi <b>DITUTUP</b> (PRD §6.3, §9.2; issue #230) —
     * daftar kandidat koreksi bendahara, terbaru dulu.
     *
     * <p>Menggantikan mock yang selama ini tampil diam-diam di
     * {@code KoreksiBendaharaPage} (FE memanggil path ini, menerima 404, lalu
     * memakai {@code MOCK_KOREKSI_BENDAHARA}).
     *
     * <p>Periode opsional: {@code tanggal} ({@code yyyy-MM-dd}, zona sekolah) atau
     * rentang {@code dari}/{@code sampai} (ISO date-time). Ketiganya kosong ⇒
     * <b>7 hari terakhir</b>. {@code batas} default 20, maks 200.
     *
     * <p>RBAC disamakan dengan aksi koreksinya (TU/bendahara, admin, pengelola) —
     * petugas kasir &amp; kepsek tidak membutuhkan daftar ini.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @GetMapping("sesi-tutup")
    public ResponseEntity<Response<List<TransaksiSesiTutupItem>>> sesiTutup(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai,
            @RequestParam(defaultValue = "0") int batas) {

        return CommonResponse.data(transaksiKasirService.sesiTutup(
                TenantContext.sekolahIdWajib(), tanggal, dari, sampai, batas));
    }
}
