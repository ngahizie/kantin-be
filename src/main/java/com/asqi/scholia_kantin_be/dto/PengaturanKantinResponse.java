package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.model.PengaturanKantin;
import lombok.Builder;
import lombok.Data;

import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * Pengaturan kantin per sekolah (PRD §9.1, issue #42).
 *
 * <p>{@code disimpan=false} berarti sekolah belum pernah menyimpan pengaturan;
 * nilai yang ditampilkan adalah <b>default aman</b> (PRD §6.1/§6.4).
 */
@Data
@Builder
public class PengaturanKantinResponse {

    private Long sekolahId;
    private String namaKantin;

    /** Jam tutup kasir otomatis (zona sekolah). */
    private LocalTime jamTutupOtomatis;

    private boolean konfirmasiManual;
    private int durasiFotoDetik;

    /** Minimum per top-up; {@code null} = tanpa batas. */
    private Long minTopup;
    /** Maksimum per top-up; {@code null} = tanpa batas. */
    private Long maksTopup;
    /** Batas saldo maksimum per siswa; {@code null} = tanpa batas. */
    private Long batasSaldoSiswa;
    /** Batas saldo maksimum per Kartu Tamu; {@code null} = tanpa batas. */
    private String kebijakanSaldoMengendap;
    private Integer ambangHari;

    /** Apakah baris pengaturan sudah benar-benar tersimpan (bukan default). */
    private boolean disimpan;

    private OffsetDateTime updatedAt;

    /**
     * ID pengguna yang terakhir mengubah pengaturan; {@code null} bila baris
     * belum pernah disimpan (masih default).
     *
     * <p>Issue #416 (OWASP A09:2025 / CWE-778): tanpa field ini klien hanya punya
     * {@code updatedAt} dan menampilkan nama statis, sehingga admin tidak bisa
     * menelusuri siapa yang mengubah konfigurasi.
     */
    private Long diperbaruiOleh;

    /**
     * Nama tampilan aktor pengubah (lookup tenant-scoped oleh service);
     * {@code null} bila {@code diperbaruiOleh} kosong atau penggunanya tidak ada
     * di sekolah tersebut.
     */
    private String updatedBy;

    /** Proyeksi entitas → DTO (baris tersimpan), tanpa nama aktor. */
    public static PengaturanKantinResponse dari(PengaturanKantin c) {
        return dari(c, null);
    }

    /**
     * Proyeksi entitas → DTO beserta nama tampilan aktor pengubah.
     *
     * @param c          baris pengaturan tersimpan
     * @param namaAktor  nama pengguna aktor (boleh {@code null})
     * @return DTO siap dikirim
     */
    public static PengaturanKantinResponse dari(PengaturanKantin c, String namaAktor) {
        return PengaturanKantinResponse.builder()
                .sekolahId(c.getSekolahId())
                .namaKantin(c.getNamaKantin())
                .jamTutupOtomatis(c.getJamTutupOtomatis())
                .konfirmasiManual(c.isKonfirmasiManual())
                .durasiFotoDetik(c.getDurasiFotoDetik())
                .minTopup(c.getMinTopup())
                .maksTopup(c.getMaksTopup())
                .batasSaldoSiswa(c.getBatasSaldoSiswa())
                .kebijakanSaldoMengendap(c.getKebijakanSaldoMengendap())
                .ambangHari(c.getAmbangHari())
                .disimpan(true)
                .updatedAt(c.getUpdatedAt())
                .diperbaruiOleh(c.getDiperbaruiOleh())
                .updatedBy(namaAktor)
                .build();
    }
}
