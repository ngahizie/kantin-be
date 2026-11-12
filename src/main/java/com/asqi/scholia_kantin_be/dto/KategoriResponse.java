package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.model.KategoriMenu;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Satu baris daftar kategori katalog (PRD §7.1).
 *
 * <p>Selain data kategori, memuat {@code jumlahItem} — jumlah menu yang belum
 * dihapus pada kategori tersebut — supaya FE tidak perlu memanggil
 * {@code GET /api/katalog/menu} terpisah hanya untuk menampilkan jumlah item.
 *
 * <p>Kategori yang sudah dihapus ({@code deleted_at} terisi) tidak pernah
 * muncul di daftar ini.
 */
@Data
@Builder
public class KategoriResponse {

    private Long id;

    private String nama;

    /** Urutan tampil di kasir (kecil lebih dulu). */
    private Integer urutan;

    /** Status aktif/nonaktif (toggle lewat PUT, bukan hapus). */
    private Boolean isActive;

    /** Jumlah menu terkait yang belum dihapus (aktif maupun nonaktif). */
    private long jumlahItem;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    public static KategoriResponse dari(KategoriMenu kategori, long jumlahItem) {
        return KategoriResponse.builder()
                .id(kategori.getId())
                .nama(kategori.getNama())
                .urutan(kategori.getUrutan())
                .isActive(kategori.getIsActive())
                .jumlahItem(jumlahItem)
                .createdAt(kategori.getCreatedAt())
                .updatedAt(kategori.getUpdatedAt())
                .build();
    }
}
