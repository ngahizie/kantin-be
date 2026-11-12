package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.model.Saldo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Akses data saldo berjalan ({@code saldo}).
 *
 * <p>Baris ini adalah <b>hot row</b> yang dikunci saat debit agar dua kasir
 * tidak memotong saldo yang sama secara bersamaan (ADR-0003, PRD §11.2).
 */
@Repository
public interface SaldoRepository extends JpaRepository<Saldo, Saldo.SaldoId> {

    /**
     * Ambil &amp; kunci baris saldo ({@code SELECT ... FOR UPDATE}).
     *
     * <p><b>Tenant-scoped (PRD §11.4):</b> {@code sekolah_id} WAJIB ikut di
     * {@code WHERE} — bukan sekadar diperiksa setelah lock. Dengan begitu baris
     * milik sekolah lain tidak pernah terkunci/diserialisasi oleh sekolah yang
     * bukan pemiliknya (memperkecil blast radius, B17).
     *
     * <p>Wajib dipanggil di dalam {@code @Transactional}: lock dilepas saat
     * transaksi selesai. Bila baris tidak ada → {@code Optional.empty()}
     * (pemanggil harus membuatnya lewat {@link #pastikanBarisAda}).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM Saldo s
            WHERE s.sekolahId = :sekolahId
              AND s.siswaId = :siswaId
            """)
    Optional<Saldo> kunciUntukUpdate(@Param("sekolahId") Long sekolahId,
                                     @Param("siswaId") Long siswaId);

    Optional<Saldo> findBySiswaId(Long siswaId);

    Optional<Saldo> findBySekolahIdAndSiswaId(Long sekolahId, Long siswaId);

    List<Saldo> findBySekolahId(Long sekolahId);

    /**
     * Total saldo mengendap (PRD §9.5) — kewajiban sekolah.
     * Mengembalikan satu baris {@code [Σ saldo, jumlah baris]}.
     */
    @Query("""
            SELECT COALESCE(SUM(s.saldo), 0), COUNT(s)
            FROM Saldo s
            WHERE s.sekolahId = :sekolahId
            """)
    List<Object[]> totalMengendap(@Param("sekolahId") Long sekolahId);

    /**
     * Pastikan baris saldo ada <b>tanpa race</b>: {@code INSERT ... ON CONFLICT
     * DO NOTHING}. Bila dua kasir mencoba membuat baris yang sama serentak,
     * hanya satu yang berhasil dan yang lain diam-diam dilewati — lalu keduanya
     * tetap mengunci baris yang sama lewat {@link #kunciUntukUpdate}.
     *
     * <p>Native karena {@code ON CONFLICT} khas PostgreSQL.
     */
    @Modifying
    @Query(value = """
            INSERT INTO saldo (sekolah_id, siswa_id, saldo, updated_at)
            VALUES (:sekolahId, :siswaId, 0, now())
            ON CONFLICT (sekolah_id, siswa_id) DO NOTHING
            """, nativeQuery = true)
    int pastikanBarisAda(@Param("sekolahId") Long sekolahId,
                         @Param("siswaId") Long siswaId);
}
