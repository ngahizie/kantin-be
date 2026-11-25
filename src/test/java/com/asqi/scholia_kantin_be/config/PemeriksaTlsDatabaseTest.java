package com.asqi.scholia_kantin_be.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji penjaga TLS database (issue #432 / OWASP A04:2025).
 *
 * <p>Memastikan koneksi PostgreSQL tanpa {@code sslmode=verify-full} <b>tidak
 * pernah lolos</b> pada profil non-dev, sementara dev lokal (profil dev + host
 * loopback) tetap bisa memakai plaintext. Tanpa Spring context (cepat).
 */
class PemeriksaTlsDatabaseTest {

    private static final String URL_TANPA_SSLMODE =
            "jdbc:postgresql://db.internal:5432/kantin_db?stringtype=unspecified";

    private MockEnvironment env(String url, String... profil) {
        MockEnvironment e = new MockEnvironment();
        e.setProperty("spring.datasource.url", url);
        if (profil.length > 0) {
            e.setActiveProfiles(profil);
        }
        return e;
    }

    private TlsDatabaseProperties props(boolean enforce) {
        TlsDatabaseProperties p = new TlsDatabaseProperties();
        p.setEnforce(enforce);
        return p;
    }

    private PemeriksaTlsDatabase pemeriksa(String url, boolean enforce, String... profil) {
        return new PemeriksaTlsDatabase(props(enforce), env(url, profil));
    }

    // ---------------------------------------------------------------- parsing

    @Test
    @DisplayName("Ambil parameter query JDBC: nilai, urutan, tanpa query, tanpa nilai, nama case-insensitive")
    void ambilParameter() {
        assertThat(PemeriksaTlsDatabase.ambilParameter(
                "jdbc:postgresql://h:5432/db?stringtype=unspecified&sslmode=verify-full", "sslmode"))
                .isEqualTo("verify-full");
        assertThat(PemeriksaTlsDatabase.ambilParameter(
                "jdbc:postgresql://h:5432/db?SSLMode=Verify-Full", "sslmode"))
                .isEqualTo("Verify-Full");
        assertThat(PemeriksaTlsDatabase.ambilParameter("jdbc:postgresql://h:5432/db", "sslmode"))
                .isNull();
        assertThat(PemeriksaTlsDatabase.ambilParameter(
                "jdbc:postgresql://h:5432/db?sslmode", "sslmode"))
                .isEmpty();
        assertThat(PemeriksaTlsDatabase.ambilParameter(null, "sslmode")).isNull();
    }

    @Test
    @DisplayName("Host lokal dikenali (localhost, 127.0.0.1, ::1, IPv6 loopback)")
    void hostLokal() {
        assertThat(PemeriksaTlsDatabase.hostLokal("localhost")).isTrue();
        assertThat(PemeriksaTlsDatabase.hostLokal("LOCALHOST")).isTrue();
        assertThat(PemeriksaTlsDatabase.hostLokal("127.0.0.1")).isTrue();
        assertThat(PemeriksaTlsDatabase.hostLokal("::1")).isTrue();
        assertThat(PemeriksaTlsDatabase.hostLokal("db.internal")).isFalse();
        assertThat(PemeriksaTlsDatabase.hostLokal(null)).isFalse();
    }

    // ------------------------------------------------------- profil produksi

    @Test
    @DisplayName("Profil prod + sslmode TIDAK diset (driver default 'prefer') → start DIGAGALKAN")
    void prodTanpaSslmode() {
        assertThatThrownBy(() -> pemeriksa(URL_TANPA_SSLMODE, true, "prod").periksa())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("verify-full")
                .hasMessageContaining("A04:2025")
                .hasMessageContaining("issue #432");
    }

    @Test
    @DisplayName("Profil prod + sslmode=prefer/disable/require/verify-ca → semua DIGAGALKAN")
    void prodModeLemah() {
        for (String mode : List.of("prefer", "disable", "require", "verify-ca", "allow")) {
            assertThatThrownBy(() -> pemeriksa(
                    "jdbc:postgresql://db.internal:5432/kantin_db?sslmode=" + mode, true, "prod").periksa())
                    .as("sslmode=%s harus ditolak", mode)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("Profil staging + sslmode=verify-full → lolos")
    void stagingVerifyFull() {
        assertThatCode(() -> pemeriksa(
                "jdbc:postgresql://db.internal:5432/kantin_db?stringtype=unspecified&sslmode=verify-full",
                true, "staging").periksa())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Profil prod + verify-full + sslrootcert → lolos (tanpa peringatan CA)")
    void prodVerifyFullDenganCa() {
        assertThatCode(() -> pemeriksa(
                "jdbc:postgresql://db.internal:5432/kantin_db?sslmode=verify-full&sslrootcert=/etc/kantin/pg-ca.pem",
                true, "prod").periksa())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Profil prod + verify-full + sslmode ditulis kapital → tetap lolos (case-insensitive)")
    void prodVerifyFullKapital() {
        assertThatCode(() -> pemeriksa(
                "jdbc:postgresql://db.internal:5432/kantin_db?sslmode=VERIFY-FULL", true, "prod").periksa())
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------ profil dev

    @Test
    @DisplayName("Profil local + host loopback + sslmode=disable → boleh (docker compose lokal)")
    void lokalLoopback() {
        assertThatCode(() -> pemeriksa(
                "jdbc:postgresql://localhost:5432/kantin_db?stringtype=unspecified&sslmode=disable",
                true, "local").periksa())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Profil local TAPI host DB non-loopback → tetap divalidasi (produksi yang lupa ganti profil)")
    void lokalTapiHostRemote() {
        assertThatThrownBy(() -> pemeriksa(
                "jdbc:postgresql://db.internal:5432/kantin_db?sslmode=disable", true, "local").periksa())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("verify-full");
    }

    @Test
    @DisplayName("Tanpa profil aktif (unit/integration test) + host loopback → tidak menggagalkan konteks")
    void tanpaProfil() {
        assertThatCode(() -> pemeriksa(
                "jdbc:postgresql://localhost:5432/test?sslmode=disable", true).periksa())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("enforce=false → pemeriksaan dilewati walau profil prod & plaintext")
    void enforceMati() {
        assertThatCode(() -> pemeriksa(URL_TANPA_SSLMODE, false, "prod").periksa())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Default properti: enforce aktif & profil dev dikecualikan")
    void defaultProperti() {
        TlsDatabaseProperties p = new TlsDatabaseProperties();
        assertThat(p.isEnforce()).isTrue();
        assertThat(p.getDikecualikanProfil())
                .containsExactly("local", "test", "demo", "loadtest");
    }
}
