package com.asqi.scholia_kantin_be.dev;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Uji pembaca identitas sekolah untuk shim login dev: nama & logo diambil dari
 * tabel {@code sekolah} (bukan hardcode), dengan fallback aman bila data/DB
 * belum tersedia sehingga login tidak ikut gagal.
 */
@DisplayName("Identitas sekolah pada shim login dev")
class DevSekolahLookupTest {

    @Test
    @DisplayName("Baris sekolah ada → nama & logo_url diteruskan apa adanya")
    void membacaNamaDanLogo() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), anyLong())).thenReturn(List.of(Map.of(
                "id", 10L,
                "nama", "SMP Negeri 10 Skoolia",
                "logo_url", "https://contoh.test/logo-10.png")));

        var hasil = new DevSekolahLookup(jdbc).cari(10L);

        assertThat(hasil.id()).isEqualTo(10L);
        assertThat(hasil.nama()).isEqualTo("SMP Negeri 10 Skoolia");
        assertThat(hasil.logoUrl()).isEqualTo("https://contoh.test/logo-10.png");
    }

    @Test
    @DisplayName("logo_url NULL → logoUrl null (FE memakai fallback /logo.png)")
    void logoTanpaNilaiMenjadiNull() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, Object> baris = new java.util.HashMap<>();
        baris.put("id", 1L);
        baris.put("nama", "SMP Negeri 1 Skoolia");
        baris.put("logo_url", null); // Map.of() menolak nilai null
        when(jdbc.queryForList(anyString(), anyLong())).thenReturn(List.of(baris));

        var hasil = new DevSekolahLookup(jdbc).cari(1L);

        assertThat(hasil.nama()).isEqualTo("SMP Negeri 1 Skoolia");
        assertThat(hasil.logoUrl()).isNull();
    }

    @Test
    @DisplayName("Sekolah tidak ditemukan → nama pengganti, logo null")
    void sekolahTidakAda() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), anyLong())).thenReturn(List.of());

        var hasil = new DevSekolahLookup(jdbc).cari(7L);

        assertThat(hasil.id()).isEqualTo(7L);
        assertThat(hasil.nama()).isEqualTo("Sekolah 7");
        assertThat(hasil.logoUrl()).isNull();
    }

    @Test
    @DisplayName("Tabel sekolah belum ada → login tidak gagal, nama pengganti")
    void tabelBelumAda() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), anyLong()))
                .thenThrow(new DataAccessResourceFailureException("relation \"sekolah\" does not exist"));

        var hasil = new DevSekolahLookup(jdbc).cari(1L);

        assertThat(hasil.nama()).isEqualTo("Sekolah 1");
        assertThat(hasil.logoUrl()).isNull();
    }
}
