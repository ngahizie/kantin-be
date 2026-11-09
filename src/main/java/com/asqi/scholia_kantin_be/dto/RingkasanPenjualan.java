package com.asqi.scholia_kantin_be.dto;

import java.time.OffsetDateTime;

/**
 * Ringkasan penjualan &amp; laba untuk satu periode (PRD §9.5).
 *
 * <p>Istilah sengaja dibuat sederhana dan seragam: <b>refund</b> (bukan void),
 * <b>modal</b> (bukan HPP), <b>laba</b> (bukan laba kotor), dan <b>margin</b>
 * dalam persen — dihitung di server supaya aplikasi tidak perlu menebak.
 *
 * @param dari            awal periode (inklusif)
 * @param sampai          akhir periode (eksklusif)
 * @param jumlahTransaksi banyak transaksi sukses
 * @param penjualanBruto  total uang masuk dari transaksi sukses
 * @param jumlahRefund    banyak transaksi yang di-refund
 * @param nilaiRefund     total nilai transaksi yang di-refund (info)
 * @param penjualanBersih penjualan yang dihitung (refund tidak pernah masuk bruto)
 * @param totalModal      total modal barang yang terjual
 * @param laba            penjualanBersih − totalModal
 * @param marginPersen    laba ÷ penjualanBersih × 100 (2 desimal; 0 bila kosong)
 */
public record RingkasanPenjualan(
        OffsetDateTime dari,
        OffsetDateTime sampai,
        long jumlahTransaksi,
        long penjualanBruto,
        long jumlahRefund,
        long nilaiRefund,
        long penjualanBersih,
        long totalModal,
        long laba,
        double marginPersen) {
}
