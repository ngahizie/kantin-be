package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.dto.RingkasanRekonsiliasi;
import com.asqi.scholia_kantin_be.dto.RingkasanSaldoMengendap;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.service.kasir.PerintahMutasiSaldo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;

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
    @DisplayName("tenant scoping — sekolah lain tidak melihat saldo sekolah ini")
    void saldoTenantScoping() {
        topup(SISWA, SubjekTipe.SISWA, 50_000, JenisMutasiSaldo.TOPUP_TUNAI);

        assertThat(laporan.saldoMengendap(SEKOLAH_LAIN).total()).isZero();
    }
}
