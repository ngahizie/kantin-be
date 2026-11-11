package com.asqi.scholia_kantin_be.service.katalog;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.enums.SatuanMenu;
import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.Menu;
import com.asqi.scholia_kantin_be.repository.KategoriMenuRepository;
import com.asqi.scholia_kantin_be.repository.MenuRepository;
import com.asqi.scholia_kantin_be.repository.StokCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("KatalogService — validasi dan normalisasi kodeBarcode")
class KatalogServiceBarcodeTest {

    private MenuRepository menuRepo;
    private KatalogService service;

    @BeforeEach
    void setUp() {
        KategoriMenuRepository kategoriRepo = mock(KategoriMenuRepository.class);
        menuRepo = mock(MenuRepository.class);
        StokCacheRepository stokRepo = mock(StokCacheRepository.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        JamKantin jam = mock(JamKantin.class);
        AuditLogger auditLogger = mock(AuditLogger.class);

        when(idGenerator.berikutnyaLong()).thenReturn(101L);
        when(jam.sekarang()).thenReturn(OffsetDateTime.parse("2026-11-11T14:00:00+07:00"));
        when(menuRepo.save(any(Menu.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new KatalogService(kategoriRepo, menuRepo, stokRepo, idGenerator, jam, auditLogger);
    }

    @Test
    void buatMenuMenormalisasiBarcodeDanMenolakDuplikat() {
        when(menuRepo.existsBySekolahIdAndKodeBarcodeAndDeletedAtIsNull(1L, "8996001300112"))
                .thenReturn(false)
                .thenReturn(true);

        Menu created = service.buatMenu(1L, null, "Teh Botol", 4_500L,
                SatuanMenu.BOTOL, null, 5, "  8996001300112  ", 3L);
        assertThat(created.getKodeBarcode()).isEqualTo("8996001300112");

        assertThatThrownBy(() -> service.buatMenu(1L, null, "Minuman Lain", 5_000L,
                SatuanMenu.BOTOL, null, 5, "8996001300112", 3L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("8996001300112");
    }

    @Test
    void ubahMenuMenjagaBarcodeJikaNullDanMenghapusJikaKosong() {
        Menu existing = Menu.builder()
                .id(18L)
                .sekolahId(1L)
                .nama("Teh Botol")
                .kodeBarcode("8996001300112")
                .hargaJual(4_500L)
                .satuan(SatuanMenu.BOTOL)
                .stokMinimum(5)
                .isActive(true)
                .build();
        when(menuRepo.findByIdAndSekolahIdAndDeletedAtIsNull(18L, 1L))
                .thenReturn(Optional.of(existing));

        // null -> pertahankan barcode lama
        Menu preserved = service.ubahMenu(1L, 18L, null, "Teh Botol", 4_500L,
                SatuanMenu.BOTOL, null, 5, false, null, 3L);
        assertThat(preserved.getKodeBarcode()).isEqualTo("8996001300112");

        // blank string -> bersihkan menjadi null
        Menu cleared = service.ubahMenu(1L, 18L, null, "Teh Botol", 4_500L,
                SatuanMenu.BOTOL, null, 5, true, "   ", 3L);
        assertThat(cleared.getKodeBarcode()).isNull();
    }
}

