package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.model.Pengguna;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Akses master pengguna lokal (ERD v2, tabel {@code pengguna}).
 */
@Repository
public interface PenggunaRepository extends JpaRepository<Pengguna, Long> {

    Optional<Pengguna> findByIdAndSekolahId(Long id, Long sekolahId);

    List<Pengguna> findByIdInAndSekolahId(Collection<Long> ids, Long sekolahId);

    List<Pengguna> findBySekolahId(Long sekolahId);
}
