package com.asqi.scholia_kantin_be.dto.kasir;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Permintaan tutup sesi kasir (PRD §6.4, C.3).
 *
 * <p>Kasir <b>wajib</b> menuliskan uang fisik yang benar-benar ada di laci. Server
 * yang menghitung {@code selisih = uangFisik − totalTunai} (C.2d); nilai
 * {@code selisih} kiriman klien sengaja <b>tidak</b> dipakai agar angka audit
 * tidak bisa ditentukan dari sisi FE.
 *
 * <p>Bila selisih ≠ 0, {@code catatan} wajib diisi sebagai alasan kelebihan /
 * kekurangan kas — aturan yang sama sudah ditegakkan FE di
 * {@code CloseSessionModal}, jadi BE tidak boleh lebih longgar.
 *
 * <p>Penutupan otomatis oleh sistem (jadwal / sesi tertinggal) tidak memakai DTO
 * ini: {@code uangFisik}, {@code selisih}, dan {@code catatan} dibiarkan
 * {@code null} karena tidak ada kasir yang menghitung laci.
 */
@Data
public class TutupSesiRequest {

    @NotNull(message = "Uang fisik wajib diisi saat tutup kasir")
    @PositiveOrZero(message = "Uang fisik tidak boleh negatif")
    private Long uangFisik;

    @Size(max = 500, message = "Catatan tutup kasir maksimal 500 karakter")
    private String catatan;
}
