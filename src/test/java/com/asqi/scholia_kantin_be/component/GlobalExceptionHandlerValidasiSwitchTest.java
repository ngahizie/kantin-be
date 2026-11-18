package com.asqi.scholia_kantin_be.component;

import com.asqi.scholia_kantin_be.config.ValidasiContractConfig;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #361 — status respons validasi kini ditentukan per instance handler
 * (lewat {@link ValidasiContractConfig}), bukan lewat static global. Dua
 * instance berbeda bisa hidup berdampingan tanpa saling menimpa.
 */
class GlobalExceptionHandlerValidasiSwitchTest {

    @Test
    @DisplayName("mode default (http-400=true) → HTTP 400")
    void modeDefault400() {
        var handler = new GlobalExceptionHandler(new ValidasiContractConfig(true));

        var response = handler.handleConstraintViolation(new ConstraintViolationException(Set.of()));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("saklar mundur (http-400=false) → HTTP 200 tanpa mengubah instance lain")
    void saklarMundur200DanTidakBocor() {
        var lama = new GlobalExceptionHandler(new ValidasiContractConfig(false));
        var baru = new GlobalExceptionHandler(new ValidasiContractConfig(true));

        assertThat(lama.handleConstraintViolation(new ConstraintViolationException(Set.of()))
                .getStatusCode().value()).isEqualTo(200);
        // instance lain tetap 400 — bukti tidak ada state global bersama.
        assertThat(baru.handleConstraintViolation(new ConstraintViolationException(Set.of()))
                .getStatusCode().value()).isEqualTo(400);
    }
}
