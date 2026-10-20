package com.asqi.scholia_kantin_be.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Riwayat satu siswa untuk menjawab <b>komplain orang tua</b> (issue #223).
 *
 * <p>Kontrak mengikuti tipe FE {@code {profil, riwayat}}; isinya disusun dari
 * data yang <b>sudah</b> dimiliki kantin-be ({@code saldo_cache},
 * {@code saldo_ledger}, {@code transaksi}, {@code limit_harian},
 * {@code blokir_kartu}) — bukan query baru dari nol.
 *
 * <p><b>Batas data (ADR-0002).</b> Kantin-be tidak menyimpan data induk siswa:
 * {@code nis}, {@code nama}, {@code kelas}, {@code fotoUrl}, {@code parentName},
 * {@code parentPhone} berasal dari admin-be sehingga bernilai {@code null} sampai
 * integrasi siswa tersedia (Q7). FE sebaiknya menampilkan identitas dari
 * datanya sendiri; {@code siswaId} selalu ada.
 *
 * @param profil  identitas + posisi saldo/limit siswa saat ini
 * @param riwayat mutasi saldo dalam rentang, terbaru lebih dulu
 */
public record LaporanRiwayatSiswa(Profil profil, List<Baris> riwayat) {

    /** Profil &amp; posisi keuangan siswa saat ini. */
    public record Profil(
            Long siswaId,
            String nis,
            String nama,
            String kelas,
            String fotoUrl,
            long saldo,
            long belanjaHariIni,
            long limitHarian,
            boolean isBlocked,
            String parentName,
            String parentPhone,
            String catatanKontrol) {
    }

    /** Satu baris riwayat (dari {@code saldo_ledger}). */
    public record Baris(
            String id,
            OffsetDateTime waktu,
            String jenis,
            String arah,
            long nominal,
            long saldoSetelah,
            String titikKasir,
            String petugas,
            String referensiId,
            String keterangan,
            List<Item> items) {
    }

    /** Rincian belanja satu transaksi (hanya untuk baris belanja/void). */
    public record Item(Long menuId, String nama, Integer qty, Long harga, Long subtotal) {
    }
}
