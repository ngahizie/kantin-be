package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.Transaksi;
import com.asqi.scholia_kantin_be.model.TransaksiItem;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Satu transaksi dalam <b>riwayat satu sesi kasir</b> (issue #229) — bentuk
 * mengikuti tipe FE {@code TransaksiSesiItem}: daftar <b>tanpa paginasi</b>
 * (terikat satu sesi) beserta rincian item belanja.
 *
 * <p><b>Batas data (ADR-0002).</b> Nama/kelas/NIS/foto siswa tidak disimpan
 * kantin-be, jadi {@code pembeliNama} untuk subjek {@code SISWA} diisi
 * {@code "Siswa #<id>"} dan field identitas lain {@code null}; untuk
 * {@code KARTU_TAMU} dipakai label pemegang (fallback nomor kartu). Nama petugas
 * juga tidak tersedia — hanya {@code voidOleh} (id akun) yang bisa diisi.
 */
public record TransaksiSesiItem(
        Long id,
        Long transaksiId,
        String nomorReferensi,
        OffsetDateTime waktu,
        String pembeliTipe,
        String pembeliNama,
        String pembeliKelas,
        String pembeliNis,
        String pembeliFotoUrl,
        String kartuUid,
        List<Item> items,
        long total,
        StatusTransaksi status,
        String voidAlasan,
        OffsetDateTime voidAt,
        String voidOleh) {

    /** Rincian satu item belanja. */
    public record Item(Long menuId, String namaMenu, Integer qty, Long hargaSatuan, Long subtotal) {
    }

    private static final DateTimeFormatter TANGGAL = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Proyeksi entitas → DTO. Nama titik kasir &amp; pemegang kartu diisi service
     * secara batch (hindari N+1).
     */
    public static TransaksiSesiItem dari(Transaksi t, String namaTitik,
                                         String namaPemegang, List<TransaksiItem> item) {
        String pembeliNama = t.getSubjekTipe() == SubjekTipe.KARTU_TAMU
                ? (namaPemegang != null ? namaPemegang : "Kartu Tamu #" + t.getSubjekId())
                : "Siswa #" + t.getSubjekId();

        return new TransaksiSesiItem(
                t.getId(),
                t.getId(),
                "TRX-" + t.getWaktu().format(TANGGAL) + "-" + String.format("%04d", t.getId()),
                t.getWaktu(),
                t.getSubjekTipe() == null ? null : t.getSubjekTipe().name(),
                pembeliNama,
                null,
                null,
                null,
                t.getKartuUid(),
                item.stream()
                        .map(i -> new Item(i.getMenuId(), i.getNamaMenu(), i.getQty(),
                                i.getHargaJual(), i.getSubtotal()))
                        .toList(),
                t.getTotal() == null ? 0L : t.getTotal(),
                t.getStatus(),
                t.getAlasanVoid(),
                t.getVoidAt(),
                t.getVoidOleh() == null ? null : String.valueOf(t.getVoidOleh()));
    }
}
