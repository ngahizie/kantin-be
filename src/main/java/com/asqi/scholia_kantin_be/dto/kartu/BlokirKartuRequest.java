package com.asqi.scholia_kantin_be.dto.kartu;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Blokir / buka blokir kartu (PRD §8.3, issue #40).
 *
 * <p>Kontrol melekat ke siswa pemilik kartu. Sejak C.2b tidak ada lagi field
 * {@code subjekTipe}: skema v2 mewajibkan kartu menunjuk siswa, jadi subjeknya
 * selalu {@code SISWA}. Berlaku <b>instan</b> — tap berikutnya langsung ditolak
 * (PRD §11.11).
 */
@Data
public class BlokirKartuRequest {

    @NotNull(message = "ID subjek wajib diisi")
    private Long siswaId;

    /** {@code true} = blokir, {@code false} = buka blokir. Default {@code true}. */
    private Boolean diblokir;

    @Size(max = 500, message = "Alasan maksimal 500 karakter")
    private String alasan;
}
