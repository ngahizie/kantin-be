package com.asqi.scholia_kantin_be.component;

import com.asqi.scholia_kantin_be.component.exception.InvariantViolationException;
import com.asqi.scholia_kantin_be.config.ValidasiContractConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pemetaan {@link InvariantViolationException} (issue #360).
 *
 * <p>Penggantian {@code IllegalArgumentException}/{@code RuntimeException}
 * generik di service <b>tidak boleh</b> mengubah kontrak HTTP: pelanggaran
 * invarian tetap dijawab 500 dengan pesan generik, dan pesan teknis hanya
 * masuk log.
 */
class GlobalExceptionHandlerInvariantTest {

    /** Konstruktor ber-argumen: #364 sudah merge lebih dulu (lihat catatan urutan merge). */
    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(new ValidasiContractConfig(true));

    @Test
    @DisplayName("pelanggaran invarian dijawab 500, bukan 4xx")
    void pelanggaranInvarianDijawab500() {
        var response = handler.invariantViolationHandler(
                new InvariantViolationException("qtyMasuk harus > 0"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("pesan internal tidak bocor ke klien")
    void pesanInternalTidakBocor() {
        var response = handler.invariantViolationHandler(
                new InvariantViolationException("sekolahId wajib diisi"));

        assertThat(response.getBody().toString()).doesNotContain("sekolahId");
    }
}
