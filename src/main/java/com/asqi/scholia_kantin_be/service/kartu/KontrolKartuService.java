package com.asqi.scholia_kantin_be.service.kartu;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.dto.BlokirItemResponse;
import com.asqi.scholia_kantin_be.dto.KontrolSubjekResponse;
import com.asqi.scholia_kantin_be.enums.JenisPembatasan;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.Pembatasan;
import com.asqi.scholia_kantin_be.repository.PembatasanRepository;
import com.asqi.scholia_kantin_be.service.integrasi.InfoKartu;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Kontrol kartu: blokir siswa, limit harian &amp; blokir item/kategori
 * (PRD §6.1 tahap 2/3/5, §8.3, issue <b>#40</b>).
 *
 * <p>Sejak ERD v2 semua kontrol disimpan di <b>satu</b> tabel {@code pembatasan}
 * ({@link Pembatasan}) dengan kolom {@code jenis}; kelas lama {@code BlokirKartu},
 * {@code BlokirItem}, dan {@code LimitHarian} sudah tidak ada.
 *
 * <p><b>Berlaku instan / tanpa cache (PRD §11.11).</b> {@link #terapkan} membaca
 * tabel pembatasan <b>setiap</b> pemanggilan, sehingga kartu yang diblokir
 * sedetik sebelumnya tetap ditolak.
 *
 * <p>Kartu tamu sudah dihapus dari skema v2, jadi semua operasi hanya untuk
 * {@link SubjekTipe#SISWA} dan parameter {@code subjekTipe} tidak lagi dipakai
 * di API (path/param lama yang masih mengirim nilainya diabaikan).
 *
 * <p>Semua operasi <b>tenant-scoped</b> (PRD §11.4) dan <b>diaudit</b>.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KontrolKartuService {

    private final PembatasanRepository pembatasanRepo;
    private final JamKantin jam;
    private final AuditLogger auditLogger;

    // ────────────────────────────────────────────────────────────────
    // BLOKIR SISWA (PRD §6.1 tahap 2, §8.3)
    // ────────────────────────────────────────────────────────────────

    /**
     * Blokir / buka blokir kartu siswa (upsert). Berlaku instan.
     *
     * @param diblokir {@code true} = blokir, {@code false} = buka blokir
     */
    @Transactional
    public KontrolSubjekResponse ubahBlokir(Long sekolahId, Long siswaId,
                                            boolean diblokir, String alasan, Long aktorId) {
        validasiSiswa(siswaId);
        OffsetDateTime now = jam.sekarang();

        Pembatasan baris = pembatasanRepo
                .findBySekolahIdAndSiswaIdAndJenis(sekolahId, siswaId, JenisPembatasan.BLOKIR_SISWA)
                .orElseGet(() -> Pembatasan.builder()
                        .sekolahId(sekolahId)
                        .siswaId(siswaId)
                        .jenis(JenisPembatasan.BLOKIR_SISWA)
                        .createdAt(now)
                        .build());
        boolean lama = baris.isAktif();
        baris.setAktif(diblokir);
        baris.setAlasan(alasan);
        baris.setDiubahOleh(aktorId);
        baris.setWaktu(now);
        pembatasanRepo.save(baris);

        auditLogger.catat(aktorId, sekolahId,
                diblokir ? "BLOKIR_KARTU" : "BUKA_BLOKIR_KARTU", "Pembatasan",
                "SISWA:" + siswaId, alasan,
                String.valueOf(lama), String.valueOf(diblokir));
        log.info("Kontrol kartu siswa:{} diblokir={} oleh={}", siswaId, diblokir, aktorId);
        return kontrol(sekolahId, siswaId);
    }

    // ────────────────────────────────────────────────────────────────
    // LIMIT HARIAN (PRD §6.1 tahap 5, §8.3)
    // ────────────────────────────────────────────────────────────────

    /**
     * Set / ubah limit belanja harian siswa (upsert). {@code nominal} {@code null}
     * = tanpa limit (baris dihapus, karena CHECK mewajibkan nominal terisi).
     */
    @Transactional
    public KontrolSubjekResponse setLimit(Long sekolahId, Long siswaId,
                                          Long nominal, Long aktorId) {
        validasiSiswa(siswaId);
        if (nominal != null && nominal < 0) {
            throw new InvalidOperationException("Limit harian tidak boleh negatif");
        }
        OffsetDateTime now = jam.sekarang();

        var lamaBaris = pembatasanRepo
                .findBySekolahIdAndSiswaIdAndJenis(sekolahId, siswaId, JenisPembatasan.LIMIT_HARIAN);
        Long lama = lamaBaris.map(Pembatasan::getNominal).orElse(null);

        if (nominal == null) {
            lamaBaris.ifPresent(pembatasanRepo::delete);
        } else {
            Pembatasan baris = lamaBaris.orElseGet(() -> Pembatasan.builder()
                    .sekolahId(sekolahId)
                    .siswaId(siswaId)
                    .jenis(JenisPembatasan.LIMIT_HARIAN)
                    .createdAt(now)
                    .build());
            baris.setNominal(nominal);
            baris.setAktif(true);
            baris.setDiubahOleh(aktorId);
            baris.setWaktu(now);
            pembatasanRepo.save(baris);
        }

        auditLogger.catat(aktorId, sekolahId, "SET_LIMIT_HARIAN", "Pembatasan",
                "SISWA:" + siswaId, "Ubah limit harian",
                lama == null ? "TANPA_LIMIT" : String.valueOf(lama),
                nominal == null ? "TANPA_LIMIT" : String.valueOf(nominal));
        log.info("Limit harian siswa:{} → {} oleh={}",
                siswaId, nominal == null ? "tanpa limit" : nominal, aktorId);
        return kontrol(sekolahId, siswaId);
    }

    // ────────────────────────────────────────────────────────────────
    // BLOKIR ITEM / KATEGORI (PRD §6.1 tahap 3, §8.3)
    // ────────────────────────────────────────────────────────────────

    /**
     * Blokir / buka blokir satu item <b>atau</b> satu kategori untuk siswa.
     *
     * @param menuId     id menu yang diblokir (isi salah satu dengan kategoriId)
     * @param kategoriId id kategori yang diblokir (isi salah satu dengan menuId)
     */
    @Transactional
    public KontrolSubjekResponse ubahBlokirItem(Long sekolahId, Long siswaId,
                                                Long menuId, Long kategoriId, boolean diblokir,
                                                Long aktorId) {
        validasiSiswa(siswaId);
        boolean adaMenu = menuId != null;
        boolean adaKategori = kategoriId != null;
        if (adaMenu == adaKategori) {
            throw new InvalidOperationException(
                    "Isi tepat satu: menuId (blokir per item) atau kategoriId (blokir per kategori)");
        }
        OffsetDateTime now = jam.sekarang();

        Pembatasan baris = (adaMenu
                ? pembatasanRepo.findBySekolahIdAndSiswaIdAndJenisAndMenuId(
                        sekolahId, siswaId, JenisPembatasan.BLOKIR_ITEM, menuId)
                : pembatasanRepo.findBySekolahIdAndSiswaIdAndJenisAndKategoriId(
                        sekolahId, siswaId, JenisPembatasan.BLOKIR_ITEM, kategoriId))
                .orElseGet(() -> Pembatasan.builder()
                        .sekolahId(sekolahId)
                        .siswaId(siswaId)
                        .jenis(JenisPembatasan.BLOKIR_ITEM)
                        .menuId(menuId)
                        .kategoriId(kategoriId)
                        .createdAt(now)
                        .build());
        boolean lama = baris.isAktif();
        baris.setAktif(diblokir);
        baris.setDiubahOleh(aktorId);
        baris.setWaktu(now);
        pembatasanRepo.save(baris);

        String target = adaMenu ? "menu:" + menuId : "kategori:" + kategoriId;
        auditLogger.catat(aktorId, sekolahId,
                diblokir ? "BLOKIR_ITEM" : "BUKA_BLOKIR_ITEM", "Pembatasan",
                "SISWA:" + siswaId, target,
                String.valueOf(lama), String.valueOf(diblokir));
        log.info("Blokir item siswa:{} {} diblokir={} oleh={}", siswaId, target, diblokir, aktorId);
        return kontrol(sekolahId, siswaId);
    }

    // ────────────────────────────────────────────────────────────────
    // BACA
    // ────────────────────────────────────────────────────────────────

    /** Ringkasan kontrol bulk beberapa siswa (tenant-scoped). */
    @Transactional(readOnly = true)
    public List<KontrolSubjekResponse> kontrolBulk(Long sekolahId, List<Long> siswaIds) {
        if (siswaIds == null || siswaIds.isEmpty()) {
            return List.of();
        }
        validasiSiswa(null);

        List<Pembatasan> semua = pembatasanRepo.findBySekolahIdAndSiswaIdIn(sekolahId, siswaIds);
        return siswaIds.stream()
                .map(siswaId -> ringkas(sekolahId, siswaId,
                        semua.stream().filter(p -> siswaId.equals(p.getSiswaId())).toList()))
                .toList();
    }

    /**
     * Tempelkan kontrol kantin-be (blokir/limit/blokir item) ke identitas kartu.
     *
     * <p>Dipanggil di jalur tap <b>setiap</b> permintaan (tanpa cache — PRD §11.11).
     * Bila kartu tidak dikenal / tanpa siswa, dikembalikan apa adanya. Bila ada
     * baris pembatasan, nilai kantin-be <b>menang</b>.
     */
    @Transactional(readOnly = true)
    public InfoKartu terapkan(Long sekolahId, InfoKartu kartu) {
        if (kartu == null || !kartu.isDikenal() || kartu.getSiswaId() == null) {
            return kartu;
        }
        Long siswaId = kartu.getSiswaId();

        Boolean diblokir = pembatasanRepo
                .findBySekolahIdAndSiswaIdAndJenis(sekolahId, siswaId, JenisPembatasan.BLOKIR_SISWA)
                .map(Pembatasan::isAktif)
                .orElse(kartu.isDiblokir());

        Long limit = kartu.getLimitHarian();
        Set<Long> menu = kartu.getMenuDiblokir() == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(kartu.getMenuDiblokir());
        Set<Long> kategori = kartu.getKategoriDiblokir() == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(kartu.getKategoriDiblokir());

        // Limit: baris aktif ada ⇒ nilai kantin-be menang.
        List<Pembatasan> limitAktif = pembatasanRepo
                .findBySekolahIdAndSiswaIdAndJenisAndAktifTrue(
                        sekolahId, siswaId, JenisPembatasan.LIMIT_HARIAN);
        if (!limitAktif.isEmpty()) {
            limit = limitAktif.get(0).getNominal();
        }
        // Blokir item/kategori (hanya baris aktif).
        for (Pembatasan b : pembatasanRepo.findBySekolahIdAndSiswaIdAndJenisAndAktifTrue(
                sekolahId, siswaId, JenisPembatasan.BLOKIR_ITEM)) {
            if (b.getMenuId() != null) {
                menu.add(b.getMenuId());
            }
            if (b.getKategoriId() != null) {
                kategori.add(b.getKategoriId());
            }
        }

        return InfoKartu.builder()
                .dikenal(true)
                .diblokir(diblokir)
                .subjekTipe(SubjekTipe.SISWA)
                .siswaId(siswaId)
                .nama(kartu.getNama())
                .kelas(kartu.getKelas())
                .fotoUrl(kartu.getFotoUrl())
                .limitHarian(limit)
                .menuDiblokir(menu.isEmpty() ? null : menu)
                .kategoriDiblokir(kategori.isEmpty() ? null : kategori)
                .build();
    }

    private void validasiSiswa(Long siswaId) {
        if (siswaId != null && siswaId <= 0) {
            throw new InvalidOperationException("ID subjek harus positif");
        }
    }

    /** Ringkasan kontrol satu siswa (tenant-scoped). */
    @Transactional(readOnly = true)
    public KontrolSubjekResponse kontrol(Long sekolahId, Long siswaId) {
        validasiSiswa(siswaId);
        return ringkas(sekolahId, siswaId, pembatasanRepo.findBySekolahIdAndSiswaId(sekolahId, siswaId));
    }

    /** Bentuk {@link KontrolSubjekResponse} dari baris pembatasan satu siswa. */
    private KontrolSubjekResponse ringkas(Long sekolahId, Long siswaId, List<Pembatasan> baris) {
        Pembatasan blokir = baris.stream()
                .filter(p -> p.getJenis() == JenisPembatasan.BLOKIR_SISWA)
                .findFirst().orElse(null);
        Long limit = baris.stream()
                .filter(p -> p.getJenis() == JenisPembatasan.LIMIT_HARIAN && p.isAktif())
                .map(Pembatasan::getNominal)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        List<Pembatasan> item = baris.stream()
                .filter(p -> p.getJenis() == JenisPembatasan.BLOKIR_ITEM && p.isAktif())
                .toList();

        return KontrolSubjekResponse.builder()
                .subjekTipe(SubjekTipe.SISWA.name())
                .siswaId(siswaId)
                .diblokir(blokir != null && blokir.isAktif())
                .alasanBlokir(blokir == null ? null : blokir.getAlasan())
                .limitHarian(limit)
                .adaLimit(limit != null)
                .menuDiblokir(item.stream().map(Pembatasan::getMenuId).filter(Objects::nonNull).distinct().toList())
                .kategoriDiblokir(item.stream().map(Pembatasan::getKategoriId).filter(Objects::nonNull).distinct().toList())
                .item(item.stream().map(BlokirItemResponse::dari).toList())
                .build();
    }
}
