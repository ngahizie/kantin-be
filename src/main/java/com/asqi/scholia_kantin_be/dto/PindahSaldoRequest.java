package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Permintaan pemindahan sisa saldo ke saudara kandung yang masih aktif di
 * sekolah yang sama (PRD §9.3, issue #38).
 *
 * <p>{@code referensiId} = nomor berita acara (idempotency) — satu berita acara
 * menghasilkan tepat satu pemindahan (dua kaki ledger).
 */
@Data
public class PindahSaldoRequest {

    @NotNull(message = "ID siswa sumber wajib diisi")
    private Long siswaSumberId;

    @NotNull(message = "ID siswa tujuan (saudara) wajib diisi")
    private Long siswaTujuanId;

    /**
     * Nomor berita acara pemindahan. Batas 49 = {@code idempotency_key} 64 karakter
     * dikurangi awalan {@code "TRANSFER-"} (9) dan akhiran {@code "-MASUK"} (6) yang
     * dipakai sisi tujuan — issue #382.
     */
    @NotBlank(message = "Nomor berita acara pemindahan wajib diisi (idempotency)")
    @Size(max = 49, message = "Nomor berita acara pemindahan maksimal 49 karakter")
    private String referensiId;

    @Size(max = 255)
    private String catatan;
}
