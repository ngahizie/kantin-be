package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.model.CatatanAudit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Akses data jejak audit ({@code audit_log}, PRD §11.7).
 *
 * <p><b>Append-only:</b> sengaja <b>tidak</b> menyediakan {@code delete}/
 * {@code update}. Hanya {@code save} (INSERT) &amp; query baca, semua
 * {@code sekolah_id}-scoped (PRD §11.4).
 */
@Repository
public interface CatatanAuditRepository extends JpaRepository<CatatanAudit, Long> {

    /** Audit satu sekolah, terbaru dulu. */
    @Query("""
            SELECT a FROM CatatanAudit a
            WHERE a.sekolahId = :sekolahId
            ORDER BY a.waktu DESC
            """)
    List<CatatanAudit> terbaru(@Param("sekolahId") Long sekolahId, Pageable pageable);

    /** Riwayat audit satu entitas (mis. satu transaksi). */
    @Query("""
            SELECT a FROM CatatanAudit a
            WHERE a.sekolahId = :sekolahId
              AND a.entitas = :entitas
              AND a.entitasId = :entitasId
            ORDER BY a.waktu DESC
            """)
    List<CatatanAudit> perEntitas(@Param("sekolahId") Long sekolahId,
                              @Param("entitas") String entitas,
                              @Param("entitasId") String entitasId,
                              Pageable pageable);

    /** Audit pada rentang waktu (audit harian / periode). */
    @Query("""
            SELECT a FROM CatatanAudit a
            WHERE a.sekolahId = :sekolahId
              AND a.waktu >= :dari
              AND a.waktu < :sampai
            ORDER BY a.waktu DESC
            """)
    List<CatatanAudit> padaRentang(@Param("sekolahId") Long sekolahId,
                               @Param("dari") OffsetDateTime dari,
                               @Param("sampai") OffsetDateTime sampai,
                               Pageable pageable);
}
