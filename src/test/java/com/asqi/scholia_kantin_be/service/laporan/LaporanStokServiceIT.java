package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.dto.BarisKerugianStok;
import com.asqi.scholia_kantin_be.dto.BarisStok;
import com.asqi.scholia_kantin_be.dto.KartuStokItem;
import com.asqi.scholia_kantin_be.enums.JenisLaporan;
import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT laporan stok (PRD §9.5): stok + nilai persediaan, kerugian stok, barang
 * masuk, kartu stok per item, dan ekspor Excel-nya.
 */
@DisplayName("LaporanStokService — stok, kerugian, & kartu stok")
class LaporanStokServiceIT extends LaporanITSupport {

    @Autowired
    private LaporanStokService laporan;

    @Test
    @DisplayName("kerugian stok — opname keluar & barang rusak beserta nilainya")
    void kerugianStok() {
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_A, 20, 4_000, "BM-1", "BM-1", 1L));
        // Opname turun 3 (hilang) & 2 rusak.
        tx.executeWithoutResult(s -> ledgerStok.sesuaikanOpname(SEKOLAH, MENU_A, 17, "opname harian", "OP-1", 1L));
        tx.executeWithoutResult(s -> ledgerStok.sesuaikanOpname(SEKOLAH, MENU_A, 15, "rusak/basi", "OP-2", true, "OPNAME", 1L));

        List<BarisKerugianStok> r = laporan.kerugianStok(SEKOLAH, null, null, null);

        assertThat(r).extracting(BarisKerugianStok::jenis)
                .containsExactlyInAnyOrder("OPNAME_KELUAR", "BARANG_RUSAK");
        BarisKerugianStok opnameKeluar = r.stream()
                .filter(b -> b.jenis().equals("OPNAME_KELUAR")).findFirst().orElseThrow();
        assertThat(opnameKeluar.totalQty()).isEqualTo(3);
        assertThat(opnameKeluar.totalNilai()).isEqualTo(3 * 4_000L);
    }

    @Test
    @DisplayName("#226: ringkasan kerugian stok konsisten dengan penjumlahan daftar")
    void ringkasanKerugianStokKonsistenDenganDaftar() {
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_A, 20, 4_000, "BM-226", "BM-226", 1L));
        tx.executeWithoutResult(s -> ledgerStok.sesuaikanOpname(SEKOLAH, MENU_A, 17, "opname harian", "OP-226-1", 1L));
        tx.executeWithoutResult(s -> ledgerStok.sesuaikanOpname(SEKOLAH, MENU_A, 15, "rusak/basi", "OP-226-2", true, "OPNAME", 1L));

        List<BarisKerugianStok> daftar = laporan.kerugianStok(SEKOLAH, null, null, null);
        var ringkasan = laporan.ringkasanKerugianStok(SEKOLAH, null, null, null);
        long nilai = daftar.stream().mapToLong(BarisKerugianStok::totalNilai).sum();
        long qty = daftar.stream().mapToLong(BarisKerugianStok::totalQty).sum();
        long kejadian = daftar.stream().mapToLong(BarisKerugianStok::jumlahBaris).sum();
        long nilaiOpname = daftar.stream().filter(b -> b.jenis().equals("OPNAME_KELUAR"))
                .mapToLong(BarisKerugianStok::totalNilai).sum();
        long qtyOpname = daftar.stream().filter(b -> b.jenis().equals("OPNAME_KELUAR"))
                .mapToLong(BarisKerugianStok::totalQty).sum();
        long nilaiRusak = daftar.stream().filter(b -> b.jenis().equals("BARANG_RUSAK"))
                .mapToLong(BarisKerugianStok::totalNilai).sum();
        long qtyRusak = daftar.stream().filter(b -> b.jenis().equals("BARANG_RUSAK"))
                .mapToLong(BarisKerugianStok::totalQty).sum();
        assertThat(ringkasan.totalKerugian()).isEqualTo(nilai);
        assertThat(ringkasan.totalQtyHilang()).isEqualTo(qty);
        assertThat(ringkasan.jumlahKejadian()).isEqualTo(kejadian);
        assertThat(ringkasan.kerugianOpname()).isEqualTo(nilaiOpname);
        assertThat(ringkasan.qtyOpname()).isEqualTo(qtyOpname);
        assertThat(ringkasan.kerugianBarangRusak()).isEqualTo(nilaiRusak);
        assertThat(ringkasan.qtyBarangRusak()).isEqualTo(qtyRusak);
    }

    @Test
    @DisplayName("kerugian stok — tenant scoping (sekolah lain kosong)")
    void kerugianStokTenantScoping() {
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_A, 20, 4_000, "BM-1", "BM-1", 1L));

        assertThat(laporan.kerugianStok(SEKOLAH_LAIN, null, null, null)).isEmpty();
    }

    @Test
    @DisplayName("laporan stok — stok sekarang, nilai persediaan, penanda menipis")
    void laporanStok() {
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_A, 10, 4_000, "BM-1", "BM-1", 1L));
        // MENU_B hanya diisi 1 → stok di bawah minimum (2).
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_B, 1, 2_000, "BM-2", "BM-2", 1L));

        List<BarisStok> semua = laporan.laporanStok(SEKOLAH, false);
        assertThat(semua).hasSize(2);

        BarisStok nasi = semua.stream().filter(b -> b.menuId().equals(MENU_A)).findFirst().orElseThrow();
        assertThat(nasi.stok()).isEqualTo(10);
        assertThat(nasi.nilaiPersediaan()).isEqualTo(40_000);
        assertThat(nasi.menipis()).isFalse();

        BarisStok es = semua.stream().filter(b -> b.menuId().equals(MENU_B)).findFirst().orElseThrow();
        assertThat(es.stok()).isEqualTo(1);
        assertThat(es.menipis()).isTrue(); // 1 <= min 2

        List<BarisStok> menipisSaja = laporan.laporanStok(SEKOLAH, true);
        assertThat(menipisSaja).extracting(BarisStok::menuId).containsExactly(MENU_B);
    }

    @Test
    @DisplayName("laporan stok — alias FE namaMenu & stokBerjalan (issue #98)")
    void laporanStokAliasFe() {
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_A, 10, 4_000, "BM-1", "BM-1", 1L));

        BarisStok nasi = laporan.laporanStok(SEKOLAH, false).stream()
                .filter(b -> b.menuId().equals(MENU_A)).findFirst().orElseThrow();

        // Alias wajib sama nilainya dengan field asal (kontrak FE Laporan Inventaris).
        assertThat(nasi.namaMenu()).isEqualTo(nasi.nama());
        assertThat(nasi.stokBerjalan()).isEqualTo(nasi.stok());
        assertThat(nasi.stokBerjalan()).isEqualTo(10);
        assertThat(nasi.namaMenu()).isNotBlank();
    }

    @Test
    @DisplayName("#118: kartu stok per item — riwayat mutasi + saldo berjalan")
    void kartuStokItem() {
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_A, 20, 4_000, "BM-1", "BM-1", 1L));
        tx.executeWithoutResult(s -> ledgerStok.sesuaikanOpname(SEKOLAH, MENU_A, 17, "opname harian", "OP-1", 1L));

        KartuStokItem kartu = laporan.kartuStokItem(SEKOLAH, MENU_A, 100);

        assertThat(kartu).isNotNull();
        assertThat(kartu.menuId()).isEqualTo(MENU_A);
        assertThat(kartu.namaMenu()).isEqualTo("Nasi Uduk");
        assertThat(kartu.stokSekarang()).isEqualTo(17);
        assertThat(kartu.mutasi()).hasSize(2);
        // terbaru dulu (id DESC)
        assertThat(kartu.mutasi().get(0).jenis()).isEqualTo(JenisMutasiStok.OPNAME_KELUAR);
        assertThat(kartu.mutasi().get(0).stokSetelah()).isEqualTo(17);
        assertThat(kartu.mutasi().get(1).jenis()).isEqualTo(JenisMutasiStok.BARANG_MASUK);
        assertThat(kartu.mutasi().get(1).qty()).isEqualTo(20);
        assertThat(kartu.mutasi().get(1).stokSetelah()).isEqualTo(20);
    }

    @Test
    @DisplayName("#118: kartu stok — menu sekolah lain / tak ada → null (→404)")
    void kartuStokItemTenantScoping() {
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_A, 20, 4_000, "BM-1", "BM-1", 1L));

        assertThat(laporan.kartuStokItem(SEKOLAH_LAIN, MENU_A, 100)).isNull();
        assertThat(laporan.kartuStokItem(SEKOLAH, 999_999L, 100)).isNull();
    }

    @Test
    @DisplayName("#118: ekspor KARTU_STOK menghasilkan .xlsx valid")
    void eksporKartuStok() throws Exception {
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_A, 20, 4_000, "BM-1", "BM-1", 1L));

        LaporanExportService.HasilEkspor hasil = exportService.ekspor(
                SEKOLAH, JenisLaporan.KARTU_STOK, null, null, null, MENU_A);

        assertThat(hasil.namaBerkas()).endsWith(".xlsx");
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(hasil.isi()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isGreaterThan(0);
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).contains("KARTU STOK");
        }
    }
}
