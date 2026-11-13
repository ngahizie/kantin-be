package com.asqi.scholia_kantin_be.service.laporan;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Penulis berkas Excel (.xlsx) untuk ekspor laporan (PRD §9.5: "Semua laporan
 * dapat diekspor ke Excel") menggunakan Apache POI.
 *
 * <p>Menghasilkan berkas Excel yang selaras dengan template resmi docs/Ekspor excel.xlsx:
 * <ul>
 *   <li>Kolom A kosong dengan lebar 3.0 sebagai margin/padding kiri.</li>
 *   <li>Kop 3 baris: Judul (16pt bold Navy #123463), Nama Sekolah (13pt bold Navy), Alamat (10pt abu-abu #646464).</li>
 *   <li>Baris pemisah kosong dan metadata periode/waktu cetak (10pt Navy).</li>
 *   <li>Header tabel Navy gelap (#123463) dengan teks putih tebal (10pt).</li>
 *   <li>Baris data zebra striping (#F0F4FB) dengan auto-alignment dan border tipis rapi.</li>
 * </ul>
 */
@Component
public class ExcelWriter {

    private static final byte[] RGB_NAVY = new byte[] {(byte) 18, (byte) 52, (byte) 99};        // #123463
    private static final byte[] RGB_ZEBRA = new byte[] {(byte) 240, (byte) 244, (byte) 251};    // #F0F4FB
    private static final byte[] RGB_GRAY = new byte[] {(byte) 100, (byte) 100, (byte) 100};     // #646464
    private static final byte[] RGB_BORDER = new byte[] {(byte) 224, (byte) 224, (byte) 224};   // #E0E0E0
    private static final byte[] RGB_WHITE = new byte[] {(byte) 255, (byte) 255, (byte) 255};    // #FFFFFF

    /**
     * Bangun workbook satu sheet standar dengan kop dan styling resmi.
     */
    public byte[] tulis(String namaSheet, List<String> judul, List<String> header,
                        List<List<Object>> baris) {
        String judulLaporan = (judul != null && !judul.isEmpty()) ? judul.get(0) : "LAPORAN KANTIN CASHLESS";
        String metaTeks = (judul != null && judul.size() > 1) ? judul.get(1) : null;
        String namaSekolah = (judul != null && judul.size() > 2) ? judul.get(2) : "SMP NEGERI 1 SKOOLIA";
        String alamatSekolah = (judul != null && judul.size() > 3) ? judul.get(3) : "Jl. Merdeka No. 1, Lingkungan Pendidikan SKOOLIA";

        return tulis(namaSheet, judulLaporan, namaSekolah, alamatSekolah, metaTeks, header, baris);
    }

    /**
     * Bangun workbook satu sheet lengkap dengan profil sekolah eksplisit.
     */
    public byte[] tulis(String namaSheet, String judulLaporan, String namaSekolah,
                        String alamatSekolah, String metadataTeks, List<String> header,
                        List<List<Object>> baris) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sanitasiNamaSheet(namaSheet));

            // Kolom A sebagai padding sisi kiri (lebar 3.0 karakter = ~768 twips)
            sheet.setColumnWidth(0, 3 * 256);

            int startCol = 1; // Kolom B
            int jumlahKolom = (header != null && !header.isEmpty()) ? header.size() : 1;
            int endCol = Math.max(startCol, startCol + jumlahKolom - 1);

            // Buat gaya sel
            XSSFCellStyle gayaJudul = buatGayaKop(wb, 16, true, RGB_NAVY, HorizontalAlignment.CENTER);
            XSSFCellStyle gayaSekolah = buatGayaKop(wb, 13, true, RGB_NAVY, HorizontalAlignment.CENTER);
            XSSFCellStyle gayaAlamat = buatGayaKop(wb, 10, false, RGB_GRAY, HorizontalAlignment.CENTER);
            XSSFCellStyle gayaMeta = buatGayaKop(wb, 10, false, RGB_NAVY, HorizontalAlignment.LEFT);

            XSSFCellStyle gayaHeader = buatGayaHeader(wb);
            XSSFCellStyle gayaDataPutihKiri = buatGayaData(wb, false, HorizontalAlignment.LEFT);
            XSSFCellStyle gayaDataPutihTengah = buatGayaData(wb, false, HorizontalAlignment.CENTER);
            XSSFCellStyle gayaDataPutihKanan = buatGayaData(wb, false, HorizontalAlignment.RIGHT);
            XSSFCellStyle gayaDataZebraKiri = buatGayaData(wb, true, HorizontalAlignment.LEFT);
            XSSFCellStyle gayaDataZebraTengah = buatGayaData(wb, true, HorizontalAlignment.CENTER);
            XSSFCellStyle gayaDataZebraKanan = buatGayaData(wb, true, HorizontalAlignment.RIGHT);

            // Baris 0: Judul Laporan (tinggi 26pt)
            Row r0 = sheet.createRow(0);
            r0.setHeightInPoints(26f);
            Cell c0 = r0.createCell(startCol);
            c0.setCellValue(judulLaporan != null ? judulLaporan.toUpperCase() : "LAPORAN KANTIN");
            c0.setCellStyle(gayaJudul);
            if (endCol > startCol) {
                sheet.addMergedRegion(new CellRangeAddress(0, 0, startCol, endCol));
            }

            // Baris 1: Nama Sekolah (tinggi 20pt)
            Row r1 = sheet.createRow(1);
            r1.setHeightInPoints(20f);
            Cell c1 = r1.createCell(startCol);
            c1.setCellValue(namaSekolah != null ? namaSekolah.toUpperCase() : "SEKOLAH SKOOLIA");
            c1.setCellStyle(gayaSekolah);
            if (endCol > startCol) {
                sheet.addMergedRegion(new CellRangeAddress(1, 1, startCol, endCol));
            }

            // Baris 2: Alamat Sekolah (tinggi 16pt)
            Row r2 = sheet.createRow(2);
            r2.setHeightInPoints(16f);
            Cell c2 = r2.createCell(startCol);
            c2.setCellValue(alamatSekolah != null ? alamatSekolah : "Jl. Merdeka No. 1, Lingkungan Pendidikan SKOOLIA");
            c2.setCellStyle(gayaAlamat);
            if (endCol > startCol) {
                sheet.addMergedRegion(new CellRangeAddress(2, 2, startCol, endCol));
            }

            // Baris 3: Pemisah kosong (tinggi 4pt)
            Row r3 = sheet.createRow(3);
            r3.setHeightInPoints(4f);

            // Baris 4: Metadata (tinggi 16pt)
            Row r4 = sheet.createRow(4);
            r4.setHeightInPoints(16f);
            Cell c4 = r4.createCell(startCol);
            c4.setCellValue(metadataTeks != null ? metadataTeks : "Dicetak pada: " + java.time.LocalDate.now());
            c4.setCellStyle(gayaMeta);
            if (endCol > startCol) {
                sheet.addMergedRegion(new CellRangeAddress(4, 4, startCol, endCol));
            }

            // Baris 5: Header Kolom Tabel (tinggi 22pt)
            int r = 5;
            if (header != null && !header.isEmpty()) {
                Row row = sheet.createRow(r++);
                row.setHeightInPoints(22f);
                for (int c = 0; c < header.size(); c++) {
                    Cell cell = row.createCell(startCol + c);
                    cell.setCellValue(header.get(c));
                    cell.setCellStyle(gayaHeader);
                }
            }

            // Baris 6+: Data baris dengan zebra striping
            if (baris != null) {
                for (int i = 0; i < baris.size(); i++) {
                    List<Object> data = baris.get(i);
                    Row row = sheet.createRow(r++);
                    row.setHeightInPoints(18f);
                    boolean isZebra = (i % 2 == 1);

                    for (int c = 0; c < data.size(); c++) {
                        Cell cell = row.createCell(startCol + c);
                        Object val = data.get(c);
                        isiSel(cell, val);

                        HorizontalAlignment align = tentukanAlignment(c < header.size() ? header.get(c) : "", val);
                        if (isZebra) {
                            if (align == HorizontalAlignment.RIGHT) cell.setCellStyle(gayaDataZebraKanan);
                            else if (align == HorizontalAlignment.CENTER) cell.setCellStyle(gayaDataZebraTengah);
                            else cell.setCellStyle(gayaDataZebraKiri);
                        } else {
                            if (align == HorizontalAlignment.RIGHT) cell.setCellStyle(gayaDataPutihKanan);
                            else if (align == HorizontalAlignment.CENTER) cell.setCellStyle(gayaDataPutihTengah);
                            else cell.setCellStyle(gayaDataPutihKiri);
                        }
                    }
                }
            }

            // Auto-width kolom B sampai selesai
            for (int c = 0; c < jumlahKolom; c++) {
                int colIdx = startCol + c;
                sheet.autoSizeColumn(colIdx);
                int lebar = Math.min(Math.max(sheet.getColumnWidth(colIdx) + 768, 11 * 256), 45 * 256);
                sheet.setColumnWidth(colIdx, lebar);
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Gagal membangun berkas Excel", e);
        }
    }

    /**
     * Bangun workbook satu sheet dengan beberapa bagian tabel (multi-tabel)
     * seperti Laporan Per Siswa.
     */
    public byte[] tulisBagian(String namaSheet, List<String> judul, List<Bagian> bagian) {
        String judulLaporan = (judul != null && !judul.isEmpty()) ? judul.get(0) : "LAPORAN KANTIN CASHLESS";
        String metaTeks = (judul != null && judul.size() > 1) ? judul.get(1) : null;
        String namaSekolah = (judul != null && judul.size() > 2) ? judul.get(2) : "SMP NEGERI 1 SKOOLIA";
        String alamatSekolah = (judul != null && judul.size() > 3) ? judul.get(3) : "Jl. Merdeka No. 1, Lingkungan Pendidikan SKOOLIA";

        return tulisBagian(namaSheet, judulLaporan, namaSekolah, alamatSekolah, metaTeks, bagian);
    }

    /**
     * Bangun workbook multi-tabel dengan profil sekolah eksplisit.
     */
    public byte[] tulisBagian(String namaSheet, String judulLaporan, String namaSekolah,
                              String alamatSekolah, String metadataTeks, List<Bagian> bagian) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sanitasiNamaSheet(namaSheet));

            sheet.setColumnWidth(0, 3 * 256);
            int startCol = 1;

            int kolomMaks = 1;
            if (bagian != null) {
                for (Bagian b : bagian) {
                    if (b.header() != null) kolomMaks = Math.max(kolomMaks, b.header().size());
                }
            }
            int endCol = Math.max(startCol, startCol + kolomMaks - 1);

            XSSFCellStyle gayaJudul = buatGayaKop(wb, 16, true, RGB_NAVY, HorizontalAlignment.CENTER);
            XSSFCellStyle gayaSekolah = buatGayaKop(wb, 13, true, RGB_NAVY, HorizontalAlignment.CENTER);
            XSSFCellStyle gayaAlamat = buatGayaKop(wb, 10, false, RGB_GRAY, HorizontalAlignment.CENTER);
            XSSFCellStyle gayaMeta = buatGayaKop(wb, 10, false, RGB_NAVY, HorizontalAlignment.LEFT);

            XSSFCellStyle gayaHeader = buatGayaHeader(wb);
            XSSFCellStyle gayaDataPutihKiri = buatGayaData(wb, false, HorizontalAlignment.LEFT);
            XSSFCellStyle gayaDataPutihTengah = buatGayaData(wb, false, HorizontalAlignment.CENTER);
            XSSFCellStyle gayaDataPutihKanan = buatGayaData(wb, false, HorizontalAlignment.RIGHT);
            XSSFCellStyle gayaDataZebraKiri = buatGayaData(wb, true, HorizontalAlignment.LEFT);
            XSSFCellStyle gayaDataZebraTengah = buatGayaData(wb, true, HorizontalAlignment.CENTER);
            XSSFCellStyle gayaDataZebraKanan = buatGayaData(wb, true, HorizontalAlignment.RIGHT);

            // Kop 3 Baris
            Row r0 = sheet.createRow(0);
            r0.setHeightInPoints(26f);
            Cell c0 = r0.createCell(startCol);
            c0.setCellValue(judulLaporan != null ? judulLaporan.toUpperCase() : "LAPORAN KANTIN");
            c0.setCellStyle(gayaJudul);
            if (endCol > startCol) sheet.addMergedRegion(new CellRangeAddress(0, 0, startCol, endCol));

            Row r1 = sheet.createRow(1);
            r1.setHeightInPoints(20f);
            Cell c1 = r1.createCell(startCol);
            c1.setCellValue(namaSekolah != null ? namaSekolah.toUpperCase() : "SEKOLAH SKOOLIA");
            c1.setCellStyle(gayaSekolah);
            if (endCol > startCol) sheet.addMergedRegion(new CellRangeAddress(1, 1, startCol, endCol));

            Row r2 = sheet.createRow(2);
            r2.setHeightInPoints(16f);
            Cell c2 = r2.createCell(startCol);
            c2.setCellValue(alamatSekolah != null ? alamatSekolah : "Jl. Merdeka No. 1, Lingkungan Pendidikan SKOOLIA");
            c2.setCellStyle(gayaAlamat);
            if (endCol > startCol) sheet.addMergedRegion(new CellRangeAddress(2, 2, startCol, endCol));

            Row r3 = sheet.createRow(3);
            r3.setHeightInPoints(4f);

            Row r4 = sheet.createRow(4);
            r4.setHeightInPoints(16f);
            Cell c4 = r4.createCell(startCol);
            c4.setCellValue(metadataTeks != null ? metadataTeks : "Dicetak pada: " + java.time.LocalDate.now());
            c4.setCellStyle(gayaMeta);
            if (endCol > startCol) sheet.addMergedRegion(new CellRangeAddress(4, 4, startCol, endCol));

            int r = 5;
            if (bagian != null) {
                for (Bagian b : bagian) {
                    if (b.header() != null && !b.header().isEmpty()) {
                        Row row = sheet.createRow(r++);
                        row.setHeightInPoints(22f);
                        for (int c = 0; c < b.header().size(); c++) {
                            Cell cell = row.createCell(startCol + c);
                            cell.setCellValue(b.header().get(c));
                            cell.setCellStyle(gayaHeader);
                        }
                    }
                    if (b.baris() != null) {
                        for (int i = 0; i < b.baris().size(); i++) {
                            List<Object> data = b.baris().get(i);
                            Row row = sheet.createRow(r++);
                            row.setHeightInPoints(18f);
                            boolean isZebra = (i % 2 == 1);

                            for (int c = 0; c < data.size(); c++) {
                                Cell cell = row.createCell(startCol + c);
                                Object val = data.get(c);
                                isiSel(cell, val);

                                String headerName = (b.header() != null && c < b.header().size()) ? b.header().get(c) : "";
                                HorizontalAlignment align = tentukanAlignment(headerName, val);
                                if (isZebra) {
                                    if (align == HorizontalAlignment.RIGHT) cell.setCellStyle(gayaDataZebraKanan);
                                    else if (align == HorizontalAlignment.CENTER) cell.setCellStyle(gayaDataZebraTengah);
                                    else cell.setCellStyle(gayaDataZebraKiri);
                                } else {
                                    if (align == HorizontalAlignment.RIGHT) cell.setCellStyle(gayaDataPutihKanan);
                                    else if (align == HorizontalAlignment.CENTER) cell.setCellStyle(gayaDataPutihTengah);
                                    else cell.setCellStyle(gayaDataPutihKiri);
                                }
                            }
                        }
                    }
                    r++; // Baris pemisah antar bagian
                }
            }

            for (int c = 0; c < kolomMaks; c++) {
                int colIdx = startCol + c;
                sheet.autoSizeColumn(colIdx);
                int lebar = Math.min(Math.max(sheet.getColumnWidth(colIdx) + 768, 11 * 256), 45 * 256);
                sheet.setColumnWidth(colIdx, lebar);
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Gagal membangun berkas Excel", e);
        }
    }

    /** Satu bagian laporan: header kolom + baris data (satu tabel). */
    public record Bagian(List<String> header, List<List<Object>> baris) {
    }

    private void isiSel(Cell cell, Object nilai) {
        if (nilai == null) {
            cell.setBlank();
        } else if (nilai instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else if (nilai instanceof Boolean b) {
            cell.setCellValue(b);
        } else {
            cell.setCellValue(nilai.toString());
        }
    }

    private HorizontalAlignment tentukanAlignment(String header, Object val) {
        String h = header != null ? header.toLowerCase() : "";
        if (h.contains("rp") || h.contains("harga") || h.contains("total") || h.contains("hpp") ||
            h.contains("laba") || h.contains("qty") || h.contains("jumlah") || h.contains("saldo") ||
            h.contains("nominal") || h.contains("margin") || h.contains("omzet") || h.contains("omset") ||
            h.contains("disetor") || h.contains("selisih") || (val instanceof Number)) {
            return HorizontalAlignment.RIGHT;
        }
        if (h.equals("no") || h.equals("id") || h.contains("kode") || h.contains("status") ||
            h.contains("tipe") || h.contains("waktu") || h.contains("tanggal") || h.contains("kelas")) {
            return HorizontalAlignment.CENTER;
        }
        return HorizontalAlignment.LEFT;
    }

    private XSSFCellStyle buatGayaKop(XSSFWorkbook wb, int ukuran, boolean tebal, byte[] warnaRgb, HorizontalAlignment align) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName("Calibri");
        font.setFontHeightInPoints((short) ukuran);
        font.setBold(tebal);
        font.setColor(new XSSFColor(warnaRgb, null));
        style.setFont(font);
        style.setAlignment(align);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private XSSFCellStyle buatGayaHeader(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName("Calibri");
        font.setFontHeightInPoints((short) 10);
        font.setBold(true);
        font.setColor(new XSSFColor(RGB_WHITE, null));
        style.setFont(font);

        style.setFillForegroundColor(new XSSFColor(RGB_NAVY, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);

        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private XSSFCellStyle buatGayaData(XSSFWorkbook wb, boolean isZebra, HorizontalAlignment align) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName("Calibri");
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);

        if (isZebra) {
            style.setFillForegroundColor(new XSSFColor(RGB_ZEBRA, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }

        style.setAlignment(align);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /** Buang karakter yang dilarang di nama sheet Excel ([:\\/?*\[\]]). */
    private String sanitasiNamaSheet(String nama) {
        if (nama == null || nama.isBlank()) {
            return "Laporan";
        }
        String bersih = nama.replaceAll("[\\\\/:?*\\[\\]]", "-");
        return bersih.length() > 31 ? bersih.substring(0, 31) : bersih;
    }
}
