package com.asqi.scholia_kantin_be.db;

import com.asqi.scholia_kantin_be.dev.DevLoginValidator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Penjaga seed dev: memastikan {@code db/seed/R__seed_dev.sql} masih sah
 * terhadap skema terbaru, idempoten, dan konsisten dengan tabel turunannya.
 *
 * <p>Tanpa test ini, migrasi baru yang menambah kolom NOT NULL akan membuat
 * seed diam-diam gagal — dan tim FE baru tahu saat aplikasinya tidak bisa start.
 *
 * <p>Test ini sengaja <b>tidak</b> memakai Spring context: cukup Flyway + JDBC,
 * jadi cepat dan tidak bergantung pada profil/konfigurasi aplikasi.
 */
@Testcontainers
class SeedDevIT {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("kantin_db")
            .withUsername("kantin")
            .withPassword("kantin");

    @Test
    void seedSahIdempotenDanKonsisten() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/seed")
                .load()
                .migrate();

        Jumlah awal = hitung(jdbc);
        assertThat(awal.menu()).as("menu dari seed").isEqualTo(21);
        assertThat(awal.kartuTamu()).as("kartu tamu dari seed").isEqualTo(14);
        assertThat(awal.transaksi()).as("transaksi dari seed").isEqualTo(12);

        // Tenant default form login FE (sekolah_id=10) wajib punya data —
        // tanpa ini semua layar FE tampak kosong walau endpoint sudah 200 (issue #231).
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM menu WHERE sekolah_id = 10", Integer.class))
                .as("menu tenant FE (sekolah 10)").isEqualTo(8);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM kartu_tamu WHERE sekolah_id = 10", Integer.class))
                .as("kartu tamu tenant FE").isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM transaksi WHERE sekolah_id = 10", Integer.class))
                .as("transaksi tenant FE").isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM saldo_cache WHERE sekolah_id = 10", Integer.class))
                .as("saldo_cache tenant FE").isEqualTo(4);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM dev_siswa_rfid WHERE sekolah_id = 10 AND siswa_id = 1001 "
                        + "AND nomor_kartu = 'RFID-SISWA-001' AND rfid_uid = 'DA100001' AND aktif",
                Integer.class))
                .as("fixture UID RFID siswa demo lokal")
                .isEqualTo(1);

        // Akun login dev: kredensial harus benar-benar diverifikasi, bukan sekadar ada.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dev_pengguna", Integer.class))
                .as("akun login dev").isEqualTo(8);

        DevLoginValidator validator = new DevLoginValidator(jdbc);
        var akun = validator.autentikasi("tu1", "Kantin#2026");
        assertThat(akun).as("login dengan kredensial seed").isPresent();
        assertThat(akun.get().role()).as("role dari akun").isEqualTo("TATA_USAHA");
        assertThat(akun.get().sekolahId()).as("sekolah dari akun").isEqualTo(1L);
        assertThat(validator.autentikasi("TU1", "Kantin#2026"))
                .as("username tidak peka huruf besar").isPresent();
        assertThat(validator.autentikasi("tu1", "salah"))
                .as("password salah harus ditolak").isEmpty();
        assertThat(validator.autentikasi("ngasal", "Kantin#2026"))
                .as("username asal-asalan harus ditolak").isEmpty();
        assertThat(validator.autentikasi("", ""))
                .as("kredensial kosong harus ditolak").isEmpty();

        // Tenant default FE (sekolah 10) wajib punya akun; token memakai sekolah
        // dari akun, jadi tanpa akun ini data sekolah 10 tak terjangkau dari UI.
        var akunFe = validator.autentikasi("kasir10", "Kantin#2026");
        assertThat(akunFe).as("akun dev tenant FE").isPresent();
        assertThat(akunFe.get().sekolahId()).as("sekolah akun tenant FE").isEqualTo(10L);
        assertThat(akunFe.get().role()).as("role akun tenant FE").isEqualTo("PETUGAS");
        assertThat(validator.autentikasi("bendahara10", "Kantin#2026"))
                .as("akun bendahara tenant FE").isPresent();

        // Jalankan ulang isi seed: harus idempoten, tidak menggandakan baris.
        new ResourceDatabasePopulator(new ClassPathResource("db/seed/R__seed_dev.sql"))
                .execute(dataSource);
        assertThat(hitung(jdbc)).as("seed idempoten").isEqualTo(awal);

        // stok_cache harus sama dengan mutasi_stok terakhir per menu.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM stok_cache c
                JOIN LATERAL (SELECT m.stok_setelah FROM mutasi_stok m
                              WHERE m.menu_id = c.menu_id
                              ORDER BY m.waktu DESC, m.id DESC LIMIT 1) m ON true
                WHERE c.stok <> m.stok_setelah
                """, Integer.class)).as("stok_cache vs mutasi_stok").isZero();

        // saldo_cache harus sama dengan saldo_ledger terakhir per subjek.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM saldo_cache s
                JOIN LATERAL (SELECT l.saldo_setelah FROM saldo_ledger l
                              WHERE l.subjek_tipe = s.subjek_tipe
                                AND l.subjek_id = s.subjek_id
                              ORDER BY l.waktu DESC, l.id DESC LIMIT 1) l ON true
                WHERE s.saldo <> l.saldo_setelah
                """, Integer.class)).as("saldo_cache vs saldo_ledger").isZero();

        // total transaksi harus sama dengan jumlah itemnya.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT t.total, coalesce(sum(i.subtotal), 0) AS item
                    FROM transaksi t LEFT JOIN transaksi_item i ON i.transaksi_id = t.id
                    GROUP BY t.id, t.total
                ) x WHERE x.total <> x.item
                """, Integer.class)).as("transaksi vs item").isZero();
    }

    private Jumlah hitung(JdbcTemplate jdbc) {
        return new Jumlah(
                jdbc.queryForObject("SELECT count(*) FROM menu", Integer.class),
                jdbc.queryForObject("SELECT count(*) FROM kartu_tamu", Integer.class),
                jdbc.queryForObject("SELECT count(*) FROM transaksi", Integer.class),
                jdbc.queryForObject("SELECT count(*) FROM saldo_ledger", Integer.class));
    }

    private record Jumlah(int menu, int kartuTamu, int transaksi, int ledger) {
    }
}
