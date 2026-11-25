package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.dto.laporan.RingkasanRekonsiliasi;
import com.asqi.scholia_kantin_be.dto.laporan.RingkasanSaldoMengendap;
import com.asqi.scholia_kantin_be.dto.laporan.SiswaSaldoDetail;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.service.integrasi.InfoSiswa;
import com.asqi.scholia_kantin_be.service.kasir.PerintahMutasiSaldo;
import com.asqi.scholia_kantin_be.support.FixtureInduk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT laporan saldo (PRD §9.5): saldo mengendap, rincian per siswa, dan
 * invariant rekonsiliasi {@code Σ KREDIT − Σ DEBIT == saldo mengendap}.
 */
@DisplayName("LaporanSaldoService — saldo mengendap & rekonsiliasi")
class LaporanSaldoServiceIT extends LaporanITSupport {

    @Autowired
    private LaporanSaldoService laporan;

    @Test
    @DisplayName("#224: saldo per siswa — filter NIS, tenant-scope, saldo & belanja hari ini")
    void saldoMengendapPerSiswa() {
        topup(SISWA, SubjekTipe.SISWA, 50_000, JenisMutasiSaldo.TOPUP_TUNAI);
        tx.executeWithoutResult(s -> ledgerSaldo.debit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.SISWA).siswaId(SISWA)
                .jenis(JenisMutasiSaldo.PENJUALAN).nominal(8_000)
                .idempotencyKey("jual-224").build()));

        var hasil = laporan.saldoMengendapSiswa(SEKOLAH, "2024001", 20);
        assertThat(hasil).hasSize(1);
        assertThat(hasil.getFirst().nis()).isEqualTo("2024001");
        assertThat(hasil.getFirst().nama()).isEqualTo("Budi Santoso");
        assertThat(hasil.getFirst().saldo()).isEqualTo(42_000);
        assertThat(hasil.getFirst().belanjaHariIni()).isEqualTo(8_000);
        OffsetDateTime waktuLedgerTerakhir = jdbc.queryForObject(
                "SELECT MAX(waktu) FROM mutasi_saldo WHERE sekolah_id = ? AND siswa_id = ?",
                OffsetDateTime.class, SEKOLAH, SISWA);
        assertThat(hasil.getFirst().terakhirTransaksi()).isEqualTo(waktuLedgerTerakhir);
        assertThat(laporan.saldoMengendapSiswa(SEKOLAH_LAIN, "2024001", 20)).isEmpty();

        var tanpaMutasi = laporan.saldoMengendapSiswa(SEKOLAH, "2024002", 20);
        assertThat(tanpaMutasi).hasSize(1);
        assertThat(tanpaMutasi.getFirst().terakhirTransaksi()).isNull();
    }

    @Test
    @DisplayName("#413: rincian saldo banyak siswa — batch query tetap presisi per siswa")
    void saldoMengendapPerSiswaBatch() {
        long siswa3 = 300L;
        FixtureInduk.siswa(jdbc, SEKOLAH, siswa3);

        topup(SISWA, SubjekTipe.SISWA, 50_000, JenisMutasiSaldo.TOPUP_TUNAI);
        topup(SISWA2, SubjekTipe.SISWA, 30_000, JenisMutasiSaldo.TOPUP_ONLINE);
        tx.executeWithoutResult(s -> ledgerSaldo.debit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.SISWA).siswaId(SISWA)
                .jenis(JenisMutasiSaldo.PENJUALAN).nominal(8_000)
                .idempotencyKey("jual-413-a").build()));
        tx.executeWithoutResult(s -> ledgerSaldo.debit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.SISWA).siswaId(SISWA2)
                .jenis(JenisMutasiSaldo.PENJUALAN).nominal(5_000)
                .idempotencyKey("jual-413-b").build()));

        // SISWA diblokir, SISWA2 punya limit aktif, siswa3 limit nonaktif (diabaikan).
        jdbc.update("INSERT INTO pembatasan (id, sekolah_id, siswa_id, jenis, alasan, aktif) "
                + "VALUES (?, ?, ?, 'BLOKIR_SISWA', 'kartu hilang', true)", 9_001L, SEKOLAH, SISWA);
        jdbc.update("INSERT INTO pembatasan (id, sekolah_id, siswa_id, jenis, nominal, aktif) "
                + "VALUES (?, ?, ?, 'LIMIT_HARIAN', ?, true)", 9_002L, SEKOLAH, SISWA2, 20_000L);
        jdbc.update("INSERT INTO pembatasan (id, sekolah_id, siswa_id, jenis, nominal, aktif) "
                + "VALUES (?, ?, ?, 'LIMIT_HARIAN', ?, false)", 9_003L, SEKOLAH, siswa3, 15_000L);

        org.mockito.Mockito.when(cariSiswaPort.cari(SEKOLAH, "batch", false, 20)).thenReturn(List.of(
                InfoSiswa.builder().siswaId(SISWA).nis("2024001").nama("Budi Santoso")
                        .kelas("7A").namaOrangTua("Pak Budi").teleponOrangTua("0811").build(),
                InfoSiswa.builder().siswaId(SISWA2).nis("2024002").nama("Siswa Tanpa Mutasi")
                        .kelas("7B").build(),
                InfoSiswa.builder().siswaId(siswa3).nis("2024003").nama("Citra")
                        .kelas("7C").build()));

        var hasil = laporan.saldoMengendapSiswa(SEKOLAH, "batch", 20);

        assertThat(hasil).hasSize(3);
        // Urutan mengikuti hasil pencarian identitas, bukan urutan tabel.
        SiswaSaldoDetail s1 = hasil.get(0);
        assertThat(s1.siswaId()).isEqualTo(SISWA);
        assertThat(s1.saldo()).isEqualTo(42_000);
        assertThat(s1.belanjaHariIni()).isEqualTo(8_000);
        assertThat(s1.isBlocked()).isTrue();
        assertThat(s1.limitHarian()).isNull();
        assertThat(s1.terakhirTransaksi()).isNotNull();

        SiswaSaldoDetail s2 = hasil.get(1);
        assertThat(s2.siswaId()).isEqualTo(SISWA2);
        assertThat(s2.saldo()).isEqualTo(25_000);
        assertThat(s2.belanjaHariIni()).isEqualTo(5_000);
        assertThat(s2.isBlocked()).isFalse();
        assertThat(s2.limitHarian()).isEqualTo(20_000L);

        // Tanpa baris saldo & tanpa mutasi hari ini → 0, dan limit nonaktif diabaikan.
        SiswaSaldoDetail s3 = hasil.get(2);
        assertThat(s3.siswaId()).isEqualTo(siswa3);
        assertThat(s3.saldo()).isZero();
        assertThat(s3.belanjaHariIni()).isZero();
        assertThat(s3.isBlocked()).isFalse();
        assertThat(s3.limitHarian()).isNull();
        assertThat(s3.terakhirTransaksi()).isNull();
    }

    @Test
    @DisplayName("saldo mengendap — total siswa (kartu tamu bukan subjek saldo)")
    void saldoMengendap() {
        topup(SISWA, SubjekTipe.SISWA, 50_000, JenisMutasiSaldo.TOPUP_TUNAI);
        topup(SISWA2, SubjekTipe.SISWA, 30_000, JenisMutasiSaldo.TOPUP_ONLINE);

        RingkasanSaldoMengendap r = laporan.saldoMengendap(SEKOLAH);

        assertThat(r.saldoSiswa()).isEqualTo(80_000);
        assertThat(r.total()).isEqualTo(80_000);
        assertThat(r.jumlahSiswa()).isEqualTo(2);
    }

    @Test
    @DisplayName("rekonsiliasi SEIMBANG — Σ KREDIT − Σ DEBIT == saldo mengendap")
    void rekonsiliasiSeimbang() {
        // Top-up + penjualan (debit saldo) → cache & ledger tetap konsisten.
        topup(SISWA, SubjekTipe.SISWA, 100_000, JenisMutasiSaldo.TOPUP_TUNAI);
        topup(SISWA2, SubjekTipe.SISWA, 50_000, JenisMutasiSaldo.TOPUP_ONLINE);
        tx.executeWithoutResult(s -> ledgerSaldo.debit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.SISWA).siswaId(SISWA)
                .jenis(JenisMutasiSaldo.PENJUALAN).nominal(21_000)
                .idempotencyKey("jual-1").build()));

        RingkasanRekonsiliasi r = laporan.rekonsiliasi(SEKOLAH, null, null, null);

        assertThat(r.topupTunai()).isEqualTo(100_000);
        assertThat(r.topupOnline()).isEqualTo(50_000);
        assertThat(r.penjualan()).isEqualTo(21_000);
        assertThat(r.saldoMengendap()).isEqualTo(129_000);
        assertThat(r.selisih()).isZero();
        assertThat(r.seimbang()).isTrue();
    }

    @Test
    @DisplayName("SEC-06 (#396): filter periode hanya membatasi arus, invariant tetap all-time")
    void invariantTidakMengikutiPeriode() {
        topup(SISWA, SubjekTipe.SISWA, 100_000, JenisMutasiSaldo.TOPUP_TUNAI);
        tx.executeWithoutResult(s -> ledgerSaldo.debit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.SISWA).siswaId(SISWA)
                .jenis(JenisMutasiSaldo.PENJUALAN).nominal(21_000)
                .idempotencyKey("jual-396").build()));

        RingkasanRekonsiliasi hariIni = laporan.rekonsiliasi(SEKOLAH, null, null, null);
        // Periode tanpa mutasi: arus kosong, tetapi invarian all-time tetap dihitung.
        RingkasanRekonsiliasi periodeKosong =
                laporan.rekonsiliasi(SEKOLAH, LocalDate.of(2099, 1, 1), null, null);

        assertThat(periodeKosong.topupTunai()).isZero();
        assertThat(periodeKosong.penjualan()).isZero();
        assertThat(periodeKosong.totalKreditSemua())
                .isEqualTo(hariIni.totalKreditSemua()).isEqualTo(100_000L);
        assertThat(periodeKosong.totalDebitSemua())
                .isEqualTo(hariIni.totalDebitSemua()).isEqualTo(21_000L);
        assertThat(periodeKosong.saldoMengendap())
                .isEqualTo(hariIni.saldoMengendap()).isEqualTo(79_000L);
        assertThat(periodeKosong.selisih()).isZero();
        assertThat(periodeKosong.seimbang()).isTrue();
    }
    @Test
    @DisplayName("tenant scoping — sekolah lain tidak melihat saldo sekolah ini")
    void saldoTenantScoping() {
        topup(SISWA, SubjekTipe.SISWA, 50_000, JenisMutasiSaldo.TOPUP_TUNAI);

        assertThat(laporan.saldoMengendap(SEKOLAH_LAIN).total()).isZero();
    }
}
