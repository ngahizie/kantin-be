package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.dto.BarisKerugianStok;
import com.asqi.scholia_kantin_be.dto.BarisStok;
import com.asqi.scholia_kantin_be.dto.KartuStokItem;
import com.asqi.scholia_kantin_be.enums.JenisLaporan;
import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        List<BarisStok> semua = laporan.laporanStok(SEKOLAH, false, 0, 200).getContent();
        assertThat(semua).hasSize(2);

        BarisStok nasi = semua.stream().filter(b -> b.menuId().equals(MENU_A)).findFirst().orElseThrow();
        assertThat(nasi.stok()).isEqualTo(10);
        assertThat(nasi.nilaiPersediaan()).isEqualTo(40_000);
        assertThat(nasi.menipis()).isFalse();

        BarisStok es = semua.stream().filter(b -> b.menuId().equals(MENU_B)).findFirst().orElseThrow();
        assertThat(es.stok()).isEqualTo(1);
        assertThat(es.menipis()).isTrue(); // 1 <= min 2

        List<BarisStok> menipisSaja = laporan.laporanStok(SEKOLAH, true, 0, 200).getContent();
        assertThat(menipisSaja).extracting(BarisStok::menuId).containsExactly(MENU_B);
    }

    @Test
    @DisplayName("#375: laporan stok dibatasi per halaman + total akurat")
    void laporanStokBerhalaman() {
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_A, 10, 4_000, "BM-1", "BM-1", 1L));
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_B, 5, 2_000, "BM-2", "BM-2", 1L));
        // Menu ketiga supaya ada 3 baris: nama "Apel" agar urutannya pasti di depan.
        jdbc.update("INSERT INTO menu (id, sekolah_id, kategori_id, nama, harga_jual, satuan, "
                + "stok_minimum, is_active, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'Apel', 3000, 'PCS', 1, true, now(), now())",
                30L, SEKOLAH, KATEGORI);
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, 30L, 4, 1_500, "BM-3", "BM-3", 1L));

        var halaman0 = laporan.laporanStok(SEKOLAH, false, 0, 2);
        assertThat(halaman0.getContent()).hasSize(2);      // dibatasi ukuran halaman
        assertThat(halaman0.getTotalElements()).isEqualTo(3);
        assertThat(halaman0.getTotalPages()).isEqualTo(2);
        assertThat(halaman0.hasNext()).isTrue();
        assertThat(halaman0.getContent()).extracting(BarisStok::nama)
                .containsExactly("Apel", "Es Jeruk");        // urut nama

        var halaman1 = laporan.laporanStok(SEKOLAH, false, 1, 2);
        assertThat(halaman1.getContent()).hasSize(1);
        assertThat(halaman1.hasNext()).isFalse();
        assertThat(halaman1.getContent().get(0).nama()).isEqualTo("Nasi Uduk");
    }

    @Test
    @DisplayName("#375: ukuran di luar rentang ditolak 400, bukan di-clamp")
    void laporanStokUkuranDiLuarBatasDitolak() {
        assertThatThrownBy(() -> laporan.laporanStok(SEKOLAH, false, 0, 0))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("ukuran minimal 1");
        assertThatThrownBy(() -> laporan.laporanStok(SEKOLAH, false, 0, 201))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("ukuran maksimal 200");
    }

    @Test
    @DisplayName("#375: filter menipis ikut terpaginasikan (total = jumlah menipis)")
    void laporanStokMenipisBerhalaman() {
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_A, 10, 4_000, "BM-1", "BM-1", 1L));
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_B, 1, 2_000, "BM-2", "BM-2", 1L));

        var menipis = laporan.laporanStok(SEKOLAH, true, 0, 1);
        assertThat(menipis.getContent()).extracting(BarisStok::menuId).containsExactly(MENU_B);
        assertThat(menipis.getTotalElements()).isEqualTo(1);   // hanya yang menipis dihitung
        assertThat(menipis.hasNext()).isFalse();
    }

    @Test
    @DisplayName("laporan stok — alias FE namaMenu & stokBerjalan (issue #98)")
    void laporanStokAliasFe() {
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_A, 10, 4_000, "BM-1", "BM-1", 1L));

        BarisStok nasi = laporan.laporanStok(SEKOLAH, false, 0, 200).getContent().stream()
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

    @Test
    @DisplayName("#375: ekspor STOK menjelajah semua halaman — 205 menu (> batas 200) utuh")
    void eksporStokSemuaHalaman() throws Exception {
        // 205 menu + baris stok, sengaja melebihi batas satu halaman (200) supaya
        // ekspor wajib membaca halaman kedua; kalau loop berhenti di halaman
        // pertama, 5 menu terakhir hilang dari berkas.
        jdbc.update("INSERT INTO menu (id, sekolah_id, kategori_id, nama, harga_jual, satuan, "
                + "stok_minimum, is_active, created_at, updated_at) "
                + "SELECT 1000 + g, ?, ?, 'Menu Uji ' || g, 1000, 'PCS', 0, true, now(), now() "
                + "FROM generate_series(1, 205) g", SEKOLAH, KATEGORI);
        // Baris stok dibuat otomatis oleh trigger trg_menu_sinkron_stok_minimum (V23);
        // cukup isi stok berjalannya (kalau di-INSERT ulang akan bentrok stok_pkey).
        jdbc.update("UPDATE stok SET stok = 5, hpp = 500 WHERE menu_id BETWEEN 1001 AND 1205");

        LaporanExportService.HasilEkspor hasil = exportService.ekspor(
                SEKOLAH, JenisLaporan.STOK, null, null, null, null);

        assertThat(hasil.namaBerkas()).endsWith(".xlsx");
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(hasil.isi()))) {
            var sheet = workbook.getSheetAt(0);
            long jumlahMenuUji = 0;
            for (var row : sheet) {
                for (var cell : row) {
                    if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
                            && cell.getStringCellValue().startsWith("Menu Uji ")) {
                        jumlahMenuUji++;
                    }
                }
            }
            assertThat(jumlahMenuUji).isEqualTo(205);
        }
    }
}
