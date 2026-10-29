package com.asqi.scholia_kantin_be.dev;

import com.asqi.scholia_kantin_be.component.exception.UnauthorizedException;
import com.asqi.scholia_kantin_be.config.security.jwt.JwtConfigValues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Uji shim login dev: token dikembalikan di body <b>dan</b> dipasang sebagai
 * cookie {@code skoolia-cookies} (parity admin-be), agar sumber daya yang
 * dimuat browser tanpa header Authorization (mis. {@code <img>} dari
 * {@code /api/storage/file}) tetap terautentikasi saat demo lokal.
 */
@DisplayName("Shim login dev — token + cookie")
class DevLoginControllerTest {

    private DevTokenService tokenService;
    private DevLoginValidator validator;
    private DevLoginController controller;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        tokenService = mock(DevTokenService.class);
        validator = mock(DevLoginValidator.class);
        JwtConfigValues jwtConfig = new JwtConfigValues(); // default "skoolia-cookies"
        controller = new DevLoginController(tokenService, validator, jwtConfig);
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("Login berhasil → body berisi token + Set-Cookie HttpOnly SameSite=Lax")
    void loginMemasangCookie() {
        when(validator.autentikasi("pengelola1", "Kantin#2026"))
                .thenReturn(Optional.of(new DevLoginValidator.AkunDev(
                        3L, 1L, "pengelola1", "Deryl Fabiensyah (Pengelola)", "PENGELOLA")));
        when(tokenService.terbitkan(anyString(), anyLong(), anyString(), anyString(), anyLong()))
                .thenReturn("token-uji");

        var hasil = controller.login(
                new DevLoginRequest(null, "pengelola1", "Kantin#2026"), response);

        assertThat(hasil.getBody()).isNotNull();
        assertThat(hasil.getBody().getData().token()).isEqualTo("token-uji");

        String cookie = response.getHeader("Set-Cookie");
        assertThat(cookie)
                .as("cookie harus memakai nama parity admin-be + atribut aman")
                .startsWith("skoolia-cookies=token-uji")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Path=/");
    }

    @Test
    @DisplayName("Password salah → 401 dan TIDAK ada cookie yang dipasang")
    void loginGagalTidakMemasangCookie() {
        when(validator.autentikasi(anyString(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.login(
                new DevLoginRequest(null, "salah", "salah"), response))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(response.getHeader("Set-Cookie")).isNull();
        verify(tokenService, never())
                .terbitkan(anyString(), anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Username kosong → memakai field email")
    void fallbackKeEmail() {
        when(validator.autentikasi("tu1", "Kantin#2026"))
                .thenReturn(Optional.of(new DevLoginValidator.AkunDev(
                        2L, 1L, "tu1", "Sri Wahyuni (TU)", "TATA_USAHA")));
        when(tokenService.terbitkan(anyString(), anyLong(), anyString(), anyString(), anyLong()))
                .thenReturn("token-tu");

        controller.login(new DevLoginRequest("tu1", null, "Kantin#2026"), response);

        verify(validator).autentikasi("tu1", "Kantin#2026");
    }
}
