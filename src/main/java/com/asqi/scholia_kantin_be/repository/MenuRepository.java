package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.model.Menu;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Akses data item katalog (PRD §7.1). Semua query tenant-scoped. */
public interface MenuRepository extends JpaRepository<Menu, Long>, JpaSpecificationExecutor<Menu> {

    /**
     * Filter daftar item katalog (Criteria API) — <b>hanya</b> menambahkan syarat
     * untuk filter yang benar-benar terisi, lalu sort nama A→Z.
     *
     * <p>Kenapa bukan JPQL {@code :x IS NULL OR ...}: pola itu membuat Hibernate
     * mengirim parameter {@code null} tanpa tipe dan PostgreSQL menolaknya
     * ({@code could not determine data type of parameter}) — akar bug #198
     * (endpoint daftar 500). Dulu di interface ini ada method {@code daftar(...)}
     * dengan pola itu; kini dipanggil:
     * {@code menuRepo.findAll(MenuRepository.daftarSpec(sekolahId, kategoriId, hanyaAktif))}
     * (issue #256).
     */
    static Specification<Menu> daftarSpec(Long sekolahId, Long kategoriId, boolean hanyaAktif) {
        return (root, query, cb) -> {
            List<Predicate> syarat = new ArrayList<>();
            syarat.add(cb.equal(root.get("sekolahId"), sekolahId)); // tenant-scoped
            if (kategoriId != null) {
                syarat.add(cb.equal(root.get("kategoriId"), kategoriId));
            }
            if (hanyaAktif) {
                syarat.add(cb.isTrue(root.<Boolean>get("isActive")));
            }
            query.orderBy(cb.asc(root.get("nama")));
            return cb.and(syarat.toArray(new Predicate[0]));
        };
    }

    /** Cari per id <b>dalam tenant</b> — sekolah lain ⇒ kosong (jadi 404). */
    Optional<Menu> findByIdAndSekolahId(Long id, Long sekolahId);

    /** Apakah menu ada <b>dan</b> milik sekolah pemanggil (validasi tenant). */
    boolean existsByIdAndSekolahId(Long id, Long sekolahId);

    /** Apakah ada item (aktif atau tidak) yang memakai kategori ini. */
    boolean existsByKategoriId(Long kategoriId);

    /** Jumlah item aktif dalam satu kategori — validasi nonaktifkan kategori. */
    long countByKategoriIdAndIsActiveTrue(Long kategoriId);
}
