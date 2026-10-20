package com.asqi.scholia_kantin_be.dto;

import java.time.Instant;

/**
 * Satu baris laporan <b>Saldo mengendap — kartu tamu</b> (issue #225).
 *
 * <p>Bentuknya mengikuti tipe FE {@code KartuTamuSaldoDetail}, dipetakan dari
 * {@link BarisKartuTamu} yang sudah ada — <b>tanpa query baru</b>, sesuai
 * keputusan (a) di issue (BE menyesuaikan path &amp; bentuk, FE tidak diubah).
 *
 * @param id            id kartu
 * @param nomorKartu    nomor tercetak di kartu
 * @param uid           UID RFID (null bila kartu fisik belum ditautkan)
 * @param labelPemegang nama pemegang
 * @param saldo         saldo berjalan (rupiah)
 * @param status        {@code ACTIVE} atau {@code BLOCKED} — dua nilai yang
 *                      dikenali FE
 * @param createdAt     waktu kartu dibuat
 */
public record KartuTamuSaldoDetail(
        Long id,
        String nomorKartu,
        String uid,
        String labelPemegang,
        long saldo,
        String status,
        Instant createdAt) {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_BLOCKED = "BLOCKED";

    public static KartuTamuSaldoDetail dari(BarisKartuTamu b) {
        return new KartuTamuSaldoDetail(b.getKartuId(), b.getNomorKartu(), b.getRfidUid(),
                b.getLabelPemegang(), b.getSaldo(),
                b.isAktif() ? STATUS_ACTIVE : STATUS_BLOCKED, b.getDibuatPada());
    }
}
