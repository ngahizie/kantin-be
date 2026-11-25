package com.asqi.scholia_kantin_be.dto.laporan;

import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.MutasiSaldo;
import com.asqi.scholia_kantin_be.model.Transaksi;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Laporan <b>Per siswa</b> (PRD §9.5, issue #117): riwayat lengkap satu siswa
 * pada periode — ringkasan, daftar transaksi, dan mutasi saldo — untuk menjawab
 * komplain orang tua. Akses Bendahara/Admin.
 *
 * <p><b>HPP (issue #402):</b> {@code totalHpp} bertipe {@link Long} dan bernilai
 * {@code null} bila pemanggil tidak berhak melihat laba (Admin Sekolah). Ini
 * sengaja bukan angka {@code 0} — nol berarti "modalnya nol", bukan "data
 * disembunyikan". Lihat {@code LaporanMasking#tanpaHpp}.
 *
 * @param subjekTipe  tipe subjek — selalu {@code SISWA} sejak C.2b
 * @param siswaId    id subjek
 * @param saldo       saldo berjalan saat ini
 * @param periode     label periode (mis. "2025-01-01 s/d 2025-01-31")
 * @param ringkasan   agregat periode
 * @param transaksi   daftar transaksi periode (urut waktu menaik)
 * @param mutasiSaldo daftar mutasi saldo periode (urut waktu menaik)
 */
public record LaporanPerSiswa(
        SubjekTipe subjekTipe,
        Long siswaId,
        long saldo,
        String periode,
        Ringkasan ringkasan,
        List<BarisTransaksi> transaksi,
        List<BarisMutasi> mutasiSaldo) {

    /** Agregat periode untuk satu siswa; {@code totalHpp} null bila disamarkan. */
    public record Ringkasan(
            long jumlahTransaksiSukses,
            long nilaiBelanjaSukses,
            long jumlahTransaksiVoid,
            long nilaiVoid,
            long totalTopup,
            Long totalHpp) {
    }

    /** Satu transaksi siswa pada periode; {@code totalHpp} null bila disamarkan. */
    public record BarisTransaksi(
            Long id,
            StatusTransaksi status,
            long total,
            Long totalHpp,
            Long petugasId,
            Long titikKasirId,
            String alasanVoid,
            OffsetDateTime waktu) {

        public static BarisTransaksi dari(Transaksi t) {
            return new BarisTransaksi(t.getId(), t.getStatus(), t.getTotal(), t.getTotalHpp(),
                    t.getPetugasId(), t.getTitikKasirId(), t.getAlasanVoid(), t.getWaktu());
        }
    }

    /** Satu mutasi saldo siswa pada periode. */
    public record BarisMutasi(
            Long id,
            JenisMutasiSaldo jenis,
            String arah,
            long nominal,
            long saldoSetelah,
            Long transaksiId,
            String keterangan,
            OffsetDateTime waktu) {

        public static BarisMutasi dari(MutasiSaldo l) {
            return new BarisMutasi(l.getId(), l.getJenis(),
                    l.getArah() == null ? null : l.getArah().name(),
                    l.getNominal() == null ? 0L : l.getNominal(),
                    l.getSaldoSetelah() == null ? 0L : l.getSaldoSetelah(),
                    l.getTransaksiId(), l.getKeterangan(), l.getWaktu());
        }
    }
}
