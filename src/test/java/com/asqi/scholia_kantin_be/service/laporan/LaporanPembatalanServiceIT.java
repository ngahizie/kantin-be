package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.dto.BarisPembatalanKasir;
import com.asqi.scholia_kantin_be.enums.JenisLaporan;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT laporan pembatalan kasir (PRD §9.5, issue #114): daftar transaksi VOID
 * beserta alasan/petugas dan ekspor Excel-nya.
 */
@DisplayName("LaporanPembatalanService — pembatalan kasir")
class LaporanPembatalanServiceIT extends LaporanITSupport {

    @Autowired
    private LaporanPembatalanService laporan;

    @Test
    @DisplayName("#114: pembatalan kasir — hanya transaksi VOID, alasan & petugas ikut")
    void pembatalanKasir() {
        transaksi(1, SISWA, 8_000, 4_000); // SUKSES → tidak masuk laporan
        transaksiVoid(2, SISWA, 16_000, "Kartu dipakai bukan pemiliknya");
        transaksiVoid(3, SISWA2, 8_000, "salah input");

        List<BarisPembatalanKasir> r = laporan.pembatalanKasir(SEKOLAH, null, null, null);

        assertThat(r).hasSize(2);
        assertThat(r).extracting(BarisPembatalanKasir::getTransaksiId)
                .containsExactlyInAnyOrder(2L, 3L);
        BarisPembatalanKasir b = r.stream()
                .filter(x -> x.getTransaksiId().equals(2L)).findFirst().orElseThrow();
        assertThat(b.getSubjekTipe()).isEqualTo(SubjekTipe.SISWA);
        assertThat(b.getSiswaId()).isEqualTo(SISWA);
        assertThat(b.getAlasanVoid()).isEqualTo("Kartu dipakai bukan pemiliknya");
        assertThat(b.getVoidOleh()).isEqualTo(777L);
        assertThat(b.getVoidAt()).isNotNull();
    }

    @Test
    @DisplayName("#114: pembatalan kasir — tenant scoping (sekolah lain kosong)")
    void pembatalanKasirTenantScoping() {
        transaksiVoid(2, SISWA, 16_000, "Kartu dipakai bukan pemiliknya");

        assertThat(laporan.pembatalanKasir(SEKOLAH_LAIN, null, null, null)).isEmpty();
    }

    @Test
    @DisplayName("#114: ekspor PEMBATALAN menghasilkan .xlsx valid")
    void eksporPembatalan() throws Exception {
        transaksiVoid(2, SISWA, 16_000, "Kartu dipakai bukan pemiliknya");

        LaporanExportService.HasilEkspor hasil = exportService.ekspor(
                SEKOLAH, JenisLaporan.PEMBATALAN, null, null, null);

        assertThat(hasil.namaBerkas()).endsWith(".xlsx");
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(hasil.isi()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isGreaterThan(0);
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).contains("PEMBATALAN");
        }
    }
}
