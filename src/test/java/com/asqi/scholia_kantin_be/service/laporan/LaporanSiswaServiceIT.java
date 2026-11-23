package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.dto.LaporanPerSiswa;
import com.asqi.scholia_kantin_be.dto.LaporanRiwayatKeseluruhanItem;
import com.asqi.scholia_kantin_be.enums.JenisLaporan;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.service.integrasi.InfoSiswa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IT laporan per siswa &amp; riwayat siswa (PRD §9.5, issue #117/#223/#297):
 * ringkasan transaksi, mutasi saldo, lookup identitas dari admin-be, dan
 * ekspor Excel tiga bagian.
 */
@DisplayName("LaporanSiswaService — per siswa & riwayat")
class LaporanSiswaServiceIT extends LaporanITSupport {

    @Autowired
    private LaporanSiswaService laporan;

    @Test
    @DisplayName("#117: per siswa — ringkasan + transaksi + mutasi saldo satu subjek")
    void laporanPerSiswa() {
        topup(SISWA, SubjekTipe.SISWA, 50_000, JenisMutasiSaldo.TOPUP_TUNAI);
        transaksi(1, SISWA, 21_000, 10_000);
        transaksi(2, SISWA, 8_000, 4_000);
        transaksiVoid(3, SISWA, 16_000, "Kartu dipakai bukan pemiliknya");
        // Subjek lain — harus TIDAK ikut
        transaksi(4, SISWA2, 30_000, 12_000);

        LaporanPerSiswa r = laporan.laporanPerSiswa(SEKOLAH, SISWA, null, null, null);

        assertThat(r.subjekTipe()).isEqualTo(SubjekTipe.SISWA);
        assertThat(r.siswaId()).isEqualTo(SISWA);
        assertThat(r.saldo()).isEqualTo(50_000L);
        assertThat(r.transaksi()).hasSize(3);
        assertThat(r.ringkasan().jumlahTransaksiSukses()).isEqualTo(2);
        assertThat(r.ringkasan().nilaiBelanjaSukses()).isEqualTo(29_000);
        assertThat(r.ringkasan().totalHpp()).isEqualTo(14_000);
        assertThat(r.ringkasan().jumlahTransaksiVoid()).isEqualTo(1);
        assertThat(r.ringkasan().nilaiVoid()).isEqualTo(16_000);
        assertThat(r.ringkasan().totalTopup()).isEqualTo(50_000);
        assertThat(r.mutasiSaldo()).hasSize(1);
        assertThat(r.mutasiSaldo().get(0).jenis()).isEqualTo(JenisMutasiSaldo.TOPUP_TUNAI);
        assertThat(r.mutasiSaldo().get(0).nominal()).isEqualTo(50_000);
    }

    @Test
    @DisplayName("#117: per siswa — siswa tanpa data → ringkasan nol, daftar kosong")
    void laporanPerSiswaKosong() {
        LaporanPerSiswa r = laporan.laporanPerSiswa(SEKOLAH, 999L, null, null, null);

        assertThat(r.saldo()).isZero();
        assertThat(r.transaksi()).isEmpty();
        assertThat(r.mutasiSaldo()).isEmpty();
        assertThat(r.ringkasan().jumlahTransaksiSukses()).isZero();
        assertThat(r.ringkasan().totalTopup()).isZero();
    }

    @Test
    @DisplayName("#117: per siswa — tenant scoping (sekolah lain kosong)")
    void laporanPerSiswaTenantScoping() {
        topup(SISWA, SubjekTipe.SISWA, 50_000, JenisMutasiSaldo.TOPUP_TUNAI);
        transaksi(1, SISWA, 21_000, 10_000);

        LaporanPerSiswa r = laporan.laporanPerSiswa(SEKOLAH_LAIN, SISWA, null, null, null);

        assertThat(r.transaksi()).isEmpty();
        assertThat(r.mutasiSaldo()).isEmpty();
        assertThat(r.saldo()).isZero();
    }

    @Test
    @DisplayName("#297: profil riwayat mengisi NIS/nama/kelas lewat lookup per ID")
    void riwayatSiswaIdentitas() {
        topup(SISWA, SubjekTipe.SISWA, 50_000, JenisMutasiSaldo.TOPUP_TUNAI);
        org.mockito.Mockito.when(cariSiswaPort.cariBerdasarkanId(SEKOLAH, SISWA))
                .thenReturn(Optional.of(InfoSiswa.builder()
                        .siswaId(SISWA).nis("2024001").nama("Budi Santoso")
                        .kelas("7A").saldo(50_000).build()));

        var r = laporan.riwayatSiswa(SEKOLAH, SISWA, null, null);

        assertThat(r.profil().siswaId()).isEqualTo(SISWA);
        assertThat(r.profil().nis()).isEqualTo("2024001");
        assertThat(r.profil().nama()).isEqualTo("Budi Santoso");
        assertThat(r.profil().kelas()).isEqualTo("7A");
        assertThat(r.riwayat()).isNotEmpty();
        org.mockito.Mockito.verify(cariSiswaPort).cariBerdasarkanId(SEKOLAH, SISWA);
    }

    @Test
    @DisplayName("#297: identitas tidak tersedia → profil tetap dikirim, identitas null")
    void riwayatSiswaTanpaIdentitas() {
        topup(SISWA, SubjekTipe.SISWA, 50_000, JenisMutasiSaldo.TOPUP_TUNAI);
        org.mockito.Mockito.when(cariSiswaPort.cariBerdasarkanId(SEKOLAH, SISWA))
                .thenReturn(Optional.empty());

        var r = laporan.riwayatSiswa(SEKOLAH, SISWA, null, null);

        assertThat(r.profil().nis()).isNull();
        assertThat(r.profil().nama()).isNull();
        assertThat(r.profil().kelas()).isNull();
        assertThat(r.profil().saldo()).isEqualTo(50_000L);
        assertThat(r.riwayat()).isNotEmpty();
    }

    @Test
    @DisplayName("#297: integrasi gagal → riwayat tetap dikirim (fail-open)")
    void riwayatSiswaIntegrasiGagal() {
        topup(SISWA, SubjekTipe.SISWA, 50_000, JenisMutasiSaldo.TOPUP_TUNAI);
        org.mockito.Mockito.when(cariSiswaPort.cariBerdasarkanId(SEKOLAH, SISWA))
                .thenThrow(new IllegalStateException("admin-be down"));

        var r = laporan.riwayatSiswa(SEKOLAH, SISWA, null, null);

        assertThat(r.profil().nama()).isNull();
        assertThat(r.riwayat()).isNotEmpty();
    }

    @Test
    @DisplayName("#297: siswa sekolah lain → NotFound (identitas tidak bocor lintas tenant)")
    void riwayatSiswaTenantScoping() {
        topup(SISWA, SubjekTipe.SISWA, 50_000, JenisMutasiSaldo.TOPUP_TUNAI);

        assertThatThrownBy(() -> laporan.riwayatSiswa(SEKOLAH_LAIN, SISWA, null, null))
                .isInstanceOf(NotFoundEntity.class);
        org.mockito.Mockito.verify(cariSiswaPort, org.mockito.Mockito.never())
                .cariBerdasarkanId(SEKOLAH_LAIN, SISWA);
    }

    @Test
    @DisplayName("#144: ekspor PER_SISWA — 3 bagian (ringkasan, transaksi, mutasi) .xlsx valid")
    void eksporPerSiswa() throws Exception {
        topup(SISWA, SubjekTipe.SISWA, 50_000, JenisMutasiSaldo.TOPUP_TUNAI);
        transaksi(1, SISWA, 21_000, 10_000);
        transaksi(2, SISWA, 8_000, 4_000);

        LaporanExportService.HasilEkspor hasil = exportService.ekspor(
                SEKOLAH, JenisLaporan.PER_SISWA, null, null, null, null, SISWA);

        assertThat(hasil.namaBerkas()).endsWith(".xlsx");
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(hasil.isi()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isGreaterThan(0);
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).contains("PER SISWA");
            // Isi memuat judul + ketiga bagian (ringkasan/transaksi/mutasi).
            StringBuilder semua = new StringBuilder();
            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                var row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    var cell = row.getCell(c);
                    if (cell != null) {
                        semua.append(cell.toString()).append(" ");
                    }
                }
            }
            assertThat(semua.toString()).contains("Subjek").contains("Transaksi ID")
                    .contains("Mutasi ID");
        }
    }

    @Test
    @DisplayName("#144: ekspor PER_SISWA tanpa siswaId → ditolak (validasi wajib)")
    void eksporPerSiswaTanpaSubjekDitolak() {
        assertThatThrownBy(() -> exportService.ekspor(
                SEKOLAH, JenisLaporan.PER_SISWA, null, null, null, null, null))
                .isInstanceOf(com.asqi.scholia_kantin_be.component.exception.InvalidOperationException.class)
                .hasMessageContaining("siswaId");
    }

    @Test
    @DisplayName("#144: ekspor PER_SISWA subjek tanpa data → tetap .xlsx valid (bagian kosong)")
    void eksporPerSiswaKosong() throws Exception {
        LaporanExportService.HasilEkspor hasil = exportService.ekspor(
                SEKOLAH, JenisLaporan.PER_SISWA, null, null, null, null, 999_999L);

        assertThat(hasil.namaBerkas()).endsWith(".xlsx");
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(hasil.isi()))) {
            assertThat(workbook.getSheetAt(0).getPhysicalNumberOfRows()).isGreaterThan(0);
        }
    }

    // ────────────────────────────────────────────────────────────────
    // #398 — SEC-RIW-02 & SEC-RIW-06
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("#398: identitas siswa di luar 50 hasil pencarian tetap terisi (SEC-RIW-02)")
    void riwayatKeseluruhanIdentitasDiLuarBatasPencarian() {
        // 60 siswa tambahan; yang punya mutasi sengaja id terakhir (959), jauh di
        // luar 50 hasil pertama kalau kode masih memakai cari(..., 50).
        jdbc.update("INSERT INTO siswa (id, sekolah_id, nis, nama, kelas, aktif) "
                + "SELECT 900 + g, ?, 'NIS-' || (900 + g), 'Siswa ' || (900 + g), '7A', true "
                + "FROM generate_series(1, 60) g", SEKOLAH);
        topup(959, SubjekTipe.SISWA, 25_000, JenisMutasiSaldo.TOPUP_TUNAI);

        OffsetDateTime sampai = jam.sekarang().plusHours(1);
        List<LaporanRiwayatKeseluruhanItem> hasil = laporan.riwayatKeseluruhan(
                SEKOLAH, null, sampai.minusDays(1), sampai, 100);

        LaporanRiwayatKeseluruhanItem baris = hasil.stream()
                .filter(r -> Long.valueOf(959L).equals(r.siswaId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("mutasi siswa 959 tidak muncul"));

        assertThat(baris.siswaNama()).isEqualTo("Siswa 959");
        assertThat(baris.siswaNis()).isEqualTo("NIS-959");
        assertThat(baris.siswaKelas()).isEqualTo("7A");
    }

    @Test
    @DisplayName("#398: rentang terbalik ditolak 400, bukan nol baris senyap (SEC-RIW-06)")
    void riwayatKeseluruhanRentangTerbalikDitolak() {
        OffsetDateTime akhir = jam.sekarang();

        assertThatThrownBy(() -> laporan.riwayatKeseluruhan(
                SEKOLAH, null, akhir, akhir.minusDays(1), 10))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("dari");
    }

    @Test
    @DisplayName("#398: `dari` di masa depan tanpa `sampai` ditolak 400 (rentang efektif)")
    void riwayatKeseluruhanDariMasaDepanDitolak() {
        // `sampai` kosong → default-nya "sekarang", sehingga rentang efektifnya
        // terbalik. Sebelum perbaikan ini query mengembalikan nol baris diam-diam.
        OffsetDateTime dari = jam.sekarang().plusYears(5);

        assertThatThrownBy(() -> laporan.riwayatKeseluruhan(SEKOLAH, null, dari, null, 10))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("dari");
    }
}
