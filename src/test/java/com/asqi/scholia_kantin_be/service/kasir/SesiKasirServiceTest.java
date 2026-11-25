package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.enums.StatusSesiKasir;
import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.SesiKasir;
import com.asqi.scholia_kantin_be.model.TitikKasir;
import com.asqi.scholia_kantin_be.repository.kasir.SesiKasirRepository;
import com.asqi.scholia_kantin_be.repository.konfigurasi.TitikKasirRepository;
import com.asqi.scholia_kantin_be.repository.kasir.TransaksiRepository;
import com.asqi.scholia_kantin_be.security.SekolahGuard;
import com.asqi.scholia_kantin_be.service.integrasi.BukuKasPostingService;
import com.asqi.scholia_kantin_be.service.konfigurasi.PengaturanKantinService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Uji unit gerbang titik kasir pada pembukaan sesi (SEC-TITIK-01, issue #421).
 *
 * <p>Fokus: titik yang dinonaktifkan admin tidak boleh membuka sesi baru lewat
 * jalur eksplisit ({@link SesiKasirService#bukaSesi}) maupun jalur tap
 * ({@link SesiKasirService#sesiTerbukaAtauBuka}). Penutupan sesi sengaja tidak
 * tunduk pada gerbang ini — lihat Javadoc
 * {@code pastikanTitikKasirAktifMilikSekolah}.
 */
@DisplayName("SesiKasirService — gerbang titik kasir aktif (SEC-TITIK-01)")
class SesiKasirServiceTest {

    private static final long SEKOLAH = 7L;
    private static final long SEKOLAH_LAIN = 8L;
    private static final long TITIK = 5L;
    private static final long AKTOR = 42L;

    private SesiKasirRepository sesiRepo;
    private TitikKasirRepository titikRepo;
    private IdGenerator idGenerator;
    private SesiKasirService service;

    @BeforeEach
    void setUp() {
        sesiRepo = mock(SesiKasirRepository.class);
        titikRepo = mock(TitikKasirRepository.class);
        idGenerator = mock(IdGenerator.class);
        service = new SesiKasirService(
                sesiRepo,
                mock(TransaksiRepository.class),
                titikRepo,
                mock(SekolahGuard.class),
                idGenerator,
                new JamKantin("Asia/Jakarta"),
                mock(BukuKasPostingService.class),
                mock(PengaturanKantinService.class));
    }

    private static TitikKasir titik(Long sekolahId, boolean aktif) {
        return TitikKasir.builder()
                .id(TITIK)
                .sekolahId(sekolahId)
                .nama("Kasir Uji")
                .aktif(aktif)
                .build();
    }

    @Test
    @DisplayName("bukaSesi menolak titik nonaktif dan tidak menyimpan sesi")
    void bukaSesiMenolakTitikNonaktif() {
        when(titikRepo.findById(TITIK)).thenReturn(Optional.of(titik(SEKOLAH, false)));

        assertThatThrownBy(() -> service.bukaSesi(SEKOLAH, TITIK, AKTOR))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("dinonaktifkan");

        verify(sesiRepo, never()).save(any());
        verifyNoInteractions(idGenerator);
    }

    @Test
    @DisplayName("sesiTerbukaAtauBuka (jalur tap) menolak titik nonaktif")
    void sesiTerbukaAtauBukaMenolakTitikNonaktif() {
        when(titikRepo.findById(TITIK)).thenReturn(Optional.of(titik(SEKOLAH, false)));

        assertThatThrownBy(() -> service.sesiTerbukaAtauBuka(SEKOLAH, TITIK, AKTOR))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("dinonaktifkan");

        verify(sesiRepo, never()).save(any());
        verifyNoInteractions(idGenerator);
    }

    @Test
    @DisplayName("titik milik sekolah lain tetap 404, bukan 400")
    void titikSekolahLainTidakDitemukan() {
        when(titikRepo.findById(TITIK)).thenReturn(Optional.of(titik(SEKOLAH_LAIN, true)));

        assertThatThrownBy(() -> service.bukaSesi(SEKOLAH, TITIK, AKTOR))
                .isInstanceOf(NotFoundEntity.class);
    }

    @Test
    @DisplayName("titik aktif tetap bisa membuka sesi baru")
    void bukaSesiTitikAktifMembuatSesiBaru() {
        when(titikRepo.findById(TITIK)).thenReturn(Optional.of(titik(SEKOLAH, true)));
        when(sesiRepo.kunciBerdasarkanTitikTanggal(eq(SEKOLAH), eq(TITIK), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(idGenerator.berikutnyaLong()).thenReturn(99L);
        when(sesiRepo.save(any(SesiKasir.class))).thenAnswer(inv -> inv.getArgument(0));

        SesiKasir sesi = service.bukaSesi(SEKOLAH, TITIK, AKTOR);

        ArgumentCaptor<SesiKasir> tersimpan = ArgumentCaptor.forClass(SesiKasir.class);
        verify(sesiRepo).save(tersimpan.capture());
        assertThat(sesi).isSameAs(tersimpan.getValue());
        assertThat(sesi.getId()).isEqualTo(99L);
        assertThat(sesi.getSekolahId()).isEqualTo(SEKOLAH);
        assertThat(sesi.getTitikKasirId()).isEqualTo(TITIK);
        assertThat(sesi.getStatus()).isEqualTo(StatusSesiKasir.TERBUKA);
        assertThat(sesi.getDibukaOleh()).isEqualTo(AKTOR);
        assertThat(sesi.getTotalBruto()).isZero();
        assertThat(sesi.isAutoTutup()).isFalse();
        assertThat(sesi.isPostingBukuKas()).isFalse();
    }
}
