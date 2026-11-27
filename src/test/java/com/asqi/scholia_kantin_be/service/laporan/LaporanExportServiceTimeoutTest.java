package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.component.exception.ServerBusyException;
import com.asqi.scholia_kantin_be.enums.JenisLaporan;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.helper.UkuranHalaman;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Batas waktu dan batas konkurensi ekspor laporan (issue #435, dilengkapi #452).
 *
 * <p>Dua hal yang dibuktikan di sini:
 * <ul>
 *   <li>{@code SET LOCAL statement_timeout} benar-benar dipasang saat
 *       {@code ekspor()} dijalankan — bukan hanya saat method pembantunya
 *       dipanggil langsung (celah yang ditutup #452).</li>
 *   <li>Ekspor bersamaan dibatasi supaya koneksi kolam tidak dihabiskan
 *       pembuatan workbook.</li>
 * </ul>
 */
class LaporanExportServiceTimeoutTest {

    private final LaporanPenjualanService penjualan = mock(LaporanPenjualanService.class);
    private final LaporanSaldoService saldo = mock(LaporanSaldoService.class);
    private final LaporanStokService stok = mock(LaporanStokService.class);
    private final LaporanPembatalanService pembatalan = mock(LaporanPembatalanService.class);
    private final LaporanSiswaService siswa = mock(LaporanSiswaService.class);
    private final LaporanSetoranService setoran = mock(LaporanSetoranService.class);
    private final ExcelWriter excel = mock(ExcelWriter.class);
    private final JamKantin jam = mock(JamKantin.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private LaporanExportService service;

    @BeforeEach
    void siap() {
        service = new LaporanExportService(penjualan, saldo, stok, pembatalan, siswa,
                setoran, excel, jam, jdbc);
        when(jam.hariIni()).thenReturn(LocalDate.of(2026, 11, 27));
        when(jam.sekarang()).thenReturn(OffsetDateTime.parse("2026-11-27T10:15:00+07:00"));
    }

    @Test
    @DisplayName("#452: ekspor() benar-benar memasang SET LOCAL (bukan hanya method pembantu)")
    void eksporMemasangBatasWaktu() {
        ReflectionTestUtils.setField(service, "batasWaktuLaporan", Duration.ofSeconds(30));
        when(stok.laporanStok(eq(7L), anyBoolean(), anyInt(), eq(UkuranHalaman.UKURAN_MAKS)))
                .thenReturn(Page.empty());

        service.ekspor(7L, JenisLaporan.STOK, null, null, null);

        verify(jdbc).execute("SET LOCAL statement_timeout = 30000");
    }

    @Test
    @DisplayName("#452: batas laporan dibaca sebagai Duration (45s -> 45000 ms)")
    void batasWaktuDurasiDikonversiKeMilidetik() {
        ReflectionTestUtils.setField(service, "batasWaktuLaporan", Duration.ofSeconds(45));

        service.pasangBatasWaktuEkspor();

        verify(jdbc).execute("SET LOCAL statement_timeout = 45000");
    }

    @Test
    @DisplayName("nilai tidak valid (0) jatuh ke default 30 detik")
    void nilaiTidakValidMemakaiDefault() {
        ReflectionTestUtils.setField(service, "batasWaktuLaporan", Duration.ZERO);

        service.pasangBatasWaktuEkspor();

        verify(jdbc).execute("SET LOCAL statement_timeout = 30000");
    }

    @Test
    @DisplayName("tanpa JDBC (unit test) tidak melakukan apa pun")
    void tanpaJdbcTidakGagal() {
        LaporanExportService tanpaJdbc = new LaporanExportService(penjualan, saldo, stok,
                pembatalan, siswa, setoran, excel, jam, null);

        assertThatCode(tanpaJdbc::pasangBatasWaktuEkspor).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("#452: ekspor melebihi batas konkurensi dijawab ServerBusyException (503), bukan menunggu")
    void eksporMelebihiBatasKonkurensiDitolak() throws Exception {
        ReflectionTestUtils.setField(service, "eksporKonkurenMaks", 1);
        service.siapkanIzinEkspor();
        Semaphore izin = (Semaphore) ReflectionTestUtils.getField(service, "izinEkspor");
        // Habiskan satu-satunya izin, seolah ada ekspor lain yang sedang berjalan.
        izin.acquire();

        try {
            assertThatThrownBy(() -> service.ekspor(7L, JenisLaporan.STOK, null, null, null))
                    .isInstanceOf(ServerBusyException.class)
                    .hasMessageContaining("bersamaan");
        } finally {
            izin.release();
        }
    }
}
