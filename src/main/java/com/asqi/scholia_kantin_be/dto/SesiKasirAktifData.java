package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.StatusSesiKasir;
import com.asqi.scholia_kantin_be.model.SesiKasir;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Sesi kasir <b>aktif (TERBUKA) hari ini</b> untuk layar POS (issue <b>#222</b>)
 * — bentuk mengikuti tipe FE {@code SesiKasirData}
 * ({@code src/features/kasir/types.ts}).
 *
 * <p>Identik dengan entitas {@link SesiKasir} untuk field inti, ditambah dua
 * field kenyamanan FE yang tidak tersimpan di {@code sesi_kasir}:
 * <ul>
 *   <li>{@code titikKasirNama} — dari tabel {@code titik_kasir} milik tenant.</li>
 *   <li>{@code totalTransaksi} — jumlah transaksi SUKSES sesi ini
 *       (sama dengan sumber rekap; bukan duplikasi logika).</li>
 * </ul>
 *
 * <p>Bila belum ada sesi terbuka, endpoint mengembalikan <b>200 dengan
 * {@code data: null}</b> (bukan 404) — begitulah FE
 * {@code KasirPosPage.loadSesiData()} melanjutkan ke {@code bukaSesi()}
 * hanya bila hasilnya {@code null}. Titik kasir milik sekolah lain tetap
 * dijawab 404 agar keberadaannya tidak bocor (PRD §11.4).
 */
public record SesiKasirAktifData(
        Long id,
        Long sekolahId,
        Long titikKasirId,
        String titikKasirNama,
        LocalDate tanggal,
        StatusSesiKasir status,
        Long totalBruto,
        Long totalVoid,
        Long totalBersih,
        Long totalTransaksi,
        OffsetDateTime dibukaAt,
        OffsetDateTime ditutupAt,
        Long ditutupOleh,
        boolean autoTutup) {

    /**
     * Proyeksi entitas → DTO. {@code namaTitik} dan angka rekap
     * ({@code totalTransaksi}/bruto/void/bersih) diisi service dari
     * {@code hitungRekap} — sumber rekap yang sama dengan tutup kasir, karena
     * kolom {@code total_*} di {@code sesi_kasir} baru terisi saat sesi
     * ditutup (0 selama sesi masih TERBUKA).
     */
    public static SesiKasirAktifData dari(SesiKasir s, String namaTitik,
                                          long totalTransaksi, long totalBruto,
                                          long totalVoid, long totalBersih) {
        return new SesiKasirAktifData(
                s.getId(),
                s.getSekolahId(),
                s.getTitikKasirId(),
                namaTitik,
                s.getTanggal(),
                s.getStatus(),
                totalBruto,
                totalVoid,
                totalBersih,
                totalTransaksi,
                s.getDibukaAt(),
                s.getDitutupAt(),
                s.getDitutupOleh(),
                s.isAutoTutup());
    }
}
