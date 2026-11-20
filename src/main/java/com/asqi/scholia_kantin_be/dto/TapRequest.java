package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.MetodeBayar;
import com.asqi.scholia_kantin_be.validation.UidKartuValid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Permintaan satu kali tap di kasir (PRD §6.1).
 *
 * <p><b>Idempotency (PRD §11.3):</b> {@code idempotencyKey} dibuat oleh
 * <b>klien kasir</b>, unik per tap. Tap ganda dengan key sama → dikembalikan
 * hasil yang sama (tidak memotong saldo dua kali).
 *
 * <p><b>Key yang sama = percobaan yang sama.</b> Replay mengembalikan transaksi
 * yang <i>sudah tercatat</i>, termasuk bila {@code metodeBayar}/
 * {@code uangDiterima} di request ulang berbeda — server tidak membandingkan
 * isi request, jadi perubahan metode bayar butuh key baru. Ini disengaja: satu
 * key mewakili satu percobaan bayar, dan membandingkan isi akan membuat retry
 * setelah kegagalan jaringan ikut gagal.
 */
@Data
public class TapRequest {

    /**
     * UID kartu yang dibaca bridge RFID. <b>Boleh kosong</b> bila pembayaran
     * tunai (C.2a): transaksi tunai sah tanpa kartu, kasir cukup mengisi
     * {@link #uangDiterima}. Bila diisi, bentuknya diperiksa {@link UidKartuValid}.
     */
    @UidKartuValid
    private String rfidUid;

    /**
     * Cara bayar. {@code null} dianggap {@code SALDO} agar klien lama tidak
     * berubah perilakunya (tetap potong saldo).
     */
    private MetodeBayar metodeBayar;

    /**
     * Uang tunai diterima kasir — <b>wajib bila {@code metodeBayar=TUNAI}</b>
     * dan tidak boleh kurang dari total (kembalian = uangDiterima − total).
     */
    private Long uangDiterima;

    /**
     * Nama pembeli untuk struk tunai tanpa kartu (opsional, maks 100 karakter).
     * Disimpan di {@code transaksi.nama_pembeli} (V38) dan dipakai struk/cetak
     * ulang; tidak pernah jadi identitas saldo.
     */
    @Size(max = 100, message = "Nama pembeli maksimal 100 karakter")
    private String namaPembeli;

    /**
     * Kasir menyatakan sadar stok sistem kurang/habis karena barangnya masih ada
     * di gudang/cadangan (ADR-0013, butir C.1 ceklis review).
     *
     * <p>Bila {@code true}: tap tidak ditolak walau stok tidak cukup, dan stok
     * dicatat sebagai mutasi {@link com.asqi.scholia_kantin_be.enums.JenisMutasiStok#PENJUALAN_MINUS}
     * (boleh negatif sampai opname menutupnya). Default {@code false} = perilaku
     * lama: tap ditolak dengan pesan "habis"/"tidak cukup".
     */
    private Boolean izinkanStokMinus;

    /** Alasan singkat penjualan melebihi catatan (opsional, maks 200) — ikut jejak audit. */
    @Size(max = 200, message = "Alasan stok minus maksimal 200 karakter")
    private String alasanStokMinus;

    /** Titik kasir tempat tap terjadi (wajib untuk audit & sesi). */
    @NotNull(message = "Titik kasir wajib diisi")
    private Long titikKasirId;

    /** Daftar item yang dibeli (id menu + qty). */
    @NotEmpty(message = "Minimal satu item harus dipilih")
    private List<ItemTap> items;

    /**
     * Kunci idempotency dari klien. Bila kosong, server menolak (wajib) —
     * jangan biarkan server membuatnya, karena retry klien tak akan idempoten.
     */
    @NotBlank(message = "Idempotency key wajib diisi")
    @Size(max = 64, message = "Idempotency key maksimal 64 karakter")
    private String idempotencyKey;

    /** Metode bayar efektif — {@code null} diperlakukan {@code SALDO}. */
    public MetodeBayar metodeBayarEfektif() {
        return metodeBayar == null ? MetodeBayar.SALDO : metodeBayar;
    }

    /** {@code true} bila pembayaran tunai (tanpa potong saldo) — C.2a. */
    public boolean tunai() {
        return metodeBayarEfektif() == MetodeBayar.TUNAI;
    }

    /**
     * Kartu wajib ada untuk pembayaran <b>saldo</b>; pembayaran tunai boleh
     * tanpa kartu (C.2a). Ditegakkan di lapisan validasi agar klien menerima
     * <b>400</b> dengan pesan jelas, bukan 409 "kartu tidak dikenal" dari alur tap.
     */
    @AssertTrue(message = "UID kartu wajib diisi untuk pembayaran saldo")
    public boolean isKartuWajibTerisi() {
        return tunai() || (rfidUid != null && !rfidUid.isBlank());
    }

    /** Satu baris item dalam tap. */
    @Data
    public static class ItemTap {
        @NotNull(message = "ID menu wajib diisi")
        private Long menuId;

        @NotNull(message = "Jumlah wajib diisi")
        private Integer qty;
    }
}
