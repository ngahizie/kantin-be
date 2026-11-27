package com.asqi.scholia_kantin_be.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Penjaga konfigurasi batas waktu database (issue #452).
 *
 * <p>Konfigurasi ini mudah "diam-diam rusak" karena berbentuk SQL di dalam
 * properti. Test ini memastikan janji di dokumentasi benar-benar ada di berkas:
 * ketiga GUC dipasang dalam satu {@code SELECT}, batas ekspor memakai format
 * durasi, dan schema Flyway dipatok.
 */
class KonfigurasiBatasWaktuDatabaseTest {

    private static final String NAMA_FILE = "application.properties";

    @Test
    @DisplayName("connection-init-sql memasang statement_timeout, lock_timeout, DAN idle_in_transaction_session_timeout")
    void initSqlMemasangTigaGuc() throws IOException {
        String isi = bacaProperties();
        String initSql = baris(isi, "spring.datasource.hikari.connection-init-sql=");

        assertThat(initSql)
                .contains("set_config('statement_timeout'")
                .contains("set_config('lock_timeout'")
                .contains("set_config('idle_in_transaction_session_timeout'");
        // Hikari hanya menjalankan satu statement: tidak boleh ada pemisah ';'.
        assertThat(initSql).doesNotContain(";");
    }

    @Test
    @DisplayName("batas ekspor memakai format durasi, bukan nama properti milidetik")
    void batasEksporMemakaiDurasi() throws IOException {
        String isi = bacaProperties();

        assertThat(isi).contains("kantin.db.statement-timeout-laporan=${KANTIN_DB_STATEMENT_TIMEOUT_LAPORAN:30s}");
        assertThat(isi).doesNotContain("statement-timeout-laporan-ms");
    }

    @Test
    @DisplayName("batas konkurensi ekspor punya default eksplisit")
    void batasKonkurensiEksporAda() throws IOException {
        String isi = bacaProperties();

        assertThat(isi).contains("kantin.laporan.ekspor-konkuren-maks=${KANTIN_LAPORAN_EKSPOR_KONKUREN_MAKS:2}");
    }

    @Test
    @DisplayName("schema Flyway dipatok agar role migrasi terpisah tidak salah schema")
    void schemaFlywayDipatok() throws IOException {
        String isi = bacaProperties();

        assertThat(isi).contains("spring.flyway.default-schema=public");
        assertThat(isi).contains("spring.flyway.table=flyway_schema_history");
    }

    private static String baris(String isi, String awalan) {
        return isi.lines()
                .filter(l -> l.startsWith(awalan))
                .findFirst()
                .orElseThrow(() -> new AssertionError("tidak ditemukan: " + awalan));
    }

    private static String bacaProperties() throws IOException {
        ClassPathResource resource = new ClassPathResource(NAMA_FILE);
        assertThat(resource.exists()).as("%s harus ada di classpath", NAMA_FILE).isTrue();
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
