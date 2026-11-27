package com.asqi.scholia_kantin_be.component;

import com.asqi.scholia_kantin_be.component.exception.ServerBusyException;
import com.asqi.scholia_kantin_be.component.metrics.MetrikDatabaseSibuk;
import com.asqi.scholia_kantin_be.config.ValidasiContractConfig;
import com.asqi.scholia_kantin_be.payload.response.Response;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.CannotSerializeTransactionException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pemetaan kondisi database sibuk (issue #435, diperluas #452).
 *
 * <p>Setelah batas waktu dipasang, query timeout, lock timeout, deadlock,
 * serialization failure, dan kolam koneksi penuh benar-benar bisa muncul saat
 * kontensi. Semuanya harus dijawab 503 + {@code Retry-After} dengan pesan yang
 * bisa ditindaklanjuti, bukan 500 generik — dan tercatat di metrik dengan label
 * yang membedakan "lambat" dari "gagal".
 */
class GlobalExceptionHandlerDatabaseBusyTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final GlobalExceptionHandler handler = handlerDenganMetrik();

    private GlobalExceptionHandler handlerDenganMetrik() {
        GlobalExceptionHandler h = new GlobalExceptionHandler(new ValidasiContractConfig(true));
        h.setMetrikDatabaseSibuk(new MetrikDatabaseSibuk(registry));
        return h;
    }

    @Test
    @DisplayName("query timeout dijawab 503, meminta ulangi, dan berlabel query_timeout")
    void queryTimeoutDijawab503() {
        var response = handler.handleDatabaseBusy(new QueryTimeoutException("statement timeout"));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(pesan(response)).contains("terlalu lama").contains("ulangi");
        assertThat(hitung("query_timeout")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("lock timeout dijawab 503 dan berlabel lock")
    void lockTimeoutDijawab503() {
        var response = handler.handleDatabaseBusy(new CannotAcquireLockException("lock timeout"));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(pesan(response)).contains("transaksi lain").contains("ulangi");
        assertThat(hitung("lock")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("#452: deadlock (40P01) dijawab 503, bukan 500")
    void deadlockDijawab503() {
        var response = handler.handleDatabaseBusy(
                new DeadlockLoserDataAccessException("deadlock detected", null));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(pesan(response)).contains("transaksi lain").contains("ulangi");
        assertThat(hitung("deadlock")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("#452: serialization failure (40001) dijawab 503, bukan 500")
    void serialisasiDijawab503() {
        var response = handler.handleDatabaseBusy(
                new CannotSerializeTransactionException("could not serialize access"));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(pesan(response)).contains("bentrok").contains("ulangi");
        assertThat(hitung("serialisasi")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("koneksi tidak tersedia dijawab 503 dengan pesan yang tidak menyesatkan")
    void kolamPenuhDijawab503() {
        var response = handler.handleDatabaseBusy(
                new CannotGetJdbcConnectionException("pool exhausted"));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        // Bukan lagi "koneksi database penuh": exception yang sama juga muncul
        // saat DB mati / auth / TLS gagal (issue #452).
        assertThat(pesan(response)).contains("tidak dapat terhubung").contains("ulangi");
        assertThat(hitung("koneksi")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("#452: semua respons 503 membawa header Retry-After")
    void respons503MembawaRetryAfter() {
        var response = handler.handleDatabaseBusy(new QueryTimeoutException("x"));

        assertThat(response.getHeaders().getFirst("Retry-After"))
                .isEqualTo(String.valueOf(GlobalExceptionHandler.RETRY_AFTER_DETIK));
    }

    @Test
    @DisplayName("#452: ServerBusyException (kapasitas ekspor penuh) dijawab 503 + Retry-After")
    void serverBusyDijawab503() {
        ResponseEntity<?> response = handler.handleServerBusy(
                new ServerBusyException("Terlalu banyak ekspor laporan berjalan bersamaan."));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(pesan(response)).contains("bersamaan");
        assertThat(response.getHeaders().getFirst("Retry-After")).isNotNull();
    }

    private double hitung(String jenis) {
        var counter = registry.find("db.busy").tag("jenis", jenis).counter();
        return counter == null ? 0.0 : counter.count();
    }

    /** Body respons adalah {@link Response}; toString()-nya tidak memuat pesan. */
    private static String pesan(ResponseEntity<?> response) {
        Response<?> body = (Response<?>) response.getBody();
        return body == null ? "" : String.valueOf(body.getMessage());
    }
}
