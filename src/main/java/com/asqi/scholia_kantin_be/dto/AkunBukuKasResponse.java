package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.model.AkunBukuKas;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Pos Buku Kas kantin (DEMO, OPEN-QUESTIONS Q8 / issue #21).
 *
 * <p>Menampilkan pos yang dipakai kantin-be per sekolah. Dibuat otomatis
 * (seeding idempoten) sampai admin-be mengonfirmasi apakah pos dibuat otomatis
 * saat modul diaktifkan.
 */
@Data
@Builder
public class AkunBukuKasResponse {

    private Long id;
    private String nama;
    private String tipe;
    private String keterangan;
    private boolean aktif;
    private OffsetDateTime createdAt;

    /** Proyeksi entitas → DTO. */
    public static AkunBukuKasResponse dari(AkunBukuKas p) {
        return AkunBukuKasResponse.builder()
                .id(p.getId())
                .nama(p.getNama())
                .tipe(p.getTipe())
                .keterangan(p.getKeterangan())
                .aktif(p.isAktif())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
