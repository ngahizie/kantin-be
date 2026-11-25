package com.asqi.scholia_kantin_be.dto.katalog;

import com.asqi.scholia_kantin_be.model.KategoriMenu;
import lombok.Builder;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * Respons `POST/PUT /api/katalog/kategori`.
 *
 * <p>Berisi kolom kategori yang boleh dibaca klien. Berbeda dengan
 * {@link KategoriResponse} yang dipakai daftar katalog dan menyertakan
 * {@code jumlahItem} (hanya dihitung untuk daftar), di sini entity tidak
 * dikirim langsung supaya kolom internal seperti {@code baru} dan
 * {@code sekolahId} tidak bocor ke API.
 */
@Data
@Builder
public class KategoriDetailResponse {
    private Long id;
    private String nama;
    /** Urutan tampil di kasir. */
    private Integer urutan;
    private Boolean isActive;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /**
     * Proyeksi entity kategori ke respons create/update.
     *
     * @param kategori entity hasil simpan; null menghasilkan DTO kosong
     * @return DTO dengan hanya kolom yang boleh dikonsumsi klien
     */
    public static KategoriDetailResponse dari(KategoriMenu kategori) {
        if (kategori == null) {
            return KategoriDetailResponse.builder().build();
        }
        return KategoriDetailResponse.builder()
                .id(kategori.getId())
                .nama(kategori.getNama())
                .urutan(kategori.getUrutan())
                .isActive(kategori.getIsActive())
                .createdAt(kategori.getCreatedAt())
                .updatedAt(kategori.getUpdatedAt())
                .build();
    }
}
