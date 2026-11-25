package com.asqi.scholia_kantin_be.service.siswa;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.dto.siswa.PasangKartuSiswaRequest;
import com.asqi.scholia_kantin_be.dto.siswa.SimpanSiswaAdminRequest;
import com.asqi.scholia_kantin_be.dto.siswa.SiswaManajemenResponse;
import com.asqi.scholia_kantin_be.service.integrasi.InfoKartu;
import com.asqi.scholia_kantin_be.service.integrasi.KartuLookupPort;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.FixtureInduk;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
@DisplayName("SiswaManajemenService — IT PostgreSQL nyata (siswa, wali, kartu, isolasi tenant)")
class SiswaManajemenServiceIT {

    private static final long SEKOLAH_1 = 9101L;
    private static final long SEKOLAH_2 = 9102L;
    private static final long ADMIN_ID = 9199L;

    @Autowired
    private SiswaManajemenService service;
    @Autowired
    private KartuLookupPort kartuLookup;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void bersihkan() {
        jdbc.execute("DELETE FROM kartu WHERE sekolah_id IN (9101, 9102)");
        jdbc.execute("DELETE FROM orang_tua WHERE sekolah_id IN (9101, 9102)");
        jdbc.execute("DELETE FROM siswa WHERE sekolah_id IN (9101, 9102)");
        FixtureInduk.sekolah(jdbc, SEKOLAH_1, SEKOLAH_2);
        FixtureInduk.pengguna(jdbc, SEKOLAH_1, ADMIN_ID);
    }

    @Test
    @DisplayName("CRUD siswa + wali + ganti kartu RFID di PostgreSQL nyata dan langsung terbaca di KartuLookupPort")
    void siklusLengkapSiswaDanKartu() {
        SimpanSiswaAdminRequest req = new SimpanSiswaAdminRequest();
        req.setNis("IT-2026-01");
        req.setNisn("0099112233");
        req.setNama("Nadia Putri");
        req.setKelas("9B");
        req.setNamaOrangTua("Ibu Ratna");
        req.setTeleponOrangTua("08199887766");
        req.setHubunganOrangTua("IBU");
        req.setRfidUid("ituid001");
        req.setNomorKartu("CARD-IT-1");

        SiswaManajemenResponse dibuat = service.buat(SEKOLAH_1, req, ADMIN_ID);
        assertThat(dibuat.getSiswaId()).isNotNull();
        assertThat(dibuat.getSaldo()).isZero();
        assertThat(dibuat.getRfidUid()).isEqualTo("ITUID001");

        InfoKartu hasilTap = kartuLookup.cariBerdasarkanUid(SEKOLAH_1, "ituid001");
        assertThat(hasilTap.isDikenal()).isTrue();
        assertThat(hasilTap.getNama()).isEqualTo("Nadia Putri");

        // Tenant lain tidak boleh bisa melihat atau mengubah siswa ini
        assertThatThrownBy(() -> service.detail(SEKOLAH_2, dibuat.getSiswaId()))
                .isInstanceOf(NotFoundEntity.class);

        // Duplikat NIS di sekolah yang sama ditolak 409
        SimpanSiswaAdminRequest dupNis = new SimpanSiswaAdminRequest();
        dupNis.setNis("IT-2026-01");
        dupNis.setNama("Duplikat");
        assertThatThrownBy(() -> service.buat(SEKOLAH_1, dupNis, ADMIN_ID))
                .isInstanceOf(ConflictException.class);

        // Ganti kartu: kartu lama nonaktif, kartu baru aktif dan terindeks tunggal
        PasangKartuSiswaRequest gantiKartu = new PasangKartuSiswaRequest();
        gantiKartu.setRfidUid("ITUID002");
        gantiKartu.setNomorKartu("CARD-IT-2");
        SiswaManajemenResponse setelahGanti = service.aturKartu(SEKOLAH_1, dibuat.getSiswaId(), gantiKartu, ADMIN_ID);
        assertThat(setelahGanti.getRfidUid()).isEqualTo("ITUID002");

        InfoKartu tapLama = kartuLookup.cariBerdasarkanUid(SEKOLAH_1, "ITUID001");
        assertThat(tapLama.isDikenal()).isFalse();

        InfoKartu tapBaru = kartuLookup.cariBerdasarkanUid(SEKOLAH_1, "ITUID002");
        assertThat(tapBaru.isDikenal()).isTrue();

        // Nonaktifkan siswa & filter
        service.ubahStatus(SEKOLAH_1, dibuat.getSiswaId(), false, ADMIN_ID);
        List<SiswaManajemenResponse> nonaktif = service.daftar(SEKOLAH_1, "Nadia", false, true, 50);
        assertThat(nonaktif).hasSize(1);
        assertThat(nonaktif.get(0).isAktif()).isFalse();
    }
}
