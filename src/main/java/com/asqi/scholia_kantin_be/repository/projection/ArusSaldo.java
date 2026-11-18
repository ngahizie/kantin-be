package com.asqi.scholia_kantin_be.repository.projection;

import com.asqi.scholia_kantin_be.enums.ArahMutasi;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;

/**
 * Arus saldo per (arah, jenis) pada rentang — laporan rekonsiliasi harian
 * (PRD §9.5).
 *
 * <p>Projection menggantikan {@code List<Object[]>} agar urutan kolom query
 * tidak bisa ketukar diam-diam.
 */
public interface ArusSaldo {

    /** Arah mutasi (KREDIT/DEBIT). */
    ArahMutasi getArah();

    /** Jenis mutasi saldo. */
    JenisMutasiSaldo getJenis();

    /** Σ nominal pada kombinasi arah + jenis ini. */
    Long getNominal();
}