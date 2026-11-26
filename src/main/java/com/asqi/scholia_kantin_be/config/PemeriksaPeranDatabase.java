package com.asqi.scholia_kantin_be.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Penjaga <b>peran database least-privilege</b> (issue <b>#433</b>, temuan
 * SEC-02 audit OWASP Top 10:2025 — <b>A01 Broken Access Control</b> &amp;
 * <b>A07 Authentication Failures</b>).
 *
 * <p>Masalah yang dicegah: {@code DB_USERNAME} dulu default {@code postgres}
 * (superuser) dan migrasi Flyway memakai kredensial yang sama dengan runtime.
 * Bila variabel environment lupa diisi di produksi, aplikasi konek sebagai
 * superuser — jalur injection/IDOR yang lolos akan memberi hak penuh atas
 * seluruh database, dan tidak ada lapisan isolasi kedua.
 *
 * <p>Aturan ditegakkan saat inisialisasi bean ({@link InitializingBean}) —
 * yaitu <b>sebelum</b> web server menerima trafik, dan berbeda dari penjaga
 * TLS/topologi yang memakai {@code ApplicationReadyEvent}: menolak superuser
 * tidak berguna bila koneksi sudah dilayani lebih dulu.
 * <ol>
 *   <li>{@code kantin.db.peran.enforce=false} ⇒ hanya INFO (dev eksperimen).</li>
 *   <li>Profil termasuk {@code dikecualikan-profil} <b>dan</b> host DB loopback
 *       ⇒ WARN, start tetap jalan (docker compose lokal, CI, demo, loadtest).
 *       Profil dev dengan host DB non-loopback tetap divalidasi — mencegah
 *       "produksi yang lupa ganti profil".</li>
 *   <li>Selain itu, start <b>DIGAGALKAN</b> bila:
 *       <ul>
 *         <li>{@code spring.datasource.username} kosong atau termasuk
 *             {@code user-terlarang} (mis. {@code postgres});</li>
 *         <li>{@code spring.datasource.password} kosong;</li>
 *         <li>{@code spring.flyway.user} kosong / sama dengan role runtime /
 *             password-nya kosong (bila {@code wajib-pisah-migrasi=true});</li>
 *         <li>role runtime ternyata superuser, punya {@code CREATEROLE},
 *             {@code CREATEDB}, {@code BYPASSRLS}, atau punya hak
 *             {@code CREATE} pada skema {@code public} (bila
 *             {@code cek-atribut=true}).</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p>Pemeriksaan atribut memakai koneksi nyata ({@code pg_roles} +
 * {@code has_schema_privilege}) karena nama user yang "terlihat aman" bisa
 * saja superuser. Bila atribut tidak dapat diverifikasi, start digagalkan
 * (fail-closed) — tanpa DB yang sehat aplikasi ini tidak bisa melayani
 * apa pun.
 *
 * <p><b>Catatan urutan:</b> penjaga berjalan saat inisialisasi bean — Flyway bisa
 * saja sudah menjalankan migrasi lebih dulu. Karena itu migrasi WAJIB memakai
 * role terpisah ({@code SPRING_FLYWAY_USER}) sehingga jalur runtime tidak pernah
 * menjadi jalur DDL. Lihat {@code docs/database-role.md}.
 */
@Component
@Slf4j
public class PemeriksaPeranDatabase implements InitializingBean {

    /** Kunci properti role runtime (datasource aplikasi). */
    static final String PROP_USER_RUNTIME = "spring.datasource.username";

    /** Kunci properti password role runtime. */
    static final String PROP_PASSWORD_RUNTIME = "spring.datasource.password";

    /** Kunci properti role migrasi Flyway. */
    static final String PROP_USER_MIGRASI = "spring.flyway.user";

    /** Kunci properti password role migrasi Flyway. */
    static final String PROP_PASSWORD_MIGRASI = "spring.flyway.password";

    private final PeranDatabaseProperties properties;
    private final Environment environment;

    /** {@code null} pada unit test yang tidak memakai DB. */
    @Nullable
    private final ObjectProvider<DataSource> dataSourceProvider;

    public PemeriksaPeranDatabase(PeranDatabaseProperties properties, Environment environment,
            @Nullable ObjectProvider<DataSource> dataSourceProvider) {
        this.properties = properties;
        this.environment = environment;
        this.dataSourceProvider = dataSourceProvider;
    }

    /** Dipanggil Spring saat bean dibuat: gagal di sini = konteks batal, sebelum Tomcat melayani. */
    @Override
    public void afterPropertiesSet() {
        periksa();
    }

    /** Pemeriksaan lengkap; dipanggil dari {@link #afterPropertiesSet()} dan unit test. */
    public void periksa() {
        String user = environment.getProperty(PROP_USER_RUNTIME);
        String password = environment.getProperty(PROP_PASSWORD_RUNTIME);
        String userMigrasi = environment.getProperty(PROP_USER_MIGRASI);
        String passwordMigrasi = environment.getProperty(PROP_PASSWORD_MIGRASI);
        String profil = Arrays.toString(environment.getActiveProfiles());
        String host = PemeriksaTopologiDatabase.host(environment.getProperty("spring.datasource.url"));

        if (!properties.isEnforce()) {
            log.info("Penjaga peran database NONAKTIF (kantin.db.peran.enforce=false): user='{}', "
                    + "user migrasi='{}', host='{}', profil={}. Aktifkan di staging/produksi (issue #433).",
                    user, userMigrasi, host, profil);
            return;
        }

        if (profilDev() && PemeriksaTlsDatabase.hostLokal(host)) {
            log.warn("Peran database TIDAK divalidasi: profil dev {} + host DB lokal '{}' (user='{}', "
                    + "user migrasi='{}'). Staging/produksi WAJIB memakai role DML-only + role migrasi "
                    + "terpisah (issue #433).", profil, host, user, userMigrasi);
            return;
        }

        // 1) Role runtime tidak boleh kosong / superuser bawaan.
        if (kosong(user)) {
            throw new IllegalStateException(
                    "DB_USERNAME (spring.datasource.username) kosong (OWASP A07:2025, issue #433): "
                            + "aplikasi tidak boleh memakai kredensial default. Isi dengan role runtime "
                            + "DML-only (mis. kantin_app), bukan superuser. Profil=" + profil);
        }
        if (userTerlarang(user)) {
            throw new IllegalStateException(
                    "DB_USERNAME='" + user + "' adalah user superuser/bawaan dan DITOLAK sebagai role "
                            + "runtime (OWASP A01/A07:2025, issue #433). Pakai role DML-only (mis. kantin_app) "
                            + "dan role migrasi terpisah (mis. kantin_owner). Profil=" + profil
                            + ", host='" + host + "'.");
        }

        // 2) Password runtime tidak boleh kosong.
        if (kosong(password)) {
            throw new IllegalStateException(
                    "DB_PASSWORD (spring.datasource.password) kosong untuk role runtime '" + user
                            + "' (OWASP A07:2025, issue #433). Isi lewat secret/environment. Profil="
                            + profil);
        }

        // 3) Pemisahan role migrasi (DDL) dari role runtime (DML).
        if (properties.isWajibPisahMigrasi()) {
            if (kosong(userMigrasi)) {
                throw new IllegalStateException(
                        "spring.flyway.user (SPRING_FLYWAY_USER) kosong (issue #433): migrasi DDL wajib "
                                + "memakai role pemilik skema yang TERPISAH dari role runtime '" + user
                                + "'. Profil=" + profil);
            }
            if (userMigrasi.equalsIgnoreCase(user)) {
                throw new IllegalStateException(
                        "Role migrasi Flyway ('" + userMigrasi + "') sama dengan role runtime "
                                + "(OWASP A01:2025, issue #433). Runtime hanya boleh DML; DDL hanya lewat "
                                + "role pemilik skema. Set SPRING_FLYWAY_USER (mis. kantin_owner) berbeda "
                                + "dari DB_USERNAME (mis. kantin_app). Profil=" + profil);
            }
            if (kosong(passwordMigrasi)) {
                throw new IllegalStateException(
                        "spring.flyway.password (SPRING_FLYWAY_PASSWORD) kosong untuk role migrasi '"
                                + userMigrasi + "' (issue #433). Profil=" + profil);
            }
        }

        // 4) Atribut role sesungguhnya (bukan sekadar nama).
        if (properties.isCekAtribut()) {
            periksaAtributRole(user);
        }

        log.info("Peran database terverifikasi: runtime='{}' (DML-only), migrasi='{}' (DDL), host='{}', "
                + "profil={} (issue #433).", user, userMigrasi, host, profil);
    }

    /**
     * Baca atribut role runtime dari katalog PostgreSQL. Melempar
     * {@link IllegalStateException} bila role ternyata superuser / punya hak
     * DDL, atau bila atribut tidak dapat diverifikasi (fail-closed).
     */
    private void periksaAtributRole(String user) {
        DataSource dataSource = dataSourceProvider == null ? null : dataSourceProvider.getIfAvailable();
        if (dataSource == null) {
            log.info("Pemeriksaan atribut role dilewati: tidak ada DataSource pada konteks ini.");
            return;
        }

        String sql = "select r.rolsuper, r.rolcreaterole, r.rolcreatedb, r.rolbypassrls,"
                + " has_schema_privilege(current_user, 'public', 'CREATE') as bisa_create_public"
                + " from pg_roles r where r.rolname = current_user";
        try (Connection koneksi = dataSource.getConnection();
                Statement st = koneksi.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) {
                throw new IllegalStateException("Tidak dapat membaca atribut role runtime '" + user
                        + "' dari pg_roles (issue #433). Periksa kredensial DB.");
            }
            boolean superuser = rs.getBoolean("rolsuper");
            boolean createrole = rs.getBoolean("rolcreaterole");
            boolean createdb = rs.getBoolean("rolcreatedb");
            boolean bypassrls = rs.getBoolean("rolbypassrls");
            boolean bisaCreatePublic = rs.getBoolean("bisa_create_public");

            if (superuser || createrole || createdb || bypassrls || bisaCreatePublic) {
                throw new IllegalStateException(
                        "Role runtime '" + user + "' TIDAK least-privilege (OWASP A01:2025, issue #433): "
                                + "superuser=" + superuser + ", createrole=" + createrole
                                + ", createdb=" + createdb + ", bypassrls=" + bypassrls
                                + ", create-on-public=" + bisaCreatePublic
                                + ". Role runtime hanya boleh SELECT/INSERT/UPDATE/DELETE; DDL lewat role "
                                + "migrasi terpisah.");
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Gagal memverifikasi atribut role runtime '" + user + "' (issue #433, fail-closed): "
                            + e.getMessage(), e);
        }
    }

    /**
     * {@code true} bila seluruh profil aktif termasuk daftar dev, atau tidak ada
     * profil aktif sama sekali (unit/integration test tanpa profil).
     */
    private boolean profilDev() {
        String[] aktif = environment.getActiveProfiles();
        if (aktif.length == 0) {
            return true;
        }
        List<String> dikecualikan = properties.getDikecualikanProfil();
        if (dikecualikan == null || dikecualikan.isEmpty()) {
            return false;
        }
        return Arrays.stream(aktif)
                .allMatch(p -> dikecualikan.stream().anyMatch(d -> d != null && d.equalsIgnoreCase(p)));
    }

    /** {@code true} bila {@code user} termasuk daftar {@code user-terlarang}. */
    boolean userTerlarang(String user) {
        List<String> terlarang = properties.getUserTerlarang();
        if (terlarang == null || terlarang.isEmpty() || user == null) {
            return false;
        }
        String kandidat = user.trim().toLowerCase(Locale.ROOT);
        return terlarang.stream()
                .filter(d -> d != null && !d.isBlank())
                .anyMatch(d -> d.trim().toLowerCase(Locale.ROOT).equals(kandidat));
    }

    private static boolean kosong(String nilai) {
        return nilai == null || nilai.isBlank();
    }
}
