package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.model.OrangTua;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Akses data wali/orang tua (ERD v2, tabel {@code orang_tua}).
 *
 * <p>Dipakai untuk menampilkan nama &amp; nomor telepon wali pada profil siswa
 * (laporan riwayat, kandidat refund) — lihat {@link com.asqi.scholia_kantin_be.model.OrangTua}.
 */
@Repository
public interface OrangTuaRepository extends JpaRepository<OrangTua, Long> {

    List<OrangTua> findBySiswaIdAndSekolahId(Long siswaId, Long sekolahId);

    List<OrangTua> findBySiswaIdInAndSekolahId(Collection<Long> siswaIds, Long sekolahId);
}
