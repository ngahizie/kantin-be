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
 * terhadap skema v2, idempoten, dan konsisten dengan tabel turunannya.
 *
 * <p>Tanpa test ini, migrasi baru yang menambah kolom NOT NULL akan membuat
 * seed diam-diam gagal — dan tim FE baru tahu saat aplikasinya tidak bisa start.
 *
 * <p>Test ini sengaja <b>tidak</b> memakai Spring context: cukup Flyway + JDBC,
 * jadi cepat dan tidak bergantung pada profil/konfigurasi aplikasi.
 *
 * <p>Sejak skema v2 tidak ada lagi kartu tamu: setiap kartu terikat ke satu
 * siswa ({@code kartu.siswa_id}) dan saldo memakai kunci
 * ({@code sekolah_id}, {@code siswa_id}).
 */
@Testcontainers
class SeedDevIT {

    private static final String PASSWORD = "Kantin#2026";

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
        assertThat(awal).as("isi seed v2").isEqualTo(new Jumlah(
                2,    // sekolah: 1 dan 10
                12,   // pengguna: 6 per sekolah
                24,   // menu: 12 per sekolah
                8,    // kategori_menu: 4 per sekolah
                24,   // stok: 1 baris per menu
                19,   // siswa: 14 (sekolah 1) + 5 (sekolah 10)
                38,   // orang_tua: 2 wali per siswa
                19,   // kartu: 1 kartu per siswa
                19,   // saldo: 1 baris per siswa
                16,   // mutasi_saldo: hanya siswa bersaldo > 0
                2,    // pengaturan_kantin: 1 per sekolah
                4,    // titik_kasir: 2 per sekolah
                8,    // akun_buku_kas: 4 per sekolah
                0));  // transaksi: seed v2 tidak membuat transaksi contoh

        // Tenant default form login FE (sekolah_id=10) wajib punya data —
        // tanpa ini semua layar FE tampak kosong walau endpoint sudah 200 (issue #231).
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM menu WHERE sekolah_id = 10", Integer.class))
                .as("menu tenant FE (sekolah 10)").isEqualTo(12);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM siswa WHERE sekolah_id = 10", Integer.class))
                .as("siswa tenant FE").isEqualTo(5);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM saldo WHERE sekolah_id = 10", Integer.class))
                .as("saldo tenant FE").isEqualTo(5);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pengguna WHERE sekolah_id = 10", Integer.class))
                .as("akun tenant FE").isEqualTo(6);

        // Fixture tap lokal: kartu uji harus aktif dan menunjuk siswa yang ada.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM kartu WHERE nomor_kartu LIKE 'UJI-RFID-%' AND aktif",
                Integer.class)).as("kartu uji RFID aktif").isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM kartu k
                JOIN siswa s ON s.id = k.siswa_id AND s.sekolah_id = k.sekolah_id
                WHERE k.nomor_kartu LIKE 'UJI-RFID-%'
                """, Integer.class)).as("kartu uji terhubung ke siswa").isEqualTo(3);

        // Satu siswa tidak boleh punya dua kartu aktif (index unik parsial).
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT sekolah_id, siswa_id FROM kartu WHERE aktif
                    GROUP BY sekolah_id, siswa_id HAVING count(*) > 1
                ) x
                """, Integer.class)).as("kartu aktif ganda per siswa").isZero();

        // Akun login dev: kredensial harus benar-benar diverifikasi, bukan sekadar ada.
        DevLoginValidator validator = new DevLoginValidator(jdbc);
        var akun = validator.autentikasi("admin1", PASSWORD);
        assertThat(akun).as("login admin dengan kredensial seed").isPresent();
        assertThat(akun.get().role()).as("role dari akun admin").isEqualTo("ADMIN_SEKOLAH");
        assertThat(akun.get().sekolahId()).as("sekolah dari akun").isEqualTo(1L);
        assertThat(validator.autentikasi("tu1", PASSWORD))
                .as("login TU dengan kredensial seed").isPresent();
        assertThat(validator.autentikasi("ADMIN1", PASSWORD))
                .as("username tidak peka huruf besar").isPresent();
        assertThat(validator.autentikasi("admin1", "salah"))
                .as("password salah harus ditolak").isEmpty();
        assertThat(validator.autentikasi("ngasal", PASSWORD))
                .as("username asal-asalan harus ditolak").isEmpty();
        assertThat(validator.autentikasi("", ""))
                .as("kredensial kosong harus ditolak").isEmpty();

        // Tenant default FE (sekolah 10) wajib punya akun; token memakai sekolah
        // dari akun, jadi tanpa akun ini data sekolah 10 tak terjangkau dari UI.
        var akunFe = validator.autentikasi("kasir10", PASSWORD);
        assertThat(akunFe).as("akun dev tenant FE").isPresent();
        assertThat(akunFe.get().sekolahId()).as("sekolah akun tenant FE").isEqualTo(10L);
        assertThat(akunFe.get().role()).as("role akun tenant FE").isEqualTo("PETUGAS");

        // Jalankan ulang isi seed: harus idempoten, tidak menggandakan baris.
        new ResourceDatabasePopulator(new ClassPathResource("db/seed/R__seed_dev.sql"))
                .execute(dataSource);
        assertThat(hitung(jdbc)).as("seed idempoten").isEqualTo(awal);

        // Seed harus SELF-HEALING (issue #255): baris cache yang sudah drift
        // diperbaiki saat seed dijalankan ulang — bukan dibiarkan karena
        // `ON CONFLICT DO NOTHING`.
        jdbc.update("UPDATE saldo SET saldo = saldo + 12345");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM saldo c
                WHERE c.saldo <> COALESCE((
                    SELECT sum(CASE WHEN l.arah = 'KREDIT' THEN l.nominal ELSE -l.nominal END)
                      FROM mutasi_saldo l
                     WHERE l.sekolah_id = c.sekolah_id
                       AND l.siswa_id = c.siswa_id), 0)
                """, Integer.class)).as("drift saldo sengaja dibuat").isPositive();

        new ResourceDatabasePopulator(new ClassPathResource("db/seed/R__seed_dev.sql"))
                .execute(dataSource);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM saldo c
                WHERE c.saldo <> COALESCE((
                    SELECT sum(CASE WHEN l.arah = 'KREDIT' THEN l.nominal ELSE -l.nominal END)
                      FROM mutasi_saldo l
                     WHERE l.sekolah_id = c.sekolah_id
                       AND l.siswa_id = c.siswa_id), 0)
                """, Integer.class)).as("seed memperbaiki cache saldo yang drift").isZero();

        // ── Invarian "cache = turunan ledger" (issue #255) ─────────────
        // cache harus sama dengan Σ ledger, bukan hanya sama dengan baris
        // ledger TERAKHIR (penjaga di atas buta terhadap ledger yang
        // saldo_setelah-nya tidak berurutan).
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM saldo c
                WHERE c.saldo <> COALESCE((
                    SELECT sum(CASE WHEN l.arah = 'KREDIT' THEN l.nominal ELSE -l.nominal END)
                      FROM mutasi_saldo l
                     WHERE l.sekolah_id = c.sekolah_id
                       AND l.siswa_id = c.siswa_id), 0)
                """, Integer.class)).as("saldo = Σ ledger").isZero();

        // Setiap baris ledger harus lanjutan yang benar dari baris sebelumnya.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM mutasi_saldo a
                JOIN LATERAL (
                    SELECT b.saldo_setelah AS prev FROM mutasi_saldo b
                     WHERE b.sekolah_id = a.sekolah_id
                       AND b.siswa_id = a.siswa_id
                       AND (b.waktu, b.id) < (a.waktu, a.id)
                     ORDER BY b.waktu DESC, b.id DESC LIMIT 1) s ON true
                WHERE a.saldo_setelah <> s.prev
                     + CASE WHEN a.arah = 'KREDIT' THEN a.nominal ELSE -a.nominal END
                """, Integer.class)).as("saldo_setelah berurutan").isZero();

        // stok tidak boleh negatif, dan baris stok harus punya menu yang sah.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM stok WHERE stok < 0", Integer.class))
                .as("stok negatif").isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM stok s
                LEFT JOIN menu m ON m.id = s.menu_id AND m.sekolah_id = s.sekolah_id
                WHERE m.id IS NULL
                """, Integer.class)).as("stok tanpa menu").isZero();

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
                jdbc.queryForObject("SELECT count(*) FROM sekolah", Integer.class),
                jdbc.queryForObject("SELECT count(*) FROM pengguna", Integer.class),
                jdbc.queryForObject("SELECT count(*) FROM menu", Integer.class),
                jdbc.queryForObject("SELECT count(*) FROM kategori_menu", Integer.class),
                jdbc.queryForObject("SELECT count(*) FROM stok", Integer.class),
                jdbc.queryForObject("SELECT count(*) FROM siswa", Integer.class),
                jdbc.queryForObject("SELECT count(*) FROM orang_tua", Integer.class),
                jdbc.queryForObject("SELECT count(*) FROM kartu", Integer.class),
                jdbc.queryForObject("SELECT count(*) FROM saldo", Integer.class),
                jdbc.queryForObject("SELECT count(*) FROM mutasi_saldo", Integer.class),
                jdbc.queryForObject("SELECT count(*) FROM pengaturan_kantin", Integer.class),
                jdbc.queryForObject("SELECT count(*) FROM titik_kasir", Integer.class),
                jdbc.queryForObject("SELECT count(*) FROM akun_buku_kas", Integer.class),
                jdbc.queryForObject("SELECT count(*) FROM transaksi", Integer.class));
    }

    private record Jumlah(int sekolah, int pengguna, int menu, int kategori, int stok,
                          int siswa, int orangTua, int kartu, int saldo, int ledger,
                          int pengaturan, int titikKasir, int akunBukuKas, int transaksi) {
    }
}
