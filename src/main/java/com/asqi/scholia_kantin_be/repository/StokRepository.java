package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.model.Stok;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Akses data stok &amp; HPP berjalan ({@code stok_cache}).
 *
 * <p>Baris dikunci ({@code FOR UPDATE}) saat penjualan agar dua kasir tidak
 * menjual stok yang sama (ADR-0003, PRD §11.2).
 */
@Repository
public interface StokRepository extends JpaRepository<Stok, Long> {

    /**
     * Ambil &amp; kunci baris stok ({@code SELECT ... FOR UPDATE}). Wajib di dalam
     * {@code @Transactional}. Bila tidak ada → pemanggil memakai
     * {@link #pastikanBarisAda} lalu mengunci ulang.
     *
     * <p><b>Tenant-scoped (PRD §11.4, B17):</b> {@code sekolah_id} ikut di
     * {@code WHERE} agar baris sekolah lain tidak terkunci oleh sekolah yang
     * bukan pemiliknya.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM Stok s
            WHERE s.sekolahId = :sekolahId
              AND s.menuId = :menuId
            """)
    Optional<Stok> kunciUntukUpdate(@Param("sekolahId") Long sekolahId,
                                         @Param("menuId") Long menuId);

    Optional<Stok> findByMenuId(Long menuId);

    /**
     * Stok berjalan untuk sekumpulan menu dalam <b>satu</b> query
     * (tenant-scoped) — dipakai mengisi {@code stokBerjalan} pada daftar menu
     * agar tidak terjadi N+1 (PRD §7.1).
     */
    @Query("""
            SELECT s FROM Stok s
            WHERE s.sekolahId = :sekolahId
              AND s.menuId IN :menuIds
            """)
    List<Stok> findBySekolahIdAndMenuIdIn(@Param("sekolahId") Long sekolahId,
                                               @Param("menuIds") Collection<Long> menuIds);

    /** Item "stok menipis": stok ≤ stok_minimum untuk menu yang belum dihapus (PRD §7.5). */
    @Query("""
            SELECT s FROM Stok s
            JOIN Menu m ON m.id = s.menuId
            WHERE s.sekolahId = :sekolahId
              AND m.deletedAt IS NULL
              AND s.stok <= s.stokMinimum
            """)
    List<Stok> stokMenipis(@Param("sekolahId") Long sekolahId);

    /**
     * Pastikan baris stok ada tanpa race ({@code INSERT ... ON CONFLICT DO
     * NOTHING}). Baris baru mulai dari stok 0, HPP 0, minimum 0; pengelola
     * menyesuaikan lewat barang masuk/opname.
     */
    @Modifying
    @Query(value = """
            INSERT INTO stok (menu_id, sekolah_id, stok, hpp, stok_minimum, updated_at)
            VALUES (:menuId, :sekolahId, 0, 0, 0, now())
            ON CONFLICT (menu_id) DO NOTHING
            """, nativeQuery = true)
    int pastikanBarisAda(@Param("menuId") Long menuId, @Param("sekolahId") Long sekolahId);

    // Catatan (issue #196): tidak ada lagi metode sinkronStokMinimum di sini.
    // Ambang stok.stok_minimum dijaga trigger DB
    // trg_menu_sinkron_stok_minimum (migrasi V23) sehingga SEMUA jalur tulis ke
    // menu tetap sinkron — bukan hanya yang lewat service ini.
}
