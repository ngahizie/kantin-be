package com.asqi.scholia_kantin_be.payload.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CommonResponseValidationTest {

    @Test
    void validationErrorHasReadableSummaryAndKeepsFieldDetails() {
        var response = CommonResponse.validationError(Map.of("nama", "Nama wajib diisi"), true);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Data tidak valid");
        assertThat(response.getBody().getValidation())
                .isEqualTo(Map.of("nama", "Nama wajib diisi"));
    }

    @Test
    @DisplayName("saklar mundur: validasiHttp400=false menjawab HTTP 200 (kontrak lama)")
    void saklarMundurMenjawab200() {
        var response = CommonResponse.validationError(Map.of("nama", "Nama wajib diisi"), false);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getValidation())
                .isEqualTo(Map.of("nama", "Nama wajib diisi"));
    }
}
