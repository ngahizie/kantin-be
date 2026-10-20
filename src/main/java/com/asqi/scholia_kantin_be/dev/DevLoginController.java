package com.asqi.scholia_kantin_be.dev;

import com.asqi.scholia_kantin_be.component.exception.UnauthorizedException;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint login <b>shim dev</b> — meniru {@code POST /api/v1/auth/login}
 * admin-be agar FE dapat token RS256 nyata yang diterima kantin-be.
 *
 * <p>Hanya hidup bila profil {@code local} <b>dan</b>
 * {@code kantin.dev-login.enabled=true}.
 *
 * <p><b>Kredensial diverifikasi</b> oleh {@link DevLoginValidator} terhadap tabel
 * {@code dev_pengguna} hasil seed dev; username/password asal-asalan ditolak 401.
 * Role, {@code userId}, dan {@code sekolahId} diambil dari akun — bukan dari body
 * request — sehingga klien tidak bisa memilih identitas sendiri.
 */
@RestController
@Profile("local")
@ConditionalOnProperty(prefix = "kantin.dev-login", name = "enabled", havingValue = "true")
@RequestMapping("api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class DevLoginController {

    private final DevTokenService tokenService;
    private final DevLoginValidator validator;

    @PostMapping("login")
    public ResponseEntity<Response<DevLoginResponse>> login(@RequestBody DevLoginRequest request) {
        String username = request.username() != null && !request.username().isBlank()
                ? request.username()
                : request.email();

        DevLoginValidator.AkunDev akun = validator.autentikasi(username, request.password())
                .orElseThrow(() -> new UnauthorizedException("Username atau password salah"));

        String token = tokenService.terbitkan(
                akun.username(), akun.id(), akun.nama(), akun.role(), akun.sekolahId());

        log.info("[DEV-LOGIN] token diterbitkan untuk {} (role={}, sekolah={})",
                akun.username(), akun.role(), akun.sekolahId());

        DevLoginResponse body = new DevLoginResponse(
                token,
                new DevLoginResponse.DevUser(
                        akun.id(),
                        akun.nama(),
                        akun.username(),
                        roleFe(akun.role()),
                        List.of("ROLE_" + akun.role()),
                        new DevLoginResponse.Sekolah(akun.sekolahId(), namaSekolah(akun.sekolahId()))));

        return CommonResponse.data(body, "Login berhasil (dev shim)");
    }

    /** Role gaya FE ({@code admin|pengelola|tu|bendahara|kasir}) untuk {@code currentRole}. */
    private String roleFe(String role) {
        return switch (role) {
            case "PETUGAS" -> "kasir";
            case "PENGELOLA" -> "pengelola";
            case "TATA_USAHA" -> "tu";
            case "BENDAHARA" -> "bendahara";
            // FE belum punya role kepsek (UserRole hanya 5 nilai) → tampil sebagai admin.
            default -> "admin";
        };
    }

    private String namaSekolah(long sekolahId) {
        return switch ((int) sekolahId) {
            case 1 -> "SMA Negeri 1 SKOOLIA";
            case 2 -> "SMA Negeri 2 SKOOLIA";
            default -> "Sekolah " + sekolahId;
        };
    }
}
