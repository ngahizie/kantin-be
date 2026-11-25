package com.asqi.scholia_kantin_be.dto.saldo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Permintaan top-up tunai saldo (PRD §9.1).
 *
 * <p>{@code referensiId} = nomor bukti/kuitansi <b>unik</b> dari TU; dipakai
 * sebagai idempotency key — menyimpan bukti yang sama dua kali tidak menambah
 * saldo dua kali (Aturan Emas §3.3).
 *
 * <p>Sejak C.2b top-up hanya untuk siswa (Kartu Tamu dibayar tunai, tanpa
 * saldo), jadi field {@code subjekTipe} dihapus.
 */
@Data
public class TopUpRequest {

    @NotNull(message = "ID subjek wajib diisi")
    private Long siswaId;

    @Min(value = 1, message = "Nominal top-up harus > 0")
    private long nominal;

    @Size(max = 120, message = "Nama penyetor maksimal 120 karakter")
    private String penyetor;

    /**
     * Nomor referensi/bukti. Batas 52 = {@code idempotency_key} 64 karakter
     * dikurangi awalan {@code "TOPUP-TUNAI-"} (12) — pola yang sama dengan
     * {@code KoreksiSaldoRequest} (issue #382).
     */
    @NotBlank(message = "Nomor referensi/bukti wajib diisi")
    @Size(max = 52, message = "Nomor referensi maksimal 52 karakter")
    private String referensiId;
}
