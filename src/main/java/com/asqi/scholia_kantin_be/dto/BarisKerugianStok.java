package com.asqi.scholia_kantin_be.dto;

import java.time.OffsetDateTime;

/**
 * Baris laporan kerugian stok (PRD §9.5): rincian kejadian penyesuaian opname keluar &amp; barang
 * rusak, beserta nilainya (qty × HPP snapshot), nomor berita acara, dan alasan.
 */
public record BarisKerugianStok(
        Long id,
        OffsetDateTime waktu,
        Long menuId,
        String namaMenu,
        String kategoriNama,
        String jenis,
        int qty,
        Long hppSnapshot,
        long totalNilai,
        String alasan,
        String beritaAcaraId,
        String petugas) {

    /** Alias untuk kompatibilitas perhitungan ringkasan &amp; tes. */
    public long totalQty() {
        return qty;
    }

    /** Alias untuk kompatibilitas ringkasan kejadian. */
    public long jumlahBaris() {
        return 1L;
    }
}
