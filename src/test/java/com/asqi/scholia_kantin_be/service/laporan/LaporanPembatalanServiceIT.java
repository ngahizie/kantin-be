package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.dto.BarisPembatalanKasir;
import com.asqi.scholia_kantin_be.enums.JenisLaporan;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.helper.MaskingKartu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
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

    // ────────────────────────────────────────────────────────────────
    // #395 — SEC-BATAL-02/03/04/05
    // ────────────────────────────────────────────────────────────────

    /** Sisip satu transaksi VOID dengan waktu transaksi & waktu void terpisah. */
    private void voidDenganWaktu(long id, String uid, String waktuTransaksi, String waktuVoid) {
        if (uid != null) {
            // transaksi.kartu_uid ber-FK ke kartu.uid → kartu harus ada lebih dulu.
            jdbc.update("INSERT INTO kartu (uid, sekolah_id, siswa_id, nomor_kartu) "
                    + "VALUES (?, ?, ?, ?) ON CONFLICT (uid) DO NOTHING",
                    uid, SEKOLAH, SISWA, "KRT-" + id);
        }
        jdbc.update("INSERT INTO transaksi (id, idempotency_key, sekolah_id, sesi_kasir_id, "
                + "titik_kasir_id, kartu_uid, siswa_id, petugas_id, total, total_hpp, status, "
                + "alasan_void, void_at, void_oleh, waktu, created_at, updated_at) "
                + "VALUES (?, ?, ?, 1, 1, ?, ?, 555, 8000, 0, 'VOID', 'uji #395', "
                + waktuVoid + ", 777, " + waktuTransaksi + ", now(), now())",
                id, "trx-" + id, SEKOLAH, uid, SISWA);
    }

    @Test
    @DisplayName("#395: periode memakai void_at — koreksi hari ini atas transaksi kemarin ikut tampil")
    void pembatalanMemakaiWaktuVoid() {
        // Dibeli kemarin, dibatalkan hari ini → harus masuk laporan hari ini.
        voidDenganWaktu(501, null, "now() - interval '1 day'", "now()");
        // Dibeli hari ini, dibatalkan kemarin → tidak boleh masuk laporan hari ini.
        voidDenganWaktu(502, null, "now()", "now() - interval '1 day'");

        List<BarisPembatalanKasir> hariIni = laporan.pembatalanKasir(SEKOLAH, jam.hariIni(), null, null);

        assertThat(hariIni).extracting(BarisPembatalanKasir::getTransaksiId).containsExactly(501L);
    }

    @Test
    @DisplayName("#395: daftar dibatasi 500 baris terbaru (SEC-BATAL-02)")
    void pembatalanDibatasi500() {
        jdbc.update("INSERT INTO transaksi (id, idempotency_key, sekolah_id, sesi_kasir_id, "
                + "titik_kasir_id, siswa_id, petugas_id, total, total_hpp, status, alasan_void, "
                + "void_at, void_oleh, waktu, created_at, updated_at) "
                + "SELECT 5000 + g, 'trx-bulk-' || g, ?, 1, 1, ?, 555, 1000, 0, 'VOID', 'uji', "
                + "now() - (g || ' minutes')::interval, 777, now(), now(), now() "
                + "FROM generate_series(1, 505) g", SEKOLAH, SISWA);

        OffsetDateTime sampai = jam.sekarang().plusMinutes(1);
        List<BarisPembatalanKasir> hasil = laporan.pembatalanKasir(
                SEKOLAH, null, sampai.minusDays(2), sampai);

        assertThat(hasil).hasSize(LaporanPembatalanService.MAKS_BARIS);
        assertThat(hasil.get(0).getTransaksiId()).isEqualTo(5001L);                 // paling baru
        assertThat(hasil.get(hasil.size() - 1).getTransaksiId()).isEqualTo(5500L);  // batas ke-500
    }

    @Test
    @DisplayName("#395: identitas siswa & nama titik kasir diisi server (SEC-BATAL-04)")
    void pembatalanEnrichment() {
        voidDenganWaktu(601, "04A1B2C3D4", "now()", "now()");

        BarisPembatalanKasir b = laporan.pembatalanKasir(SEKOLAH, null, null, null).stream()
                .filter(r -> r.getTransaksiId() == 601L).findFirst().orElseThrow();

        assertThat(b.getSiswaNama()).isEqualTo("Siswa Uji " + SISWA);
        assertThat(b.getSiswaNis()).isEqualTo("NIS-" + SISWA);
        assertThat(b.getSiswaKelas()).isEqualTo("7A");
        assertThat(b.getTitikKasirNama()).isEqualTo("Kasir 1");
    }

    @Test
    @DisplayName("#395: kartuUid tersamar — hanya 4 karakter terakhir (SEC-BATAL-05)")
    void pembatalanMaskingKartuUid() {
        voidDenganWaktu(701, "04A1B2C3D4", "now()", "now()");

        BarisPembatalanKasir b = laporan.pembatalanKasir(SEKOLAH, null, null, null).stream()
                .filter(r -> r.getTransaksiId() == 701L).findFirst().orElseThrow();

        assertThat(b.getKartuUid()).isEqualTo(MaskingKartu.TOPENG + "C3D4");
        assertThat(b.getKartuUid()).doesNotContain("04A1B2");
    }

    @Test
    @DisplayName("#397: ekspor PEMBATALAN utuh walau > 500 baris, endpoint JSON tetap dibatasi")
    void eksporPembatalanTidakTerpotong500() throws Exception {
        jdbc.update("INSERT INTO transaksi (id, idempotency_key, sekolah_id, sesi_kasir_id, "
                + "titik_kasir_id, siswa_id, petugas_id, total, total_hpp, status, alasan_void, "
                + "void_at, void_oleh, waktu, created_at, updated_at) "
                + "SELECT 6000 + g, 'trx-ekspor-' || g, ?, 1, 1, ?, 555, 1000, 0, 'VOID', "
                + "'uji ekspor #397', now() - (g || ' minutes')::interval, 777, now(), now(), now() "
                + "FROM generate_series(1, 600) g", SEKOLAH, SISWA);

        OffsetDateTime sampai = jam.sekarang().plusMinutes(1);
        OffsetDateTime dari = sampai.minusDays(2);

        // Endpoint JSON tetap dibatasi 500 (SEC-BATAL-02).
        assertThat(laporan.pembatalanKasir(SEKOLAH, null, dari, sampai)).hasSize(500);

        LaporanExportService.HasilEkspor hasil = exportService.ekspor(
                SEKOLAH, JenisLaporan.PEMBATALAN, null, dari, sampai);

        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(hasil.isi()))) {
            var sheet = workbook.getSheetAt(0);
            long jumlahBaris = 0;
            for (var row : sheet) {
                for (var cell : row) {
                    if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
                            && "uji ekspor #397".equals(cell.getStringCellValue())) {
                        jumlahBaris++;
                    }
                }
            }
            // 600 pembatalan harus semuanya ada di berkas, bukan 500 terbaru saja.
            assertThat(jumlahBaris).isEqualTo(600);
        }
    }
}
