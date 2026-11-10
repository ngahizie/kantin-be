package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;

import java.time.OffsetDateTime;

/**
 * Identitas pemilik kartu untuk <b>layar kasir</b>, dibaca tanpa memotong
 * saldo (beda dengan {@link TapResponse} yang langsung membuat transaksi).
 *
 * <p>Dipakai saat kasir menempelkan kartu: aplikasi bisa menampilkan foto,
 * nama, kelas, dan sisa saldo <i>sebelum</i> pembayaran diproses, sehingga
 * kartu tertukar bisa dicegah tanpa perlu verifikasi teks.
 *
 * @param nomorKartu     UID yang dibaca (dikembalikan apa adanya, sudah dirapikan)
 * @param subjekTipe     SISWA (Kartu Tamu sudah dihapus dari sistem)
 * @param subjekId       id siswa di pusat data
 * @param nama           nama pemilik kartu (null bila pusat data tidak mengirim)
 * @param kelas          kelas pemilik kartu (null bila tidak ada)
 * @param fotoUrl        URL foto (null bila tidak ada)
 * @param saldo          saldo saat ini
 * @param belanjaHariIni total belanja hari ini (dasar hitung limit harian)
 * @param limitHarian    limit harian (null = tanpa limit)
 * @param sisaLimit      sisa limit hari ini (null = tanpa limit)
 * @param diblokir       kartu sedang diblokir (ortu/admin/TU)
 * @param bisaBelanja    ringkasan: tidak diblokir, saldo &gt; 0, sisa limit &gt; 0
 * @param waktuCek       waktu pembacaan (untuk jejak di layar kasir)
 */
public record CekKartuResponse(
        String nomorKartu,
        SubjekTipe subjekTipe,
        Long subjekId,
        String nama,
        String kelas,
        String fotoUrl,
        long saldo,
        long belanjaHariIni,
        Long limitHarian,
        Long sisaLimit,
        boolean diblokir,
        boolean bisaBelanja,
        OffsetDateTime waktuCek) {
}
