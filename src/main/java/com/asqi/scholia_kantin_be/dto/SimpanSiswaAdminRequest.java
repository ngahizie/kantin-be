package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request tambah/ubah data siswa sementara oleh Admin Sekolah.
 */
@Data
public class SimpanSiswaAdminRequest {

    @NotBlank(message = "NIS wajib diisi")
    @Size(max = 30, message = "NIS maksimal 30 karakter")
    private String nis;

    @Size(max = 30, message = "NISN maksimal 30 karakter")
    private String nisn;

    @NotBlank(message = "Nama siswa wajib diisi")
    @Size(max = 100, message = "Nama siswa maksimal 100 karakter")
    private String nama;

    @Size(max = 30, message = "Kelas maksimal 30 karakter")
    private String kelas;

    @Size(max = 255, message = "URL foto maksimal 255 karakter")
    @Pattern(
            regexp = "^$|^(https?://|/api/storage/file\\?path=|sekolah-[0-9]+/).*",
            flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "URL foto harus berupa URL http(s) atau path storage yang valid"
    )
    private String fotoUrl;

    @Size(max = 50, message = "UID RFID maksimal 50 karakter")
    @Pattern(
            regexp = "^$|^[A-Za-z0-9_-]{4,50}$",
            message = "UID RFID hanya boleh berisi huruf, angka, '-' atau '_' (4-50 karakter)"
    )
    private String rfidUid;

    @Size(max = 32, message = "Nomor kartu maksimal 32 karakter")
    private String nomorKartu;

    @Size(max = 100, message = "Nama orang tua/wali maksimal 100 karakter")
    private String namaOrangTua;

    @Size(max = 30, message = "Telepon orang tua/wali maksimal 30 karakter")
    private String teleponOrangTua;

    @Size(max = 20, message = "Hubungan wali maksimal 20 karakter")
    private String hubunganOrangTua;
}
