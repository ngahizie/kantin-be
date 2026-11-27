package com.asqi.scholia_kantin_be.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Penjaga konfigurasi URL database (issue #438).
 *
 * <p>{@code stringtype=unspecified} membuat driver PostgreSQL mengirim setiap
 * parameter {@code String} tanpa tipe, sehingga PostgreSQL harus menebak lewat
 * cast implisit. Itu bukan vektor injeksi (semua query parameterized), tetapi
 * melemahkan type-safety: parameter {@code null} tanpa tipe pernah ditolak
 * PostgreSQL ({@code could not determine data type of parameter}, akar bug
 * #198) dan perubahan tipe kolom bisa mengubah hasil query tanpa peringatan.
 *
 * <p>Flag sudah dilepas dan seluruh integration test lulus tanpa flag. Test ini
 * menjaga agar flag tidak kembali lewat salin-tempel URL dari dokumentasi lama.
 * Alasannya ditulis di {@code docs/spesifikasi-service-repository.md} §5.
 */
class KonfigurasiUrlDatabaseTest {

    private static final String FLAG = "stringtype=unspecified";

    @Test
    @DisplayName("URL datasource default tidak memakai stringtype=unspecified")
    void urlDefaultTanpaFlag() throws IOException {
        String isi = bacaDariClasspath("application.properties");

        assertThat(isi)
                .as("application.properties masih memuat %s", FLAG)
                .doesNotContain(FLAG);
        assertThat(isi).contains("spring.datasource.url=");
    }

    @Test
    @DisplayName("contoh konfigurasi lokal tidak memakai stringtype=unspecified")
    void contohLokalTanpaFlag() throws IOException {
        String isi = bacaDariClasspath("application-local.properties.example");

        assertThat(isi)
                .as("application-local.properties.example masih memuat %s", FLAG)
                .doesNotContain(FLAG);
    }

    private static String bacaDariClasspath(String nama) throws IOException {
        ClassPathResource resource = new ClassPathResource(nama);
        assertThat(resource.exists()).as("%s harus ada di classpath", nama).isTrue();
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
