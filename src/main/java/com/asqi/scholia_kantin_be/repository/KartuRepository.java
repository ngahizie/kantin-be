package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.model.Kartu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Akses data kartu RFID (ERD v2, tabel {@code kartu}, PK {@code uid}).
 *
 * <p>Lookup kasir memakai {@link #findByUidAndSekolahId} (PK, O(1)); kontrol
 * kartu memakai {@link #findFirstBySekolahIdAndSiswaIdAndAktifTrue} karena
 * partial unique index menjamin paling banyak satu kartu aktif per siswa.
 */
@Repository
public interface KartuRepository extends JpaRepository<Kartu, String> {

    Optional<Kartu> findByUidAndSekolahId(String uid, Long sekolahId);

    Optional<Kartu> findFirstBySekolahIdAndSiswaIdAndAktifTrue(Long sekolahId, Long siswaId);

    List<Kartu> findBySekolahIdAndSiswaIdOrderByCreatedAtDesc(Long sekolahId, Long siswaId);
}
