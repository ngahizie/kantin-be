package com.asqi.scholia_kantin_be.service.siswa;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.dto.PasangKartuSiswaRequest;
import com.asqi.scholia_kantin_be.dto.SimpanSiswaAdminRequest;
import com.asqi.scholia_kantin_be.dto.SiswaManajemenResponse;
import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.Kartu;
import com.asqi.scholia_kantin_be.model.OrangTua;
import com.asqi.scholia_kantin_be.model.Saldo;
import com.asqi.scholia_kantin_be.model.Siswa;
import com.asqi.scholia_kantin_be.repository.KartuRepository;
import com.asqi.scholia_kantin_be.repository.OrangTuaRepository;
import com.asqi.scholia_kantin_be.repository.SaldoRepository;
import com.asqi.scholia_kantin_be.repository.SiswaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SiswaManajemenService — unit test aturan bisnis & isolasi tenant")
class SiswaManajemenServiceTest {

    private static final Long SEKOLAH_A = 1L;
    private static final Long SEKOLAH_B = 2L;
    private static final Long AKTOR_ADMIN = 99L;
    private static final OffsetDateTime WAKTU = OffsetDateTime.of(2026, 11, 24, 10, 0, 0, 0, ZoneOffset.ofHours(7));

    @Mock
    private SiswaRepository siswaRepo;
    @Mock
    private KartuRepository kartuRepo;
    @Mock
    private OrangTuaRepository orangTuaRepo;
    @Mock
    private SaldoRepository saldoRepo;
    @Mock
    private AuditLogger auditLogger;
    @Mock
    private IdGenerator idGenerator;
    @Mock
    private JamKantin jam;

    @InjectMocks
    private SiswaManajemenService service;

    @BeforeEach
    void setup() {
        lenient().when(jam.sekarang()).thenReturn(WAKTU);
        lenient().when(idGenerator.berikutnyaLong()).thenReturn(1001L);
    }

    @Test
    @DisplayName("buat siswa baru dengan wali dan RFID mulai dengan saldo Rp 0 tanpa mutasi")
    void buatSiswaBaruDenganKartuDanWali() {
        SimpanSiswaAdminRequest req = new SimpanSiswaAdminRequest();
        req.setNis("2026001");
        req.setNisn("0088123456");
        req.setNama("Budi Santoso");
        req.setKelas("8A");
        req.setFotoUrl("sekolah-1/siswa/budi.png");
        req.setNamaOrangTua("Ahmad Santoso");
        req.setTeleponOrangTua("081234567890");
        req.setHubunganOrangTua("ayah");
        req.setRfidUid("ab12cd34");
        req.setNomorKartu("K-001");

        when(siswaRepo.existsBySekolahIdAndNis(SEKOLAH_A, "2026001")).thenReturn(false);
        when(kartuRepo.findByUidIgnoreCase("AB12CD34")).thenReturn(Optional.empty());
        when(kartuRepo.existsBySekolahIdAndNomorKartu(SEKOLAH_A, "K-001")).thenReturn(false);
        when(kartuRepo.findBySekolahIdAndSiswaIdOrderByCreatedAtDesc(SEKOLAH_A, 1001L)).thenReturn(List.of());
        when(orangTuaRepo.findBySiswaIdAndSekolahId(1001L, SEKOLAH_A)).thenReturn(List.of());
        when(kartuRepo.findFirstBySekolahIdAndSiswaIdAndAktifTrue(SEKOLAH_A, 1001L))
                .thenReturn(Optional.of(Kartu.builder().uid("AB12CD34").nomorKartu("K-001").aktif(true).build()));
        when(saldoRepo.findBySekolahIdAndSiswaId(SEKOLAH_A, 1001L)).thenReturn(Optional.empty());

        SiswaManajemenResponse hasil = service.buat(SEKOLAH_A, req, AKTOR_ADMIN);

        assertThat(hasil.getSiswaId()).isEqualTo(1001L);
        assertThat(hasil.getNis()).isEqualTo("2026001");
        assertThat(hasil.getSaldo()).isZero();
        assertThat(hasil.getRfidUid()).isEqualTo("AB12CD34");

        ArgumentCaptor<OrangTua> waliCaptor = ArgumentCaptor.forClass(OrangTua.class);
        verify(orangTuaRepo).save(waliCaptor.capture());
        assertThat(waliCaptor.getValue().getHubungan()).isEqualTo("AYAH");
        verify(saldoRepo, never()).save(any(Saldo.class));
        verify(auditLogger).catat(eq(AKTOR_ADMIN), eq(SEKOLAH_A), eq("TAMBAH_SISWA_SEMENTARA"),
                eq("Siswa"), eq("1001"), any(), any(), any());
    }

    @Test
    @DisplayName("tolak NIS duplikat dalam sekolah yang sama (409)")
    void tolakNisDuplikat() {
        SimpanSiswaAdminRequest req = new SimpanSiswaAdminRequest();
        req.setNis("2026001");
        req.setNama("Siti");

        when(siswaRepo.existsBySekolahIdAndNis(SEKOLAH_A, "2026001")).thenReturn(true);

        assertThatThrownBy(() -> service.buat(SEKOLAH_A, req, AKTOR_ADMIN))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("NIS");
        verify(siswaRepo, never()).save(any());
    }

    @Test
    @DisplayName("tolak fotoUrl milik tenant sekolah lain")
    void tolakFotoUrlTenantLain() {
        SimpanSiswaAdminRequest req = new SimpanSiswaAdminRequest();
        req.setNis("2026002");
        req.setNama("Rina");
        req.setFotoUrl("sekolah-2/siswa/rina.png");

        assertThatThrownBy(() -> service.buat(SEKOLAH_A, req, AKTOR_ADMIN))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("storage sekolah ini");
        verifyNoInteractions(siswaRepo);
    }

    @Test
    @DisplayName("tolak UID RFID yang sudah dipakai siswa atau sekolah lain (409)")
    void tolakRfidMilikSiswaLainAtauSekolahLain() {
        SimpanSiswaAdminRequest req = new SimpanSiswaAdminRequest();
        req.setNis("2026003");
        req.setNama("Doni");
        req.setRfidUid("DEAD1234");

        when(siswaRepo.existsBySekolahIdAndNis(SEKOLAH_A, "2026003")).thenReturn(false);
        when(kartuRepo.findByUidIgnoreCase("DEAD1234"))
                .thenReturn(Optional.of(Kartu.builder().uid("DEAD1234").sekolahId(SEKOLAH_B).siswaId(55L).build()));

        assertThatThrownBy(() -> service.buat(SEKOLAH_A, req, AKTOR_ADMIN))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("sekolah lain");
        verify(siswaRepo, never()).save(any());
    }

    @Test
    @DisplayName("ganti kartu RFID menonaktifkan kartu lama dan flush sebelum memasang kartu baru")
    void gantiKartuMenonaktifkanKartuLama() {
        Siswa siswa = Siswa.builder().id(500L).sekolahId(SEKOLAH_A).nis("2026004").nama("Laras").aktif(true).build();
        Kartu kartuLama = Kartu.builder().uid("OLD1111").sekolahId(SEKOLAH_A).siswaId(500L).aktif(true).build();

        when(siswaRepo.findByIdAndSekolahId(500L, SEKOLAH_A)).thenReturn(Optional.of(siswa));
        when(kartuRepo.findFirstBySekolahIdAndSiswaIdAndAktifTrue(SEKOLAH_A, 500L))
                .thenReturn(Optional.of(kartuLama))
                .thenReturn(Optional.of(Kartu.builder().uid("NEW2222").nomorKartu("K-022").aktif(true).build()));
        when(kartuRepo.findByUidIgnoreCase("NEW2222")).thenReturn(Optional.empty());
        when(kartuRepo.existsBySekolahIdAndNomorKartu(SEKOLAH_A, "K-022")).thenReturn(false);
        when(kartuRepo.findBySekolahIdAndSiswaIdOrderByCreatedAtDesc(SEKOLAH_A, 500L))
                .thenReturn(List.of(kartuLama));
        when(orangTuaRepo.findBySiswaIdAndSekolahId(500L, SEKOLAH_A)).thenReturn(List.of());
        when(saldoRepo.findBySekolahIdAndSiswaId(SEKOLAH_A, 500L)).thenReturn(Optional.empty());

        PasangKartuSiswaRequest req = new PasangKartuSiswaRequest();
        req.setRfidUid("new2222");
        req.setNomorKartu("K-022");

        SiswaManajemenResponse hasil = service.aturKartu(SEKOLAH_A, 500L, req, AKTOR_ADMIN);

        assertThat(kartuLama.isAktif()).isFalse();
        assertThat(hasil.getRfidUid()).isEqualTo("NEW2222");
        verify(kartuRepo).flush();
    }

    @Test
    @DisplayName("detail siswa sekolah lain tidak ditemukan (404)")
    void isolasiTenantNotFound() {
        when(siswaRepo.findByIdAndSekolahId(500L, SEKOLAH_B)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(SEKOLAH_B, 500L))
                .isInstanceOf(NotFoundEntity.class);
    }
}
