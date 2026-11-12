package com.asqi.scholia_kantin_be.service.integrasi;

import com.asqi.scholia_kantin_be.enums.JenisPembatasan;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.Kartu;
import com.asqi.scholia_kantin_be.model.Pembatasan;
import com.asqi.scholia_kantin_be.model.Siswa;
import com.asqi.scholia_kantin_be.repository.KartuRepository;
import com.asqi.scholia_kantin_be.repository.PembatasanRepository;
import com.asqi.scholia_kantin_be.repository.SiswaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementasi {@link KartuLookupPort} berbasis <b>tabel lokal skema v2</b>
 * ({@code kartu} + {@code siswa} + {@code pembatasan}).
 *
 * <p><b>Kenapa ada.</b> Di v1 pemilik kartu harus ditanyakan ke admin-be (Q7)
 * sehingga {@link KartuLookupFallback} menolak semua tap. ERD v2 menyimpan
 * kartu dan pembatasan di kantin-be, jadi tap bisa dilayani lokal: satu query
 * PK {@code kartu.uid}, satu baris {@code siswa}, lalu pembatasan siswa.
 *
 * <p><b>Status kontrol dibaca segar setiap tap</b> (PRD §11.11): blokir/limit
 * yang baru disimpan langsung berlaku, tanpa cache.
 *
 * <p>Aktif saat {@code kantin.siswa.sumber=lokal} (default) dan menang atas
 * {@link KartuLookupFallback}; {@code KartuLookupRestClient} tetap tersedia
 * untuk mode integrasi.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "kantin.siswa", name = "sumber",
        havingValue = "lokal", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class KartuLookupLokal implements KartuLookupPort {

    private final KartuRepository kartuRepo;
    private final SiswaRepository siswaRepo;
    private final PembatasanRepository pembatasanRepo;

    @Override
    public InfoKartu cariBerdasarkanUid(Long sekolahId, String rfidUid) {
        if (rfidUid == null || rfidUid.isBlank()) {
            return InfoKartu.tidakDikenal();
        }
        Kartu kartu = kartuRepo.findByUidAndSekolahId(rfidUid.trim(), sekolahId).orElse(null);
        if (kartu == null || !kartu.isAktif()) {
            return InfoKartu.tidakDikenal();
        }

        Siswa siswa = siswaRepo.findByIdAndSekolahId(kartu.getSiswaId(), sekolahId).orElse(null);
        if (siswa == null || !siswa.isAktif()) {
            return InfoKartu.tidakDikenal();
        }

        List<Pembatasan> pembatasan = pembatasanRepo
                .findBySekolahIdAndSiswaId(sekolahId, siswa.getId());

        boolean diblokir = pembatasan.stream()
                .anyMatch(p -> p.getJenis() == JenisPembatasan.BLOKIR_SISWA && p.isAktif());

        Long limitHarian = pembatasan.stream()
                .filter(p -> p.getJenis() == JenisPembatasan.LIMIT_HARIAN && p.isAktif())
                .map(Pembatasan::getNominal)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        Set<Long> menuDiblokir = pembatasan.stream()
                .filter(p -> p.getJenis() == JenisPembatasan.BLOKIR_ITEM && p.isAktif())
                .map(Pembatasan::getMenuId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> kategoriDiblokir = pembatasan.stream()
                .filter(p -> p.getJenis() == JenisPembatasan.BLOKIR_ITEM && p.isAktif())
                .map(Pembatasan::getKategoriId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return InfoKartu.builder()
                .dikenal(true)
                .diblokir(diblokir)
                .subjekTipe(SubjekTipe.SISWA)
                .siswaId(siswa.getId())
                .nama(siswa.getNama())
                .kelas(siswa.getKelas())
                .fotoUrl(siswa.getFotoUrl())
                .limitHarian(limitHarian)
                .menuDiblokir(menuDiblokir)
                .kategoriDiblokir(kategoriDiblokir)
                .build();
    }
}
