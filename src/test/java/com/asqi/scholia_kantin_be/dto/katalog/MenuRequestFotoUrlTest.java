package com.asqi.scholia_kantin_be.dto.katalog;

import com.asqi.scholia_kantin_be.enums.SatuanMenu;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji validasi format {@code fotoUrl} pada {@link MenuRequest} (M-03).
 *
 * <p>Anotasi hanya menjaga bentuk/skema; kepemilikan tenant untuk path
 * relatif diuji terpisah di {@code KatalogServiceFotoUrlTest}.
 */
@DisplayName("MenuRequest — validasi fotoUrl (M-03)")
class MenuRequestFotoUrlTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private MenuRequest request(String fotoUrl) {
        MenuRequest request = new MenuRequest();
        request.setNama("Bakso Sapi");
        request.setHargaJual(12_000L);
        request.setSatuan(SatuanMenu.PORSI);
        request.setFotoUrl(fotoUrl);
        return request;
    }

    private Set<ConstraintViolation<MenuRequest>> violations(String fotoUrl) {
        return validator.validate(request(fotoUrl));
    }

    @Test
    @DisplayName("format yang diizinkan lolos validasi")
    void formatDiizinkanLolos() {
        String[] valid = {
                "",
                "https://cdn.sekolah.id/kantin/sekolah-1/menu/bakso.jpg",
                "http://localhost:9000/sekolah-1/menu/bakso.jpg",
                "/api/storage/file?path=sekolah-1%2Fmenu%2Fbakso.jpg",
                "sekolah-1/menu/uuid-bakso.jpg"
        };

        for (String url : valid) {
            assertThat(violations(url))
                    .as("fotoUrl %s", url)
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("skema berbahaya dan path sembarang ditolak")
    void formatBerbahayaDitolak() {
        String[] invalid = {
                "javascript:alert(1)",
                "data:text/html,<script>alert(1)</script>",
                "vbscript:msgbox(1)",
                "file:///etc/passwd",
                "foto.jpg",
                "sekolah-1/../../rahasia.png",
                "//evil.example/pixel.png"
        };

        for (String url : invalid) {
            assertThat(violations(url))
                    .as("fotoUrl %s", url)
                    .anyMatch(v -> v.getPropertyPath().toString().equals("fotoUrl"));
        }
    }

    @Test
    @DisplayName("null tetap boleh (foto opsional)")
    void nullBoleh() {
        assertThat(violations(null)).isEmpty();
    }
}
