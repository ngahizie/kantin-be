package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request pasang, ganti, atau nonaktifkan kartu RFID siswa.
 */
@Data
public class PasangKartuSiswaRequest {

    @Size(max = 50, message = "UID RFID maksimal 50 karakter")
    @Pattern(
            regexp = "^$|^[A-Za-z0-9_-]{4,50}$",
            message = "UID RFID hanya boleh berisi huruf, angka, '-' atau '_' (4-50 karakter)"
    )
    private String rfidUid;

    @Size(max = 32, message = "Nomor kartu maksimal 32 karakter")
    private String nomorKartu;

    /** Jika {@code true}, seluruh kartu aktif siswa dinonaktifkan (lepas kartu). */
    private Boolean nonaktifkanKartu;
}
