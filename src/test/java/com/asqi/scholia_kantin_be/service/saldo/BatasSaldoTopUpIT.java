package com.asqi.scholia_kantin_be.service.saldo;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.dto.PengaturanKantinRequest;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import com.asqi.scholia_kantin_be.service.kasir.PerintahMutasiSaldo;
import com.asqi.scholia_kantin_be.service.konfigurasi.PengaturanKantinService;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.FixtureInduk;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji integrasi penegakan batas saldo &amp; min/maks top-up (PRD §8.2, §9.1,
 * §9.2, §9.4) — issue #112.
 *
 * <p>Membuktikan setelan yang dikonfigurasi sekolah benar-benar berdampak:
 * top-up di luar rentang min/maks ditolak, dan top-up yang membuat saldo
 * melebihi plafon (per siswa) ditolak — termasuk saat dua
 * top-up nyaris bersamaan (ditegakkan di seksi terkunci ledger).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class BatasSaldoTopUpIT {

    private static final long SEKOLAH = 1L;
    private static final long SEKOLAH_LAIN = 2L;
    private static final long SISWA = 7L;
    /** Siswa di sekolah lain — id siswa bersifat global di v2 (PK {@code siswa.id}). */
    private static final long SISWA_LAIN = 8L;
    private static final long AKTOR = 99L;

    @Autowired
    private SaldoTopUpService saldoTopUp;

    @Autowired
    private LedgerSaldoService ledgerSaldo;

    @Autowired
    private PengaturanKantinService pengaturan;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE mutasi_saldo, saldo, pengaturan_kantin, catatan_audit CASCADE");

        // Induk wajib skema v2 (FK): sekolah → siswa/pengguna.
        FixtureInduk.sekolah(jdbc, SEKOLAH, SEKOLAH_LAIN);
        FixtureInduk.pengguna(jdbc, SEKOLAH, AKTOR);
        FixtureInduk.pengguna(jdbc, SEKOLAH_LAIN, AKTOR);
        FixtureInduk.siswa(jdbc, SEKOLAH, SISWA);
        FixtureInduk.siswa(jdbc, SEKOLAH_LAIN, SISWA_LAIN);
    }

    private void aturBatas(Long minTopup, Long maksTopup, Long batasSiswa) {
        PengaturanKantinRequest req = new PengaturanKantinRequest();
        req.setMinTopup(minTopup);
        req.setMaksTopup(maksTopup);
        req.setBatasSaldoSiswa(batasSiswa);
        pengaturan.ubah(SEKOLAH, req, 99L);
    }

    @Test
    @DisplayName("#112: top-up di bawah minimum ditolak")
    void tolakDiBawahMinimum() {
        aturBatas(10_000L, 1_000_000L, null);

        assertThatThrownBy(() -> saldoTopUp.topUpTunai(SEKOLAH, SubjekTipe.SISWA, SISWA,
                5_000L, "Ibu", "TU-1", 99L))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("minimum");
    }

    @Test
    @DisplayName("#112: top-up di atas maksimum ditolak")
    void tolakDiAtasMaksimum() {
        aturBatas(10_000L, 100_000L, null);

        assertThatThrownBy(() -> saldoTopUp.topUpTunai(SEKOLAH, SubjekTipe.SISWA, SISWA,
                150_000L, "Ibu", "TU-2", 99L))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("maksimum");
    }

    @Test
    @DisplayName("#112: top-up melebihi batas saldo siswa ditolak; saldo tidak bertambah")
    void tolakMelebihiBatasSaldoSiswa() {
        aturBatas(1_000L, 1_000_000L, 100_000L);
        saldoTopUp.topUpTunai(SEKOLAH, SubjekTipe.SISWA, SISWA, 90_000L, "Ibu", "TU-3", 99L);

        assertThatThrownBy(() -> saldoTopUp.topUpTunai(SEKOLAH, SubjekTipe.SISWA, SISWA,
                20_000L, "Ibu", "TU-4", 99L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("batas maksimum");

        // Top-up kedua di-rollback: saldo tetap 90.000 (bukan 110.000).
        assertThat(ledgerSaldo.saldo(SEKOLAH, SISWA)).isEqualTo(90_000L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM mutasi_saldo WHERE sekolah_id = ? AND siswa_id = ?",
                Integer.class, SEKOLAH, SISWA)).isEqualTo(1);
    }


    @Test
    @DisplayName("#112: top-up tepat menyentuh batas tetap diterima")
    void tepatDiBatasDiterima() {
        aturBatas(1_000L, 1_000_000L, 100_000L);

        saldoTopUp.topUpTunai(SEKOLAH, SubjekTipe.SISWA, SISWA, 100_000L, "Ibu", "TU-7", 99L);

        assertThat(ledgerSaldo.saldo(SEKOLAH, SISWA)).isEqualTo(100_000L);
    }

    @Test
    @DisplayName("#112: tanpa pengaturan → tanpa batas (perilaku lama dipertahankan)")
    void tanpaPengaturanTanpaBatas() {
        // Tidak ada baris pengaturan_kantin → semua null.
        saldoTopUp.topUpTunai(SEKOLAH_LAIN, SubjekTipe.SISWA, SISWA_LAIN, 5_000_000L, "Ibu", "TU-8", 99L);

        assertThat(ledgerSaldo.saldo(SEKOLAH_LAIN, SISWA_LAIN)).isEqualTo(5_000_000L);
    }

    @Test
    @DisplayName("#112: batas ditegakkan juga pada top-up online (webhook)")
    void batasBerlakuTopUpOnline() {
        aturBatas(null, null, 100_000L);

        assertThatThrownBy(() -> saldoTopUp.topUpOnline(SEKOLAH, SubjekTipe.SISWA, SISWA,
                120_000L, "PG-REF-1", "Ortu", "QRIS", null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("batas maksimum");
    }

    @Test
    @DisplayName("#112: batas saldo ditegakkan di seksi terkunci — kredit langsung pun patuh")
    void batasDitegakkanDiLedgerLangsung() {
        // Seed 20.000 (di bawah batas 25.000) → sukses.
        tx.executeWithoutResult(s -> ledgerSaldo.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.SISWA).siswaId(SISWA)
                .jenis(JenisMutasiSaldo.KOREKSI).nominal(20_000)
                .batasSaldoMaksimum(25_000L)
                .idempotencyKey("seed-" + System.nanoTime()).build()));

        // Kredit 10.000 lagi → 30.000 > 25.000 → ditolak di seksi terkunci.
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> ledgerSaldo.kredit(
                PerintahMutasiSaldo.builder()
                        .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.SISWA).siswaId(SISWA)
                        .jenis(JenisMutasiSaldo.KOREKSI).nominal(10_000)
                        .batasSaldoMaksimum(25_000L)
                        .idempotencyKey("k-" + System.nanoTime()).build())))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("batas maksimum");

        // Saldo tetap 20.000 (kredit kedua di-rollback).
        assertThat(ledgerSaldo.saldo(SEKOLAH, SISWA)).isEqualTo(20_000L);
    }
}
