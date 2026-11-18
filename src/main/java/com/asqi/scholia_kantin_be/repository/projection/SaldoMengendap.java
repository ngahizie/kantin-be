package com.asqi.scholia_kantin_be.repository.projection;

/**
 * Total saldo mengendap — kewajiban sekolah (PRD §9.5).
 *
 * <p>Projection menggantikan {@code List<Object[]>} agar urutan kolom query
 * tidak bisa ketukar diam-diam.
 */
public interface SaldoMengendap {

    /** Σ saldo seluruh siswa pada sekolah. */
    Long getSaldo();

    /** Banyaknya baris saldo. */
    Long getJumlah();
}