package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.helper.JamKantin;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Utilitas bersama modul laporan: perhitungan periode, persentase, dan
 * pembulatan aman-null.
 *
 * <p>Modul laporan dipecah per domain (lihat <code>LaporanPenjualanService</code>,
 * <code>LaporanStokService</code>, <code>LaporanSaldoService</code>,
 * <code>LaporanSiswaService</code>, <code>LaporanSetoranService</code>,
 * <code>LaporanPembatalanService</code>). Tiga helper di bawah dipakai hampir
 * semua domain, jadi ditulis sekali di sini daripada disalin enam kali.
 */
final class LaporanHelper {

    /**
     * Batas maksimum rentang satu permintaan laporan (hari).
     *
     * <p>Mencegah query agregat/ekspor dengan rentang puluhan tahun yang memicu
     * full-table scan dan alokasi memori besar di generator Excel (OWASP A06:2025,
     * CWE-400 — SEC-03 issue #388).
     */
    static final int MAKS_HARI = 366;

    private LaporanHelper() {
    }

    /**
     * Rentang waktu efektif laporan: {@code [awal, akhir)} dengan akhir eksklusif.
     *
     * <p>Bila {@code tanggal} maupun {@code dari} kosong, periode dianggap
     * <b>hari ini</b> menurut zona sekolah. Bila hanya {@code sampai} yang
     * kosong, default-nya {@code awal + 1 hari}.
     *
     * @param jam     sumber "hari ini" &amp; zona sekolah
     * @param tanggal tanggal tunggal (opsional, dipakai bila {@code dari} kosong)
     * @param dari    awal periode eksplisit (opsional)
     * @param sampai  akhir periode eksplisit, eksklusif (opsional)
     * @return array dua elemen {@code [awal, akhir]}
     */
    static OffsetDateTime[] rentang(JamKantin jam, LocalDate tanggal,
                                    OffsetDateTime dari, OffsetDateTime sampai) {
        LocalDate tgl = (tanggal != null) ? tanggal : jam.hariIni();
        OffsetDateTime awal = (dari != null) ? dari : tgl.atStartOfDay(jam.zona()).toOffsetDateTime();
        OffsetDateTime akhir = (sampai != null) ? sampai : awal.plusDays(1);

        // SEC-03 (#388): tolak rentang terbalik & rentang tak terbatas.
        if (!akhir.isAfter(awal)) {
            throw new InvalidOperationException(
                    "Rentang tanggal tidak valid: 'dari' harus sebelum 'sampai'");
        }
        if (akhir.isAfter(awal.plusDays(MAKS_HARI))) {
            throw new InvalidOperationException(
                    "Rentang tanggal terlalu panjang: maksimal " + MAKS_HARI + " hari per permintaan");
        }
        return new OffsetDateTime[]{awal, akhir};
    }

    /**
     * Persentase dua desimal; {@code 0.0} bila pembagi nol.
     *
     * @param bagian pembilang
     * @param total  pembagi
     * @return persentase dibulatkan dua desimal
     */
    static double persen(long bagian, long total) {
        if (total == 0) {
            return 0.0;
        }
        return Math.round(bagian * 10_000.0 / total) / 100.0;
    }

    /**
     * Ubah {@code Long} yang boleh null menjadi {@code 0}.
     *
     * @param nilai nilai nullable
     * @return nilai, atau {@code 0L} bila null
     */
    static long nolJikaNull(Long nilai) {
        return nilai == null ? 0L : nilai;
    }
}