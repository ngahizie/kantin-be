package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.MetodeBayar;
import com.asqi.scholia_kantin_be.enums.MetodeRequestKartu;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Hasil satu kali tap — dikirim ke layar kasir (PRD §6.2).
 *
 * <p>Respons <b>harus</b> memuat identitas ringkas pemilik kartu (nama, kelas,
 * foto) untuk ditampilkan ±3 detik, serta sisa saldo agar kasir bisa
 * membatalkan bila foto tidak cocok.
 */
@Data
@Builder
public class TapResponse {

    /** ID transaksi yang baru dibuat. */
    private Long transaksiId;

    private SubjekTipe subjekTipe;

    /** Nama pemilik kartu (null untuk transaksi tunai tanpa kartu). */
    private String nama;

    /** Kelas (khusus siswa; null untuk tunai tanpa kartu). */
    private String kelas;

    /** URL foto untuk verifikasi visual (null bila tidak ada). */
    private String fotoUrl;

    /** Total belanja tap ini (rupiah). */
    private Long total;

    /** Sisa saldo setelah tap (rupiah). */
    private Long saldoSisa;

    /** Rincian item (untuk struk/notifikasi). */
    private List<String> namaItem;

    /** Cara identitas dikenali (UID / nomor kartu). */
    private MetodeRequestKartu metode;

    /**
     * {@code true} bila sekolah mengaktifkan konfirmasi manual dan tap ini
     * <b>belum</b> memotong saldo/stok — petugas harus mengonfirmasi (PRD §6.1).
     */
    @Builder.Default
    private boolean menungguKonfirmasi = false;

    /** ID baris pending yang harus dikonfirmasi (diisi bila {@link #menungguKonfirmasi}). */
    private Long pendingId;

    /** Nama titik kasir tempat transaksi terjadi (untuk struk). */
    private String titikKasir;

    /** Nomor struk: {@code TRX-yyyyMMdd-<id>} — sama dengan yang tampil di riwayat sesi. */
    private String nomorStruk;

    /** Waktu transaksi (untuk struk). */
    private OffsetDateTime waktu;

    /**
     * Cara bayar transaksi (C.2a). {@code null} pada respons lama dibaca klien
     * sebagai {@code SALDO}. Selalu terisi untuk transaksi yang baru dibuat.
     */
    private MetodeBayar metodeBayar;

    /** Uang tunai diterima — hanya diisi bila {@link #metodeBayar} = TUNAI. */
    private Long uangDiterima;

    /** Kembalian (uangDiterima − total) — hanya bila {@link #metodeBayar} = TUNAI. */
    private Long kembalian;

    /**
     * Nama pembeli yang diketik kasir untuk transaksi TUNAI tanpa kartu (C.2a).
     * {@code null} bila pembayaran saldo, kartu siswa terbaca, atau kasir tidak
     * mengisi nama.
     */
    private String namaPembeli;
}
