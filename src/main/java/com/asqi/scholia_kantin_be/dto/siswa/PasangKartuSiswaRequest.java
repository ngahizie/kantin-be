package com.asqi.scholia_kantin_be.dto.siswa;

import com.asqi.scholia_kantin_be.validation.UidKartuValid;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request pasang, ganti, atau nonaktifkan kartu RFID siswa.
 */
@Data
public class PasangKartuSiswaRequest {

    @Size(max = 64, message = "UID RFID maksimal 64 karakter")
    @UidKartuValid
    private String rfidUid;

    @Size(max = 32, message = "Nomor kartu maksimal 32 karakter")
    private String nomorKartu;

    /** Jika {@code true}, seluruh kartu aktif siswa dinonaktifkan (lepas kartu). */
    private Boolean nonaktifkanKartu;
}
