package com.asqi.scholia_kantin_be.helper;

import java.util.Locale;

/**
 * Bentuk kanonik UID kartu RFID.
 *
 * <p><b>Kenapa ada (issue #346).</b> Kolom {@code kartu.uid} bertipe
 * {@code VARCHAR} sehingga perbandingannya <b>case-sensitive</b>, sementara
 * pembaca kartu bisa mengirim huruf kecil ({@code 04a1b2c3d4}) atau huruf
 * campur. Tanpa normalisasi, lookup kartu dan tap menjawab 404 "Kartu tidak
 * dikenali" untuk kartu yang sebenarnya terdaftar.
 *
 * <p>Semua jalur yang menerima UID dari klien (lookup kasir, cek saldo, tap)
 * wajib melewatkan nilai ini lebih dulu supaya nilai yang dicari — dan yang
 * disimpan pada transaksi/pending — selalu bentuk kanonik: tanpa spasi di ujung
 * dan huruf kapital.
 */
public final class UidKartu {

    private UidKartu() {
    }

    /**
     * @param uid UID mentah dari klien (boleh null)
     * @return UID tanpa spasi ujung dalam huruf kapital; {@code null} bila masukan null
     */
    public static String normalisasi(String uid) {
        return uid == null ? null : uid.trim().toUpperCase(Locale.ROOT);
    }
}
