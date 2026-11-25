package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.dto.laporan.BarisPenjualan;
import com.asqi.scholia_kantin_be.dto.laporan.BarisPenjualanDimensi;
import com.asqi.scholia_kantin_be.dto.laporan.LaporanPerSiswa;
import com.asqi.scholia_kantin_be.dto.laporan.RingkasanPenjualan;
import com.asqi.scholia_kantin_be.enums.AktorKantin;

import java.util.List;

/**
 * Penyamaran atribut finansial pada laporan penjualan (SEC-02, issue #388).
 *
 * <p>PRD §9.5 memisahkan dua baris akses: <b>Penjualan</b> (Bendahara, Kepsek,
 * Admin, Pengelola) dan <b>Laba kotor</b> (Bendahara, Kepsek, Pengelola — <b>tanpa
 * Admin</b>). Karena itu endpoint penjualan tetap boleh dibaca Admin, tetapi
 * {@code modal}/{@code hpp}/{@code laba}/{@code margin} dijawab {@code null}
 * untuk peran yang tidak berhak, bukan angka palsu {@code 0}.
 */
public final class LaporanMasking {

    private LaporanMasking() {
    }

    /**
     * Peran yang berhak melihat modal, HPP, laba, dan margin (PRD §9.5 baris
     * "Laba kotor"). Bendahara = {@link AktorKantin#TU_SEKOLAH}.
     */
    public static boolean bolehLihatLaba(AktorKantin peran) {
        return peran == AktorKantin.TU_SEKOLAH
                || peran == AktorKantin.PENGELOLA_KANTIN
                || peran == AktorKantin.KEPSEK;
    }

    /** Salinan ringkasan tanpa total modal, laba, dan margin. */
    public static RingkasanPenjualan tanpaLaba(RingkasanPenjualan r) {
        if (r == null) {
            return null;
        }
        return new RingkasanPenjualan(r.dari(), r.sampai(), r.jumlahTransaksi(), r.penjualanBruto(),
                r.jumlahRefund(), r.nilaiRefund(), r.penjualanBersih(), null, null, null);
    }

    /** Salinan baris penjualan tanpa modal dan margin. */
    public static List<BarisPenjualan> tanpaLaba(List<BarisPenjualan> baris) {
        if (baris == null) {
            return null;
        }
        return baris.stream()
                .map(b -> new BarisPenjualan(b.kunciId(), b.nama(), b.qty(), b.total(),
                        b.hargaSatuan(), null, null))
                .toList();
    }

    /** Salinan baris dimensi (titik/petugas) tanpa HPP dan laba kotor. */
    public static List<BarisPenjualanDimensi> tanpaLabaDimensi(List<BarisPenjualanDimensi> baris) {
        if (baris == null) {
            return null;
        }
        return baris.stream()
                .map(b -> new BarisPenjualanDimensi(b.kunciId(), b.nama(), b.jumlahTransaksi(),
                        b.nilai(), null, null))
                .toList();
    }

    /**
     * Salinan laporan per siswa tanpa HPP pada ringkasan maupun tiap transaksi
     * (issue #402). Angka belanja, saldo, dan mutasi tetap utuh.
     *
     * <p>Memakai {@code null}, bukan {@code 0}, supaya pembaca tidak mengira
     * modalnya nol. DTO asli tidak dimutasi.
     */
    public static LaporanPerSiswa tanpaHpp(LaporanPerSiswa laporan) {
        if (laporan == null) {
            return null;
        }
        LaporanPerSiswa.Ringkasan asli = laporan.ringkasan();
        LaporanPerSiswa.Ringkasan ringkasan = asli == null ? null
                : new LaporanPerSiswa.Ringkasan(asli.jumlahTransaksiSukses(),
                        asli.nilaiBelanjaSukses(), asli.jumlahTransaksiVoid(), asli.nilaiVoid(),
                        asli.totalTopup(), null);
        List<LaporanPerSiswa.BarisTransaksi> transaksi = laporan.transaksi() == null ? null
                : laporan.transaksi().stream()
                        .map(t -> new LaporanPerSiswa.BarisTransaksi(t.id(), t.status(),
                                t.total(), null, t.petugasId(), t.titikKasirId(),
                                t.alasanVoid(), t.waktu()))
                        .toList();
        return new LaporanPerSiswa(laporan.subjekTipe(), laporan.siswaId(), laporan.saldo(),
                laporan.periode(), ringkasan, transaksi, laporan.mutasiSaldo());
    }
}
