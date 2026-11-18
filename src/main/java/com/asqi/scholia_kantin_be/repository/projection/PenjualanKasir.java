package com.asqi.scholia_kantin_be.repository.projection;

/**
 * Baris penjualan per kasir — grouping per titik kasir + petugas (PRD §9.5,
 * issue #227).
 *
 * <p>Projection menggantikan {@code List<Object[]>} agar urutan kolom query
 * tidak bisa ketukar diam-diam. SUKSES dan VOID dihitung pada grup yang sama
 * supaya bruto/void/bersih konsisten dengan rekap sesi kasir.
 */
public interface PenjualanKasir {

    /** Id titik kasir. */
    Long getTitikKasirId();

    /** Id petugas. */
    Long getPetugasId();

    /** Banyaknya transaksi SUKSES. */
    Long getJumlahSukses();

    /** Σ total nominal SUKSES. */
    Long getNilaiSukses();

    /** Σ total nominal VOID. */
    Long getNilaiVoid();
}