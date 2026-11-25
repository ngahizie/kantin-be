package com.asqi.scholia_kantin_be.dto.konfigurasi;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Request buat/ubah titik kasir (PRD §9.1, issue #42). */
@Data
public class TitikKasirRequest {

    @NotBlank(message = "Nama titik kasir wajib diisi")
    @Size(max = 100, message = "Nama titik kasir maksimal 100 karakter")
    private String nama;

    /**
     * Kode unik per sekolah (opsional). Hanya huruf, angka, {@code -}, {@code _};
     * string kosong diterima untuk menghapus kode saat {@code PUT} (SEC-TITIK-05).
     * Newline, markup, spasi, dan karakter kontrol ditolak agar kode tidak
     * menjadi kanal injeksi ke struk/ekspor.
     */
    @Size(max = 30, message = "Kode maksimal 30 karakter")
    @Pattern(
            regexp = "^[A-Za-z0-9_-]*$",
            message = "Kode perangkat hanya boleh huruf, angka, tanda minus (-), atau garis bawah (_)")
    private String kode;

    /** Status aktif (opsional; {@code null} = pertahankan). */
    private Boolean aktif;
}
