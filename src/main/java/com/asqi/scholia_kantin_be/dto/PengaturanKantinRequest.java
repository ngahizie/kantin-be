package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;

/**
 * Ubah pengaturan kantin (PRD §9.1, issue #42).
 *
 * <p>Semua field opsional: {@code null} = pertahankan nilai lama. Batas
 * min/maks top-up &amp; batas saldo {@code null} = tanpa batas (PRD §8.2/§9.4).
 *
 * <p><b>Rentang nilai (issue #415, OWASP A06:2025 / CWE-1284):</b> setiap angka
 * dibatasi dua sisi, bukan hanya bawah.
 * <ul>
 *   <li>{@code durasiFotoDetik} 1–60 detik — nilai 0 mematikan verifikasi visual
 *       wajah siswa di kasir POS (celah penipuan identitas kartu), nilai ekstrem
 *       membuat layar POS menunggu timer foto yang tidak wajar.</li>
 *   <li>{@code minTopup}/{@code maksTopup} Rp1.000–Rp100.000.000.</li>
 *   <li>{@code batasSaldoSiswa} Rp10.000–Rp500.000.000.</li>
 *   <li>{@code ambangHari} 0–3.650 hari (10 tahun) — mencegah overflow saat
 *       tanggal tindak lanjut dihitung.</li>
 * </ul>
 * Batas atas ditegakkan lagi di database (V41) sebagai pertahanan berlapis untuk
 * baris lama dan jalur SQL langsung.
 */
@Data
public class PengaturanKantinRequest {

    @Size(max = 150, message = "Nama kantin maksimal 150 karakter")
    private String namaKantin;

    /** Jam tutup kasir otomatis (zona sekolah) — dipakai penjadwal auto-close (§6.4). */
    private LocalTime jamTutupOtomatis;

    /** Langkah konfirmasi manual sebelum transaksi tercatat (default nonaktif, §6.1). */
    private Boolean konfirmasiManual;

    @Min(value = 1, message = "Durasi foto minimal 1 detik")
    @Max(value = 60, message = "Durasi foto maksimal 60 detik")
    private Integer durasiFotoDetik;

    @Min(value = 1_000, message = "Minimum top-up minimal Rp1.000")
    @Max(value = 100_000_000L, message = "Minimum top-up maksimal Rp100.000.000")
    private Long minTopup;

    @Min(value = 1_000, message = "Maksimum top-up minimal Rp1.000")
    @Max(value = 100_000_000L, message = "Maksimum top-up maksimal Rp100.000.000")
    private Long maksTopup;

    @Min(value = 10_000, message = "Batas saldo siswa minimal Rp10.000")
    @Max(value = 500_000_000L, message = "Batas saldo siswa maksimal Rp500.000.000")
    private Long batasSaldoSiswa;

    /** Kebijakan saldo mengendap: {@code REFUND} atau {@code PINDAH_SAUDARA}/{@code DIBIARKAN}. */
    private String kebijakanSaldoMengendap;

    @Min(value = 0, message = "Ambang hari tidak boleh negatif")
    @Max(value = 3_650, message = "Ambang hari maksimal 3650 hari")
    private Integer ambangHari;
}
