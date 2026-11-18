package com.asqi.scholia_kantin_be.repository.projection;

/**
 * Total qty yang sudah dibalik per mutasi asal (PRD §8.4).
 *
 * <p>Projection menggantikan {@code List<Object[]>} agar urutan kolom query
 * tidak bisa ketukar diam-diam. Dipakai mengisi {@code sisaDapatDibalik}
 * dalam satu query (hindari N+1).
 */
public interface DibalikPerAsal {

    /** Id mutasi stok asal. */
    Long getMutasiAsalId();

    /** Σ qty yang sudah dibalik dari mutasi asal tersebut. */
    Long getQty();
}