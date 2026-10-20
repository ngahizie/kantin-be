package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Satu baris <b>daftar transaksi pada sesi yang sudah DITUTUP</b> — kandidat
 * koreksi bendahara (PRD §6.3, §9.2; issue <b>#230</b>) untuk
 * {@code GET /api/transaksi/sesi-tutup}.
 *
 * <p><b>Latar.</b> {@code KoreksiBendaharaPage.tsx} memanggil
 * {@code GET /api/transaksi/sesi-tutup} dengan {@code .catch(() => null)}; karena
 * endpoint-nya belum ada, halaman jatuh ke {@code MOCK_KOREKSI_BENDAHARA} —
 * bendahara melihat <b>data palsu di mode live tanpa terlihat sebagai error</b>.
 * Aksi koreksinya sendiri sudah ada
 * ({@code POST /api/kasir/transaksi/{transaksiId}/koreksi}); yang hilang adalah
 * cara menemukan transaksinya.
 *
 * <p><b>Kontrak = milik FE, jadi nama field mengikuti FE ({@code snake_case}).</b>
 * Berbeda dari endpoint kantin-be lain yang memakai {@code camelCase}: halaman
 * pemanggil membaca {@code subjek_nama}, {@code titik_kasir}, {@code items[].menu_id},
 * dst. langsung (tanpa lapisan pemetaan), sehingga nama field di sini <b>wajib</b>
 * sama persis. Semua nilai di bawah tetap berasal dari entitas lokal yang
 * tenant-scoped — tidak ada data karangan.
 *
 * <p><b>Field yang tidak tersedia di kantin-be</b> (bukan karena bug):
 * {@code petugas_kasir} (nama petugas ada di admin-be, ADR-0002) dikirim
 * {@code ""}; {@code subjek_nama}/{@code subjek_info} diisi bila dapat
 * diresolusi (Kartu Tamu lokal, siswa lewat {@code CariSiswaPort}) dan
 * {@code ""} bila tidak — FE memanggil {@code .toLowerCase()} pada keduanya
 * sehingga {@code null} akan membuat halaman crash.
 *
 * @param id                   id transaksi (dipakai FE sebagai {@code transaksiId} saat koreksi)
 * @param waktu                waktu transaksi (zona sekolah)
 * @param sesiKasirId          sesi kasir asal (sudah DITUTUP)
 * @param titikKasir           nama titik kasir; {@code ""} bila titik sudah dihapus
 * @param petugasKasir         nama petugas; selalu {@code ""} (data admin-be)
 * @param subjekTipe           SISWA / KARTU_TAMU
 * @param subjekId             id subjek pemilik saldo
 * @param subjekNama           nama siswa / label pemegang kartu; {@code ""} bila tak diketahui
 * @param subjekInfo           keterangan tambahan: siswa = {@code "kelas (NIS: nis)"},
 *                             kartu tamu = nomor kartu
 * @param total                nilai transaksi (rupiah)
 * @param items                rincian belanja (snapshot)
 * @param status               {@code SELESAI} (SUKSES) / {@code DIKOREKSI} (VOID)
 * @param koreksiReferensiId   referensi mutasi koreksi bila transaksi sudah dikoreksi
 */
public record TransaksiSesiTutupItem(
        @JsonProperty("id") String id,
        @JsonProperty("waktu") OffsetDateTime waktu,
        @JsonProperty("sesi_kasir_id") Long sesiKasirId,
        @JsonProperty("titik_kasir") String titikKasir,
        @JsonProperty("petugas_kasir") String petugasKasir,
        @JsonProperty("subjek_tipe") SubjekTipe subjekTipe,
        @JsonProperty("subjek_id") Long subjekId,
        @JsonProperty("subjek_nama") String subjekNama,
        @JsonProperty("subjek_info") String subjekInfo,
        @JsonProperty("total") Long total,
        @JsonProperty("items") List<Item> items,
        @JsonProperty("status") String status,
        @JsonProperty("koreksi_referensi_id") String koreksiReferensiId) {

    /** Status yang dibaca FE: transaksi SUKSES = selesai, VOID = sudah dikoreksi. */
    public static String statusFe(StatusTransaksi status) {
        return status == StatusTransaksi.VOID ? "DIKOREKSI" : "SELESAI";
    }

    /** Rincian belanja satu transaksi (snapshot nama, harga, qty saat transaksi). */
    public record Item(
            @JsonProperty("menu_id") Long menuId,
            @JsonProperty("nama") String nama,
            @JsonProperty("qty") Integer qty,
            @JsonProperty("harga") Long harga,
            @JsonProperty("subtotal") Long subtotal) {
    }
}
