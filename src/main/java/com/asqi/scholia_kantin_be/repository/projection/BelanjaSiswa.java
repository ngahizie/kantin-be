package com.asqi.scholia_kantin_be.repository.projection;

/**
 * Total belanja bersih hari ini per siswa (PRD §6.1 tahap 5, issue #413).
 *
 * <p>Projection batch: satu baris per siswa untuk banyak siswa sekaligus,
 * menggantikan pola query per-siswa (N+1) pada rincian saldo mengendap.
 */
public interface BelanjaSiswa {

    /** Id siswa. */
    Long getSiswaId();

    /** Σ DEBIT PENJUALAN − Σ KREDIT VOID_PENJUALAN sejak awal hari. */
    Long getTotal();
}
