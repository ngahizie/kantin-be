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
                14,   // pengguna: 6 peran + 1 kasir kedua, per sekolah (#345)
                24,   // menu: 12 per sekolah
                8,    // kategori_menu: 4 per sekolah
                24,   // stok: 1 baris per menu
                21,   // siswa: 19 aktif + 2 nonaktif (kandidat refund & riwayat refund)
                42,   // orang_tua: 2 wali per siswa
                21,   // kartu: 1 kartu per siswa (2 nonaktif)
                21,   // saldo: 1 baris per siswa
                80,   // mutasi_saldo: topup/penjualan/void + refund, transfer, koreksi, topup online
                2,    // pengaturan_kantin: 1 per sekolah
                4,    // titik_kasir: 2 per sekolah
                8,    // akun_buku_kas: 4 per sekolah
                62)); // transaksi: 55 saldo + 6 tunai (C.2a) + 1 void

        // Tenant default form login FE (sekolah_id=10) wajib punya data —
        // tanpa ini semua layar FE tampak kosong walau endpoint sudah 200 (issue #231).
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM menu WHERE sekolah_id = 10", Integer.class))
                .as("menu tenant FE (sekolah 10)").isEqualTo(12);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM siswa WHERE sekolah_id = 10", Integer.class))
                .as("siswa tenant FE").isEqualTo(6);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM saldo WHERE sekolah_id = 10", Integer.class))
                .as("saldo tenant FE").isEqualTo(6);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pengguna WHERE sekolah_id = 10", Integer.class))
                .as("akun tenant FE").isEqualTo(7); // 6 peran + kasir20 (#345)

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

        // Kasir kedua per sekolah (issue #345) — syarat uji multi-titik: dua loket
        // di sekolah yang sama harus bisa dibuka petugas berbeda.
        var kasir2 = validator.autentikasi("kasir2", PASSWORD);
        assertThat(kasir2).as("akun kasir kedua sekolah 1").isPresent();
        assertThat(kasir2.get().sekolahId()).as("sekolah akun kasir2").isEqualTo(1L);
        assertThat(kasir2.get().role()).as("role akun kasir2").isEqualTo("PETUGAS");

        var kasir20 = validator.autentikasi("kasir20", PASSWORD);
        assertThat(kasir20).as("akun kasir kedua sekolah 10").isPresent();
        assertThat(kasir20.get().sekolahId()).as("sekolah akun kasir20").isEqualTo(10L);
        assertThat(kasir20.get().role()).as("role akun kasir20").isEqualTo("PETUGAS");

        // Sesi seed harus punya jejak pembuka (issue #345), dan titik 2 sekolah 1
        // dibuka kasir2 — bukan kasir1 — supaya demo multi-titik realistis.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM sesi_kasir WHERE dibuka_oleh IS NULL", Integer.class))
                .as("sesi seed tanpa jejak pembuka").isZero();
        assertThat(jdbc.queryForObject(
                "SELECT dibuka_oleh FROM sesi_kasir WHERE sekolah_id = 1 AND titik_kasir_id = 2 "
                        + "ORDER BY tanggal DESC LIMIT 1",
                Long.class)).as("pembuka sesi titik 2 sekolah 1").isEqualTo(7L);

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

        // stok_setelah juga harus lanjutan yang benar: riwayat_stok append-only,
        // jadi nilai ini tidak bisa dikoreksi setelah insert (seed wajib benar).
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM riwayat_stok a
                JOIN LATERAL (
                    SELECT b.stok_setelah AS prev FROM riwayat_stok b
                     WHERE b.sekolah_id = a.sekolah_id
                       AND b.menu_id = a.menu_id
                       AND (b.waktu, b.id) < (a.waktu, a.id)
                     ORDER BY b.waktu DESC, b.id DESC LIMIT 1) s ON true
                WHERE a.stok_setelah <> s.prev
                     + CASE WHEN a.arah = 'MASUK' THEN a.qty ELSE -a.qty END
                """, Integer.class)).as("stok_setelah berurutan").isZero();

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

        // ── Cakupan demo: tidak ada layar yang kosong ─────────────────
        // Laporan kerugian stok butuh OPNAME_KELUAR/BARANG_RUSAK (PRD §9.5).
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM riwayat_stok
                 WHERE jenis IN ('OPNAME_KELUAR', 'BARANG_RUSAK')
                """, Integer.class)).as("baris kerugian stok").isEqualTo(5);

        // Refund saldo, pindah saldo, koreksi bendahara, topup online.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM mutasi_saldo WHERE jenis = 'REFUND'", Integer.class))
                .as("riwayat refund saldo").isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM mutasi_saldo WHERE jenis = 'TRANSFER'", Integer.class))
                .as("riwayat pindah saldo (keluar+masuk)").isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM mutasi_saldo WHERE jenis = 'KOREKSI'", Integer.class))
                .as("riwayat koreksi bendahara").isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM mutasi_saldo WHERE jenis = 'TOPUP_ONLINE'", Integer.class))
                .as("riwayat topup online").isEqualTo(1);

        // Kandidat refund: siswa nonaktif yang masih bersaldo.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM siswa s
                  JOIN saldo c ON c.siswa_id = s.id AND c.sekolah_id = s.sekolah_id
                 WHERE s.aktif = false AND c.saldo > 0
                """, Integer.class)).as("kandidat refund").isEqualTo(1);

        // Bayar tunai (C.2a): tanpa siswa, lengkap uang diterima + nama pembeli.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM transaksi
                 WHERE metode_bayar = 'TUNAI' AND siswa_id IS NULL
                   AND uang_diterima IS NOT NULL AND nama_pembeli IS NOT NULL
                """, Integer.class)).as("transaksi tunai Kartu Tamu").isEqualTo(6);

        // Kas fisik (C.3) wajib ada di sesi tertutup dan selisihnya harus sama
        // dengan uang fisik − totalTunai (C.2d).
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM sesi_kasir WHERE status = 'DITUTUP' AND uang_fisik IS NULL",
                Integer.class)).as("sesi tertutup tanpa kas fisik").isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM sesi_kasir s
                 WHERE s.status = 'DITUTUP'
                   AND s.selisih <> s.uang_fisik - COALESCE((
                        SELECT sum(t.total) FROM transaksi t
                         WHERE t.sesi_kasir_id = s.id AND t.status = 'SUKSES'
                           AND t.metode_bayar = 'TUNAI'), 0)
                """, Integer.class)).as("selisih tidak sama dengan uang fisik - totalTunai").isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM sesi_kasir WHERE selisih <> 0", Integer.class))
                .as("contoh selisih kas untuk demo C.3").isEqualTo(1);
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
