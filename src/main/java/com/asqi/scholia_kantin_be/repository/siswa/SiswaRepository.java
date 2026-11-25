package com.asqi.scholia_kantin_be.repository.siswa;

import com.asqi.scholia_kantin_be.model.Siswa;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Akses data master siswa lokal (ERD v2, tabel {@code siswa}).
 *
 * <p>Selalu tenant-scoped: setiap finder menyertakan {@code sekolahId} supaya
 * data lintas sekolah tidak pernah bocor (PRD §11.4).
 */
@Repository
public interface SiswaRepository extends JpaRepository<Siswa, Long> {

    Optional<Siswa> findByIdAndSekolahId(Long id, Long sekolahId);

    List<Siswa> findByIdInAndSekolahId(Collection<Long> ids, Long sekolahId);

    boolean existsBySekolahIdAndNis(Long sekolahId, String nis);

    boolean existsBySekolahIdAndNisAndIdNot(Long sekolahId, String nis, Long id);

    /**
     * Cari siswa berdasarkan nama atau NIS (PRD §9.2 — layar TU).
     * {@code aktif} boleh {@code null} untuk mengabaikan filter status.
     */
    @Query("""
            select s from Siswa s
             where s.sekolahId = :sekolahId
               and (:aktif is null or s.aktif = :aktif)
               and (:kata = '' or lower(s.nama) like lower(concat('%', :kata, '%'))
                                 or s.nis like concat('%', :kata, '%'))
             order by s.nama asc
            """)
    List<Siswa> cari(@Param("sekolahId") Long sekolahId,
                     @Param("kata") String kata,
                     @Param("aktif") Boolean aktif,
                     Pageable pageable);

    /**
     * Pencarian lengkap untuk manajemen siswa sementara Admin (nama, NIS, NISN, atau kelas).
     */
    @Query("""
            select s from Siswa s
             where s.sekolahId = :sekolahId
               and (:aktif is null or s.aktif = :aktif)
               and (:kata = '' or lower(s.nama) like lower(concat('%', :kata, '%'))
                                 or lower(s.nis) like lower(concat('%', :kata, '%'))
                                 or lower(coalesce(s.nisn, '')) like lower(concat('%', :kata, '%'))
                                 or lower(coalesce(s.kelas, '')) like lower(concat('%', :kata, '%')))
             order by s.updatedAt desc, s.nama asc
            """)
    List<Siswa> cariAdmin(@Param("sekolahId") Long sekolahId,
                          @Param("kata") String kata,
                          @Param("aktif") Boolean aktif,
                          Pageable pageable);
}
