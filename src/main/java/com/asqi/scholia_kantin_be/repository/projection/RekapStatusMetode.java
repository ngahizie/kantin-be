package com.asqi.scholia_kantin_be.repository.projection;

import com.asqi.scholia_kantin_be.enums.MetodeBayar;
import com.asqi.scholia_kantin_be.enums.StatusTransaksi;

/**
 * Rekap jumlah &amp; total transaksi per <b>status × metode bayar</b> dalam
 * satu sesi kasir (PRD §6.4, C.2d).
 *
 * <p>Dibuat terpisah dari {@link RekapStatus} karena query-nya berbeda
 * granularitas: {@link RekapStatus} dipakai laporan penjualan yang hanya
 * mengelompokkan per status, sedangkan rekap sesi perlu memisahkan
 * <b>TUNAI</b> (uang fisik yang seharusnya ada di laci) dari <b>SALDO</b>
 * (potongan saldo siswa, tanpa uang fisik).
 *
 * <p>Projection dipakai agar urutan kolom query tidak bisa ketukar diam-diam;
 * alias kolom di query wajib sama dengan nama getter.
 */
public interface RekapStatusMetode {

    /** Status transaksi yang direkap. */
    StatusTransaksi getStatus();

    /** Metode bayar baris ini. */
    MetodeBayar getMetodeBayar();

    /** Banyaknya transaksi pada kombinasi status × metode ini. */
    Long getJumlah();

    /** Σ total nominal pada kombinasi status × metode ini. */
    Long getTotal();
}
