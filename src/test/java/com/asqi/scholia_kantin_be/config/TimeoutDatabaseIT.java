package com.asqi.scholia_kantin_be.config;

import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bukti bahwa batas waktu DB benar-benar bekerja, bukan cuma tertulis di
 * konfigurasi (issue #435).
 *
 * <p>Uji memakai konfigurasi default supaya tidak membuat konteks Spring baru.
 * Supaya cepat, test yang butuh timeout pendek memasang {@code SET LOCAL}
 * di dalam transaksi — mekanisme yang sama dipakai laporan berat untuk
 * memperpanjang batas.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class TimeoutDatabaseIT {

    private static final long SEKOLAH_UJI = 987_654_321L;
    private static final int KUNCI_ADVISORY = 435;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private DataSource dataSource;

    /**
     * Nilai yang diharapkan dibaca dari konfigurasi/env yang sama dengan
     * connection-init-sql, bukan ditulis ulang sebagai literal di test. Dengan
     * begitu, mengubah default lewat environment tidak membuat test merah.
     */
    @Value("${KANTIN_DB_STATEMENT_TIMEOUT:10s}")
    private String statementTimeout;

    @Value("${KANTIN_DB_LOCK_TIMEOUT:3s}")
    private String lockTimeout;

    @Value("${KANTIN_DB_CONNECTION_TIMEOUT_MS:5000}")
    private long connectionTimeoutMs;

    /** Cek setiap koneksi baru dari kolam sudah membawa batas waktu. */
    @Test
    @DisplayName("batas waktu terpasang di koneksi kolam")
    void batasWaktuTerpasangDiKoneksi() {
        assertThat(jdbc.queryForObject("SHOW statement_timeout", String.class))
                .isEqualTo(statementTimeout);
        assertThat(jdbc.queryForObject("SHOW lock_timeout", String.class))
                .isEqualTo(lockTimeout);
    }

    /** Batas tunggu koneksi dari kolam harus eksplisit, bukan default Hikari. */
    @Test
    @DisplayName("batas tunggu koneksi kolam terpasang")
    void connectionTimeoutTerpasang() {
        assertThat(((HikariDataSource) dataSource).getConnectionTimeout())
                .isEqualTo(connectionTimeoutMs);
    }

    /** Query lambat harus dipotong PostgreSQL, bukan memegang koneksi selamanya. */
    @Test
    @DisplayName("query melebihi batas dihentikan")
    void queryLambatDihentikan() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            jdbc.execute("SET LOCAL statement_timeout = '200ms'");
            jdbc.execute("SELECT pg_sleep(1)");
        })).getRootCause().hasMessageContaining("statement timeout");
    }

    /**
     * Transaksi yang kena lock timeout harus rollback: baris yang ditulis
     * sebelum menunggu kunci tidak boleh tertinggal.
     */
    @Test
    @DisplayName("lock timeout menghentikan transaksi dan membatalkan tulisan")
    void lockTimeoutMembatalkanTulisan() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch kunciDipegang = new CountDownLatch(1);
        CountDownLatch lepas = new CountDownLatch(1);

        Future<?> pemegang = pool.submit(() -> tx.executeWithoutResult(s -> {
            jdbc.execute("SELECT pg_advisory_xact_lock(" + KUNCI_ADVISORY + ")");
            kunciDipegang.countDown();
            try {
                if (!lepas.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("pemegang kunci tidak dilepas");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }));

        try {
            assertThat(kunciDipegang.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
                jdbc.execute("SET LOCAL lock_timeout = '200ms'");
                jdbc.update("INSERT INTO sekolah (id, nama) VALUES (?, ?)",
                        SEKOLAH_UJI, "Uji timeout");
                jdbc.execute("SELECT pg_advisory_xact_lock(" + KUNCI_ADVISORY + ")");
            })).getRootCause().hasMessageContaining("lock timeout");

            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sekolah WHERE id = ?",
                    Integer.class, SEKOLAH_UJI)).as("tulisan harus rollback").isZero();
        } finally {
            lepas.countDown();
            pemegang.get(10, TimeUnit.SECONDS);
            pool.shutdownNow();
            jdbc.update("DELETE FROM sekolah WHERE id = ?", SEKOLAH_UJI);
        }
    }

    /** Laporan berat butuh batas lebih panjang; SET LOCAL harus menimpa batas sesi. */
    @Test
    @DisplayName("SET LOCAL bisa memperpanjang batas untuk satu transaksi")
    void overridePerTransaksiBerfungsi() {
        tx.executeWithoutResult(s -> {
            jdbc.execute("SET LOCAL statement_timeout = '2s'");
            assertThat(jdbc.queryForObject("SHOW statement_timeout", String.class)).isEqualTo("2s");
        });
        assertThat(jdbc.queryForObject("SHOW statement_timeout", String.class))
                .isEqualTo(statementTimeout);
    }
}
