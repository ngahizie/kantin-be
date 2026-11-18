package com.asqi.scholia_kantin_be.repository.projection;

import java.time.OffsetDateTime;

/**
 * Waktu mutasi saldo terakhir per siswa (PRD §9.5).
 *
 * <p>Projection menggantikan {@code List<Object[]>} agar urutan kolom query
 * tidak bisa ketukar diam-diam.
 */
public interface WaktuTerakhirSiswa {

    /** Id siswa. */
    Long getSiswaId();

    /** Waktu mutasi saldo terakhir siswa tersebut. */
    OffsetDateTime getWaktu();
}