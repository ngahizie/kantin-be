-- ============================================================
-- R__seed_dev.sql — Data dev/dummy untuk FE & demo
-- ------------------------------------------------------------
-- Tujuan: satu dataset yang MENUTUP SEMUA LAYAR FE — katalog,
-- kasir, riwayat transaksi, koreksi saldo, stok, laporan,
-- setoran TU, dan pengaturan — tanpa mock.
--
-- ⚠️ BUKAN data produksi.
--    Flyway REPEATABLE migration: hanya dijalankan bila lokasi `db/seed`
--    ikut didaftarkan — lihat `spring.flyway.locations` di
--    application-local.properties.example. Default TIDAK aktif, jadi
--    CI dan seluruh test (@SpringBootTest) tidak pernah memuatnya.
--    Dijalankan manual bila perlu:
--      psql "$DB_URL" -f src/main/resources/db/seed/R__seed_dev.sql
--
-- Idempoten: semua INSERT memakai ON CONFLICT DO NOTHING, jadi aman
-- dijalankan berulang dan TIDAK menimpa data yang sudah diubah aplikasi.
--
-- Deterministik: ID tetap, tanggal relatif ke CURRENT_DATE (zona sekolah),
-- tak ada random. Screenshot & test FE tidak berubah antar-run.
--
-- Konvensi ID pengguna (aktor_id / petugas_id / dibuat_oleh):
--   1 = Kepala Sekolah   2 = TU/Bendahara
--   3 = Pengelola Kantin 4 = Petugas Kasir
--
-- Sekolah: 1 = SMA Negeri 1 SKOOLIA (default shim login),
--          2 = SMA Negeri 2 SKOOLIA (uji isolasi tenant).
-- ============================================================

-- ── Konfigurasi kantin ───────────────────────────────────────
INSERT INTO sekolah_kantin_config (sekolah_id, nama_kantin, jam_tutup_otomatis,
                                   konfirmasi_manual, durasi_foto_detik,
                                   min_topup, maks_topup,
                                   batas_saldo_siswa, batas_saldo_kartu_tamu,
                                   diperbarui_oleh, created_at, updated_at)
VALUES (1, 'Kantin Sekolah', '15:00', false, 3, 5000, 500000, 500000, 1000000, 2, now(), now()),
       (2, 'Kantin SMA 2',   '15:00', false, 3, 5000, 500000, 500000, 1000000, 2, now(), now())
ON CONFLICT (sekolah_id) DO NOTHING;

-- ── Kebijakan saldo mengendap (PRD §8) ───────────────────────
INSERT INTO kebijakan_kantin (sekolah_id, kebijakan_saldo_mengendap, ambang_hari,
                              catatan, diperbarui_oleh, updated_at)
VALUES (1, 'REFUND', 90, 'Saldo mengendap > 90 hari dikembalikan ke wali murid', 2, now())
ON CONFLICT (sekolah_id) DO NOTHING;

-- ── Titik kasir ──────────────────────────────────────────────
INSERT INTO titik_kasir (id, sekolah_id, nama, kode, is_active, created_at, updated_at)
VALUES (1, 1, 'Kasir Kantin Utama', 'DEMO1', true, now(), now()),
       (2, 1, 'Kasir Kelas 7',      'DEMO2', true, now(), now()),
       (3, 2, 'Kasir SMA 2',        'S2-01', true, now(), now())
ON CONFLICT DO NOTHING;

-- ── Kategori menu ────────────────────────────────────────────
INSERT INTO kategori_menu (id, sekolah_id, nama, urutan, is_active, created_at, updated_at)
VALUES (1, 1, 'Makanan',    1, true, now(), now()),
       (2, 1, 'Minuman',    2, true, now(), now()),
       (3, 1, 'Snack',      3, true, now(), now()),
       (4, 1, 'Alat Tulis', 4, true, now(), now()),
       (10, 2, 'Makanan',   1, true, now(), now())
ON CONFLICT DO NOTHING;

-- ── Stok & HPP ───────────────────────────────────────────────
-- PENTING: diisi SEBELUM menu. Trigger trg_menu_sinkron_stok_minimum
-- membuat baris stok_cache (stok=0) saat menu di-insert; bila baris sudah
-- ada, trigger hanya menyinkronkan stok_minimum tanpa menyentuh stok/hpp.
-- Stok awal 50; menu yang terjual sudah dikurangi (lihat mutasi_stok).
INSERT INTO stok_cache (menu_id, sekolah_id, stok, hpp, stok_minimum, updated_at)
VALUES (1,  1, 49, 7000,  5,  now()),
       (2,  1, 49, 6000,  5,  now()),
       (3,  1, 47, 1000,  10, now()),
       (4,  1, 48, 2000,  10, now()),
       (5,  1, 49, 9000,  5,  now()),
       (6,  1, 50, 3000,  10, now()),   -- terjual lalu di-void → kembali 50
       (7,  1, 48, 5000,  5,  now()),
       (8,  1, 50, 4000,  10, now()),
       (9,  1, 50, 2000,  20, now()),
       (10, 1, 50, 5000,  10, now()),
       (11, 1, 49, 7000,  5,  now()),
       (12, 1, 50, 5000,  5,  now()),
       (101, 2, 39, 8000, 5,  now())
ON CONFLICT (menu_id) DO NOTHING;

-- ── Menu (harga rupiah integer; satuan PCS|PORSI|BOTOL) ──────
INSERT INTO menu (id, sekolah_id, kategori_id, nama, harga_jual, satuan,
                  stok_minimum, is_active, created_at, updated_at)
VALUES (1,  1, 1, 'Nasi Goreng',   12000, 'PORSI', 5,  true, now(), now()),
       (2,  1, 1, 'Mie Ayam',      10000, 'PORSI', 5,  true, now(), now()),
       (3,  1, 2, 'Es Teh Manis',   3000, 'PCS',   10, true, now(), now()),
       (4,  1, 2, 'Air Mineral',    4000, 'BOTOL', 10, true, now(), now()),
       (5,  1, 1, 'Ayam Geprek',   13000, 'PORSI', 5,  true, now(), now()),
       (6,  1, 3, 'Keripik',        5000, 'PCS',   10, true, now(), now()),
       (7,  1, 3, 'Roti Bakar',     8000, 'PCS',   5,  true, now(), now()),
       (8,  1, 2, 'Susu Kotak',     6000, 'PCS',   10, true, now(), now()),
       (9,  1, 4, 'Pulpen',         3000, 'PCS',   20, true, now(), now()),
       (10, 1, 4, 'Buku Tulis',     7000, 'PCS',   10, true, now(), now()),
       (11, 1, 1, 'Bakso',         11000, 'PORSI', 5,  true, now(), now()),
       (12, 1, 2, 'Jus Jeruk',      7000, 'PCS',   5,  true, now(), now()),
       (101, 2, 10, 'Nasi Uduk',   12000, 'PORSI', 5,  true, now(), now())
ON CONFLICT DO NOTHING;

-- ── Kartu Tamu (UID heksa 8 karakter; DE000001..5 dipakai docs demo) ──
INSERT INTO kartu_tamu (id, sekolah_id, nomor_kartu, rfid_uid, aktif,
                        label_pemegang, dibuat_oleh, dibuat_pada)
VALUES (1,  1, 'KT-DEMO-001', 'DE000001', true, 'Budi (Guru)',    2, now()),
       (2,  1, 'KT-DEMO-002', 'DE000002', true, 'Sari (Staf TU)', 2, now()),
       (3,  1, 'KT-DEMO-003', 'DE000003', true, 'Tamu Umum',      2, now()),
       (4,  1, 'KT-DEMO-004', 'DE000004', true, 'Rina (Guru)',    2, now()),
       (5,  1, 'KT-DEMO-005', 'DE000005', true, 'Tamu Umum',      2, now()),
       (6,  1, 'KT-DEMO-006', 'DE000006', true, 'Pak Andi (Security)', 2, now()),
       (7,  1, 'KT-DEMO-007', 'DE000007', true, 'Bu Wati (Kantin)',    2, now()),
       (8,  1, 'KT-DEMO-008', 'DE000008', true, 'Tamu Rapat',     2, now()),
       (9,  1, 'KT-DEMO-009', 'DE000009', true, null,              2, now()),
       (10, 1, 'KT-DEMO-010', 'DE000010', true, 'Kurir Paket',    2, now()),
       (11, 2, 'KT-S2-001',   'S2000001', true, 'Tamu SMA 2',     2, now())
ON CONFLICT DO NOTHING;

-- ── Sesi kasir: 1 TERBUKA hari ini + riwayat DITUTUP 2 hari ──
INSERT INTO sesi_kasir (id, sekolah_id, titik_kasir_id, tanggal, status,
                        total_bruto, total_void, total_bersih, dibuka_at,
                        ditutup_at, ditutup_oleh, auto_tutup,
                        posting_buku_kas, created_at, updated_at)
VALUES (1, 1, 1, CURRENT_DATE, 'TERBUKA', 36000, 0, 36000,
        ((CURRENT_DATE) + time '07:00') AT TIME ZONE 'Asia/Jakarta',
        NULL, NULL, false, false, now(), now()),
       (2, 1, 1, CURRENT_DATE - 1, 'DITUTUP', 21000, 5000, 16000,
        ((CURRENT_DATE - 1) + time '07:00') AT TIME ZONE 'Asia/Jakarta',
        ((CURRENT_DATE - 1) + time '14:30') AT TIME ZONE 'Asia/Jakarta', 4, false, true, now(), now()),
       (3, 1, 2, CURRENT_DATE - 1, 'DITUTUP', 16000, 0, 16000,
        ((CURRENT_DATE - 1) + time '07:15') AT TIME ZONE 'Asia/Jakarta',
        ((CURRENT_DATE - 1) + time '12:00') AT TIME ZONE 'Asia/Jakarta', 4, false, false, now(), now()),
       (4, 1, 1, CURRENT_DATE - 2, 'DITUTUP', 11000, 0, 11000,
        ((CURRENT_DATE - 2) + time '07:00') AT TIME ZONE 'Asia/Jakarta',
        ((CURRENT_DATE - 2) + time '13:45') AT TIME ZONE 'Asia/Jakarta', 4, true, true, now(), now()),
       (10, 2, 3, CURRENT_DATE, 'TERBUKA', 12000, 0, 12000,
        ((CURRENT_DATE) + time '07:30') AT TIME ZONE 'Asia/Jakarta',
        NULL, NULL, false, false, now(), now())
ON CONFLICT DO NOTHING;

-- ── Transaksi (7 di sekolah 1, 1 di sekolah 2) ───────────────
INSERT INTO transaksi (id, idempotency_key, sekolah_id, sesi_kasir_id, titik_kasir_id,
                       subjek_tipe, subjek_id, kartu_uid, petugas_id,
                       total, total_hpp, status, alasan_void, void_at, void_oleh,
                       waktu, created_at, updated_at)
VALUES
  -- hari ini, sesi 1 (TERBUKA) — bruto 36.000
  (900101, 'seed-dev-t1', 1, 1, 1, 'KARTU_TAMU', 1, 'DE000001', 4, 15000, 8000, 'SUKSES',
   NULL, NULL, NULL, ((CURRENT_DATE) + time '09:15') AT TIME ZONE 'Asia/Jakarta', now(), now()),
  (900102, 'seed-dev-t2', 1, 1, 1, 'KARTU_TAMU', 3, 'DE000003', 4, 13000, 9000, 'SUKSES',
   NULL, NULL, NULL, ((CURRENT_DATE) + time '10:05') AT TIME ZONE 'Asia/Jakarta', now(), now()),
  (900103, 'seed-dev-t3', 1, 1, 1, 'KARTU_TAMU', 2, 'DE000002', 4, 8000, 4000, 'SUKSES',
   NULL, NULL, NULL, ((CURRENT_DATE) + time '11:40') AT TIME ZONE 'Asia/Jakarta', now(), now()),
  -- kemarin, sesi 2 — bruto 21.000 / void 5.000
  (900104, 'seed-dev-t4', 1, 2, 1, 'KARTU_TAMU', 4, 'DE000004', 4, 16000, 8000, 'SUKSES',
   NULL, NULL, NULL, ((CURRENT_DATE - 1) + time '09:30') AT TIME ZONE 'Asia/Jakarta', now(), now()),
  (900105, 'seed-dev-t5', 1, 2, 1, 'KARTU_TAMU', 5, 'DE000005', 4, 5000, 3000, 'VOID',
   'Salah input item', ((CURRENT_DATE - 1) + time '11:10') AT TIME ZONE 'Asia/Jakarta', 2,
   ((CURRENT_DATE - 1) + time '10:20') AT TIME ZONE 'Asia/Jakarta', now(), now()),
  -- kemarin, sesi 3 (titik 2) — bruto 16.000
  (900106, 'seed-dev-t6', 1, 3, 2, 'KARTU_TAMU', 6, 'DE000006', 4, 16000, 10000, 'SUKSES',
   NULL, NULL, NULL, ((CURRENT_DATE - 1) + time '10:45') AT TIME ZONE 'Asia/Jakarta', now(), now()),
  -- dua hari lalu, sesi 4 — bruto 11.000
  (900107, 'seed-dev-t7', 1, 4, 1, 'KARTU_TAMU', 7, 'DE000007', 4, 11000, 7000, 'SUKSES',
   NULL, NULL, NULL, ((CURRENT_DATE - 2) + time '09:50') AT TIME ZONE 'Asia/Jakarta', now(), now()),
  -- sekolah 2
  (900110, 'seed-dev-s2t1', 2, 10, 3, 'KARTU_TAMU', 11, 'S2000001', 4, 12000, 8000, 'SUKSES',
   NULL, NULL, NULL, ((CURRENT_DATE) + time '08:20') AT TIME ZONE 'Asia/Jakarta', now(), now())
ON CONFLICT DO NOTHING;

-- ── Item transaksi (subtotal = harga_jual × qty) ─────────────
INSERT INTO transaksi_item (id, transaksi_id, menu_id, nama_menu, kategori_id,
                            harga_jual, qty, hpp_snapshot, subtotal, created_at)
VALUES
  (900201, 900101, 1,  'Nasi Goreng',  1, 12000, 1, 7000, 12000, now()),
  (900202, 900101, 3,  'Es Teh Manis', 2, 3000,  1, 1000, 3000,  now()),
  (900203, 900102, 5,  'Ayam Geprek',  1, 13000, 1, 9000, 13000, now()),
  (900204, 900103, 4,  'Air Mineral',  2, 4000,  2, 2000, 8000,  now()),
  (900205, 900104, 2,  'Mie Ayam',     1, 10000, 1, 6000, 10000, now()),
  (900206, 900104, 3,  'Es Teh Manis', 2, 3000,  2, 1000, 6000,  now()),
  (900207, 900105, 6,  'Keripik',      3, 5000,  1, 3000, 5000,  now()),
  (900208, 900106, 7,  'Roti Bakar',   3, 8000,  2, 5000, 16000, now()),
  (900209, 900107, 11, 'Bakso',        1, 11000, 1, 7000, 11000, now()),
  (900210, 900110, 101, 'Nasi Uduk',   10, 12000, 1, 8000, 12000, now())
ON CONFLICT DO NOTHING;

-- ── Mutasi stok: MASUK awal + KELUAR penjualan + PEMBALIK void ──
-- stok_cache di atas HARUS = stok_setelah mutasi terakhir per menu.
INSERT INTO mutasi_stok (id, sekolah_id, menu_id, arah, jenis, qty, stok_setelah,
                         hpp_snapshot, transaksi_id, referensi_tipe, referensi_id,
                         alasan, aktor_id, aktor_nama, waktu, created_at,
                         harga_beli_satuan)
VALUES
  -- stok awal (7 hari lalu)
  (900301, 1, 1,  'MASUK', 'BARANG_MASUK', 50, 50, 7000,  NULL, 'BARANG_MASUK', 'BM-001', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 7000),
  (900302, 1, 2,  'MASUK', 'BARANG_MASUK', 50, 50, 6000,  NULL, 'BARANG_MASUK', 'BM-001', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 6000),
  (900303, 1, 3,  'MASUK', 'BARANG_MASUK', 50, 50, 1000,  NULL, 'BARANG_MASUK', 'BM-001', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 1000),
  (900304, 1, 4,  'MASUK', 'BARANG_MASUK', 50, 50, 2000,  NULL, 'BARANG_MASUK', 'BM-001', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 2000),
  (900305, 1, 5,  'MASUK', 'BARANG_MASUK', 50, 50, 9000,  NULL, 'BARANG_MASUK', 'BM-001', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 9000),
  (900306, 1, 6,  'MASUK', 'BARANG_MASUK', 50, 50, 3000,  NULL, 'BARANG_MASUK', 'BM-001', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 3000),
  (900307, 1, 7,  'MASUK', 'BARANG_MASUK', 50, 50, 5000,  NULL, 'BARANG_MASUK', 'BM-001', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 5000),
  (900308, 1, 8,  'MASUK', 'BARANG_MASUK', 50, 50, 4000,  NULL, 'BARANG_MASUK', 'BM-001', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 4000),
  (900309, 1, 9,  'MASUK', 'BARANG_MASUK', 50, 50, 2000,  NULL, 'BARANG_MASUK', 'BM-001', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 2000),
  (900310, 1, 10, 'MASUK', 'BARANG_MASUK', 50, 50, 5000,  NULL, 'BARANG_MASUK', 'BM-001', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 5000),
  (900311, 1, 11, 'MASUK', 'BARANG_MASUK', 50, 50, 7000,  NULL, 'BARANG_MASUK', 'BM-001', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 7000),
  (900312, 1, 12, 'MASUK', 'BARANG_MASUK', 50, 50, 5000,  NULL, 'BARANG_MASUK', 'BM-001', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 5000),
  (900320, 2, 101, 'MASUK', 'BARANG_MASUK', 40, 40, 8000, NULL, 'BARANG_MASUK', 'BM-002', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 8000),
  -- penjualan (stok_setelah menurun sesuai urutan waktu)
  (900331, 1, 7,  'KELUAR', 'PENJUALAN', 2, 48, 5000, 900106, 'TRANSAKSI', '900106', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE - 1) + time '10:45') AT TIME ZONE 'Asia/Jakarta', now(), NULL),
  (900332, 1, 4,  'KELUAR', 'PENJUALAN', 2, 48, 2000, 900103, 'TRANSAKSI', '900103', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE) + time '11:40') AT TIME ZONE 'Asia/Jakarta', now(), NULL),
  (900333, 1, 3,  'KELUAR', 'PENJUALAN', 2, 49, 1000, 900104, 'TRANSAKSI', '900104', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE - 1) + time '09:30') AT TIME ZONE 'Asia/Jakarta', now(), NULL),
  (900334, 1, 2,  'KELUAR', 'PENJUALAN', 1, 49, 6000, 900104, 'TRANSAKSI', '900104', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE - 1) + time '09:30') AT TIME ZONE 'Asia/Jakarta', now(), NULL),
  (900335, 1, 3,  'KELUAR', 'PENJUALAN', 1, 47, 1000, 900101, 'TRANSAKSI', '900101', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE) + time '09:15') AT TIME ZONE 'Asia/Jakarta', now(), NULL),
  (900336, 1, 1,  'KELUAR', 'PENJUALAN', 1, 49, 7000, 900101, 'TRANSAKSI', '900101', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE) + time '09:15') AT TIME ZONE 'Asia/Jakarta', now(), NULL),
  (900337, 1, 5,  'KELUAR', 'PENJUALAN', 1, 49, 9000, 900102, 'TRANSAKSI', '900102', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE) + time '10:05') AT TIME ZONE 'Asia/Jakarta', now(), NULL),
  (900338, 1, 11, 'KELUAR', 'PENJUALAN', 1, 49, 7000, 900107, 'TRANSAKSI', '900107', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE - 2) + time '09:50') AT TIME ZONE 'Asia/Jakarta', now(), NULL),
  (900339, 2, 101, 'KELUAR', 'PENJUALAN', 1, 39, 8000, 900110, 'TRANSAKSI', '900110', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE) + time '08:20') AT TIME ZONE 'Asia/Jakarta', now(), NULL),
  -- void transaksi 900105 → stok keripik kembali
  (900340, 1, 6,  'KELUAR', 'PENJUALAN', 1, 49, 3000, 900105, 'TRANSAKSI', '900105', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE - 1) + time '10:20') AT TIME ZONE 'Asia/Jakarta', now(), NULL),
  (900341, 1, 6,  'MASUK', 'VOID_PENJUALAN', 1, 50, 3000, 900105, 'TRANSAKSI', '900105', 'Pembalik void', 2, 'Bendahara', ((CURRENT_DATE - 1) + time '11:10') AT TIME ZONE 'Asia/Jakarta', now(), NULL)
ON CONFLICT DO NOTHING;

-- ── Ledger saldo (append-only; sumber kebenaran) ─────────────
-- saldo_cache di bawah HARUS = saldo_setelah mutasi terakhir per subjek.
INSERT INTO saldo_ledger (id, sekolah_id, subjek_tipe, subjek_id, arah, jenis,
                          nominal, saldo_setelah, transaksi_id, idempotency_key,
                          referensi_tipe, referensi_id, keterangan, aktor_id, waktu, created_at)
VALUES
  -- Kartu 1: topup 100.000 → belanja 15.000 = 85.000
  (900001, 1, 'KARTU_TAMU', 1, 'KREDIT', 'TOPUP_TUNAI', 100000, 100000, NULL, 'seed-dev-kt1-topup',    'TOPUP',     'TOPUP-001', 'Top up tunai di TU', 2, ((CURRENT_DATE - 3) + time '08:30') AT TIME ZONE 'Asia/Jakarta', now()),
  (900002, 1, 'KARTU_TAMU', 1, 'DEBIT',  'PENJUALAN',    15000,  85000, 900101, 'seed-dev-kt1-trx900101', 'TRANSAKSI', '900101',    NULL, 4, ((CURRENT_DATE) + time '09:15') AT TIME ZONE 'Asia/Jakarta', now()),
  -- Kartu 2: topup 100.000 → belanja 8.000 = 92.000
  (900003, 1, 'KARTU_TAMU', 2, 'KREDIT', 'TOPUP_TUNAI', 100000, 100000, NULL, 'seed-dev-kt2-topup',    'TOPUP',     'TOPUP-002', 'Top up tunai di TU', 2, ((CURRENT_DATE - 3) + time '08:35') AT TIME ZONE 'Asia/Jakarta', now()),
  (900004, 1, 'KARTU_TAMU', 2, 'DEBIT',  'PENJUALAN',     8000,  92000, 900103, 'seed-dev-kt2-trx900103', 'TRANSAKSI', '900103',    NULL, 4, ((CURRENT_DATE) + time '11:40') AT TIME ZONE 'Asia/Jakarta', now()),
  -- Kartu 3: topup 100.000 → belanja 13.000 → KOREKSI +5.000 = 92.000
  (900005, 1, 'KARTU_TAMU', 3, 'KREDIT', 'TOPUP_TUNAI', 100000, 100000, NULL, 'seed-dev-kt3-topup',    'TOPUP',     'TOPUP-003', 'Top up tunai di TU', 2, ((CURRENT_DATE - 3) + time '08:40') AT TIME ZONE 'Asia/Jakarta', now()),
  (900006, 1, 'KARTU_TAMU', 3, 'DEBIT',  'PENJUALAN',    13000,  87000, 900102, 'seed-dev-kt3-trx900102', 'TRANSAKSI', '900102',    NULL, 4, ((CURRENT_DATE) + time '10:05') AT TIME ZONE 'Asia/Jakarta', now()),
  (900007, 1, 'KARTU_TAMU', 3, 'KREDIT', 'KOREKSI',       5000,  92000, NULL, 'seed-dev-kt3-koreksi',  'KOREKSI',   'BA-001',    'Koreksi selisih setoran topup', 2, ((CURRENT_DATE) + time '12:30') AT TIME ZONE 'Asia/Jakarta', now()),
  -- Kartu 4: topup 100.000 → belanja 16.000 = 84.000
  (900008, 1, 'KARTU_TAMU', 4, 'KREDIT', 'TOPUP_TUNAI', 100000, 100000, NULL, 'seed-dev-kt4-topup',    'TOPUP',     'TOPUP-004', 'Top up tunai di TU', 2, ((CURRENT_DATE - 3) + time '08:45') AT TIME ZONE 'Asia/Jakarta', now()),
  (900009, 1, 'KARTU_TAMU', 4, 'DEBIT',  'PENJUALAN',    16000,  84000, 900104, 'seed-dev-kt4-trx900104', 'TRANSAKSI', '900104',    NULL, 4, ((CURRENT_DATE - 1) + time '09:30') AT TIME ZONE 'Asia/Jakarta', now()),
  -- Kartu 5: topup 50.000 → belanja 5.000 (VOID, kembali) → KOREKSI −2.000 = 48.000
  (900010, 1, 'KARTU_TAMU', 5, 'KREDIT', 'TOPUP_TUNAI',   50000,  50000, NULL, 'seed-dev-kt5-topup',    'TOPUP',     'TOPUP-005', 'Top up tunai di TU', 2, ((CURRENT_DATE - 3) + time '08:50') AT TIME ZONE 'Asia/Jakarta', now()),
  (900011, 1, 'KARTU_TAMU', 5, 'DEBIT',  'PENJUALAN',      5000,  45000, 900105, 'seed-dev-kt5-trx900105', 'TRANSAKSI', '900105',    NULL, 4, ((CURRENT_DATE - 1) + time '10:20') AT TIME ZONE 'Asia/Jakarta', now()),
  (900012, 1, 'KARTU_TAMU', 5, 'KREDIT', 'VOID_PENJUALAN', 5000,  50000, 900105, 'seed-dev-kt5-void',     'TRANSAKSI', '900105',    'Pembalik void transaksi', 2, ((CURRENT_DATE - 1) + time '11:10') AT TIME ZONE 'Asia/Jakarta', now()),
  (900013, 1, 'KARTU_TAMU', 5, 'DEBIT',  'KOREKSI',        2000,  48000, NULL, 'seed-dev-kt5-koreksi',   'KOREKSI',   'BA-002',    'Koreksi salah input nominal', 2, ((CURRENT_DATE - 1) + time '13:00') AT TIME ZONE 'Asia/Jakarta', now()),
  -- Kartu 6: topup 100.000 → belanja 16.000 = 84.000
  (900014, 1, 'KARTU_TAMU', 6, 'KREDIT', 'TOPUP_TUNAI', 100000, 100000, NULL, 'seed-dev-kt6-topup',    'TOPUP',     'TOPUP-006', 'Top up tunai di TU', 2, ((CURRENT_DATE - 3) + time '08:55') AT TIME ZONE 'Asia/Jakarta', now()),
  (900015, 1, 'KARTU_TAMU', 6, 'DEBIT',  'PENJUALAN',    16000,  84000, 900106, 'seed-dev-kt6-trx900106', 'TRANSAKSI', '900106',    NULL, 4, ((CURRENT_DATE - 1) + time '10:45') AT TIME ZONE 'Asia/Jakarta', now()),
  -- Kartu 7: topup 100.000 → belanja 11.000 = 89.000
  (900016, 1, 'KARTU_TAMU', 7, 'KREDIT', 'TOPUP_TUNAI', 100000, 100000, NULL, 'seed-dev-kt7-topup',    'TOPUP',     'TOPUP-007', 'Top up tunai di TU', 2, ((CURRENT_DATE - 3) + time '09:00') AT TIME ZONE 'Asia/Jakarta', now()),
  (900017, 1, 'KARTU_TAMU', 7, 'DEBIT',  'PENJUALAN',    11000,  89000, 900107, 'seed-dev-kt7-trx900107', 'TRANSAKSI', '900107',    NULL, 4, ((CURRENT_DATE - 2) + time '09:50') AT TIME ZONE 'Asia/Jakarta', now()),
  -- Kartu 8: saldo kecil (uji tampilan saldo rendah)
  (900018, 1, 'KARTU_TAMU', 8, 'KREDIT', 'TOPUP_ONLINE',  25000,  25000, NULL, 'seed-dev-kt8-topup',    'TOPUP',     'TOPUP-008', 'Top up online via wali murid', 2, ((CURRENT_DATE - 2) + time '14:00') AT TIME ZONE 'Asia/Jakarta', now()),
  -- Kartu 10: topup 75.000 → KOREKSI −5.000 = 70.000
  (900019, 1, 'KARTU_TAMU', 10, 'KREDIT', 'TOPUP_TUNAI', 75000,  75000, NULL, 'seed-dev-kt10-topup',   'TOPUP',     'TOPUP-010', 'Top up tunai di TU', 2, ((CURRENT_DATE - 2) + time '08:15') AT TIME ZONE 'Asia/Jakarta', now()),
  (900020, 1, 'KARTU_TAMU', 10, 'DEBIT',  'KOREKSI',      5000,  70000, NULL, 'seed-dev-kt10-koreksi', 'KOREKSI',   'BA-003',    'Koreksi kelebihan topup', 2, ((CURRENT_DATE - 1) + time '15:10') AT TIME ZONE 'Asia/Jakarta', now()),
  -- Sekolah 2: kartu 11 topup 75.000 → belanja 12.000 = 63.000
  (900030, 2, 'KARTU_TAMU', 11, 'KREDIT', 'TOPUP_TUNAI', 75000, 75000, NULL, 'seed-dev-s2kt1-topup',   'TOPUP',     'S2-TOPUP-001', 'Top up tunai di TU', 2, ((CURRENT_DATE - 1) + time '08:00') AT TIME ZONE 'Asia/Jakarta', now()),
  (900031, 2, 'KARTU_TAMU', 11, 'DEBIT',  'PENJUALAN',    12000, 63000, 900110, 'seed-dev-s2kt1-trx',    'TRANSAKSI', '900110',    NULL, 4, ((CURRENT_DATE) + time '08:20') AT TIME ZONE 'Asia/Jakarta', now())
ON CONFLICT DO NOTHING;

-- ── Saldo cache (= saldo_setelah terakhir per subjek) ────────
INSERT INTO saldo_cache (subjek_tipe, subjek_id, sekolah_id, saldo, updated_at)
VALUES ('KARTU_TAMU', 1, 1, 85000, now()),
       ('KARTU_TAMU', 2, 1, 92000, now()),
       ('KARTU_TAMU', 3, 1, 92000, now()),
       ('KARTU_TAMU', 4, 1, 84000, now()),
       ('KARTU_TAMU', 5, 1, 48000, now()),
       ('KARTU_TAMU', 6, 1, 84000, now()),
       ('KARTU_TAMU', 7, 1, 89000, now()),
       ('KARTU_TAMU', 8, 1, 25000, now()),
       ('KARTU_TAMU', 9, 1, 0,     now()),
       ('KARTU_TAMU', 10, 1, 70000, now()),
       ('KARTU_TAMU', 11, 2, 63000, now())
ON CONFLICT DO NOTHING;

-- ── Pengaturan per kartu (layar "Kontrol Kartu") ─────────────
INSERT INTO limit_harian (id, sekolah_id, subjek_tipe, subjek_id, nominal,
                          diubah_oleh, diubah_pada, created_at)
VALUES (1, 1, 'KARTU_TAMU', 1, 50000, 2, now(), now()),
       (2, 1, 'KARTU_TAMU', 8, 25000, 2, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO blokir_item (id, sekolah_id, subjek_tipe, subjek_id, menu_id, kategori_id,
                         diblokir, diubah_oleh, diubah_pada, created_at)
VALUES (1, 1, 'KARTU_TAMU', 8, 6, NULL, true, 2, now(), now()),
       (2, 1, 'KARTU_TAMU', 8, NULL, 2, true, 2, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO blokir_kartu (id, sekolah_id, subjek_tipe, subjek_id, diblokir, alasan,
                          diubah_oleh, diubah_pada, created_at)
VALUES (1, 1, 'KARTU_TAMU', 10, true, 'Kartu dilaporkan hilang', 2, now(), now())
ON CONFLICT DO NOTHING;

-- ── Setoran TU (rekap topup tunai) ───────────────────────────
INSERT INTO setoran_tu (id, sekolah_id, tanggal, petugas_id, total_topup,
                        jumlah_disetor, referensi_id, catatan,
                        dikonfirmasi_oleh, waktu, created_at)
VALUES (1, 1, CURRENT_DATE - 1, 2, 725000, 725000, 'SETORAN-001',
        'Setoran topup tunai kemarin', 1,
        ((CURRENT_DATE - 1) + time '15:30') AT TIME ZONE 'Asia/Jakarta', now())
ON CONFLICT DO NOTHING;

-- ── Buku kas ─────────────────────────────────────────────────
INSERT INTO pos_buku_kas (id, sekolah_id, nama, tipe, keterangan, aktif, created_at)
VALUES (1, 1, 'Penjualan Kantin', 'MASUK',  'Pos utama penjualan harian', true, now()),
       (2, 1, 'Belanja Bahan',    'KELUAR', 'Pembelian bahan baku',       true, now())
ON CONFLICT DO NOTHING;

INSERT INTO posting_buku_kas (id, sekolah_id, referensi_id, entitas, mutasi_id,
                              ref_buku_kas, jumlah, status, waktu, created_at)
VALUES (1, 1, '900104', 'SESI_KASIR', NULL, 'BK-2026-001', 16000, 'SUKSES',
        ((CURRENT_DATE - 1) + time '14:35') AT TIME ZONE 'Asia/Jakarta', now()),
       (2, 1, '900106', 'SESI_KASIR', NULL, 'BK-2026-002', 16000, 'SUKSES',
        ((CURRENT_DATE - 1) + time '12:05') AT TIME ZONE 'Asia/Jakarta', now())
ON CONFLICT DO NOTHING;

-- ── Transaksi menunggu konfirmasi (sekolah dengan konfirmasi manual) ──
INSERT INTO transaksi_menunggu_konfirmasi (id, sekolah_id, idempotency_key, rfid_uid,
                                           titik_kasir_id, subjek_tipe, subjek_id,
                                           petugas_id, total, items_json, status,
                                           dibuat_at, kedaluwarsa_at, created_at, updated_at)
VALUES (1, 2, 'seed-dev-pending-1', 'S2000001', 3, 'KARTU_TAMU', 11, 4, 12000,
        '[{"menuId":101,"qty":1}]', 'MENUNGGU',
        ((CURRENT_DATE) + time '12:10') AT TIME ZONE 'Asia/Jakarta',
        ((CURRENT_DATE) + time '12:25') AT TIME ZONE 'Asia/Jakarta', now(), now())
ON CONFLICT DO NOTHING;

-- ── Insiden offline (laporan monitoring) ─────────────────────
INSERT INTO insiden_offline (id, sekolah_id, titik_kasir_id, mulai, selesai,
                             durasi_menit, keterangan, dilaporkan_oleh, created_at)
VALUES (1, 1, 2,
        ((CURRENT_DATE - 2) + time '09:00') AT TIME ZONE 'Asia/Jakarta',
        ((CURRENT_DATE - 2) + time '09:12') AT TIME ZONE 'Asia/Jakarta',
        12, 'Koneksi internet terputus', 4, now())
ON CONFLICT DO NOTHING;

-- ── Ringkas ──────────────────────────────────────────────────
-- Isi seed: 2 sekolah | 13 menu | 11 kartu tamu | 4 sesi kasir |
-- 8 transaksi | 22 mutasi saldo | 13 baris stok | pengaturan kantin.
-- (Tanpa blok DO $$ agar berkas ini aman dijalankan psql, Flyway, maupun
--  ResourceDatabasePopulator yang memecah pernyataan pada titik koma.)
