package com.asqi.scholia_kantin_be.component;

import com.asqi.scholia_kantin_be.config.ValidasiContractConfig;
import com.asqi.scholia_kantin_be.payload.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pemetaan kondisi database sibuk (issue #435).
 *
 * <p>Setelah batas waktu dipasang, query timeout, lock timeout, dan kolam
 * koneksi penuh benar-benar bisa muncul. Ketiganya harus dijawab 503 dengan
 * pesan yang bisa ditindaklanjuti, bukan 500 generik.
 */
class GlobalExceptionHandlerDatabaseBusyTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(new ValidasiContractConfig(true));

    @Test
    @DisplayName("query timeout dijawab 503 dan meminta ulangi")
    void queryTimeoutDijawab503() {
        var response = handler.handleDatabaseBusy(
                new QueryTimeoutException("statement timeout"));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(pesan(response)).contains("terlalu lama").contains("ulangi");
    }

    @Test
    @DisplayName("lock timeout dijawab 503 dan meminta ulangi")
    void lockTimeoutDijawab503() {
        var response = handler.handleDatabaseBusy(
                new CannotAcquireLockException("lock timeout"));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(pesan(response)).contains("transaksi lain").contains("ulangi");
    }

    @Test
    @DisplayName("kolam koneksi penuh dijawab 503 dan meminta ulangi")
    void kolamPenuhDijawab503() {
        var response = handler.handleDatabaseBusy(
                new CannotGetJdbcConnectionException("pool exhausted"));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(pesan(response)).contains("koneksi database penuh").contains("ulangi");
    }

    /** Body respons adalah {@link Response}; toString()-nya tidak memuat pesan. */
    private static String pesan(org.springframework.http.ResponseEntity<?> response) {
        Response<?> body = (Response<?>) response.getBody();
        return body == null ? "" : String.valueOf(body.getMessage());
    }
}
