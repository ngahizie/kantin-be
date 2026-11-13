package com.asqi.scholia_kantin_be.service.laporan;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelWriterTest {

    private final ExcelWriter writer = new ExcelWriter();

    @Test
    @DisplayName("tulis menghasilkan berkas .xlsx dengan template kop resmi Navy, padding kolom A, dan zebra striping")
    void tulisTemplateResmiValid() throws Exception {
        List<String> header = List.of("No", "Nama Menu", "Kategori", "Harga Satuan", "Qty", "Total");
        List<List<Object>> baris = List.of(
                List.of(1, "Nasi Goreng", "Makanan", 15000, 2, 30000),
                List.of(2, "Es Teh Manis", "Minuman", 5000, 3, 15000),
                List.of(3, "Pisang Goreng", "Snack", 3000, 4, 12000)
        );

        byte[] xlsxBytes = writer.tulis(
                "Penjualan",
                "LAPORAN PENJUALAN KANTIN",
                "SMP NEGERI 1 SKOOLIA",
                "Jl. Merdeka No. 1, Lingkungan Pendidikan SKOOLIA",
                "Dicetak pada: 13-11-2026 09:00 | Periode: 01-11-2026 s/d 13-11-2026",
                header,
                baris
        );

        assertThat(xlsxBytes).isNotEmpty();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet).isNotNull();

            // 1. Kolom A padding lebar 3.0 karakter (3 * 256 = 768)
            assertThat(sheet.getColumnWidth(0)).isEqualTo(3 * 256);

            // 2. Baris 0: Judul Laporan (Kolom B / index 1)
            var r0 = sheet.getRow(0);
            assertThat(r0.getCell(1).getStringCellValue()).isEqualTo("LAPORAN PENJUALAN KANTIN");

            // 3. Baris 1: Nama Sekolah (Kolom B / index 1)
            var r1 = sheet.getRow(1);
            assertThat(r1.getCell(1).getStringCellValue()).isEqualTo("SMP NEGERI 1 SKOOLIA");

            // 4. Baris 2: Alamat Sekolah (Kolom B / index 1)
            var r2 = sheet.getRow(2);
            assertThat(r2.getCell(1).getStringCellValue()).contains("Jl. Merdeka No. 1");

            // 5. Baris 3: Pemisah kosong (height 4pt)
            var r3 = sheet.getRow(3);
            assertThat(r3.getHeightInPoints()).isEqualTo(4f);

            // 6. Baris 4: Metadata (Kolom B / index 1)
            var r4 = sheet.getRow(4);
            assertThat(r4.getCell(1).getStringCellValue()).contains("Periode:");

            // 7. Baris 5: Header Tabel (Kolom B..G)
            var r5 = sheet.getRow(5);
            assertThat(r5.getCell(1).getStringCellValue()).isEqualTo("No");
            assertThat(r5.getCell(2).getStringCellValue()).isEqualTo("Nama Menu");
            assertThat(r5.getCell(6).getStringCellValue()).isEqualTo("Total");

            // Cek warna background Header = Navy (#123463 -> R:18, G:52, B:99)
            var styleHeader = (org.apache.poi.xssf.usermodel.XSSFCellStyle) r5.getCell(1).getCellStyle();
            XSSFColor headerColor = styleHeader.getFillForegroundXSSFColor();
            assertThat(headerColor).isNotNull();
            byte[] rgbHeader = headerColor.getRGB();
            assertThat(rgbHeader[0] & 0xFF).isEqualTo(18);
            assertThat(rgbHeader[1] & 0xFF).isEqualTo(52);
            assertThat(rgbHeader[2] & 0xFF).isEqualTo(99);

            // 8. Baris 6: Data baris 1 (Putih #FFFFFF)
            var r6 = sheet.getRow(6);
            assertThat(r6.getCell(2).getStringCellValue()).isEqualTo("Nasi Goreng");

            // 9. Baris 7: Data baris 2 (Zebra stripe #F0F4FB -> R:240, G:244, B:251)
            var r7 = sheet.getRow(7);
            assertThat(r7.getCell(2).getStringCellValue()).isEqualTo("Es Teh Manis");
            var styleZebra = (org.apache.poi.xssf.usermodel.XSSFCellStyle) r7.getCell(1).getCellStyle();
            XSSFColor zebraColor = styleZebra.getFillForegroundXSSFColor();
            assertThat(zebraColor).isNotNull();
            byte[] rgbZebra = zebraColor.getRGB();
            assertThat(rgbZebra[0] & 0xFF).isEqualTo(240);
            assertThat(rgbZebra[1] & 0xFF).isEqualTo(244);
            assertThat(rgbZebra[2] & 0xFF).isEqualTo(251);

            // 10. Baris 8: Data baris 3 (Putih kembali)
            var r8 = sheet.getRow(8);
            assertThat(r8.getCell(2).getStringCellValue()).isEqualTo("Pisang Goreng");
        }
    }

    @Test
    @DisplayName("tulisBagian menghasilkan multi-bagian dengan kop resmi Navy")
    void tulisBagianValid() throws Exception {
        List<ExcelWriter.Bagian> bagianList = List.of(
                new ExcelWriter.Bagian(
                        List.of("Metrik", "Nilai"),
                        List.of(
                                List.of("Saldo Saat Ini", 50000),
                                List.of("Total Belanja", 15000)
                        )
                ),
                new ExcelWriter.Bagian(
                        List.of("ID", "Total", "Waktu"),
                        List.of(
                                List.of("TRX-001", 15000, "13-11-2026 08:30")
                        )
                )
        );

        byte[] xlsxBytes = writer.tulisBagian(
                "Per Siswa",
                "LAPORAN PER SISWA",
                "SMP NEGERI 1 SKOOLIA",
                "Jl. Merdeka No. 1",
                "Periode: Hari ini",
                bagianList
        );

        assertThat(xlsxBytes).isNotEmpty();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getColumnWidth(0)).isEqualTo(3 * 256);
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("LAPORAN PER SISWA");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("SMP NEGERI 1 SKOOLIA");
            // Header bagian 1 di baris 5
            assertThat(sheet.getRow(5).getCell(1).getStringCellValue()).isEqualTo("Metrik");
        }
    }
}
