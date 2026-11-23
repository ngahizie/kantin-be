package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.model.Transaksi;
import com.asqi.scholia_kantin_be.repository.projection.PenjualanDimensi;
import com.asqi.scholia_kantin_be.repository.projection.PenjualanKasir;
import com.asqi.scholia_kantin_be.repository.projection.RekapStatus;
import com.asqi.scholia_kantin_be.repository.projection.RekapStatusMetode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Akses data transaksi kasir ({@code transaksi}).
 *
 * <p>Idempotency tap dijaga kolom {@code idempotency_key} UNIQUE. Query agregat
 * di sini dipakai untuk rekap sesi kasir &amp; laporan (PRD §6.4, §9.5).
 */
@Repository
public interface TransaksiRepository extends JpaRepository<Transaksi, Long>,
        JpaSpecificationExecutor<Transaksi> {

    /** Muat dan lock transaksi sebelum operasi kompensasi (void/koreksi) agar hanya satu transaksi balapan menang. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaksi t WHERE t.id = :id")
    Optional<Transaksi> kunciUntukVoid(@Param("id") Long id);

    /** Idempotency: cari transaksi berdasarkan key dari klien kasir (PRD §11.3). */
    Optional<Transaksi> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * Idempotency <b>tenant-scoped</b> (PRD §11.4) — key yang sama di sekolah
     * berbeda adalah transaksi berbeda. Wajib dipakai untuk replay tap agar
     * klien sekolah lain tidak bisa menelan/membaca transaksi kita.
     */
    Optional<Transaksi> findBySekolahIdAndIdempotencyKey(Long sekolahId, String idempotencyKey);

    boolean existsBySekolahIdAndIdempotencyKey(Long sekolahId, String idempotencyKey);

    /** Transaksi satu sesi, terfilter status (rekap tutup kasir). */
    Page<Transaksi> findBySesiKasirIdAndStatus(Long sesiKasirId, StatusTransaksi status, Pageable pageable);

    /** Riwayat transaksi per subjek (siswa / kartu tamu) untuk ortu &amp; laporan. */
    Page<Transaksi> findBySekolahIdAndSiswaIdOrderByIdDesc(
            Long sekolahId, Long siswaId, Pageable pageable);

    /**
     * Rekap sesi per <b>status × metode bayar</b> (C.2d) — memisahkan penjualan
     * TUNAI (uang fisik di laci) dari SALDO (potongan saldo siswa).
     *
     * <p>Dipakai {@code SesiKasirService} untuk mengisi {@code totalTunai} /
     * {@code totalSaldo} pada rekap sesi; dengan begitu kasir dapat mencocokkan
     * uang fisik terhadap <b>Σ penjualan tunai</b>, bukan terhadap seluruh
     * penjualan (yang sebagian tidak berbentuk uang tunai).
     *
     * <p>Projection {@link RekapStatusMetode}: nama properti = alias kolom.
     */
    @Query("""
            SELECT t.status AS status, t.metodeBayar AS metodeBayar,
                   COUNT(t) AS jumlah, COALESCE(SUM(t.total), 0) AS total
            FROM Transaksi t
            WHERE t.sekolahId = :sekolahId AND t.sesiKasirId = :sesiKasirId
            GROUP BY t.status, t.metodeBayar
            """)
    List<RekapStatusMetode> rekapSesiPerMetode(@Param("sekolahId") Long sekolahId,
                                               @Param("sesiKasirId") Long sesiKasirId);

    /** Total penjualan bersih (SUKSES) pada rentang waktu — untuk laporan. */
    @Query("""
            SELECT COALESCE(SUM(t.total), 0)
            FROM Transaksi t
            WHERE t.sekolahId = :sekolahId
              AND t.status = :status
              AND t.waktu >= :dari AND t.waktu < :sampai
            """)
    Long totalPenjualanRentang(@Param("sekolahId") Long sekolahId,
                               @Param("status") StatusTransaksi status,
                               @Param("dari") OffsetDateTime dari,
                               @Param("sampai") OffsetDateTime sampai);

    /** Σ HPP transaksi SUKSES pada rentang waktu — dasar laba kotor (PRD §5). */
    @Query("""
            SELECT COALESCE(SUM(t.totalHpp), 0)
            FROM Transaksi t
            WHERE t.sekolahId = :sekolahId
              AND t.status = :status
              AND t.waktu >= :dari AND t.waktu < :sampai
            """)
    Long totalModalRentang(@Param("sekolahId") Long sekolahId,
                         @Param("status") StatusTransaksi status,
                         @Param("dari") OffsetDateTime dari,
                         @Param("sampai") OffsetDateTime sampai);

    /**
     * Rekap per status pada rentang waktu — laporan penjualan/laba kotor
     * (PRD §9.5). Projection {@link RekapStatus}: {@code status, jumlah,
     * total}. Alias kolom wajib — lihat {@link RekapStatus}.
     */
    @Query("""
            SELECT t.status AS status, COUNT(t) AS jumlah,
                   COALESCE(SUM(t.total), 0) AS total
            FROM Transaksi t
            WHERE t.sekolahId = :sekolahId
              AND t.waktu >= :dari AND t.waktu < :sampai
            GROUP BY t.status
            """)
    List<RekapStatus> rekapPerStatusRentang(@Param("sekolahId") Long sekolahId,
                                            @Param("dari") OffsetDateTime dari,
                                            @Param("sampai") OffsetDateTime sampai);

    /**
     * Transaksi yang di-<b>VOID</b> pada rentang waktu — laporan Pembatalan
     * Kasir (PRD §9.5, issue #114). Diurut terbaru dulu agar jejak terakhir
     * mudah dilihat. Tenant-scoped.
     */
    @Query("""
            SELECT t FROM Transaksi t
            WHERE t.sekolahId = :sekolahId
              AND t.status = com.asqi.scholia_kantin_be.enums.StatusTransaksi.VOID
              AND t.voidAt >= :dari AND t.voidAt < :sampai
            ORDER BY t.voidAt DESC, t.id DESC
            """)
    List<Transaksi> pembatalanRentang(@Param("sekolahId") Long sekolahId,
                                      @Param("dari") OffsetDateTime dari,
                                      @Param("sampai") OffsetDateTime sampai,
                                      Pageable pageable);

    /**
     * Penjualan per <b>titik kasir</b> pada rentang (PRD §9.5, issue #116).
     * Projection {@link PenjualanDimensi}: {@code dimensiId, jumlah, nilai,
     * hpp}; hanya transaksi SUKSES. Diurut nilai menurun.
     */
    @Query("""
            SELECT t.titikKasirId AS dimensiId, COUNT(t) AS jumlah,
                   COALESCE(SUM(t.total), 0) AS nilai,
                   COALESCE(SUM(t.totalHpp), 0) AS hpp
            FROM Transaksi t
            WHERE t.sekolahId = :sekolahId
              AND t.status = com.asqi.scholia_kantin_be.enums.StatusTransaksi.SUKSES
              AND t.waktu >= :dari AND t.waktu < :sampai
            GROUP BY t.titikKasirId
            ORDER BY SUM(t.total) DESC
            """)
    List<PenjualanDimensi> penjualanPerTitikRentang(@Param("sekolahId") Long sekolahId,
                                                     @Param("dari") OffsetDateTime dari,
                                                     @Param("sampai") OffsetDateTime sampai);

    /**
     * Penjualan per <b>petugas</b> pada rentang (PRD §9.5, issue #116).
     * Projection {@link PenjualanDimensi}: {@code dimensiId, jumlah, nilai,
     * hpp}; hanya transaksi SUKSES. Diurut nilai menurun.
     */
    @Query("""
            SELECT t.petugasId AS dimensiId, COUNT(t) AS jumlah,
                   COALESCE(SUM(t.total), 0) AS nilai,
                   COALESCE(SUM(t.totalHpp), 0) AS hpp
            FROM Transaksi t
            WHERE t.sekolahId = :sekolahId
              AND t.status = com.asqi.scholia_kantin_be.enums.StatusTransaksi.SUKSES
              AND t.waktu >= :dari AND t.waktu < :sampai
            GROUP BY t.petugasId
            ORDER BY SUM(t.total) DESC
            """)
    List<PenjualanDimensi> penjualanPerPetugasRentang(@Param("sekolahId") Long sekolahId,
                                                       @Param("dari") OffsetDateTime dari,
                                                       @Param("sampai") OffsetDateTime sampai);

    /**
     * Penjualan <b>per kasir</b> — grouping per titik kasir + petugas pada
     * rentang (PRD §9.5, issue #227). Projection {@link PenjualanKasir}: {@code
     * titikKasirId, petugasId, jumlahSukses, nilaiSukses, nilaiVoid}. SUKSES dan
     * VOID dihitung pada grup yang sama agar bruto/void/bersih konsisten dengan
     * rekap sesi kasir. Tenant-scoped, diurut per titik lalu petugas.
     */
    @Query("""
            SELECT t.titikKasirId AS titikKasirId, t.petugasId AS petugasId,
                   COUNT(CASE WHEN t.status = com.asqi.scholia_kantin_be.enums.StatusTransaksi.SUKSES THEN 1 END) AS jumlahSukses,
                   COALESCE(SUM(CASE WHEN t.status = com.asqi.scholia_kantin_be.enums.StatusTransaksi.SUKSES THEN t.total END), 0) AS nilaiSukses,
                   COALESCE(SUM(CASE WHEN t.status = com.asqi.scholia_kantin_be.enums.StatusTransaksi.VOID THEN t.total END), 0) AS nilaiVoid
            FROM Transaksi t
            WHERE t.sekolahId = :sekolahId
              AND t.status IN (
                  com.asqi.scholia_kantin_be.enums.StatusTransaksi.SUKSES,
                  com.asqi.scholia_kantin_be.enums.StatusTransaksi.VOID)
              AND t.waktu >= :dari AND t.waktu < :sampai
            GROUP BY t.titikKasirId, t.petugasId
            ORDER BY t.titikKasirId, t.petugasId
            """)
    List<PenjualanKasir> penjualanPerKasirRentang(@Param("sekolahId") Long sekolahId,
                                                   @Param("dari") OffsetDateTime dari,
                                                   @Param("sampai") OffsetDateTime sampai);

    /**
     * Semua transaksi satu subjek pada rentang (laporan per siswa, PRD §9.5,
     * issue #117), urut waktu menaik. Tenant-scoped.
     */
    @Query("""
            SELECT t FROM Transaksi t
            WHERE t.sekolahId = :sekolahId
              AND t.siswaId = :siswaId
              AND t.waktu >= :dari AND t.waktu < :sampai
            ORDER BY t.waktu ASC
            """)
    List<Transaksi> transaksiSubjekRentang(@Param("sekolahId") Long sekolahId,
                                           @Param("siswaId") Long siswaId,
                                           @Param("dari") OffsetDateTime dari,
                                           @Param("sampai") OffsetDateTime sampai);
}
