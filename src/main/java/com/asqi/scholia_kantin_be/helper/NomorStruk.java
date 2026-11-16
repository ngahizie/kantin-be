package com.asqi.scholia_kantin_be.helper;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Nomor struk transaksi — satu sumber untuk <b>struk kasir</b> dan
 * <b>riwayat sesi</b>, supaya nomor yang tercetak sama dengan yang tampil
 * di daftar transaksi.
 *
 * <p>Bentuk: {@code TRX-yyyyMMdd-<id transaksi>}. ID transaksi ikut penuh
 * (bukan dipotong) agar nomor tetap unik — nomor ini nanti dipakai untuk
 * mencocokkan struk saat refund/void.
 *
 * <p><b>Tanggal selalu WIB.</b> Nilai {@code waktu} bisa datang dengan offset
 * berbeda untuk instant yang sama: respons tap memakai zona JVM
 * ({@code OffsetDateTime.now()}), sedangkan nilai yang dibaca ulang dari
 * database memakai offset hasil konversi Hibernate. Kalau tanggal diformat apa
 * adanya, dua sisi bisa menghasilkan tanggal berbeda satu hari saat instant-nya
 * jatuh di 17:00–24:00 UTC, sehingga validasi refund menolak kode struk yang
 * benar. Karena itu instant dinormalkan ke {@code Asia/Jakarta} sebelum
 * diformat.
 */
public final class NomorStruk {

    private static final ZoneId ZONA_STRUK = ZoneId.of("Asia/Jakarta");

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
        String tanggal = waktu == null
                ? ""
                : waktu.atZoneSameInstant(ZONA_STRUK).format(TANGGAL) + "-";
        return "TRX-" + tanggal + transaksiId;
    }
}
