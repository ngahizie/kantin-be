package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.enums.JenisPembatasan;
import com.asqi.scholia_kantin_be.model.Pembatasan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Akses data {@code pembatasan} (blokir siswa, blokir item/kategori, limit harian)
 * — ERD v2, PRD §8.3.
 *
 * <p>Selalu tenant-scoped lewat {@code sekolah_id} (PRD §11.4). Status dibaca tiap
 * tap (tanpa cache, PRD §11.11).
 */
@Repository
public interface PembatasanRepository extends JpaRepository<Pembatasan, Long> {

    Optional<Pembatasan> findBySekolahIdAndSiswaIdAndJenis(
            Long sekolahId, Long siswaId, JenisPembatasan jenis);

    Optional<Pembatasan> findBySekolahIdAndSiswaIdAndJenisAndMenuId(
            Long sekolahId, Long siswaId, JenisPembatasan jenis, Long menuId);

    Optional<Pembatasan> findBySekolahIdAndSiswaIdAndJenisAndKategoriId(
            Long sekolahId, Long siswaId, JenisPembatasan jenis, Long kategoriId);

    List<Pembatasan> findBySekolahIdAndSiswaId(Long sekolahId, Long siswaId);

    List<Pembatasan> findBySekolahIdAndSiswaIdIn(Long sekolahId, Collection<Long> siswaIds);

    List<Pembatasan> findBySekolahIdAndSiswaIdAndJenisAndAktifTrue(
            Long sekolahId, Long siswaId, JenisPembatasan jenis);
}
