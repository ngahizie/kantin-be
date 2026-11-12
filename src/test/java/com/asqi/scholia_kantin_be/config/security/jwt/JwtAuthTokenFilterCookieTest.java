package com.asqi.scholia_kantin_be.config.security.jwt;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji pengambilan token dari cookie pada {@link JwtAuthTokenFilter}.
 *
 * <p>Latar belakang: kantin-fe menyimpan token di cookie {@code kantin_access_token}
 * dengan nilai JSON string ({@code "eyJ..."}), sementara kantin-be hanya membaca
 * cookie bernama {@code jwt.cookieName} ({@code skoolia-cookies}). Akibatnya
 * {@code <img src="/api/storage/file?path=...">} selalu 401 — tag gambar tidak
 * bisa mengirim header {@code Authorization} — sehingga foto menu tampak kosong
 * padahal unggahannya berhasil.
 */
class JwtAuthTokenFilterCookieTest {

    private static final String NAMA_UTAMA = "skoolia-cookies";

    @Test
    @DisplayName("tanpa cookie → null")
    void tanpaCookie() {
        assertThat(JwtAuthTokenFilter.tokenDariCookie(null, NAMA_UTAMA)).isNull();
        assertThat(JwtAuthTokenFilter.tokenDariCookie(new Cookie[0], NAMA_UTAMA)).isNull();
    }

    @Test
    @DisplayName("cookie resmi (skoolia-cookies) tetap dipakai lebih dulu")
    void cookieResmiDiutamakan() {
        Cookie[] cookies = {
                new Cookie(JwtAuthTokenFilter.COOKIE_FE, "\"token-fe\""),
                new Cookie(NAMA_UTAMA, "token-resmi")
        };

        assertThat(JwtAuthTokenFilter.tokenDariCookie(cookies, NAMA_UTAMA)).isEqualTo("token-resmi");
    }

    @Test
    @DisplayName("cookie kantin_access_token dibaca dan kutip JSON-nya dibuang")
    void cookieFeDibaca() {
        Cookie[] cookies = {new Cookie(JwtAuthTokenFilter.COOKIE_FE, "\"eyJhbGciOiJSUzI1NiJ9.token\"")};

        assertThat(JwtAuthTokenFilter.tokenDariCookie(cookies, NAMA_UTAMA))
                .isEqualTo("eyJhbGciOiJSUzI1NiJ9.token");
    }

    @Test
    @DisplayName("cookie FE tanpa kutip juga diterima")
    void cookieFeTanpaKutip() {
        Cookie[] cookies = {new Cookie(JwtAuthTokenFilter.COOKIE_FE, "token-mentah")};

        assertThat(JwtAuthTokenFilter.tokenDariCookie(cookies, NAMA_UTAMA)).isEqualTo("token-mentah");
    }

    @Test
    @DisplayName("cookie resmi kosong → jatuh ke cookie FE")
    void cookieResmiKosongJatuhKeCookieFe() {
        Cookie[] cookies = {
                new Cookie(NAMA_UTAMA, "   "),
                new Cookie(JwtAuthTokenFilter.COOKIE_FE, "\"token-fe\"")
        };

        assertThat(JwtAuthTokenFilter.tokenDariCookie(cookies, NAMA_UTAMA)).isEqualTo("token-fe");
    }

    @Test
    @DisplayName("nilai kosong/null dinormalisasi menjadi null")
    void normalisasiNilai() {
        assertThat(JwtAuthTokenFilter.bersihkanNilaiCookie(null)).isNull();
        assertThat(JwtAuthTokenFilter.bersihkanNilaiCookie("")).isNull();
        assertThat(JwtAuthTokenFilter.bersihkanNilaiCookie("  \"\"  ")).isNull();
        assertThat(JwtAuthTokenFilter.bersihkanNilaiCookie("  abc  ")).isEqualTo("abc");
        assertThat(JwtAuthTokenFilter.bersihkanNilaiCookie("\"abc\"")).isEqualTo("abc");
    }
}
