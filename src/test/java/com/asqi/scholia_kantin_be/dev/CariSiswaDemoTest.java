package com.asqi.scholia_kantin_be.dev;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.service.integrasi.InfoSiswa;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Uji pencarian siswa DEMO (issue #214) — memastikan layar TU (top-up tunai
 * §9.2, kontrol siswa §9.6, kandidat refund §9.3) dapat diuji <b>tanpa</b>
 * menunggu data/kontrak admin-be (Q7).
 *
 * <p>Fokus: filter kata kunci (nama/NIS), filter {@code hanyaTidakAktif},
 * pembatasan {@code batas}, dan pengambilan saldo dari ledger lokal.
 */
class CariSiswaDemoTest {

    private final LedgerSaldoService ledger = mock(LedgerSaldoService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CariSiswaDemo cari = new CariSiswaDemo(ledger, jdbc);

    CariSiswaDemoTest() {
        // Saldo demo: semua subjek SISWA bernilai tetap agar mudah diperiksa.
        when(ledger.saldo(anyLong(), eq(SubjekTipe.SISWA), anyLong())).thenReturn(25_000L);
    }

    @Test
    @DisplayName("#214: tanpa kata kunci → daftar awal (semua siswa aktif)")
    void tanpaKataKunciDaftarAwal() {
        List<InfoSiswa> hasil = cari.cari(1L, null, false, 50);

        assertThat(hasil).hasSize(9);
        assertThat(hasil).allSatisfy(s -> assertThat(s.getSaldo()).isEqualTo(25_000L));
    }

    @Test
    @DisplayName("#214: cari berdasarkan potongan NAMA (case-insensitive)")
    void cariBerdasarkanNama() {
        List<InfoSiswa> hasil = cari.cari(1L, "budi", false, 50);

        assertThat(hasil).hasSize(1);
        assertThat(hasil.get(0).getNama()).isEqualTo("Budi Santoso");
        assertThat(hasil.get(0).getNis()).isEqualTo("2024001");
        assertThat(hasil.get(0).getKelas()).isEqualTo("7A");
    }

    @Test
    @DisplayName("#214: cari berdasarkan NIS")
    void cariBerdasarkanNis() {
        List<InfoSiswa> hasil = cari.cari(1L, "2024003", false, 50);

        assertThat(hasil).hasSize(1);
        assertThat(hasil.get(0).getNama()).isEqualTo("Andi Wijaya");
    }

    @Test
    @DisplayName("#214: hanyaTidakAktif=true → hanya siswa nonaktif (kandidat refund §9.3)")
    void hanyaTidakAktif() {
        List<InfoSiswa> hasil = cari.cari(1L, null, true, 50);

        assertThat(hasil).hasSize(1);
        assertThat(hasil.get(0).getNama()).isEqualTo("Maya Sari");
        assertThat(hasil.get(0).getTidakAktif()).isTrue();
    }

    @Test
    @DisplayName("#214: batas membatasi jumlah hasil")
    void batasMembatasiHasil() {
        assertThat(cari.cari(1L, null, false, 2)).hasSize(2);
    }

    @Test
    @DisplayName("#214: kata kunci tak ada hasil → daftar kosong (bukan error)")
    void kataKunciTakAdaHasil() {
        assertThat(cari.cari(1L, "zzz-tidak-ada", false, 50)).isEmpty();
    }

    @Test
    @DisplayName("#214: identitas Siti Rahmawati konsisten dengan data demo frontend")
    void identitasSitiRahmawatiSesuaiFrontend() {
        List<InfoSiswa> hasil = cari.cari(1L, "2026002", false, 50);

        assertThat(hasil).singleElement().satisfies(s -> {
            assertThat(s.getSiswaId()).isEqualTo(102L);
            assertThat(s.getNis()).isEqualTo("2026002");
            assertThat(s.getNama()).isEqualTo("Siti Rahmawati");
            assertThat(s.getKelas()).isEqualTo("XI IPS 2");
        });
    }

    @Test
    @DisplayName("#214: identitas Abizard konsisten dengan data demo frontend dan seed RFID")
    void identitasAbizardSesuaiFrontend() {
        List<InfoSiswa> hasil = cari.cari(1L, "2024007", false, 50);

        assertThat(hasil).singleElement().satisfies(s -> {
            assertThat(s.getSiswaId()).isEqualTo(107L);
            assertThat(s.getNis()).isEqualTo("2024007");
            assertThat(s.getNama()).isEqualTo("Abizard");
            assertThat(s.getKelas()).isEqualTo("7A");
        });
    }

    @Test
    @DisplayName("#214: identitas siswa demo tambahan Fajar dan Citra konsisten dengan FE dan RFID")
    void identitasSiswaTambahanSesuaiFrontend() {
        List<InfoSiswa> fajar = cari.cari(1L, "2024008", false, 50);
        assertThat(fajar).singleElement().satisfies(s -> {
            assertThat(s.getSiswaId()).isEqualTo(108L);
            assertThat(s.getNis()).isEqualTo("2024008");
            assertThat(s.getNama()).isEqualTo("Fajar Nugraha");
            assertThat(s.getKelas()).isEqualTo("7B");
        });

        List<InfoSiswa> citra = cari.cari(1L, "2024009", false, 50);
        assertThat(citra).singleElement().satisfies(s -> {
            assertThat(s.getSiswaId()).isEqualTo(109L);
            assertThat(s.getNis()).isEqualTo("2024009");
            assertThat(s.getNama()).isEqualTo("Citra Kirana");
            assertThat(s.getKelas()).isEqualTo("8A");
        });
    }

    @Test
    @DisplayName("#214: saldo diambil per siswa dari ledger lokal (bukan hard-code)")
    void saldoDariLedger() {
        when(ledger.saldo(eq(1L), eq(SubjekTipe.SISWA), eq(101L))).thenReturn(99_000L);

        List<InfoSiswa> hasil = cari.cari(1L, "Budi", false, 50);

        assertThat(hasil.get(0).getSaldo()).isEqualTo(99_000L);
    }

    @Test
    @DisplayName("#214: sekolah lain tetap dilayani port (tenant dari pemanggil)")
    void sekolahDiteruskanKeLedger() {
        cari.cari(7L, "Budi", false, 50);

        org.mockito.Mockito.verify(ledger).saldo(eq(7L), eq(SubjekTipe.SISWA), anyLong());
    }

    // ── UID kartu per siswa (issue #301) ─────────────────────────────

    @Test
    @DisplayName("#301: UID siswa diambil dari fixture dev_siswa_rfid (bukan dikarang)")
    void uidSiswaDitemukan() {
        doReturn(List.of("DA100001")).when(jdbc)
                .query(anyString(), any(RowMapper.class), any(), any());

        Optional<String> uid = cari.cariUidSiswa(10L, 1001L);

        assertThat(uid).contains("DA100001");
    }

    @Test
    @DisplayName("#301: UID tidak ada di fixture → Optional kosong, bukan error")
    void uidSiswaTidakDitemukan() {
        doReturn(List.of()).when(jdbc)
                .query(anyString(), any(RowMapper.class), any(), any());

        assertThat(cari.cariUidSiswa(10L, 999L)).isEmpty();
    }

    @Test
    @DisplayName("#301: fixture belum dimuat / query gagal → fail-open (kosong)")
    void uidSiswaGagalAksesData() {
        doThrow(new DataAccessResourceFailureException("tabel fixture tidak ada")).when(jdbc)
                .query(anyString(), any(RowMapper.class), any(), any());

        assertThat(cari.cariUidSiswa(10L, 1001L)).isEmpty();
    }

    @Test
    @DisplayName("#301: sekolah/siswa null → kosong tanpa menyentuh DB")
    void uidSiswaParameterKosong() {
        assertThat(cari.cariUidSiswa(null, 1001L)).isEmpty();
        assertThat(cari.cariUidSiswa(10L, null)).isEmpty();
    }
}
