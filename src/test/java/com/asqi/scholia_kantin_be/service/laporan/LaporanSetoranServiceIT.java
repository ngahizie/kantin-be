package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.dto.umum.RekapSetoranItem;
import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.enums.JenisLaporan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IT laporan setoran kas TU (PRD §9.2 &amp; §9.5, issue #143): rekap top-up
 * tunai per petugas, uang disetor, selisih, dan ekspor Excel-nya.
 */
@DisplayName("LaporanSetoranService — setoran kas TU")
class LaporanSetoranServiceIT extends LaporanITSupport {

    @Autowired
    private LaporanSetoranService laporan;

    @Test
    @DisplayName("#143: laporan setoran TU — rekap per petugas + selisih (belum disetor)")
    void laporanSetoran() {
        topupTunaiPetugas(555L, 50_000);
        topupTunaiPetugas(555L, 30_000);
        topupTunaiPetugas(777L, 20_000);

        List<RekapSetoranItem> r = laporan.laporanSetoran(SEKOLAH, jam.hariIni());

        assertThat(r).hasSize(2);
        RekapSetoranItem a = r.stream().filter(x -> x.getPetugasId().equals(555L))
                .findFirst().orElseThrow();
        assertThat(a.getTotalTopup()).isEqualTo(80_000);
        assertThat(a.getJumlahDisetor()).isZero();
        assertThat(a.getSelisih()).isEqualTo(80_000);
        assertThat(a.getTanggal()).isEqualTo(jam.hariIni());
    }

    @Test
    @DisplayName("#143: laporan setoran TU — selisih benar setelah konfirmasi setoran")
    void laporanSetoranSetelahKonfirmasi() {
        topupTunaiPetugas(555L, 80_000);
        // Kurang setor Rp5.000 → selisih = 5.000 (dicatat, bukan dihapus).
        setoranService.konfirmasi(SEKOLAH, jam.hariIni(), 555L, 75_000, "BA-143-1",
                "kurang Rp5.000", 999L);

        RekapSetoranItem a = laporan.laporanSetoran(SEKOLAH, jam.hariIni()).stream()
                .filter(x -> x.getPetugasId().equals(555L)).findFirst().orElseThrow();
        assertThat(a.getJumlahDisetor()).isEqualTo(75_000);
        assertThat(a.getSelisih()).isEqualTo(5_000);
        assertThat(a.getReferensiId()).isEqualTo("BA-143-1");
    }

    @Test
    @DisplayName("#143: laporan setoran TU — tenant scoping (sekolah lain kosong)")
    void laporanSetoranTenantScoping() {
        topupTunaiPetugas(555L, 80_000);

        assertThat(laporan.laporanSetoran(SEKOLAH_LAIN, jam.hariIni())).isEmpty();
    }

    @Test
    @DisplayName("#143: ekspor SETORAN_TU menghasilkan .xlsx valid + berisi baris rekap")
    void eksporSetoran() throws Exception {
        topupTunaiPetugas(555L, 80_000);

        LaporanExportService.HasilEkspor hasil = exportService.ekspor(
                SEKOLAH, JenisLaporan.SETORAN_TU, jam.hariIni(), null, null);

        assertThat(hasil.namaBerkas()).endsWith(".xlsx");
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(hasil.isi()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isGreaterThan(0);
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).contains("SETORAN");
        }
    }

    @Test
    @DisplayName("#388 SEC-03: rentang setoran lebih dari 366 hari ditolak")
    void rentangSetoranDibatasi() {
        topupTunaiPetugas(555L, 50_000);

        OffsetDateTime dari = OffsetDateTime.parse("2020-01-01T00:00:00+07:00");
        OffsetDateTime sampai = OffsetDateTime.parse("2026-01-01T00:00:00+07:00");

        assertThatThrownBy(() -> laporan.laporanSetoran(SEKOLAH, null, dari, sampai))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("maksimal 366 hari");
    }

    @Test
    @DisplayName("#388 SEC-03: rentang setoran terbalik ditolak")
    void rentangSetoranTerbalikDitolak() {
        OffsetDateTime dari = OffsetDateTime.parse("2026-11-10T00:00:00+07:00");
        OffsetDateTime sampai = OffsetDateTime.parse("2026-11-01T00:00:00+07:00");

        assertThatThrownBy(() -> laporan.laporanSetoran(SEKOLAH, null, dari, sampai))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("'dari' harus sebelum 'sampai'");
    }

}
