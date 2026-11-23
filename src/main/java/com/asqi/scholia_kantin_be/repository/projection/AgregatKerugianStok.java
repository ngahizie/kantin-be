package com.asqi.scholia_kantin_be.repository.projection;

import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;

/**
 * Agregat kerugian stok per jenis mutasi (SEC-03, issue #391): Σ qty, Σ nilai
 * (qty × HPP snapshot), dan jumlah baris — dihitung di database.
 *
 * <p>Projection menggantikan {@code List<Object[]>} agar urutan kolom query
 * tidak bisa ketukar diam-diam (pola sama dengan {@link TotalPerArah}).
 */
public interface AgregatKerugianStok {

    /** Jenis mutasi kerugian ({@code OPNAME_KELUAR} / {@code BARANG_RUSAK}). */
    JenisMutasiStok getJenis();

    /** Σ qty kerugian untuk jenis ini. */
    Long getQty();

    /** Σ nilai kerugian (qty × HPP snapshot) untuk jenis ini. */
    Long getNilai();

    /** Jumlah baris mutasi (kejadian) untuk jenis ini. */
    Long getJumlah();
}
