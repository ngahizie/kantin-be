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
    private DevSekolahLookup sekolahLookup;
    private DevLoginController controller;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        tokenService = mock(DevTokenService.class);
        validator = mock(DevLoginValidator.class);
        sekolahLookup = mock(DevSekolahLookup.class);
        JwtConfigValues jwtConfig = new JwtConfigValues(); // default "skoolia-cookies"
        controller = new DevLoginController(tokenService, validator, sekolahLookup, jwtConfig);
        response = new MockHttpServletResponse();

        // Default: sekolah 1 seperti hasil seed dev (nama & logo dari tabel sekolah).
        when(sekolahLookup.cari(anyLong()))
                .thenReturn(new DevSekolahLookup.InfoSekolah(
                        1L, "SMP Negeri 1 Skoolia", "https://contoh.test/logo-sekolah-1.png"));
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

    @Test
    @DisplayName("Respons login memuat nama & logo sekolah dari tabel sekolah, bukan hardcode")
    void loginMengirimIdentitasSekolahDariTabel() {
        when(validator.autentikasi("kasir10", "Kantin#2026"))
                .thenReturn(Optional.of(new DevLoginValidator.AkunDev(
                        14L, 10L, "kasir10", "Rizky Pratama (Kasir)", "PETUGAS")));
        when(tokenService.terbitkan(anyString(), anyLong(), anyString(), anyString(), anyLong()))
                .thenReturn("token-kasir10");
        when(sekolahLookup.cari(10L))
                .thenReturn(new DevSekolahLookup.InfoSekolah(
                        10L,
                        "SMP Negeri 10 Skoolia",
                        "https://contoh.test/logo-sekolah-10.png"));

        var hasil = controller.login(
                new DevLoginRequest(null, "kasir10", "Kantin#2026"), response);

        assertThat(hasil.getBody()).isNotNull();
        DevLoginResponse.Sekolah sekolah = hasil.getBody().getData().user().sekolah();
        assertThat(sekolah.id()).isEqualTo(10L);
        assertThat(sekolah.nama())
                .as("nama sekolah harus berasal dari tabel sekolah, bukan switch hardcode")
                .isEqualTo("SMP Negeri 10 Skoolia");
        assertThat(sekolah.logoUrl())
                .as("logo sekolah (sekolah.logo_url) harus ikut dikirim ke FE")
                .isEqualTo("https://contoh.test/logo-sekolah-10.png");
    }

    @Test
    @DisplayName("Sekolah tanpa logo → logoUrl null agar FE memakai fallback")
    void loginTanpaLogoTetapBerhasil() {
        when(validator.autentikasi("admin1", "Kantin#2026"))
                .thenReturn(Optional.of(new DevLoginValidator.AkunDev(
                        1L, 1L, "admin1", "Wibisana Bama (Kepsek)", "KEPSEK")));
        when(tokenService.terbitkan(anyString(), anyLong(), anyString(), anyString(), anyLong()))
                .thenReturn("token-admin1");
        when(sekolahLookup.cari(1L))
                .thenReturn(new DevSekolahLookup.InfoSekolah(1L, "SMP Negeri 1 Skoolia", null));

        var hasil = controller.login(
                new DevLoginRequest(null, "admin1", "Kantin#2026"), response);

        assertThat(hasil.getBody().getData().user().sekolah().logoUrl()).isNull();
        assertThat(hasil.getBody().getData().user().sekolah().nama()).isEqualTo("SMP Negeri 1 Skoolia");
    }
}
