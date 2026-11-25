package com.asqi.scholia_kantin_be.dto.laporan;

import java.time.OffsetDateTime;

/**
 * Laporan rekonsiliasi saldo (PRD §9.5, invariant §5).
 *
 * <p>Memuat dua hal: <b>(a)</b> arus saldo pada periode (top-up, penjualan,
 * void, koreksi, refund, penyesuaian) dan <b>(b)</b> pemeriksaan invariant
 * menyeluruh: {@code Σ KREDIT − Σ DEBIT (seluruh waktu) == saldo mengendap}.
 * Bila tidak sama, {@link #seimbang} {@code false} dan {@link #selisih} ≠ 0 →
 * FE menampilkan peringatan mencolok.
 *
 * <p><b>Batas periode hanya memengaruhi (a).</b> {@code dari}/{@code sampai}
 * tidak mengubah {@link #selisih}, {@link #seimbang}, {@link #saldoMengendap},
 * {@link #totalKreditSemua}, maupun {@link #totalDebitSemua} — kelima field itu
 * dihitung <b>all-time</b> untuk tenant. Memilih periode tanpa mutasi hanya
 * mengosongkan field arus; ia <b>tidak</b> membuat selisih jadi nol otomatis.
 *
 * @param dari               awal periode arus (inklusif; tidak memengaruhi invariant)
 * @param sampai             akhir periode arus (eksklusif; tidak memengaruhi invariant)
 * @param topupOnline        Σ KREDIT TOPUP_ONLINE pada periode
 * @param topupTunai         Σ KREDIT TOPUP_TUNAI pada periode
 * @param penjualan          Σ DEBIT PENJUALAN pada periode
 * @param voidPenjualan      Σ KREDIT VOID_PENJUALAN pada periode
 * @param koreksiMasuk       Σ KREDIT KOREKSI/PENYESUAIAN pada periode
 * @param koreksiKeluar      Σ DEBIT KOREKSI/PENYESUAIAN pada periode
 * @param refund             Σ DEBIT REFUND pada periode
 * @param transfer           Σ mutasi TRANSFER pada periode (net 0, info)
 * @param saldoMengendap     saldo titipan berjalan (semua subjek, seluruh waktu)
 * @param totalKreditSemua   Σ KREDIT seluruh waktu
 * @param totalDebitSemua    Σ DEBIT seluruh waktu
 * @param selisih            (Σ KREDIT − Σ DEBIT) − saldoMengendap; 0 = seimbang
 * @param seimbang           {@code true} bila {@link #selisih} == 0
 */
public record RingkasanRekonsiliasi(
        OffsetDateTime dari,
        OffsetDateTime sampai,
        long topupOnline,
        long topupTunai,
        long penjualan,
        long voidPenjualan,
        long koreksiMasuk,
        long koreksiKeluar,
        long refund,
        long transfer,
        long saldoMengendap,
        long totalKreditSemua,
        long totalDebitSemua,
        long selisih,
        boolean seimbang) {
}
