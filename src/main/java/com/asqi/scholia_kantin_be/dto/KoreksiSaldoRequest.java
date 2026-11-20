package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.ArahMutasi;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Permintaan koreksi saldo oleh bendahara (PRD §9.2) — dengan alasan &amp;
 * nomor berita acara <b>wajib</b> (jejak audit).
 *
 * <p>Sejak C.2b subjek koreksi selalu siswa (Kartu Tamu tidak punya saldo),
 * jadi field {@code subjekTipe} dihapus.
 */
@Data
public class KoreksiSaldoRequest {

    @NotNull
    private Long siswaId;

    @NotNull(message = "Arah koreksi wajib (KREDIT menambah / DEBIT mengurangi)")
    private ArahMutasi arah;

    @Min(value = 1, message = "Nominal koreksi harus > 0")
    private long nominal;

    @NotBlank(message = "Alasan koreksi wajib diisi (PRD §9.2)")
    @Size(max = 255)
    private String alasan;

    /**
     * Nomor berita acara koreksi. Batas 56 = {@code idempotency_key} 64 karakter
     * dikurangi awalan {@code "KOREKSI-"} (8) yang dipasang
     * {@code SaldoTopUpService}; sebelumnya 60 sehingga referensi 57–60 karakter
     * menghasilkan kunci 65–68 karakter dan INSERT gagal (HTTP 500, issue #382).
     */
    @NotBlank(message = "Nomor berita acara koreksi wajib diisi (idempotency)")
    @Size(max = 56, message = "Nomor berita acara koreksi maksimal 56 karakter")
    private String referensiId;
}
