package com.asqi.scholia_kantin_be.helper;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Nomor struk transaksi — satu sumber untuk <b>struk kasir</b> dan
 * <b>riwayat sesi</b>, supaya nomor yang tercetak sama dengan yang tampil
 * di daftar transaksi.
 *
 * <p>Bentuk: {@code TRX-yyyyMMdd-<id transaksi>}. ID transaksi ikut penuh
 * (bukan dipotong) agar nomor tetap unik — nomor ini nanti dipakai untuk
 * mencocokkan struk saat refund/void.
 */
public final class NomorStruk {

    private static final DateTimeFormatter TANGGAL = DateTimeFormatter.ofPattern("yyyyMMdd");

    private NomorStruk() {
    }

    /**
     * @param transaksiId ID transaksi (null → null)
     * @param waktu       waktu transaksi (null → tanggal dikosongkan)
     */
    public static String dari(Long transaksiId, OffsetDateTime waktu) {
        if (transaksiId == null) {
            return null;
        }
        String tanggal = waktu == null ? "" : waktu.format(TANGGAL) + "-";
        return "TRX-" + tanggal + transaksiId;
    }
}
