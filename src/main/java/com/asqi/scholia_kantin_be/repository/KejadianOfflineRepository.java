package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.model.KejadianOffline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Akses data insiden offline ({@code insiden_offline}) — DEMO Q14/#23.
 *
 * <p><b>Append-only (PRD §11.1):</b> sengaja <b>tidak</b> menyediakan method
 * {@code delete}/{@code update}. Hanya {@code save} (INSERT) &amp; query baca;
 * {@link KejadianOffline} juga {@code @Immutable} + trigger DB.
 */
@Repository
public interface KejadianOfflineRepository extends JpaRepository<KejadianOffline, Long> {

    /** Insiden satu sekolah, terbaru dulu (tenant-scoped, PRD §11.4). */
    List<KejadianOffline> findBySekolahIdOrderByMulaiDesc(Long sekolahId);
}
