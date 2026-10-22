package com.asqi.scholia_kantin_be.web;

import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kontrak status HTTP error — issue <b>#251</b>.
 *
 * <p>Dulu kesalahan validasi dijawab <b>HTTP 200</b> dengan body error
 * ({@code status:0} / {@code code=100}). Akibatnya:
 * <ul>
 *   <li>klien tak bisa membedakan sukses dari gagal lewat status code;</li>
 *   <li>request cacat "lolos" sebagai 200 sehingga jalur RBAC-nya tak pernah
 *       diuji — permintaan tanpa hak pun dijawab 200.</li>
 * </ul>
 *
 * <p>Sekarang: validasi → <b>400</b>, peran tak berhak → <b>403</b>, data
 * milik tenant lain → <b>404</b> (PRD §11.4). Bentuk body tetap
 * {@code {code, message, timestamp}} supaya klien FE yang sudah membaca
 * {@code body.code} tidak rusak.
 *
 * <p>Diuji lewat <b>HTTP sungguhan</b> ke PostgreSQL nyata (Testcontainers) —
 * bukan {@code MockMvc} ber-mock, karena yang perlu diuji justru pemetaan
 * exception → status HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class ValidasiHttpStatusIT {

    /** Tenant pemanggil (token). */
    private static final long SEKOLAH_ID = 1L;

    /** Tenant lain — datanya tak boleh terlihat oleh pemanggil (PRD §11.4). */
    private static final long SEKOLAH_LAIN = 2L;

    private static final long USER_ID = 42L;

    private static final KeyPair KEYPAIR = buatKeyPair();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void jwtDummy(DynamicPropertyRegistry registry) {
        registry.add("jwt.admin-public-key",
                () -> Base64.getEncoder().encodeToString(KEYPAIR.getPublic().getEncoded()));
        registry.add("jwt.issuer.admin", () -> "skoolia-admin");
    }

    // ---------------------------------------------------------------- helper

    private static KeyPair buatKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Gagal membuat keypair RSA uji", e);
        }
    }

    /** Token RS256 sungguhan — jalur verifikasi produksi benar-benar dilewati. */
    private static String token(String klaimPeran) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject("validasi01")
                .claim("typ", "access")
                .claim("user_id", USER_ID)
                .claim("nama", "Pemilik Uji " + klaimPeran)
                .claim("role", klaimPeran)
                .claim("sekolah_id", SEKOLAH_ID)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(KEYPAIR.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + path))
                .GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return HttpClient.newHttpClient()
                .send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return HttpClient.newHttpClient()
                .send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ------------------------------------------------------------- skenario

    /** Endpoint dengan {@code @RequestParam} wajib + peran yang boleh memanggilnya. */
    static Stream<Arguments> endpointParamWajib() {
        return Stream.of(
                Arguments.of("/api/laporan/per-siswa", "TATA_USAHA"),
                Arguments.of("/api/kasir/transaksi/sesi", "PETUGAS"),
                Arguments.of("/api/kasir/sesi/aktif", "PETUGAS"),
                Arguments.of("/api/storage/file", "PETUGAS"));
    }

    @ParameterizedTest(name = "400 → {0}")
    @MethodSource("endpointParamWajib")
    @DisplayName("#251: @RequestParam wajib yang hilang dibalas HTTP 400 (bukan 200)")
    void paramWajibHilangBalas400(String path, String peran) throws Exception {
        HttpResponse<String> res = get(path, token(peran));

        assertThat(res.statusCode())
                .as("GET %s — badan: %s", path, potong(res.body()))
                .isEqualTo(400);
    }

    @Test
    @DisplayName("#251: request cacat dari peran tanpa hak tidak lagi 'lolos' sebagai 200")
    void requestCacatTidakLolos200() throws Exception {
        HttpResponse<String> res = get("/api/laporan/per-siswa", token("KEPSEK"));

        assertThat(res.statusCode())
                .as("badan: %s", potong(res.body()))
                .isNotEqualTo(200)
                .isEqualTo(400);
    }

    @Test
    @DisplayName("#251: tipe parameter salah dibalas HTTP 400")
    void tipeParameterSalahBalas400() throws Exception {
        HttpResponse<String> res = get("/api/laporan/per-siswa?subjekId=abc", token("TATA_USAHA"));

        assertThat(res.statusCode())
                .as("badan: %s", potong(res.body()))
                .isEqualTo(400);
    }

    @Test
    @DisplayName("#251: body tidak valid dibalas HTTP 400 dengan badan {code, message, timestamp}")
    void bodyTidakValidBalas400() throws Exception {
        HttpResponse<String> res = post("/api/saldo/topup", token("TATA_USAHA"), "{}");

        assertThat(res.statusCode())
                .as("badan: %s", potong(res.body()))
                .isEqualTo(400);
        assertThat(res.body())
                .as("badan: %s", potong(res.body()))
                .contains("\"code\"", "\"message\"", "\"timestamp\"");
    }

    @Test
    @DisplayName("#251: peran tidak berhak dibalas HTTP 403")
    void peranTidakBerhakBalas403() throws Exception {
        HttpResponse<String> res = get("/api/laporan/per-siswa?subjekId=1", token("KEPSEK"));

        assertThat(res.statusCode())
                .as("badan: %s", potong(res.body()))
                .isEqualTo(403);
    }

    @Test
    @DisplayName("#251: data milik tenant lain dibalas HTTP 404 (bukan 403)")
    void dataTenantLainBalas404() throws Exception {
        long idMenu = 900_001L;
        jdbc.update("INSERT INTO menu (id, sekolah_id, nama, harga_jual, satuan, is_active) "
                        + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING",
                idMenu, SEKOLAH_LAIN, "Menu Tenant Lain", 1_000L, "PCS", true);

        HttpResponse<String> res = get("/api/katalog/menu/" + idMenu, token("PETUGAS"));

        assertThat(res.statusCode())
                .as("badan: %s", potong(res.body()))
                .isEqualTo(404);
    }

    private static String potong(String body) {
        if (body == null) {
            return "(kosong)";
        }
        return body.length() <= 300 ? body : body.substring(0, 300) + "…";
    }
}
