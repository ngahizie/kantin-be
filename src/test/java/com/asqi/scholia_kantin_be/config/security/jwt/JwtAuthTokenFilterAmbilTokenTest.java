package com.asqi.scholia_kantin_be.config.security.jwt;

import com.asqi.scholia_kantin_be.config.security.KantinJwtDecoder;
import com.asqi.scholia_kantin_be.security.KlaimResolver;
import com.asqi.scholia_kantin_be.security.blacklist.TokenBlacklistPort;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Uji gerbang sumber token pada {@link JwtAuthTokenFilter} (SEC-02).
 *
 * <p>Cookie hanya boleh mengautentikasi {@code GET /api/storage/**} agar
 * request mutasi tidak bisa memakai cookie (CSRF). Header Bearer tetap
 * berlaku untuk semua rute.
 */
@DisplayName("JwtAuthTokenFilter — gerbang cookie vs Bearer (SEC-02)")
class JwtAuthTokenFilterAmbilTokenTest {

    private JwtAuthTokenFilter filter;

    @BeforeEach
    void setUp() {
        JwtConfigValues config = new JwtConfigValues();
        config.setCookieName("skoolia-cookies");
        filter = new JwtAuthTokenFilter(
                mock(KantinJwtDecoder.class),
                mock(KlaimResolver.class),
                config,
                mock(TokenBlacklistPort.class));
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(uri);
        return request;
    }

    @Test
    @DisplayName("Bearer header diterima untuk request mutasi")
    void bearerDiterimaUntukMutasi() {
        MockHttpServletRequest request = request("POST", "/api/katalog/menu");
        request.addHeader("Authorization", "Bearer token-header");

        assertThat(filter.ambilToken(request)).isEqualTo("token-header");
    }

    @Test
    @DisplayName("skema Bearer tidak peka huruf besar/kecil")
    void bearerCaseInsensitive() {
        MockHttpServletRequest request = request("POST", "/api/katalog/menu");
        request.addHeader("Authorization", "bearer token-header");

        assertThat(filter.ambilToken(request)).isEqualTo("token-header");
    }

    @Test
    @DisplayName("cookie diterima untuk GET /api/storage/file (tag <img>)")
    void cookieDiterimaUntukGetStorage() {
        MockHttpServletRequest request = request("GET", "/api/storage/file");
        request.setCookies(new Cookie(JwtAuthTokenFilter.COOKIE_FE, "\"token-cookie\""));

        assertThat(filter.ambilToken(request)).isEqualTo("token-cookie");
    }

    @Test
    @DisplayName("cookie ditolak untuk POST /api/storage/upload")
    void cookieDitolakUntukMutasiStorage() {
        MockHttpServletRequest request = request("POST", "/api/storage/upload");
        request.setCookies(new Cookie(JwtAuthTokenFilter.COOKIE_FE, "\"token-cookie\""));

        assertThat(filter.ambilToken(request)).isNull();
    }

    @Test
    @DisplayName("cookie ditolak untuk GET di luar /api/storage")
    void cookieDitolakUntukGetNonStorage() {
        MockHttpServletRequest request = request("GET", "/api/katalog/menu");
        request.setCookies(new Cookie(JwtAuthTokenFilter.COOKIE_FE, "\"token-cookie\""));

        assertThat(filter.ambilToken(request)).isNull();
    }

    @Test
    @DisplayName("prefix mirip /api/storagefoo tidak ikut lolos")
    void prefixStoragePalsuDitolak() {
        MockHttpServletRequest request = request("GET", "/api/storagefoo/file");
        request.setCookies(new Cookie(JwtAuthTokenFilter.COOKIE_FE, "\"token-cookie\""));

        assertThat(filter.ambilToken(request)).isNull();
    }

    @Test
    @DisplayName("header non-Bearer tidak jatuh ke cookie")
    void headerNonBearerTidakFallbackKeCookie() {
        MockHttpServletRequest request = request("GET", "/api/storage/file");
        request.addHeader("Authorization", "Basic abc");
        request.setCookies(new Cookie(JwtAuthTokenFilter.COOKIE_FE, "\"token-cookie\""));

        assertThat(filter.ambilToken(request)).isNull();
    }

    @Test
    @DisplayName("Bearer kosong tidak jatuh ke cookie")
    void bearerKosongTidakFallbackKeCookie() {
        MockHttpServletRequest request = request("GET", "/api/storage/file");
        request.addHeader("Authorization", "Bearer   ");
        request.setCookies(new Cookie(JwtAuthTokenFilter.COOKIE_FE, "\"token-cookie\""));

        assertThat(filter.ambilToken(request)).isNull();
    }

    @Test
    @DisplayName("tanpa header dan tanpa cookie → null")
    void tanpaSumberToken() {
        assertThat(filter.ambilToken(request("GET", "/api/storage/file"))).isNull();
    }
}
