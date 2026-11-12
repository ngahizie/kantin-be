package com.asqi.scholia_kantin_be.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Baris <b>induk</b> yang wajib ada sebelum baris uji lain ditulis (skema v2).
 *
 * <p><b>Kenapa perlu.</b> Sejak V28 (skema v2) master {@code sekolah},
 * {@code siswa}, dan {@code pengguna} hidup di kantin-be sendiri, dan baris
 * transaksi/ledger ber-<i>foreign key</i> ke sana
 * ({@code fk_transaksi_siswa}, {@code fk_saldo_siswa},
 * {@code fk_mutasi_saldo_siswa}, {@code fk_transaksi_petugas}, …). IT yang dulu
 * cukup menulis {@code subjek_id} kini <b>wajib</b> menyiapkan induknya lebih
 * dulu — tanpa itu PostgreSQL menolak dengan
 * {@code violates foreign key constraint}, bukan karena SQL-nya salah tulis.
 *
 * <p>Semua helper <b>idempoten</b> ({@code ON CONFLICT (id) DO NOTHING}): aman
 * dipanggil dari {@code @BeforeEach} berkali-kali dan aman antar kelas IT,
 * karena Testcontainers me-reuse satu kontainer (satu database) untuk seluruh
 * suite. Baris induk sengaja <b>tidak</b> ikut di-{@code TRUNCATE} oleh kelas
 * IT (yang memangkas tabel transaksi/ledger saja).
 */
public final class FixtureInduk {

    private FixtureInduk() {
    }

    /** Satu sekolah (tenant) minimum: {@code id} + {@code nama}. */
    public static void sekolah(JdbcTemplate jdbc, long id) {
        jdbc.update("INSERT INTO sekolah (id, nama) VALUES (?, ?) ON CONFLICT (id) DO NOTHING",
                id, "Sekolah Uji " + id);
    }

    /** Sekolah untuk beberapa id sekaligus. */
    public static void sekolah(JdbcTemplate jdbc, long... ids) {
        for (long id : ids) {
            sekolah(jdbc, id);
        }
    }

    /**
     * Akun petugas/bendahara minimum — dipakai {@code transaksi.petugas_id},
     * {@code transaksi.void_oleh}, dan {@code mutasi_saldo.aktor_id}.
     */
    public static void pengguna(JdbcTemplate jdbc, long sekolahId, long... ids) {
        for (long id : ids) {
            jdbc.update("""
                    INSERT INTO pengguna (id, sekolah_id, username, nama, role, password_hash)
                    VALUES (?, ?, ?, ?, 'BENDAHARA', 'x')
                    ON CONFLICT (id) DO NOTHING
                    """, id, sekolahId, "uji-" + id, "Pengguna Uji " + id);
        }
    }

    /**
     * Siswa minimum — dipakai {@code saldo}, {@code mutasi_saldo},
     * {@code transaksi}, dan {@code kartu}. NIS dibentuk dari id agar unik
     * per sekolah ({@code uq_siswa_sekolah_nis}).
     */
    public static void siswa(JdbcTemplate jdbc, long sekolahId, long... ids) {
        for (long id : ids) {
            jdbc.update("""
                    INSERT INTO siswa (id, sekolah_id, nis, nama, kelas, aktif)
                    VALUES (?, ?, ?, ?, '7A', true)
                    ON CONFLICT (id) DO NOTHING
                    """, id, sekolahId, "NIS-" + id, "Siswa Uji " + id);
        }
    }

    /** Kategori menu minimum — {@code menu.kategori_id} ber-FK ke sini. */
    public static void kategori(JdbcTemplate jdbc, long sekolahId, long id) {
        kategori(jdbc, sekolahId, id, "Kategori Uji " + id);
    }

    /** Kategori menu bernama tertentu. */
    public static void kategori(JdbcTemplate jdbc, long sekolahId, long id, String nama) {
        sekolah(jdbc, sekolahId);
        jdbc.update("""
                INSERT INTO kategori_menu (id, sekolah_id, nama, urutan)
                VALUES (?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, id, sekolahId, nama, id);
    }

    /** Menu minimum milik satu sekolah — prasyarat {@code stok}/{@code riwayat_stok}. */
    public static void menu(JdbcTemplate jdbc, long sekolahId, long... ids) {
        for (long id : ids) {
            menu(jdbc, sekolahId, id, "Menu Uji " + id);
        }
    }

    /** Menu bernama tertentu (harga 8.000, satuan PCS). */
    public static void menu(JdbcTemplate jdbc, long sekolahId, long id, String nama) {
        sekolah(jdbc, sekolahId);
        jdbc.update("""
                INSERT INTO menu (id, sekolah_id, nama, harga_jual)
                VALUES (?, ?, ?, 8000)
                ON CONFLICT (id) DO NOTHING
                """, id, sekolahId, nama);
    }

    /** Titik kasir minimum milik satu sekolah. */
    public static void titikKasir(JdbcTemplate jdbc, long sekolahId, long... ids) {
        sekolah(jdbc, sekolahId);
        for (long id : ids) {
            jdbc.update("""
                    INSERT INTO titik_kasir (id, sekolah_id, nama)
                    VALUES (?, ?, ?)
                    ON CONFLICT (id) DO NOTHING
                    """, id, sekolahId, "Kasir Uji " + id);
        }
    }

    /**
     * Kumpulan pengguna "umum" yang sering dipakai uji sebagai aktor/petugas
     * ({@code 555}, {@code 777}, {@code 900}, {@code 1}, …). Semua FK
     * {@code aktor_id}/{@code petugas_id}/{@code ditutup_oleh} menunjuk ke
     * {@code pengguna}, jadi baris ini wajib ada sebelum ledger menulis.
     */
    public static void penggunaUmum(JdbcTemplate jdbc, long sekolahId) {
        pengguna(jdbc, sekolahId, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 42, 77, 100, 555, 777, 900, 999);
    }

    /** Kartu RFID minimum — {@code transaksi_tertunda.rfid_uid} ber-FK ke sini. */
    public static void kartu(JdbcTemplate jdbc, long sekolahId, long siswaId, String uid) {
        siswa(jdbc, sekolahId, siswaId);
        jdbc.update("""
                INSERT INTO kartu (uid, sekolah_id, siswa_id, nomor_kartu)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (uid) DO NOTHING
                """, uid, sekolahId, siswaId, "KRT-" + uid);
    }

    /**
     * Kategori bawaan yang dipakai {@code MenuLookupPort} fake di banyak IT
     * ({@code kategoriId(100)} / {@code (200)}). {@code transaksi_item.kategori_id}
     * ber-FK ke {@code kategori_menu}, jadi barisnya wajib ada.
     */
    public static void kategoriUmum(JdbcTemplate jdbc, long sekolahId) {
        kategori(jdbc, sekolahId, 100L);
        kategori(jdbc, sekolahId, 200L);
    }
}
