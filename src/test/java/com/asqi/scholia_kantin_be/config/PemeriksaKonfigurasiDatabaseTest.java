package com.asqi.scholia_kantin_be.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Penjaga konfigurasi database kosong (issue #452).
 *
 * <p>Placeholder Spring hanya memakai default bila variabel <b>tidak ada</b>.
 * Variabel yang ada tetapi kosong menghasilkan {@code statement_timeout=''} atau
 * datasource Flyway tanpa URL — kegagalan samar yang baru ketahuan jauh kemudian.
 * Penjaga ini harus gagal cepat dengan pesan yang menyebut variabelnya.
 */
class PemeriksaKonfigurasiDatabaseTest {

    @Test
    @DisplayName("konfigurasi bersih tidak melempar apa pun")
    void konfigurasiBersihLolos() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("KANTIN_DB_STATEMENT_TIMEOUT", "10s")
                .withProperty("KANTIN_DB_LOCK_TIMEOUT", "3s")
                .withProperty("spring.datasource.hikari.connection-init-sql",
                        "SELECT set_config('statement_timeout', '10s', false)")
                .withProperty("spring.datasource.hikari.connection-timeout", "5000")
                .withProperty("kantin.laporan.ekspor-konkuren-maks", "2");

        assertThatCode(() -> new PemeriksaKonfigurasiDatabase(env).periksa())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("variabel yang diisi kosong membuat start gagal dengan pesan yang jelas")
    void variabelKosongMenggagalkanStart() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("KANTIN_DB_STATEMENT_TIMEOUT", " ");

        assertThatThrownBy(() -> new PemeriksaKonfigurasiDatabase(env).periksa())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KANTIN_DB_STATEMENT_TIMEOUT")
                .hasMessageContaining("HAPUS barisnya");
    }

    @Test
    @DisplayName("connection-init-sql dengan GUC kosong ditolak")
    void initSqlDenganGucKosongDitolak() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.hikari.connection-init-sql",
                        "SELECT set_config('statement_timeout', '', false)");

        assertThatThrownBy(() -> new PemeriksaKonfigurasiDatabase(env).periksa())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GUC kosong");
    }

    @Test
    @DisplayName("connection-timeout di bawah batas Hikari ditolak")
    void connectionTimeoutTerlaluKecilDitolak() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.hikari.connection-timeout", "100");

        assertThatThrownBy(() -> new PemeriksaKonfigurasiDatabase(env).periksa())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("batas minimum Hikari");
    }

    @Test
    @DisplayName("batas konkurensi ekspor 0 ditolak")
    void konkurensiEksporNolDitolak() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("kantin.laporan.ekspor-konkuren-maks", "0");

        assertThatThrownBy(() -> new PemeriksaKonfigurasiDatabase(env).periksa())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ekspor-konkuren-maks");
    }
}
