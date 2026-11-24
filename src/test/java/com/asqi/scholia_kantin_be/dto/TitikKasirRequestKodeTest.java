package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji validasi format {@code kode} titik kasir (SEC-TITIK-05, issue #421).
 *
 * <p>Anotasi hanya menjaga bentuk; keunikan per sekolah tetap diperiksa
 * {@code TitikKasirService} (409), dan {@code null}/kosong berarti "tanpa kode"
 * (dipakai {@code PUT} untuk menghapus kode).
 */
@DisplayName("TitikKasirRequest — validasi kode (SEC-TITIK-05)")
class TitikKasirRequestKodeTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private TitikKasirRequest request(String kode) {
        TitikKasirRequest request = new TitikKasirRequest();
        request.setNama("Kasir Depan");
        request.setKode(kode);
        return request;
    }

    private Set<ConstraintViolation<TitikKasirRequest>> violations(String kode) {
        return validator.validate(request(kode));
    }

    @Test
    @DisplayName("kode valid: huruf, angka, minus, garis bawah; null/kosong = tanpa kode")
    void kodeValidLolos() {
        String[] valid = {null, "", "POS-01", "kasir_2", "A1B2C3D4", "a".repeat(30)};

        for (String kode : valid) {
            assertThat(violations(kode))
                    .as("kode %s", kode)
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("karakter injeksi, spasi, dan karakter kontrol ditolak")
    void kodeBerbahayaDitolak() {
        String[] invalid = {
                "<script>alert(1)</script>",
                "POS 01",
                "POS\n01",
                "POS;DROP TABLE titik_kasir",
                "kasir/..",
                "kode'atau\"",
                "POS\u0000",
                "📍"
        };

        for (String kode : invalid) {
            assertThat(violations(kode))
                    .as("kode %s", kode)
                    .anyMatch(v -> v.getPropertyPath().toString().equals("kode"));
        }
    }

    @Test
    @DisplayName("kode lebih dari 30 karakter ditolak")
    void kodeKepanjanganDitolak() {
        assertThat(violations("A".repeat(31)))
                .anyMatch(v -> v.getPropertyPath().toString().equals("kode"));
    }
}
