package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.ArahStok;
import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;
import com.asqi.scholia_kantin_be.model.RiwayatStok;

import java.time.OffsetDateTime;

/**
 * Baris laporan <b>barang masuk</b> &amp; kartu stok (PRD §9.5).
 *
 * <p><b>Kenapa ada (issue #362).</b> Service sebelumnya mengembalikan entity
 * {@link RiwayatStok}, sehingga pemanggil (controller/ekspor/test) terikat pada
 * bentuk tabel: kolom baru atau relasi baru di entity otomatis mengubah kontrak
 * laporan. Read model ini memuat hanya kolom yang dipakai laporan, dengan
 * factory {@link #dari(RiwayatStok)} sebagai satu-satunya titik pemetaan.
 */
public record BarisBarangMasuk(
        Long id,
        Long menuId,
        OffsetDateTime waktu,
        JenisMutasiStok jenis,
        ArahStok arah,
        int qty,
        int stokSetelah,
        Long hppSnapshot,
        Long hargaBeliSatuan,
        String referensiId,
        String alasan,
        String aktorNama) {

    /** Proyeksikan satu baris mutasi stok; nilai null pada qty/stok → 0. */
    public static BarisBarangMasuk dari(RiwayatStok m) {
        return new BarisBarangMasuk(
                m.getId(),
                m.getMenuId(),
                m.getWaktu(),
                m.getJenis(),
                m.getArah(),
                m.getQty() == null ? 0 : m.getQty(),
                m.getStokSetelah() == null ? 0 : m.getStokSetelah(),
                m.getHppSnapshot(),
                m.getHargaBeliSatuan(),
                m.getReferensiId(),
                m.getAlasan(),
                m.getAktorNama());
    }
}
