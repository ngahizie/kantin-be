package com.asqi.scholia_kantin_be.config;

import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import com.asqi.scholia_kantin_be.service.kasir.PerintahMutasiSaldo;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.FixtureInduk;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Batas waktu query &amp; tunggu kunci PostgreSQL (issue #435).
 *
 * <p>Membuktikan empat hal yang diminta Definition of Done:
 * <ol>
 *   <li>timeout benar-benar terpasang pada koneksi kolam ({@code current_setting}),</li>
 *   <li>query lambat dipotong, bukan menggantung,</li>
 *   <li>tunggu {@code FOR UPDATE} dibatalkan setelah batasnya,</li>
 *   <li>transaksi saldo yang kena timeout <b>tidak</b> meninggalkan perubahan
 *       setengah jadi (rollback penuh).</li>
 * </ol>
 *
 * <p>Nilai timeout sengaja diperkecil lewat {@code @TestPropertySource} (bukan
 * angka produksi) supaya test cepat, sekaligus membuktikan nilainya bisa
 * di-override dari luar — jalur yang sama dipakai environment variable
 * {@code KANTIN_DB_STATEMENT_TIMEOUT_MS} / {@code KANTIN_DB_LOCK_TIMEOUT_MS}.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
@TestPropertySource(properties = {
        "spring.datasource.hikari.connection-init-sql="
                + "SELECT set_config('statement_timeout', '800', false), "
                + "set_config('lock_timeout', '300', false)"
})
@DisplayName("Batas waktu query & tunggu kunci DB (#435)")
class BatasWaktuDatabaseIT {

    private static final long SEKOLAH = 1L;
    private static final long SISWA = 100L;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LedgerSaldoService ledgerSaldo;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private MeterRegistry meterRegistry;

    private TransactionTemplate tx;

    @BeforeEach
    void siapkan() {
        tx = new TransactionTemplate(txManager);
        jdbc.execute("TRUNCATE TABLE mutasi_saldo, saldo, pengaturan_kantin CASCADE");
        FixtureInduk.sekolah(jdbc, SEKOLAH);
        FixtureInduk.siswa(jdbc, SEKOLAH, SISWA);
    }

    @Test
    @DisplayName("Timeout terpasang pada koneksi kolam (statement_timeout & lock_timeout)")
    void timeoutTerpasangDiKoneksi() {
        // DoD butir 1: mekanisme dibuktikan lewat current_setting, bukan diasumsikan.
        assertThat(jdbc.queryForObject("SELECT current_setting('statement_timeout')", String.class))
                .isEqualTo("800ms");
        assertThat(jdbc.queryForObject("SELECT current_setting('lock_timeout')", String.class))
                .isEqualTo("300ms");
    }

    @Test
    @DisplayName("Query lambat dipotong batas waktu, tidak menggantung")
    void queryLambatDibatalkan() {
        long mulai = System.nanoTime();

        assertThatThrownBy(() -> jdbc.queryForObject("SELECT pg_sleep(5)", Object.class))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("statement timeout");

        long milidetik = (System.nanoTime() - mulai) / 1_000_000;
        // 800 ms + kelonggaran; yang penting jauh di bawah pg_sleep(5).
        assertThat(milidetik).isLessThan(3_000L);
    }

    @Test
    @DisplayName("Tunggu FOR UPDATE dibatalkan setelah lock_timeout")
    void tungguKunciDibatalkan() throws Exception {
        jdbc.update("INSERT INTO pengaturan_kantin (sekolah_id) VALUES (?)", SEKOLAH);

        CountDownLatch terkunci = new CountDownLatch(1);
        CountDownLatch lepas = new CountDownLatch(1);
        Thread pengunci = new Thread(() -> tx.executeWithoutResult(status -> {
            jdbc.queryForList(
                    "SELECT sekolah_id FROM pengaturan_kantin WHERE sekolah_id = ? FOR UPDATE", SEKOLAH);
            terkunci.countDown();
            try {
                lepas.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }), "pengunci-baris");
        pengunci.start();
        assertThat(terkunci.await(10, TimeUnit.SECONDS)).isTrue();

        try {
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> jdbc.queryForList(
                    "SELECT sekolah_id FROM pengaturan_kantin WHERE sekolah_id = ? FOR UPDATE", SEKOLAH)))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("lock timeout");
        } finally {
            lepas.countDown();
            pengunci.join(10_000);
        }
    }

    @Test
    @DisplayName("Timeout di tengah transaksi saldo → seluruh perubahan dibatalkan")
    void timeoutMembatalkanTransaksiSaldo() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            ledgerSaldo.kredit(PerintahMutasiSaldo.builder()
                    .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.SISWA).siswaId(SISWA)
                    .jenis(JenisMutasiSaldo.TOPUP_TUNAI).nominal(50_000L)
                    .idempotencyKey("timeout-435-" + System.nanoTime()).build());
            // Query yang sengaja melewati statement_timeout (800 ms).
            jdbc.queryForObject("SELECT pg_sleep(5)", Object.class);
        })).isInstanceOf(DataAccessException.class);

        // DoD butir 4: tidak ada saldo/mutasi setengah berubah.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mutasi_saldo", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM saldo", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Metrik kolam hikaricp_connections_pending tersedia untuk alert")
    void metrikKolamTersedia() {
        // Memicu pemakaian kolam agar metrik terikat pada datasource.
        jdbc.queryForObject("SELECT 1", Integer.class);

        assertThat(meterRegistry.find("hikaricp.connections.pending").meter())
                .as("metrik hikaricp.connections.pending harus diekspos ke /actuator/prometheus")
                .isNotNull();
    }
}
