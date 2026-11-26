package com.asqi.scholia_kantin_be.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Penjaga konfigurasi migrasi Flyway (issue #436, OWASP A08:2025).
 *
 * <p>Kebijakan ini mudah dibalik tanpa sengaja (satu baris di
 * {@code application.properties}) dan akibatnya senyap: skema yang tidak sesuai
 * tertutupi, atau sebaliknya database lokal developer tidak bisa start. Test ini
 * membaca berkas konfigurasi apa adanya — bukan {@code Environment} hasil resolve
 * — supaya yang dijaga adalah nilai yang benar-benar dikomit.
 */
@DisplayName("Konfigurasi migrasi Flyway (#436)")
class KonfigurasiMigrasiTest {

    private static Properties muat(String nama) throws Exception {
        Properties props = new Properties();
        try (InputStream in = KonfigurasiMigrasiTest.class.getClassLoader().getResourceAsStream(nama)) {
            assertThat(in).as("berkas %s harus ada di classpath", nama).isNotNull();
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return props;
    }

    private static String muatTeks(String nama) throws Exception {
        try (InputStream in = KonfigurasiMigrasiTest.class.getClassLoader().getResourceAsStream(nama)) {
            assertThat(in).as("berkas %s harus ada di classpath", nama).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("baseline-on-migrate mati secara default — skema asing harus terlihat")
    void baselineMatiSecaraDefault() throws Exception {
        assertThat(muat("application.properties")
                .getProperty("spring.flyway.baseline-on-migrate"))
                .isEqualTo("false");
    }

    @Test
    @DisplayName("validate-on-migrate menyala — checksum migrasi yang dirilis dijaga")
    void validateMenyala() throws Exception {
        assertThat(muat("application.properties")
                .getProperty("spring.flyway.validate-on-migrate"))
                .isEqualTo("true");
    }

    @Test
    @DisplayName("hanya profil local yang membuka baseline-on-migrate")
    void hanyaLocalYangMembukaBaseline() throws Exception {
        // Template yang dikomit — `application-local.properties` sendiri gitignored
        // sehingga tidak ada di CI.
        assertThat(muat("application-local.properties.example")
                .getProperty("spring.flyway.baseline-on-migrate"))
                .isEqualTo("true");
    }

    @Test
    @DisplayName("callback beforeMigrate menjaga prasyarat V28")
    void callbackPenjagaV28Ada() throws Exception {
        String sql = muatTeks("db/migration/beforeMigrate.sql");

        assertThat(sql)
                .as("callback harus menyebut migrasi destruktif yang dijaganya")
                .contains("V28__SkemaV2.sql");
        assertThat(sql)
                .as("callback harus menghentikan migrate, bukan sekadar memberi komentar")
                .contains("RAISE EXCEPTION");
        assertThat(sql)
                .as("penjaga harus melewatkan database yang sudah menerapkan V28")
                .contains("v28_terpasang");
    }
}
