package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Permintaan refund (pembatalan) transaksi kasir (PRD §6.3).
 *
 * <p>Sejak revisi PRD, kasir <b>wajib mengetik/memindai kode struk</b> yang
 * tercetak pada bukti pembayaran. Tujuannya mencegah salah pilih transaksi:
 * kode struk harus cocok dengan transaksi yang akan di-refund.
 *
 * <p>Alasan juga <b>wajib</b> — dicatat ke {@code audit_log} (§11.7) dan
 * disimpan pada transaksi sebagai {@code alasan_void}.
 */
@Data
public class VoidRequest {

    @NotBlank(message = "Alasan refund wajib diisi (PRD §6.3)")
    @Size(max = 255, message = "Alasan refund maksimal 255 karakter")
    private String alasan;

    /** Kode struk transaksi, mis. {@code TRX-20261109-1794208109157170}. */
    @NotBlank(message = "Kode struk wajib diisi (PRD §6.3)")
    @Size(max = 64, message = "Kode struk maksimal 64 karakter")
    private String kodeStruk;
}
