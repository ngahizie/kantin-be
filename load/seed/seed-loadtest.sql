-- ============================================================
-- seed-loadtest.sql — Data seed untuk uji beban k6 (issue #145)
-- ------------------------------------------------------------
-- Tujuan: menyediakan data MINIMAL agar POST /api/kasir/tap menembus
-- jalur SUKSES pada skenario "N kasir tap bersamaan".
--
-- ⚠️ BUKAN migrasi Flyway dan BUKAN data produksi. Jalankan manual:
--     psql "$DB_URL" -f load/seed/seed-loadtest.sql
--
-- Idempoten: aman dijalankan berulang (ON CONFLICT DO NOTHING/UPDATE).
-- Sekolah uji = 1 (tenant). Kartu uji = Kartu Tamu (data milik kantin-be,
-- tak terblokir kontrak Q7 — lihat KartuLookupLoadTest).
-- ============================================================

-- ── Konfigurasi kantin (konfirmasi_manual=false → tap langsung potong) ──
INSERT INTO sekolah_kantin_config (sekolah_id, nama_kantin, jam_tutup_otomatis,
                                   konfirmasi_manual, created_at, updated_at)
VALUES (1, 'Kantin Load Test', '23:59', false, now(), now())
ON CONFLICT (sekolah_id) DO UPDATE
    SET konfirmasi_manual = false, updated_at = now();

-- ── Titik kasir ──
INSERT INTO titik_kasir (id, sekolah_id, nama, kode, is_active, created_at, updated_at)
VALUES (1, 1, 'Kasir Load Test', 'LOAD1', true, now(), now())
ON CONFLICT (id) DO NOTHING;

-- ── Sesi kasir hari ini (TERBUKA) → hindari balapan "buka sesi" saat beban ──
INSERT INTO sesi_kasir (id, sekolah_id, titik_kasir_id, tanggal, status,
                        total_bruto, total_void, total_bersih, dibuka_at,
                        created_at, updated_at)
VALUES (1, 1, 1, CURRENT_DATE, 'TERBUKA', 0, 0, 0, now(), now(), now())
ON CONFLICT (titik_kasir_id, tanggal) DO NOTHING;

-- ── Kategori & menu ──
INSERT INTO kategori_menu (id, sekolah_id, nama, urutan, is_active, created_at, updated_at)
VALUES (1, 1, 'Makanan Load', 1, true, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO menu (id, sekolah_id, kategori_id, nama, harga_jual, satuan,
                  stok_minimum, is_active, created_at, updated_at)
VALUES (1, 1, 1, 'Nasi Load', 8000, 'PCS', 0, true, now(), now())
ON CONFLICT (id) DO NOTHING;

-- ── Stok besar (agar tak habis selama skenario berjalan) ──
INSERT INTO stok_cache (menu_id, sekolah_id, stok, hpp, stok_minimum, updated_at)
VALUES (1, 1, 100000000, 4000, 0, now())
ON CONFLICT (menu_id) DO UPDATE
    SET stok = EXCLUDED.stok, hpp = EXCLUDED.hpp, updated_at = now();

-- ── 20 Kartu Tamu + saldo besar (sebar beban, hindari hot-row tunggal) ──
-- rfid_uid = '04' + 6 digit (desimal ter-pad) → 8 karakter heksa valid, unik.
--   g=1  → '04000001'   g=20 → '04000020'
-- Nilai ini SAMA dengan yang dibangkitkan skrip k6 (load/k6/tap-slo.js).
-- id sengaja di rentang 9001..9020: seed dev (R__seed_dev.sql) sudah memakai
-- id 1..7+ untuk kartu demo, sehingga `ON CONFLICT (id) DO NOTHING` dulu
-- membuat UID 04000001..04000007 tidak pernah dibuat → tap dianggap
-- "kartu tidak dikenal" dan uji beban gagal walau login sudah benar.
INSERT INTO kartu_tamu (id, sekolah_id, nomor_kartu, rfid_uid, aktif,
                        label_pemegang, dibuat_oleh, dibuat_pada)
SELECT 9000 + g, 1,
       'KT-LOAD-' || lpad(g::text, 3, '0'),
       '04' || lpad(g::text, 6, '0'),
       true,
       'Load Tester ' || g,
       1, now()
FROM generate_series(1, 20) AS g
ON CONFLICT (sekolah_id, nomor_kartu) DO UPDATE
    SET rfid_uid = EXCLUDED.rfid_uid,
        aktif    = true;

-- Saldo diambil dari kartu yang benar-benar ada (bukan dari rumus id), agar
-- tetap benar walau kartu uji terlanjur dibuat versi seed lama dengan id lain.
INSERT INTO saldo_cache (subjek_tipe, subjek_id, sekolah_id, saldo, updated_at)
SELECT 'KARTU_TAMU', k.id, 1, 1000000000, now()
FROM kartu_tamu k
WHERE k.sekolah_id = 1 AND k.nomor_kartu LIKE 'KT-LOAD-%'
ON CONFLICT (subjek_tipe, subjek_id) DO UPDATE
    SET saldo = EXCLUDED.saldo, updated_at = now();

-- ── Akun login khusus uji beban ──
-- Dibuat di sini (bukan di seed dev) agar uji beban TIDAK bergantung pada
-- kredensial dev yang bisa berubah. Tabel `dev_pengguna` hanya ada di lokal/CI
-- (profil `local`); kantin-be tidak menyimpan master pengguna (ADR-0002).
-- Password: loadtest (BCrypt cost 10). Hanya untuk lokal & runner CI.
CREATE TABLE IF NOT EXISTS dev_pengguna (
    id            bigint  PRIMARY KEY,
    sekolah_id    bigint  NOT NULL,
    username      text    NOT NULL UNIQUE,
    nama          text    NOT NULL,
    role          text    NOT NULL,
    password_hash text    NOT NULL,
    aktif         boolean NOT NULL DEFAULT true
);

INSERT INTO dev_pengguna (id, sekolah_id, username, nama, role, password_hash, aktif)
VALUES (9001, 1, 'loadtest', 'Load Test (Petugas POS)', 'PETUGAS',
        '$2a$10$O27Z3A5gSOPV1G0JvD/S4eoqPyFr7.ZKpcMFgwM2PI/WT3qz/NTVS', true)
ON CONFLICT (username) DO UPDATE
    SET password_hash = EXCLUDED.password_hash,
        role          = EXCLUDED.role,
        sekolah_id    = EXCLUDED.sekolah_id,
        aktif         = true;

-- ── Ringkas ──
DO $$
BEGIN
    RAISE NOTICE 'Seed uji beban siap: 1 sesi kasir terbuka, 1 menu, 20 Kartu Tamu (UID 04000001..04000020), stok & saldo besar, akun loadtest/loadtest.';
END $$;
