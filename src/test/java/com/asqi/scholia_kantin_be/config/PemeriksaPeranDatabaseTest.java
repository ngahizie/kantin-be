package com.asqi.scholia_kantin_be.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Uji penjaga peran database least-privilege (issue #433 / OWASP A01 &amp; A07:2025).
 *
 * <p>Memastikan start <b>tidak pernah lolos</b> pada profil non-dev bila role
 * runtime adalah superuser, password kosong, role migrasi tidak terpisah, atau
 * atribut role di PostgreSQL menunjukkan hak DDL. Dev lokal (profil dev + host
 * loopback) tetap boleh memakai kredensial longgar. Tanpa Spring context (cepat).
 */
class PemeriksaPeranDatabaseTest {

    private static final String HOST_LOKAL = "jdbc:postgresql://localhost:5432/kantin_db";
    private static final String HOST_REMOTE = "jdbc:postgresql://db.internal:5432/kantin_db";

    private MockEnvironment env(String url, String user, String password,
            String userMigrasi, String passwordMigrasi, String... profil) {
        MockEnvironment e = new MockEnvironment();
        e.setProperty("spring.datasource.url", url);
        if (user != null) {
            e.setProperty("spring.datasource.username", user);
        }
        if (password != null) {
            e.setProperty("spring.datasource.password", password);
        }
        if (userMigrasi != null) {
            e.setProperty("spring.flyway.user", userMigrasi);
        }
        if (passwordMigrasi != null) {
            e.setProperty("spring.flyway.password", passwordMigrasi);
        }
        if (profil.length > 0) {
            e.setActiveProfiles(profil);
        }
        return e;
    }

    private PeranDatabaseProperties props() {
        return new PeranDatabaseProperties();
    }

    private PemeriksaPeranDatabase pemeriksa(MockEnvironment env) {
        return new PemeriksaPeranDatabase(props(), env, null);
    }

    /** Konfigurasi sehat: role runtime DML-only + role migrasi terpisah. */
    private PemeriksaPeranDatabase sehat(String... profil) {
        return pemeriksa(env(HOST_REMOTE, "kantin_app", "rahasia-app",
                "kantin_owner", "rahasia-owner", profil));
    }

    // ------------------------------------------------------------- properti

    @Test
    @DisplayName("Default properti: enforce aktif, profil dev dikecualikan, pisah migrasi & cek atribut aktif")
    void defaultProperti() {
        PeranDatabaseProperties p = new PeranDatabaseProperties();
        assertThat(p.isEnforce()).isTrue();
        assertThat(p.isWajibPisahMigrasi()).isTrue();
        assertThat(p.isCekAtribut()).isTrue();
        assertThat(p.getDikecualikanProfil()).containsExactly("local", "test", "demo", "loadtest");
        assertThat(p.getUserTerlarang()).contains("postgres");
    }

    @Test
    @DisplayName("User terlarang dikenali case-insensitive & hanya bila sama persis")
    void userTerlarang() {
        PemeriksaPeranDatabase p = pemeriksa(env(HOST_REMOTE, "kantin_app", "x", "owner", "y", "prod"));
        assertThat(p.userTerlarang("postgres")).isTrue();
        assertThat(p.userTerlarang("POSTGRES")).isTrue();
        assertThat(p.userTerlarang(" postgres ")).isTrue();
        assertThat(p.userTerlarang("rdsadmin")).isTrue();
        assertThat(p.userTerlarang("kantin_app")).isFalse();
        assertThat(p.userTerlarang("postgres_app")).isFalse();
        assertThat(p.userTerlarang(null)).isFalse();
    }

    // ------------------------------------------------------- profil produksi

    @Test
    @DisplayName("Profil prod + DB_USERNAME=postgres → start DIGAGALKAN (superuser ditolak)")
    void prodSuperuserDitolak() {
        assertThatThrownBy(() -> pemeriksa(env(HOST_REMOTE, "postgres", "rahasia",
                "kantin_owner", "rahasia-owner", "prod")).periksa())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("postgres")
                .hasMessageContaining("A01")
                .hasMessageContaining("issue #433");
    }

    @Test
    @DisplayName("Profil prod + DB_USERNAME kosong → start DIGAGALKAN")
    void prodUserKosong() {
        assertThatThrownBy(() -> pemeriksa(env(HOST_REMOTE, "", "rahasia",
                "kantin_owner", "rahasia-owner", "prod")).periksa())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_USERNAME")
                .hasMessageContaining("issue #433");
    }

    @Test
    @DisplayName("Profil prod + DB_PASSWORD kosong → start DIGAGALKAN")
    void prodPasswordKosong() {
        assertThatThrownBy(() -> pemeriksa(env(HOST_REMOTE, "kantin_app", "",
                "kantin_owner", "rahasia-owner", "prod")).periksa())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD")
                .hasMessageContaining("issue #433");
    }

    @Test
    @DisplayName("Profil prod + role migrasi SAMA dengan role runtime → start DIGAGALKAN")
    void prodMigrasiSamaDenganRuntime() {
        assertThatThrownBy(() -> pemeriksa(env(HOST_REMOTE, "kantin_app", "rahasia",
                "kantin_app", "rahasia", "prod")).periksa())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sama dengan role runtime")
                .hasMessageContaining("issue #433");
    }

    @Test
    @DisplayName("Profil prod + role migrasi kosong → start DIGAGALKAN")
    void prodMigrasiKosong() {
        assertThatThrownBy(() -> pemeriksa(env(HOST_REMOTE, "kantin_app", "rahasia",
                "", "rahasia-owner", "prod")).periksa())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.flyway.user")
                .hasMessageContaining("issue #433");
    }

    @Test
    @DisplayName("Profil prod + password role migrasi kosong → start DIGAGALKAN")
    void prodPasswordMigrasiKosong() {
        assertThatThrownBy(() -> pemeriksa(env(HOST_REMOTE, "kantin_app", "rahasia",
                "kantin_owner", "", "prod")).periksa())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.flyway.password")
                .hasMessageContaining("issue #433");
    }

    @Test
    @DisplayName("Profil staging + role runtime DML-only & role migrasi terpisah → lolos")
    void stagingSehat() {
        assertThatCode(() -> sehat("staging").periksa()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Profil prod + pisah-migrasi dimatikan & user non-superuser → lolos (tanpa DataSource)")
    void pisahMigrasiDimatikan() {
        PeranDatabaseProperties p = props();
        p.setWajibPisahMigrasi(false);
        PemeriksaPeranDatabase pemeriksa = new PemeriksaPeranDatabase(p,
                env(HOST_REMOTE, "kantin_app", "rahasia", null, null, "prod"), null);
        assertThatCode(pemeriksa::periksa).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------ profil dev

    @Test
    @DisplayName("Profil local + host loopback + postgres → boleh (docker compose lokal)")
    void lokalLoopback() {
        assertThatCode(() -> pemeriksa(env(HOST_LOKAL, "postgres", "rahasia",
                "postgres", "rahasia", "local")).periksa())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Profil local TAPI host DB non-loopback → tetap divalidasi (produksi yang lupa ganti profil)")
    void lokalTapiHostRemote() {
        assertThatThrownBy(() -> pemeriksa(env(HOST_REMOTE, "postgres", "rahasia",
                "postgres", "rahasia", "local")).periksa())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issue #433");
    }

    @Test
    @DisplayName("Tanpa profil aktif (unit/integration test) + host loopback → tidak menggagalkan konteks")
    void tanpaProfil() {
        assertThatCode(() -> pemeriksa(env(HOST_LOKAL, "postgres", "rahasia",
                "postgres", "rahasia")).periksa())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("enforce=false → pemeriksaan dilewati walau profil prod & superuser")
    void enforceMati() {
        PeranDatabaseProperties p = props();
        p.setEnforce(false);
        PemeriksaPeranDatabase pemeriksa = new PemeriksaPeranDatabase(p,
                env(HOST_REMOTE, "postgres", "", "postgres", "", "prod"), null);
        assertThatCode(pemeriksa::periksa).doesNotThrowAnyException();
    }

    // -------------------------------------------------- atribut role (DB nyata)

    @Test
    @DisplayName("Role runtime terlihat aman tapi ternyata superuser di pg_roles → start DIGAGALKAN")
    void atributSuperuserDitolak() throws Exception {
        PemeriksaPeranDatabase pemeriksa = pemeriksaDenganAtribut(true, false, false, false, false);

        assertThatThrownBy(pemeriksa::periksa)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TIDAK least-privilege")
                .hasMessageContaining("superuser=true")
                .hasMessageContaining("issue #433");
    }

    @Test
    @DisplayName("Role runtime punya CREATE pada skema public → start DIGAGALKAN (jalur DDL tertutup)")
    void atributBisaDdlDitolak() throws Exception {
        PemeriksaPeranDatabase pemeriksa = pemeriksaDenganAtribut(false, false, false, false, true);

        assertThatThrownBy(pemeriksa::periksa)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("create-on-public=true")
                .hasMessageContaining("issue #433");
    }

    @Test
    @DisplayName("Role runtime DML-only (semua atribut aman) → lolos")
    void atributAmanLolos() throws Exception {
        PemeriksaPeranDatabase pemeriksa = pemeriksaDenganAtribut(false, false, false, false, false);

        assertThatCode(pemeriksa::periksa).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("cek-atribut=false → atribut role tidak dibaca (dipakai saat DB belum siap)")
    void cekAtributDimatikan() throws Exception {
        PeranDatabaseProperties p = props();
        p.setCekAtribut(false);
        PemeriksaPeranDatabase pemeriksa = new PemeriksaPeranDatabase(p,
                env(HOST_REMOTE, "kantin_app", "rahasia", "kantin_owner", "rahasia-owner", "prod"),
                provider(mock(DataSource.class)));

        assertThatCode(pemeriksa::periksa).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------- helper

    /**
     * Susun penjaga dengan DataSource palsu yang mengembalikan satu baris
     * {@code pg_roles} sesuai argumen.
     */
    @SuppressWarnings("unchecked")
    private PemeriksaPeranDatabase pemeriksaDenganAtribut(boolean superuser, boolean createrole,
            boolean createdb, boolean bypassrls, boolean bisaCreatePublic) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getBoolean("rolsuper")).thenReturn(superuser);
        when(rs.getBoolean("rolcreaterole")).thenReturn(createrole);
        when(rs.getBoolean("rolcreatedb")).thenReturn(createdb);
        when(rs.getBoolean("rolbypassrls")).thenReturn(bypassrls);
        when(rs.getBoolean("bisa_create_public")).thenReturn(bisaCreatePublic);

        Statement st = mock(Statement.class);
        when(st.executeQuery(anyString())).thenReturn(rs);

        Connection koneksi = mock(Connection.class);
        when(koneksi.createStatement()).thenReturn(st);

        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(koneksi);

        return new PemeriksaPeranDatabase(props(),
                env(HOST_REMOTE, "kantin_app", "rahasia", "kantin_owner", "rahasia-owner", "prod"),
                provider(dataSource));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<DataSource> provider(DataSource dataSource) {
        ObjectProvider<DataSource> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(dataSource);
        return provider;
    }
}
