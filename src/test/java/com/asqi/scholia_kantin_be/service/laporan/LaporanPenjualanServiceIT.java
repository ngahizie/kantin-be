package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.dto.BarisPenjualan;
import com.asqi.scholia_kantin_be.dto.BarisPenjualanDimensi;
import com.asqi.scholia_kantin_be.dto.BarisPenjualanKasir;
import com.asqi.scholia_kantin_be.dto.RingkasanPenjualan;
import com.asqi.scholia_kantin_be.enums.JenisLaporan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT laporan penjualan &amp; laba kotor (PRD §9.5): ringkasan, per item, per
 * kategori, per titik kasir, per petugas, per kasir, plus ekspor Excel-nya.
 */
@DisplayName("LaporanPenjualanService — agregasi penjualan & laba kotor")
class LaporanPenjualanServiceIT extends LaporanITSupport {

    @Autowired
    private LaporanPenjualanService laporan;

    @Test
    @DisplayName("ringkasan penjualan — bruto, HPP, laba kotor dari transaksi SUKSES")
    void ringkasanPenjualan() {
        transaksi(1, SISWA, 21_000, 10_000);
        item(1, 1, MENU_A, "Nasi Uduk", 2, 8_000, 4_000);
        item(2, 1, MENU_B, "Es Jeruk", 1, 5_000, 2_000);
        transaksi(2, SISWA, 8_000, 4_000);
        item(3, 2, MENU_A, "Nasi Uduk", 1, 8_000, 4_000);

        RingkasanPenjualan r = laporan.ringkasanPenjualan(SEKOLAH, null, null, null);

        assertThat(r.jumlahTransaksi()).isEqualTo(2);
        assertThat(r.penjualanBruto()).isEqualTo(29_000);
        assertThat(r.totalModal()).isEqualTo(14_000);
        assertThat(r.laba()).isEqualTo(15_000);
        assertThat(r.marginPersen()).isEqualTo(51.72); // 15.000 / 29.000
        assertThat(r.jumlahRefund()).isZero();
    }

    @Test
    @DisplayName("penjualan per item & per kategori")
    void penjualanPerItemDanKategori() {
        transaksi(1, SISWA, 21_000, 10_000);
        item(1, 1, MENU_A, "Nasi Uduk", 2, 8_000, 4_000);
        item(2, 1, MENU_B, "Es Jeruk", 1, 5_000, 2_000);

        List<BarisPenjualan> perItem = laporan.penjualanPerItem(SEKOLAH, null, null, null);
        assertThat(perItem).hasSize(2);
        assertThat(perItem.get(0).nama()).isEqualTo("Nasi Uduk"); // terlaris dulu (qty 2)
        assertThat(perItem.get(0).qty()).isEqualTo(2);
        assertThat(perItem.get(0).total()).isEqualTo(16_000);
        assertThat(perItem.get(0).hargaSatuan()).isEqualTo(8_000L); // 16.000 ÷ 2
        assertThat(perItem.get(0).modal()).isEqualTo(8_000);
        assertThat(perItem.get(0).marginPersen()).isEqualTo(50.0);

        List<BarisPenjualan> perKategori = laporan.penjualanPerKategori(SEKOLAH, null, null, null);
        assertThat(perKategori).hasSize(1);
        assertThat(perKategori.get(0).kunciId()).isEqualTo(KATEGORI);
        assertThat(perKategori.get(0).nama()).isEqualTo("Makanan");
        assertThat(perKategori.get(0).total()).isEqualTo(21_000);
        assertThat(perKategori.get(0).hargaSatuan()).isNull(); // kategori tanpa harga satuan
        assertThat(perKategori.get(0).modal()).isEqualTo(10_000);
        assertThat(perKategori.get(0).marginPersen()).isEqualTo(52.38);
    }

    @Test
    @DisplayName("#116: penjualan per titik kasir — grup + nama + laba kotor")
    void penjualanPerTitik() {
        titikKasir(2, "Kasir 2");
        transaksiDimensi(1, 1, 5, 21_000, 10_000);
        transaksiDimensi(2, 1, 5, 8_000, 4_000);
        transaksiDimensi(3, 2, 6, 15_000, 6_000);

        List<BarisPenjualanDimensi> r = laporan.penjualanPerTitik(SEKOLAH, null, null, null);

        assertThat(r).hasSize(2);
        BarisPenjualanDimensi t1 = r.stream().filter(x -> x.kunciId().equals(1L)).findFirst().orElseThrow();
        assertThat(t1.nama()).isEqualTo("Kasir 1");
        assertThat(t1.jumlahTransaksi()).isEqualTo(2);
        assertThat(t1.nilai()).isEqualTo(29_000);
        assertThat(t1.hpp()).isEqualTo(14_000);
        assertThat(t1.labaKotor()).isEqualTo(15_000);

        BarisPenjualanDimensi t2 = r.stream().filter(x -> x.kunciId().equals(2L)).findFirst().orElseThrow();
        assertThat(t2.nama()).isEqualTo("Kasir 2");
        assertThat(t2.nilai()).isEqualTo(15_000);
    }

    @Test
    @DisplayName("#116: penjualan per petugas — grup + laba kotor")
    void penjualanPerPetugas() {
        titikKasir(2, "Kasir 2");
        transaksiDimensi(1, 1, 5, 21_000, 10_000);
        transaksiDimensi(2, 1, 5, 8_000, 4_000);
        transaksiDimensi(3, 2, 6, 15_000, 6_000);

        List<BarisPenjualanDimensi> r = laporan.penjualanPerPetugas(SEKOLAH, null, null, null);

        assertThat(r).hasSize(2);
        BarisPenjualanDimensi p5 = r.stream().filter(x -> x.kunciId().equals(5L)).findFirst().orElseThrow();
        assertThat(p5.jumlahTransaksi()).isEqualTo(2);
        assertThat(p5.nilai()).isEqualTo(29_000);
        assertThat(p5.labaKotor()).isEqualTo(15_000);

        BarisPenjualanDimensi p6 = r.stream().filter(x -> x.kunciId().equals(6L)).findFirst().orElseThrow();
        assertThat(p6.jumlahTransaksi()).isEqualTo(1);
        assertThat(p6.nilai()).isEqualTo(15_000);
    }

    @Test
    @DisplayName("#116: penjualan per titik — transaksi VOID tidak dihitung")
    void penjualanPerTitikAbaikanVoid() {
        transaksiDimensi(1, 1, 5, 21_000, 10_000);
        transaksiVoid(2, SISWA, 8_000, "Kartu dipakai bukan pemiliknya");

        List<BarisPenjualanDimensi> r = laporan.penjualanPerTitik(SEKOLAH, null, null, null);

        assertThat(r).hasSize(1);
        assertThat(r.get(0).jumlahTransaksi()).isEqualTo(1);
        assertThat(r.get(0).nilai()).isEqualTo(21_000);
    }

    @Test
    @DisplayName("#116: penjualan per titik — tenant scoping (sekolah lain kosong)")
    void penjualanPerTitikTenantScoping() {
        transaksiDimensi(1, 1, 5, 21_000, 10_000);

        assertThat(laporan.penjualanPerTitik(SEKOLAH_LAIN, null, null, null)).isEmpty();
    }

    @Test
    @DisplayName("#227: penjualan per kasir — grup titik+petugas, void terpisah, bersih = ringkasan sesi")
    void penjualanPerKasir() {
        titikKasir(2, "Kasir 2");
        transaksiDimensi(1, 1, 5, 21_000, 10_000);
        transaksiDimensi(2, 1, 5, 8_000, 4_000);
        transaksiDimensi(3, 2, 6, 15_000, 6_000);
        // VOID: tetap di titik 1 / petugas 5 — hitung di nilaiVoid, bukan penjualan
        transaksiVoid(4, SISWA, 9_000, "Salah tap ganda");

        List<BarisPenjualanKasir> r = laporan.penjualanPerKasir(SEKOLAH, null, null, null);

        assertThat(r).hasSize(2);
        BarisPenjualanKasir k15 = r.stream()
                .filter(x -> x.titikKasirId().equals(1L) && x.petugasId().equals(5L))
                .findFirst().orElseThrow();
        assertThat(k15.namaTitikKasir()).isEqualTo("Kasir 1");
        assertThat(k15.jumlahTransaksi()).isEqualTo(2);            // hanya SUKSES
        assertThat(k15.penjualanBruto()).isEqualTo(38_000);         // 21k + 8k + 9k void
        assertThat(k15.nilaiVoid()).isEqualTo(9_000);
        assertThat(k15.penjualanBersih()).isEqualTo(29_000);

        BarisPenjualanKasir k26 = r.stream()
                .filter(x -> x.titikKasirId().equals(2L))
                .findFirst().orElseThrow();
        assertThat(k26.namaTitikKasir()).isEqualTo("Kasir 2");
        assertThat(k26.jumlahTransaksi()).isEqualTo(1);
        assertThat(k26.nilaiVoid()).isZero();
        assertThat(k26.penjualanBersih()).isEqualTo(15_000);
    }

    @Test
    @DisplayName("#227: penjualan per kasir — tenant scoping (sekolah lain kosong)")
    void penjualanPerKasirTenantScoping() {
        transaksiDimensi(1, 1, 5, 21_000, 10_000);

        assertThat(laporan.penjualanPerKasir(SEKOLAH_LAIN, null, null, null)).isEmpty();
    }

    @Test
    @DisplayName("tenant scoping — sekolah lain tidak melihat penjualan sekolah ini")
    void penjualanTenantScoping() {
        topup(SISWA, com.asqi.scholia_kantin_be.enums.SubjekTipe.SISWA,
                50_000, com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo.TOPUP_TUNAI);
        transaksi(1, SISWA, 8_000, 4_000);
        item(1, 1, MENU_A, "Nasi Uduk", 1, 8_000, 4_000);

        assertThat(laporan.ringkasanPenjualan(SEKOLAH_LAIN, null, null, null).penjualanBruto()).isZero();
    }

    @Test
    @DisplayName("ekspor Excel — berkas .xlsx valid & berisi data")
    void eksporExcelValid() throws Exception {
        transaksi(1, SISWA, 8_000, 4_000);
        item(1, 1, MENU_A, "Nasi Uduk", 1, 8_000, 4_000);

        LaporanExportService.HasilEkspor hasil = exportService.ekspor(
                SEKOLAH, JenisLaporan.PENJUALAN, null, null, null);

        assertThat(hasil.namaBerkas()).endsWith(".xlsx");
        assertThat(hasil.isi()).isNotEmpty();

        // Berkas harus benar-benar terbaca sebagai workbook Excel.
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(hasil.isi()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isGreaterThan(0);
            // Template resmi (navy zebra) menampilkan judul laporan dalam HURUF BESAR.
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).contains("PENJUALAN");
        }
    }

    @Test
    @DisplayName("#227: ekspor PENJUALAN_KASIR menghasilkan .xlsx valid")
    void eksporPenjualanKasir() throws Exception {
        titikKasir(2, "Kasir 2");
        transaksiDimensi(1, 1, 5, 21_000, 10_000);
        transaksiDimensi(3, 2, 6, 15_000, 6_000);

        LaporanExportService.HasilEkspor hasil = exportService.ekspor(
                SEKOLAH, JenisLaporan.PENJUALAN_KASIR, null, null, null);
        assertThat(hasil.namaBerkas()).endsWith(".xlsx");
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(hasil.isi()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isGreaterThan(0);
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).contains("PENJUALAN PER KASIR");
        }
    }

    @Test
    @DisplayName("#116: ekspor PENJUALAN_TITIK & PENJUALAN_PETUGAS menghasilkan .xlsx valid")
    void eksporPenjualanDimensi() throws Exception {
        titikKasir(2, "Kasir 2");
        transaksiDimensi(1, 1, 5, 21_000, 10_000);
        transaksiDimensi(3, 2, 6, 15_000, 6_000);

        for (JenisLaporan jenis : List.of(JenisLaporan.PENJUALAN_TITIK, JenisLaporan.PENJUALAN_PETUGAS)) {
            LaporanExportService.HasilEkspor hasil = exportService.ekspor(SEKOLAH, jenis, null, null, null);
            assertThat(hasil.namaBerkas()).endsWith(".xlsx");
            try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                    new java.io.ByteArrayInputStream(hasil.isi()))) {
                var sheet = workbook.getSheetAt(0);
                assertThat(sheet.getPhysicalNumberOfRows()).isGreaterThan(0);
                assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).contains("PENJUALAN PER");
            }
        }
    }
}
