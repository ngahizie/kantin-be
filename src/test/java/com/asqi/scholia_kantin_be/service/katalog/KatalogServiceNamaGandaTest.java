package com.asqi.scholia_kantin_be.service.katalog;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.enums.SatuanMenu;
import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.Menu;
import com.asqi.scholia_kantin_be.repository.KategoriMenuRepository;
import com.asqi.scholia_kantin_be.repository.MenuRepository;
import com.asqi.scholia_kantin_be.repository.StokRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Nama menu tidak boleh ganda di dalam satu sekolah. Tanpa aturan ini dua baris
 * "Jus Jeruk" bisa dibuat lewat UI dan kasir tidak tahu harus memilih yang mana
 * (kejadian nyata: id 1011 dari seed vs id 1794800737755973 dari UI).
 *
 * <p>Kelas ini memakai mock — bukan Testcontainers — karena yang diuji aturan
 * di service, bukan query SQL-nya.
 */
@DisplayName("KatalogService — nama menu tidak boleh ganda per sekolah")
class KatalogServiceNamaGandaTest {

    private MenuRepository menuRepo;
    private KatalogService service;

    @BeforeEach
    void setUp() {
        KategoriMenuRepository kategoriRepo = mock(KategoriMenuRepository.class);
        menuRepo = mock(MenuRepository.class);
        StokRepository stokRepo = mock(StokRepository.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        JamKantin jam = mock(JamKantin.class);
        AuditLogger auditLogger = mock(AuditLogger.class);

        when(idGenerator.berikutnyaLong()).thenReturn(500L);
        when(jam.sekarang()).thenReturn(OffsetDateTime.parse("2026-11-16T10:00:00+07:00"));
        when(menuRepo.save(any(Menu.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new KatalogService(kategoriRepo, menuRepo, stokRepo, idGenerator, jam, auditLogger);
    }

    @Test
    void buatMenuMenolakNamaYangSudahAdaDiSekolahYangSama() {
        when(menuRepo.existsBySekolahIdAndNamaIgnoreCaseAndDeletedAtIsNull(1L, "Jus Jeruk"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.buatMenu(1L, null, "Jus Jeruk", 7_000L,
                SatuanMenu.PCS, null, 0, null, 1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Jus Jeruk");

        verify(menuRepo, never()).save(any(Menu.class));
    }

    @Test
    void buatMenuMenerimaNamaYangSamaDiSekolahLain() {
        // Nama sama di sekolah lain itu sah — "Ayam Geprek" dua kantin, dua produk.
        when(menuRepo.existsBySekolahIdAndNamaIgnoreCaseAndDeletedAtIsNull(10L, "Ayam Geprek"))
                .thenReturn(false);

        Menu tersimpan = service.buatMenu(10L, null, "Ayam Geprek", 13_000L,
                SatuanMenu.PORSI, null, 0, null, 16L);

        assertThat(tersimpan.getNama()).isEqualTo("Ayam Geprek");
        assertThat(tersimpan.getSekolahId()).isEqualTo(10L);
    }

    @Test
    void buatMenuMenganggapNamaSamaTanpaPeduliHurufBesarKecil() {
        // "es teh manis" vs "Es Teh Manis": normalisasiNama hanya merapikan spasi,
        // yang menyamakan kapital adalah IgnoreCase pada nama query repository.
        // Karena itu argumen yang diteruskan service tetap "es teh manis".
        when(menuRepo.existsBySekolahIdAndNamaIgnoreCaseAndDeletedAtIsNull(1L, "es teh manis"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.buatMenu(1L, null, "  es   teh manis  ", 3_000L,
                SatuanMenu.PCS, null, 0, null, 6L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("es teh manis");
    }

    @Test
    void ubahMenuMenolakNamaYangDipakaiMenuLain() {
        Menu menu = Menu.builder()
                .id(700L).sekolahId(1L).nama("Nasi Goreng").hargaJual(12_000L)
                .satuan(SatuanMenu.PORSI).stokMinimum(0).isActive(true)
                .createdAt(OffsetDateTime.parse("2026-11-16T09:00:00+07:00"))
                .updatedAt(OffsetDateTime.parse("2026-11-16T09:00:00+07:00"))
                .build();
        when(menuRepo.findByIdAndSekolahIdAndDeletedAtIsNull(700L, 1L))
                .thenReturn(Optional.of(menu));
        when(menuRepo.existsBySekolahIdAndNamaIgnoreCaseAndIdNotAndDeletedAtIsNull(
                1L, "Bakso", 700L)).thenReturn(true);

        assertThatThrownBy(() -> service.ubahMenu(1L, 700L, null, "Bakso", null, null,
                null, null, null, 6L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Bakso");
    }

    @Test
    void ubahMenuMenerimaNamaSendiriSaatHanyaMerapikanPenulisan() {
        Menu menu = Menu.builder()
                .id(700L).sekolahId(1L).nama("Nasi Goreng").hargaJual(12_000L)
                .satuan(SatuanMenu.PORSI).stokMinimum(0).isActive(true)
                .createdAt(OffsetDateTime.parse("2026-11-16T09:00:00+07:00"))
                .updatedAt(OffsetDateTime.parse("2026-11-16T09:00:00+07:00"))
                .build();
        when(menuRepo.findByIdAndSekolahIdAndDeletedAtIsNull(700L, 1L))
                .thenReturn(Optional.of(menu));
        // Menu ini tidak menghitung dirinya sendiri (IdNot).
        when(menuRepo.existsBySekolahIdAndNamaIgnoreCaseAndIdNotAndDeletedAtIsNull(
                1L, "Nasi Goreng Spesial", 700L)).thenReturn(false);

        Menu hasil = service.ubahMenu(1L, 700L, null, "Nasi Goreng Spesial", null, null,
                null, null, null, 6L);

        assertThat(hasil.getNama()).isEqualTo("Nasi Goreng Spesial");
    }
}