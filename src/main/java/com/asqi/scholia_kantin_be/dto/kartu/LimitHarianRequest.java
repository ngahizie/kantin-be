package com.asqi.scholia_kantin_be.dto.kartu;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Set / ubah limit belanja harian (PRD §8.3, issue #40).
 *
 * <p>{@code nominal} {@code null} = <b>tanpa limit</b>. Berlaku seketika; hari
 * direset pukul 00:00 zona sekolah (PRD §11.9).
 *
 * <p>Sejak C.2b subjek selalu siswa pemilik kartu, jadi field
 * {@code subjekTipe} dihapus.
 */
@Data
public class LimitHarianRequest {

    @NotNull(message = "ID subjek wajib diisi")
    private Long siswaId;

    /** Batas belanja harian (rupiah); {@code null} = tanpa limit. */
    @Min(value = 0, message = "Limit tidak boleh negatif")
    private Long nominal;
}
