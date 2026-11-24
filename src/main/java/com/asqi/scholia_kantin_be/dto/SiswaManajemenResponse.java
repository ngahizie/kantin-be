package com.asqi.scholia_kantin_be.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * DTO lengkap manajemen sementara Siswa, Wali, &amp; Kartu RFID oleh Admin Sekolah.
 */
@Getter
@Builder
public class SiswaManajemenResponse {

    private final Long siswaId;
    private final String nis;
    private final String nisn;
    private final String nama;
    private final String kelas;
    private final String fotoUrl;
    private final boolean aktif;
    private final long saldo;
    private final String rfidUid;
    private final String nomorKartu;
    private final String namaOrangTua;
    private final String teleponOrangTua;
    private final String hubunganOrangTua;
    private final OffsetDateTime updatedAt;
}
