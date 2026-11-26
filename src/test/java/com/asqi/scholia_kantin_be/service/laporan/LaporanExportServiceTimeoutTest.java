package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.helper.JamKantin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Bukti bahwa ekspor laporan memakai batas waktu transaksi sendiri (issue #435).
 *
 * <p>Batas global 10 dtk melindungi kolam koneksi, tetapi laporan setahun bisa
 * lebih lama. Nilai {@code SET LOCAL} berasal dari konfigurasi bertipe long,
 * bukan input pengguna.
 */
class LaporanExportServiceTimeoutTest {

    private static LaporanExportService service(JdbcTemplate jdbc) {
        return new LaporanExportService(
                mock(LaporanPenjualanService.class),
                mock(LaporanSaldoService.class),
                mock(LaporanStokService.class),
                mock(LaporanPembatalanService.class),
                mock(LaporanSiswaService.class),
                mock(LaporanSetoranService.class),
                mock(ExcelWriter.class),
                mock(JamKantin.class),
                jdbc);
    }

    @Test
    @DisplayName("batas laporan dipasang sebagai SET LOCAL di transaksi ekspor")
    void memasangBatasWaktuEkspor() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LaporanExportService service = service(jdbc);
        ReflectionTestUtils.setField(service, "batasWaktuLaporanMs", 45_000L);

        service.pasangBatasWaktuEkspor();

        verify(jdbc).execute("SET LOCAL statement_timeout = 45000");
    }

    @Test
    @DisplayName("nilai tidak valid jatuh ke default 30 detik")
    void nilaiTidakValidMemakaiDefault() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LaporanExportService service = service(jdbc);
        ReflectionTestUtils.setField(service, "batasWaktuLaporanMs", 0L);

        service.pasangBatasWaktuEkspor();

        verify(jdbc).execute("SET LOCAL statement_timeout = 30000");
    }

    @Test
    @DisplayName("tanpa JDBC (unit test) tidak melakukan apa pun")
    void tanpaJdbcTidakGagal() {
        LaporanExportService service = service(null);

        assertThatCode(service::pasangBatasWaktuEkspor).doesNotThrowAnyException();
    }
}
