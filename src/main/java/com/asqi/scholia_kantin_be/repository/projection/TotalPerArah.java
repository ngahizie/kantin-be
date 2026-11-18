package com.asqi.scholia_kantin_be.repository.projection;

import com.asqi.scholia_kantin_be.enums.ArahMutasi;

/**
 * Σ nominal per arah untuk seluruh waktu — dasar pemeriksaan invariant
 * rekonsiliasi (PRD §5).
 *
 * <p>Projection menggantikan {@code List<Object[]>} agar urutan kolom query
 * tidak bisa ketukar diam-diam.
 */
public interface TotalPerArah {

    /** Arah mutasi (KREDIT/DEBIT). */
    ArahMutasi getArah();

    /** Σ nominal untuk arah ini. */
    Long getNominal();
}