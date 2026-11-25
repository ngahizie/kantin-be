package com.asqi.scholia_kantin_be.dto.stok;

import com.asqi.scholia_kantin_be.service.stok.HasilMutasiStok;
import lombok.Builder;
import lombok.Data;

/**
 * Respons `POST /api/stok/barang-masuk`, `barang-masuk-pembalik`, dan `opname`.
 *
 * <p>Menggantikan value object {@link HasilMutasiStok} yang memuat entity
 * {@code RiwayatStok}. Field {@code mutasi} kini berupa {@link RiwayatStokItem}
 * agar kolom ledger internal tidak dikirim ke klien.
 */
@Data
@Builder
public class HasilMutasiStokResponse {
    private RiwayatStokItem mutasi;
    private int stokSetelah;
    private long hppSetelah;

    /**
     * Konversi hasil mutasi layer service ke DTO respons API.
     *
     * <p>{@code mutasi} boleh null (dipakai tes controller dengan service
     * mock).
     *
     * @param hasil hasil mutasi dari service; null menghasilkan DTO kosong
     * @return DTO respons yang tidak membawa entity
     */
    public static HasilMutasiStokResponse dari(HasilMutasiStok hasil) {
        if (hasil == null) {
            return HasilMutasiStokResponse.builder().build();
        }
        return HasilMutasiStokResponse.builder()
                .mutasi(hasil.getMutasi() == null ? null : RiwayatStokItem.dari(hasil.getMutasi()))
                .stokSetelah(hasil.getStokSetelah())
                .hppSetelah(hasil.getHppSetelah())
                .build();
    }
}
