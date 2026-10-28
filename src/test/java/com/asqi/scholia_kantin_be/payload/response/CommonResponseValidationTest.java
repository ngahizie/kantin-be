package com.asqi.scholia_kantin_be.payload.response;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CommonResponseValidationTest {

    @Test
    void validationErrorHasReadableSummaryAndKeepsFieldDetails() {
        var response = CommonResponse.validationError(Map.of("nama", "Nama wajib diisi"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Data tidak valid");
        assertThat(response.getBody().getValidation())
                .isEqualTo(Map.of("nama", "Nama wajib diisi"));
    }
}
