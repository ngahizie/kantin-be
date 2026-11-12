package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.Pembatasan;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/** Baris blokir item/kategori (PRD §8.3, issue #40). */
@Data
@Builder
public class BlokirItemResponse {

    private Long id;
    private String subjekTipe;
    private Long siswaId;
    private Long menuId;
    private Long kategoriId;
    private boolean diblokir;
    private OffsetDateTime diubahPada;

    /** Proyeksi entitas → DTO. */
    public static BlokirItemResponse dari(Pembatasan b) {
        return BlokirItemResponse.builder()
                .id(b.getId())
                .subjekTipe(SubjekTipe.SISWA.name())
                .siswaId(b.getSiswaId())
                .menuId(b.getMenuId())
                .kategoriId(b.getKategoriId())
                .diblokir(b.isAktif())
                .diubahPada(b.getWaktu())
                .build();
    }
}
