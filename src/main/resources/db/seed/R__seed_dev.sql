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
-- Sekolah: 1  = SMA Negeri 1 SKOOLIA (default shim login),
--          2  = SMA Negeri 2 SKOOLIA (uji isolasi tenant),
--          10 = tenant default form login FE (kantin-fe mengirim sekolah_id=10).
--               Tenant ini WAJIB berisi data, kalau tidak semua layar FE tampak
--               kosong saat QA lokal walau endpoint sudah 200 (issue #231).
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
  (900333, 1, 3,  'KELUAR', 'PENJUALAN', 2, 48, 1000, 900104, 'TRANSAKSI', '900104', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE - 1) + time '09:30') AT TIME ZONE 'Asia/Jakarta', now(), NULL),
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


-- ── Sekolah 10: tenant default form login FE ─────────────────
-- Form login kantin-fe mengirim `sekolah_id: 10` secara hardcoded
-- (src/features/auth/sign-in/components/user-auth-form.tsx), jadi tenant ini
-- WAJIB berisi data agar layar katalog/laporan tidak tampak kosong saat QA lokal.
--
-- Semua ID memakai rentang 1001+ supaya tidak bentrok dengan data yang dibuat
-- aplikasi (id snowflake). Kategori di-lookup lewat nama, bukan ID tetap, karena
-- tenant 10 bisa saja sudah punya kategori buatan aplikasi (mis. "Snack").

INSERT INTO sekolah_kantin_config (sekolah_id, nama_kantin, jam_tutup_otomatis,
                                   konfirmasi_manual, durasi_foto_detik,
                                   min_topup, maks_topup,
                                   batas_saldo_siswa, batas_saldo_kartu_tamu,
                                   diperbarui_oleh, created_at, updated_at)
VALUES (10, 'Kantin Sekolah 10', '15:00', false, 3, 5000, 500000, 500000, 1000000, 2, now(), now())
ON CONFLICT (sekolah_id) DO NOTHING;

INSERT INTO kebijakan_kantin (sekolah_id, kebijakan_saldo_mengendap, ambang_hari,
                              catatan, diperbarui_oleh, updated_at)
VALUES (10, 'REFUND', 90, 'Saldo mengendap > 90 hari dikembalikan ke wali murid', 2, now())
ON CONFLICT (sekolah_id) DO NOTHING;

INSERT INTO titik_kasir (id, sekolah_id, nama, kode, is_active, created_at, updated_at)
VALUES (1001, 10, 'Kasir Kantin Utama', 'FE10-1', true, now(), now()),
       (1002, 10, 'Kasir Kelas 7',      'FE10-2', true, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO kategori_menu (id, sekolah_id, nama, urutan, is_active, created_at, updated_at)
VALUES (1001, 10, 'Makanan',    1, true, now(), now()),
       (1002, 10, 'Minuman',    2, true, now(), now()),
       (1003, 10, 'Snack',      3, true, now(), now()),
       (1004, 10, 'Alat Tulis', 4, true, now(), now())
ON CONFLICT DO NOTHING;

-- Stok diisi SEBELUM menu (trigger hanya menyinkronkan stok_minimum bila baris ada).
INSERT INTO stok_cache (menu_id, sekolah_id, stok, hpp, stok_minimum, updated_at)
VALUES (1001, 10, 49, 7000,  5,  now()),
       (1002, 10, 50, 6000,  5,  now()),
       (1003, 10, 48, 1000,  10, now()),
       (1004, 10, 48, 2000,  10, now()),
       (1005, 10, 50, 9000,  5,  now()),
       (1006, 10, 50, 3000,  10, now()),
       (1007, 10, 48, 5000,  5,  now()),
       (1008, 10, 50, 2000,  20, now())
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO menu (id, sekolah_id, kategori_id, nama, harga_jual, satuan,
                  stok_minimum, is_active, created_at, updated_at)
SELECT v.id, 10, k.id, v.nama, v.harga, v.satuan, v.stok_minimum, true, now(), now()
FROM (VALUES
        (1001::bigint, 'Nasi Goreng'::varchar, 12000::bigint, 'PORSI'::varchar, 5::integer,  'Makanan'::varchar),
        (1002, 'Mie Ayam',     10000, 'PORSI', 5,  'Makanan'),
        (1003, 'Es Teh Manis',  3000, 'PCS',   10, 'Minuman'),
        (1004, 'Air Mineral',   4000, 'BOTOL', 10, 'Minuman'),
        (1005, 'Ayam Geprek',  13000, 'PORSI', 5,  'Makanan'),
        (1006, 'Keripik',       5000, 'PCS',   10, 'Snack'),
        (1007, 'Roti Bakar',    8000, 'PCS',   5,  'Snack'),
        (1008, 'Pulpen',        3000, 'PCS',   20, 'Alat Tulis')
     ) AS v(id, nama, harga, satuan, stok_minimum, kategori)
JOIN kategori_menu k ON k.sekolah_id = 10 AND k.nama = v.kategori
ON CONFLICT DO NOTHING;

INSERT INTO kartu_tamu (id, sekolah_id, nomor_kartu, rfid_uid, aktif,
                        label_pemegang, dibuat_oleh, dibuat_pada)
VALUES (1001, 10, 'KT-FE10-001', 'DA000001', true, 'Budi (Guru)',    2, now()),
       (1002, 10, 'KT-FE10-002', 'DA000002', true, 'Sari (Staf TU)', 2, now()),
       (1003, 10, 'KT-FE10-003', 'DA000003', true, 'Tamu Umum',      2, now())
ON CONFLICT DO NOTHING;

INSERT INTO sesi_kasir (id, sekolah_id, titik_kasir_id, tanggal, status,
                        total_bruto, total_void, total_bersih, dibuka_at,
                        ditutup_at, ditutup_oleh, auto_tutup,
                        posting_buku_kas, created_at, updated_at)
VALUES (1001, 10, 1001, CURRENT_DATE, 'TERBUKA', 23000, 0, 23000,
        ((CURRENT_DATE) + time '07:00') AT TIME ZONE 'Asia/Jakarta',
        NULL, NULL, false, false, now(), now()),
       (1002, 10, 1001, CURRENT_DATE - 1, 'DITUTUP', 21000, 5000, 16000,
        ((CURRENT_DATE - 1) + time '07:00') AT TIME ZONE 'Asia/Jakarta',
        ((CURRENT_DATE - 1) + time '14:30') AT TIME ZONE 'Asia/Jakarta', 4, false, true, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO transaksi (id, idempotency_key, sekolah_id, sesi_kasir_id, titik_kasir_id,
                       subjek_tipe, subjek_id, kartu_uid, petugas_id,
                       total, total_hpp, status, alasan_void, void_at, void_oleh,
                       waktu, created_at, updated_at)
VALUES
  (910101, 'seed-fe10-t1', 10, 1001, 1001, 'KARTU_TAMU', 1001, 'DA000001', 4, 15000, 8000, 'SUKSES',
   NULL, NULL, NULL, ((CURRENT_DATE) + time '09:15') AT TIME ZONE 'Asia/Jakarta', now(), now()),
  (910102, 'seed-fe10-t2', 10, 1001, 1001, 'KARTU_TAMU', 1002, 'DA000002', 4, 8000, 4000, 'SUKSES',
   NULL, NULL, NULL, ((CURRENT_DATE) + time '10:05') AT TIME ZONE 'Asia/Jakarta', now(), now()),
  (910103, 'seed-fe10-t3', 10, 1002, 1001, 'KARTU_TAMU', 1003, 'DA000003', 4, 16000, 10000, 'SUKSES',
   NULL, NULL, NULL, ((CURRENT_DATE - 1) + time '09:30') AT TIME ZONE 'Asia/Jakarta', now(), now()),
  (910104, 'seed-fe10-t4', 10, 1002, 1001, 'KARTU_TAMU', 1003, 'DA000003', 4, 5000, 3000, 'VOID',
   'Salah input item', ((CURRENT_DATE - 1) + time '11:10') AT TIME ZONE 'Asia/Jakarta', 2,
   ((CURRENT_DATE - 1) + time '10:20') AT TIME ZONE 'Asia/Jakarta', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO transaksi_item (id, transaksi_id, menu_id, nama_menu, kategori_id,
                            harga_jual, qty, hpp_snapshot, subtotal, created_at)
VALUES
  (910201, 910101, 1001, 'Nasi Goreng',  1001, 12000, 1, 7000, 12000, now()),
  (910202, 910101, 1003, 'Es Teh Manis', 1002, 3000,  1, 1000, 3000,  now()),
  (910203, 910102, 1004, 'Air Mineral',  1002, 4000,  2, 2000, 8000,  now()),
  (910204, 910103, 1007, 'Roti Bakar',   1003, 8000,  2, 5000, 16000, now()),
  (910205, 910104, 1006, 'Keripik',      1003, 5000,  1, 3000, 5000,  now())
ON CONFLICT DO NOTHING;

INSERT INTO mutasi_stok (id, sekolah_id, menu_id, arah, jenis, qty, stok_setelah,
                         hpp_snapshot, transaksi_id, referensi_tipe, referensi_id,
                         alasan, aktor_id, aktor_nama, waktu, created_at,
                         harga_beli_satuan)
VALUES
  -- stok awal (7 hari lalu)
  (910301, 10, 1001, 'MASUK', 'BARANG_MASUK', 50, 50, 7000, NULL, 'BARANG_MASUK', 'BM-FE10-001', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 7000),
  (910302, 10, 1002, 'MASUK', 'BARANG_MASUK', 50, 50, 6000, NULL, 'BARANG_MASUK', 'BM-FE10-001', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 6000),
  (910303, 10, 1003, 'MASUK', 'BARANG_MASUK', 50, 50, 1000, NULL, 'BARANG_MASUK', 'BM-FE10-001', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 1000),
  (910304, 10, 1004, 'MASUK', 'BARANG_MASUK', 50, 50, 2000, NULL, 'BARANG_MASUK', 'BM-FE10-001', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 2000),
  (910305, 10, 1005, 'MASUK', 'BARANG_MASUK', 50, 50, 9000, NULL, 'BARANG_MASUK', 'BM-FE10-001', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 9000),
  (910306, 10, 1006, 'MASUK', 'BARANG_MASUK', 50, 50, 3000, NULL, 'BARANG_MASUK', 'BM-FE10-001', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 3000),
  (910307, 10, 1007, 'MASUK', 'BARANG_MASUK', 50, 50, 5000, NULL, 'BARANG_MASUK', 'BM-FE10-001', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 5000),
  (910308, 10, 1008, 'MASUK', 'BARANG_MASUK', 50, 50, 2000, NULL, 'BARANG_MASUK', 'BM-FE10-001', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 2000),
  -- penjualan
  (910331, 10, 1001, 'KELUAR', 'PENJUALAN', 1, 49, 7000, 910101, 'TRANSAKSI', '910101', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE) + time '09:15') AT TIME ZONE 'Asia/Jakarta', now(), NULL),
  (910332, 10, 1003, 'KELUAR', 'PENJUALAN', 1, 48, 1000, 910101, 'TRANSAKSI', '910101', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE) + time '09:15') AT TIME ZONE 'Asia/Jakarta', now(), NULL),
  (910333, 10, 1004, 'KELUAR', 'PENJUALAN', 2, 48, 2000, 910102, 'TRANSAKSI', '910102', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE) + time '10:05') AT TIME ZONE 'Asia/Jakarta', now(), NULL),
  (910334, 10, 1007, 'KELUAR', 'PENJUALAN', 2, 48, 5000, 910103, 'TRANSAKSI', '910103', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE - 1) + time '09:30') AT TIME ZONE 'Asia/Jakarta', now(), NULL),
  (910335, 10, 1003, 'KELUAR', 'PENJUALAN', 1, 49, 1000, 910103, 'TRANSAKSI', '910103', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE - 1) + time '09:30') AT TIME ZONE 'Asia/Jakarta', now(), NULL),
  -- void transaksi 910104 → stok keripik kembali
  (910336, 10, 1006, 'KELUAR', 'PENJUALAN', 1, 49, 3000, 910104, 'TRANSAKSI', '910104', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE - 1) + time '10:20') AT TIME ZONE 'Asia/Jakarta', now(), NULL),
  (910337, 10, 1006, 'MASUK', 'VOID_PENJUALAN', 1, 50, 3000, 910104, 'TRANSAKSI', '910104', 'Pembalik void', 2, 'Bendahara', ((CURRENT_DATE - 1) + time '11:10') AT TIME ZONE 'Asia/Jakarta', now(), NULL)
ON CONFLICT DO NOTHING;

INSERT INTO saldo_ledger (id, sekolah_id, subjek_tipe, subjek_id, arah, jenis,
                          nominal, saldo_setelah, transaksi_id, idempotency_key,
                          referensi_tipe, referensi_id, keterangan, aktor_id, waktu, created_at)
VALUES
  -- Kartu 1001: topup 100.000 → belanja 15.000 = 85.000
  (910001, 10, 'KARTU_TAMU', 1001, 'KREDIT', 'TOPUP_TUNAI', 100000, 100000, NULL, 'seed-fe10-kt1-topup', 'TOPUP', 'TOPUP-FE10-001', 'Top up tunai di TU', 2, ((CURRENT_DATE - 3) + time '08:30') AT TIME ZONE 'Asia/Jakarta', now()),
  (910002, 10, 'KARTU_TAMU', 1001, 'DEBIT',  'PENJUALAN',    15000,  85000, 910101, 'seed-fe10-kt1-trx910101', 'TRANSAKSI', '910101', NULL, 4, ((CURRENT_DATE) + time '09:15') AT TIME ZONE 'Asia/Jakarta', now()),
  -- Kartu 1002: topup 50.000 → belanja 8.000 = 42.000
  (910003, 10, 'KARTU_TAMU', 1002, 'KREDIT', 'TOPUP_TUNAI', 50000, 50000, NULL, 'seed-fe10-kt2-topup', 'TOPUP', 'TOPUP-FE10-002', 'Top up tunai di TU', 2, ((CURRENT_DATE - 3) + time '08:35') AT TIME ZONE 'Asia/Jakarta', now()),
  (910004, 10, 'KARTU_TAMU', 1002, 'DEBIT',  'PENJUALAN',    8000,  42000, 910102, 'seed-fe10-kt2-trx910102', 'TRANSAKSI', '910102', NULL, 4, ((CURRENT_DATE) + time '10:05') AT TIME ZONE 'Asia/Jakarta', now()),
  -- Kartu 1003: topup 30.000 → belanja 16.000 → void 5.000 (kembali) = 14.000
  (910005, 10, 'KARTU_TAMU', 1003, 'KREDIT', 'TOPUP_TUNAI', 30000, 30000, NULL, 'seed-fe10-kt3-topup', 'TOPUP', 'TOPUP-FE10-003', 'Top up tunai di TU', 2, ((CURRENT_DATE - 3) + time '08:40') AT TIME ZONE 'Asia/Jakarta', now()),
  (910006, 10, 'KARTU_TAMU', 1003, 'DEBIT',  'PENJUALAN',    16000,  14000, 910103, 'seed-fe10-kt3-trx910103', 'TRANSAKSI', '910103', NULL, 4, ((CURRENT_DATE - 1) + time '09:30') AT TIME ZONE 'Asia/Jakarta', now()),
  (910007, 10, 'KARTU_TAMU', 1003, 'DEBIT',  'PENJUALAN',     5000,   9000, 910104, 'seed-fe10-kt3-trx910104', 'TRANSAKSI', '910104', NULL, 4, ((CURRENT_DATE - 1) + time '10:20') AT TIME ZONE 'Asia/Jakarta', now()),
  (910008, 10, 'KARTU_TAMU', 1003, 'KREDIT', 'VOID_PENJUALAN', 5000, 14000, 910104, 'seed-fe10-kt3-void', 'TRANSAKSI', '910104', 'Pembalik void transaksi', 2, ((CURRENT_DATE - 1) + time '11:10') AT TIME ZONE 'Asia/Jakarta', now()),
  -- SISWA demo 1001 (sekolah 10, UID RFID DA100001): topup tunai 75.000.
  -- UID hanya dipetakan oleh KartuLookupDemo saat profil demo aktif (lokal); bukan master siswa.
  (910009, 10, 'SISWA', 1001, 'KREDIT', 'TOPUP_TUNAI', 75000, 75000, NULL, 'seed-fe10-siswa1-topup', 'TOPUP', 'TOPUP-FE10-S001', 'Top up tunai di TU', 2, ((CURRENT_DATE - 2) + time '08:10') AT TIME ZONE 'Asia/Jakarta', now())
ON CONFLICT DO NOTHING;

INSERT INTO saldo_cache (subjek_tipe, subjek_id, sekolah_id, saldo, updated_at)
VALUES ('KARTU_TAMU', 1001, 10, 85000, now()),
       ('KARTU_TAMU', 1002, 10, 42000, now()),
       ('KARTU_TAMU', 1003, 10, 14000, now()),
       ('SISWA',      1001, 10, 75000, now())
ON CONFLICT DO NOTHING;

INSERT INTO limit_harian (id, sekolah_id, subjek_tipe, subjek_id, nominal,
                          diubah_oleh, diubah_pada, created_at)
VALUES (1001, 10, 'KARTU_TAMU', 1002, 50000, 2, now(), now()),
       (1002, 10, 'SISWA',      1001, 25000, 2, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO blokir_item (id, sekolah_id, subjek_tipe, subjek_id, menu_id, kategori_id,
                         diblokir, diubah_oleh, diubah_pada, created_at)
VALUES (1001, 10, 'SISWA', 1001, 1006, NULL, true, 2, now(), now()),
       (1002, 10, 'SISWA', 1001, NULL, 1003, true, 2, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO setoran_tu (id, sekolah_id, tanggal, petugas_id, total_topup,
                        jumlah_disetor, referensi_id, catatan,
                        dikonfirmasi_oleh, waktu, created_at)
VALUES (1001, 10, CURRENT_DATE - 1, 2, 155000, 155000, 'SETORAN-FE10-001',
        'Setoran topup tunai kemarin', 1,
        ((CURRENT_DATE - 1) + time '15:30') AT TIME ZONE 'Asia/Jakarta', now())
ON CONFLICT DO NOTHING;

INSERT INTO pos_buku_kas (id, sekolah_id, nama, tipe, keterangan, aktif, created_at)
VALUES (1001, 10, 'Penjualan Kantin', 'MASUK',  'Pos utama penjualan harian', true, now()),
       (1002, 10, 'Belanja Bahan',    'KELUAR', 'Pembelian bahan baku',       true, now())
ON CONFLICT DO NOTHING;

INSERT INTO posting_buku_kas (id, sekolah_id, referensi_id, entitas, mutasi_id,
                              ref_buku_kas, jumlah, status, waktu, created_at)
VALUES (1001, 10, '910103', 'SESI_KASIR', NULL, 'BK-FE10-001', 16000, 'SUKSES',
        ((CURRENT_DATE - 1) + time '14:35') AT TIME ZONE 'Asia/Jakarta', now())
ON CONFLICT DO NOTHING;

INSERT INTO insiden_offline (id, sekolah_id, titik_kasir_id, mulai, selesai,
                             durasi_menit, keterangan, dilaporkan_oleh, created_at)
VALUES (1001, 10, 1002,
        ((CURRENT_DATE - 2) + time '09:00') AT TIME ZONE 'Asia/Jakarta',
        ((CURRENT_DATE - 2) + time '09:12') AT TIME ZONE 'Asia/Jakarta',
        12, 'Koneksi internet terputus', 4, now())
ON CONFLICT DO NOTHING;

-- ── Mapping kartu siswa demo (tabel HANYA ada di lokal) ───────
-- Bukan master siswa: hanya fixture untuk lookup satu UID saat profil demo aktif.
CREATE TABLE IF NOT EXISTS dev_siswa_rfid (
    sekolah_id BIGINT NOT NULL,
    siswa_id BIGINT NOT NULL,
    nomor_kartu VARCHAR(32) NOT NULL,
    rfid_uid VARCHAR(50) NOT NULL UNIQUE,
    aktif BOOLEAN NOT NULL DEFAULT true,
    PRIMARY KEY (sekolah_id, siswa_id),
    UNIQUE (sekolah_id, nomor_kartu)
);

INSERT INTO dev_siswa_rfid (sekolah_id, siswa_id, nomor_kartu, rfid_uid, aktif)
VALUES (10, 1001, 'RFID-SISWA-001', 'DA100001', true)
ON CONFLICT DO NOTHING;

-- ── Akun login dev (tabel HANYA ada di lokal) ────────────────
-- Dibuat di sini, BUKAN sebagai migrasi biasa: skema produksi tidak boleh
-- punya tabel akun — kantin-be tidak menyimpan master pengguna (ADR-0002),
-- identitas asli datang dari admin-be lewat JWT.
-- Password seluruh akun: Kantin#2026  (BCrypt cost 10). Hanya untuk lokal.
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
VALUES
    (1, 1, 'kepsek1',    'Wibisana Bama (Kepsek)',        'KEPSEK',
     '$2a$10$QuQdle/cq0LmMnK6NuzTn.FamhgtEtsPlLXBXbLX6M5qvN.MIF2B.', true),
    (2, 1, 'tu1',        'Sri Wahyuni (TU)',              'TATA_USAHA',
     '$2a$10$gIRI1JII3tHKLy.JqJQgS.6Oh2aO.5awJMLPKOAXZct1/b1wKjvIC', true),
    (3, 1, 'pengelola1', 'Deryl Fabiensyah (Pengelola)',  'PENGELOLA',
     '$2a$10$dzn8cuWxXJX0HXEtVohumuWYg9ri41W4jC8DA4c6moZghqtOcvH5W', true),
    (4, 1, 'kasir1',     'Ahmad Kasir (Petugas POS)',     'PETUGAS',
     '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
    (5, 1, 'bendahara1', 'Siti Rahma (Bendahara)',        'BENDAHARA',
     '$2a$10$0PkEyDmQ0sxGV/kdv5XE.udnbKO1N1yWLx7hnbA3cTBFG17fE9Byu', true),
    (6, 2, 'tu2',        'Andika Pratama (TU Sekolah 2)', 'TATA_USAHA',
     '$2a$10$3vynpehMjgcknvLbhkE4kuucUfxfSsSxiq9fTAJ0VYbLzGbcXRJwa', true),
    (7, 10, 'kasir10',     'Rizky Pratama (Petugas POS 10)', 'PETUGAS',
     '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
    (8, 10, 'bendahara10', 'Nurul Hidayah (Bendahara 10)',    'BENDAHARA',
     '$2a$10$0PkEyDmQ0sxGV/kdv5XE.udnbKO1N1yWLx7hnbA3cTBFG17fE9Byu', true),
    -- Satu akun admin dev untuk menguji role ADMIN_SEKOLAH.
    -- Hash disamakan dengan akun dev yang password-nya Kantin#2026.
    (9, 1, 'admin1', 'Budi Santoso (Admin Sekolah)', 'ADMIN_SEKOLAH',
     '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true)
ON CONFLICT (id) DO NOTHING;

-- ── Data pelengkap: menutup semua jalur fitur ─────────────────────
-- Ditambahkan setelah data dasar agar ID tetap stabil; blok self-healing
-- di bawah tetap menjadi penentu nilai cache akhir.

INSERT INTO titik_kasir (id, sekolah_id, nama, kode, is_active, created_at, updated_at)
VALUES (4, 2, 'Kasir Kantin Belakang', 'S2-02', true, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO kategori_menu (id, sekolah_id, nama, urutan, is_active, created_at, updated_at)
VALUES (11, 2, 'Minuman',    2, true, now(), now()),
       (12, 2, 'Snack',      3, true, now(), now()),
       (13, 2, 'Alat Tulis', 4, true, now(), now())
ON CONFLICT DO NOTHING;

-- Stok diisi sebelum menu: trg_menu_sinkron_stok_minimum hanya
-- menyinkronkan stok_minimum bila baris sudah ada.
INSERT INTO stok_cache (menu_id, sekolah_id, stok, hpp, stok_minimum, updated_at)
VALUES (13, 1, 50, 10000, 5, now()),
       (14, 1, 50, 6000, 5, now()),
       (15, 1, 6, 5000, 10, now()),
       (16, 1, 8, 3500, 10, now()),
       (17, 1, 50, 1200, 20, now()),
       (18, 1, 50, 2500, 10, now()),
       (102, 2, 50, 9000, 5, now()),
       (103, 2, 50, 7000, 5, now()),
       (104, 2, 50, 3000, 10, now()),
       (105, 2, 50, 4500, 10, now()),
       (106, 2, 50, 5500, 10, now()),
       (107, 2, 50, 2000, 20, now()),
       (1009, 10, 50, 7000, 5, now()),
       (1010, 10, 50, 6000, 10, now()),
       (1011, 10, 50, 3500, 10, now())
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO menu (id, sekolah_id, kategori_id, nama, harga_jual, satuan,
                  stok_minimum, is_active, created_at, updated_at)
VALUES (13, 1, 1, 'Nasi Padang', 15000, 'PORSI', 5, true, now(), now()),
       (14, 1, 1, 'Soto Ayam', 9000, 'PORSI', 5, true, now(), now()),
       (15, 1, 2, 'Kopi Susu', 8000, 'PCS', 10, true, now(), now()),
       (16, 1, 3, 'Pisang Goreng', 6000, 'PCS', 10, true, now(), now()),
       (17, 1, 4, 'Penghapus', 2000, 'PCS', 20, true, now(), now()),
       (18, 1, 2, 'Teh Botol', 4500, 'BOTOL', 10, true, now(), now()),
       (102, 2, 10, 'Nasi Goreng Spesial', 14000, 'PORSI', 5, true, now(), now()),
       (103, 2, 10, 'Mie Goreng', 11000, 'PORSI', 5, true, now(), now()),
       (104, 2, 11, 'Es Jeruk', 5000, 'PCS', 10, true, now(), now()),
       (105, 2, 11, 'Susu Coklat', 7000, 'PCS', 10, true, now(), now()),
       (106, 2, 12, 'Kentang Goreng', 9000, 'PCS', 10, true, now(), now()),
       (107, 2, 13, 'Pensil', 3500, 'PCS', 20, true, now(), now()),
       (1009, 10, 1001, 'Nasi Kuning', 11000, 'PORSI', 5, true, now(), now()),
       (1010, 10, 1002, 'Jus Alpukat', 9000, 'PCS', 10, true, now(), now()),
       (1011, 10, 1003, 'Pisang Goreng', 6000, 'PCS', 10, true, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO kartu_tamu (id, sekolah_id, nomor_kartu, rfid_uid, aktif,
                        label_pemegang, dibuat_oleh, dibuat_pada)
VALUES (12, 1, 'KT-DEMO-012', 'DE000012', true, 'Hendra (Guru)', 2, now()),
       (13, 1, 'KT-DEMO-013', 'DE000013', true, 'Tamu Dinas', 2, now()),
       (14, 2, 'KT-S2-002', 'S2000002', true, 'Tamu SMA 2 B', 2, now()),
       (15, 2, 'KT-S2-003', 'S2000003', true, 'Tamu SMA 2 C', 2, now()),
       (1004, 10, 'KT-FE10-004', 'DA000004', true, 'Tamu FE10', 2, now())
ON CONFLICT DO NOTHING;

INSERT INTO sesi_kasir (id, sekolah_id, titik_kasir_id, tanggal, status,
                        total_bruto, total_void, total_bersih, dibuka_at,
                        ditutup_at, ditutup_oleh, auto_tutup,
                        posting_buku_kas, created_at, updated_at)
VALUES (5, 1, 2, CURRENT_DATE, 'TERBUKA', 0, 0, 0, ((CURRENT_DATE + 0) + time '07:10') AT TIME ZONE 'Asia/Jakarta', NULL, NULL, false, false, now(), now()),
       (11, 2, 4, CURRENT_DATE, 'TERBUKA', 0, 0, 0, ((CURRENT_DATE + 0) + time '07:25') AT TIME ZONE 'Asia/Jakarta', NULL, NULL, false, false, now(), now()),
       (1003, 10, 1002, CURRENT_DATE, 'TERBUKA', 0, 0, 0, ((CURRENT_DATE + 0) + time '07:05') AT TIME ZONE 'Asia/Jakarta', NULL, NULL, false, false, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO transaksi (id, idempotency_key, sekolah_id, sesi_kasir_id, titik_kasir_id,
                       subjek_tipe, subjek_id, kartu_uid, petugas_id,
                       total, total_hpp, status, alasan_void, void_at, void_oleh,
                       waktu, created_at, updated_at)
VALUES
  (900201, 'seed-add-t900201', 1, 5, 2, 'KARTU_TAMU', 12, 'DE000012', 4, 27500, 17500, 'SUKSES', NULL, NULL, NULL, ((CURRENT_DATE + 0) + time '08:40') AT TIME ZONE 'Asia/Jakarta', now(), now()),
  (900202, 'seed-add-t900202', 1, 5, 2, 'KARTU_TAMU', 13, 'DE000013', 4, 20000, 11700, 'SUKSES', NULL, NULL, NULL, ((CURRENT_DATE + 0) + time '09:20') AT TIME ZONE 'Asia/Jakarta', now(), now()),
  (900203, 'seed-add-t900203', 1, 5, 2, 'KARTU_TAMU', 4, 'DE000004', 4, 5000, 3000, 'VOID', 'Salah pilih menu', ((CURRENT_DATE + 0) + time '11:59') AT TIME ZONE 'Asia/Jakarta', 2, ((CURRENT_DATE + 0) + time '10:10') AT TIME ZONE 'Asia/Jakarta', now(), now()),
  (900204, 'seed-add-t900204', 1, 5, 2, 'KARTU_TAMU', 5, 'DE000005', 4, 8000, 4000, 'SUKSES', NULL, NULL, NULL, ((CURRENT_DATE + 0) + time '12:20') AT TIME ZONE 'Asia/Jakarta', now(), now()),
  (900205, 'seed-add-t900205', 1, 5, 2, 'KARTU_TAMU', 6, 'DE000006', 4, 12000, 7400, 'SUKSES', NULL, NULL, NULL, ((CURRENT_DATE + 0) + time '11:30') AT TIME ZONE 'Asia/Jakarta', now(), now()),
  (900206, 'seed-add-t900206', 1, 5, 2, 'KARTU_TAMU', 7, 'DE000007', 4, 15000, 10200, 'SUKSES', NULL, NULL, NULL, ((CURRENT_DATE + 0) + time '12:05') AT TIME ZONE 'Asia/Jakarta', now(), now()),
  (900211, 'seed-add-t900211', 2, 11, 3, 'KARTU_TAMU', 14, 'S2000002', 4, 19000, 12000, 'SUKSES', NULL, NULL, NULL, ((CURRENT_DATE + 0) + time '08:10') AT TIME ZONE 'Asia/Jakarta', now(), now()),
  (900212, 'seed-add-t900212', 2, 11, 3, 'KARTU_TAMU', 15, 'S2000003', 4, 20000, 12500, 'SUKSES', NULL, NULL, NULL, ((CURRENT_DATE + 0) + time '09:05') AT TIME ZONE 'Asia/Jakarta', now(), now()),
  (900213, 'seed-add-t900213', 2, 11, 3, 'KARTU_TAMU', 14, 'S2000002', 4, 3500, 2000, 'VOID', 'Kartu tidak terbaca', ((CURRENT_DATE + 0) + time '11:59') AT TIME ZONE 'Asia/Jakarta', 2, ((CURRENT_DATE + 0) + time '09:45') AT TIME ZONE 'Asia/Jakarta', now(), now()),
  (900221, 'seed-add-t900221', 10, 1003, 1001, 'KARTU_TAMU', 1004, 'DA000004', 4, 20000, 13000, 'SUKSES', NULL, NULL, NULL, ((CURRENT_DATE + 0) + time '09:40') AT TIME ZONE 'Asia/Jakarta', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO transaksi_item (id, transaksi_id, menu_id, nama_menu, kategori_id,
                            harga_jual, qty, hpp_snapshot, subtotal, created_at)
VALUES
  (900401, 900201, 13, 'Nasi Padang', 1, 15000, 1, 10000, 15000, now()),
  (900402, 900201, 18, 'Teh Botol', 2, 4500, 1, 2500, 4500, now()),
  (900403, 900201, 15, 'Kopi Susu', 2, 8000, 1, 5000, 8000, now()),
  (900404, 900202, 14, 'Soto Ayam', 1, 9000, 1, 6000, 9000, now()),
  (900405, 900202, 16, 'Pisang Goreng', 3, 6000, 1, 3500, 6000, now()),
  (900406, 900202, 17, 'Penghapus', 4, 2000, 1, 1200, 2000, now()),
  (900407, 900202, 3, 'Es Teh Manis', 2, 3000, 1, 1000, 3000, now()),
  (900408, 900203, 6, 'Keripik', 3, 5000, 1, 3000, 5000, now()),
  (900409, 900204, 4, 'Air Mineral', 2, 4000, 2, 2000, 8000, now()),
  (900410, 900205, 7, 'Roti Bakar', 3, 8000, 1, 5000, 8000, now()),
  (900411, 900205, 17, 'Penghapus', 4, 2000, 2, 1200, 4000, now()),
  (900412, 900206, 5, 'Ayam Geprek', 1, 13000, 1, 9000, 13000, now()),
  (900413, 900206, 17, 'Penghapus', 4, 2000, 1, 1200, 2000, now()),
  (900414, 900211, 102, 'Nasi Goreng Spesial', 10, 14000, 1, 9000, 14000, now()),
  (900415, 900211, 104, 'Es Jeruk', 11, 5000, 1, 3000, 5000, now()),
  (900416, 900212, 103, 'Mie Goreng', 10, 11000, 1, 7000, 11000, now()),
  (900417, 900212, 106, 'Kentang Goreng', 12, 9000, 1, 5500, 9000, now()),
  (900418, 900213, 107, 'Pensil', 13, 3500, 1, 2000, 3500, now()),
  (900419, 900221, 1009, 'Nasi Kuning', 1001, 11000, 1, 7000, 11000, now()),
  (900420, 900221, 1010, 'Jus Alpukat', 1002, 9000, 1, 6000, 9000, now())
ON CONFLICT DO NOTHING;

INSERT INTO mutasi_stok (id, sekolah_id, menu_id, arah, jenis, qty, stok_setelah,
                         hpp_snapshot, transaksi_id, referensi_tipe, referensi_id,
                         alasan, aktor_id, aktor_nama, waktu, created_at,
                         harga_beli_satuan)
VALUES
  (900522, 1, 3, 'KELUAR', 'PENJUALAN', 1, 46, 1000, '900202', 'TRANSAKSI', '900202', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE + 0) + time '09:20') AT TIME ZONE 'Asia/Jakarta', now(), 1000),
  (900525, 1, 4, 'KELUAR', 'PENJUALAN', 2, 46, 2000, '900204', 'TRANSAKSI', '900204', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE + 0) + time '12:20') AT TIME ZONE 'Asia/Jakarta', now(), 2000),
  (900528, 1, 5, 'KELUAR', 'PENJUALAN', 1, 48, 9000, '900206', 'TRANSAKSI', '900206', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE + 0) + time '12:05') AT TIME ZONE 'Asia/Jakarta', now(), 9000),
  (900523, 1, 6, 'KELUAR', 'PENJUALAN', 1, 49, 3000, '900203', 'TRANSAKSI', '900203', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE + 0) + time '10:10') AT TIME ZONE 'Asia/Jakarta', now(), 3000),
  (900524, 1, 6, 'MASUK', 'VOID_PENJUALAN', 1, 50, 3000, '900203', 'TRANSAKSI', '900203', 'Pembalik void', 2, 'Bendahara', ((CURRENT_DATE + 0) + time '13:00') AT TIME ZONE 'Asia/Jakarta', now(), 3000),
  (900526, 1, 7, 'KELUAR', 'PENJUALAN', 1, 47, 5000, '900205', 'TRANSAKSI', '900205', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE + 0) + time '11:30') AT TIME ZONE 'Asia/Jakarta', now(), 5000),
  (900540, 1, 8, 'KELUAR', 'BARANG_RUSAK', 2, 48, 4000, NULL, 'BARANG_RUSAK', 'RSK-001', 'Susu kedaluwarsa', 3, 'Pengelola Kantin', ((CURRENT_DATE + 0) + time '07:40') AT TIME ZONE 'Asia/Jakarta', now(), 4000),
  (900538, 1, 9, 'MASUK', 'OPNAME_MASUK', 5, 55, 2000, NULL, 'OPNAME', 'OP-001', 'Opname bertambah', 3, 'Pengelola Kantin', ((CURRENT_DATE - 1) + time '16:00') AT TIME ZONE 'Asia/Jakarta', now(), 2000),
  (900539, 1, 10, 'KELUAR', 'OPNAME_KELUAR', 3, 47, 5000, NULL, 'OPNAME', 'OP-002', 'Opname berkurang', 3, 'Pengelola Kantin', ((CURRENT_DATE - 1) + time '16:05') AT TIME ZONE 'Asia/Jakarta', now(), 5000),
  (900541, 1, 12, 'MASUK', 'BARANG_MASUK_PEMBALIK', 1, 51, 5000, NULL, 'BARANG_MASUK', 'BM-PLG-001', 'Pembeli mengembalikan pesanan', 3, 'Pengelola Kantin', ((CURRENT_DATE + 0) + time '07:50') AT TIME ZONE 'Asia/Jakarta', now(), 5000),
  (900501, 1, 13, 'MASUK', 'BARANG_MASUK', 50, 50, 10000, NULL, 'BARANG_MASUK', 'BM-013', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 10000),
  (900516, 1, 13, 'KELUAR', 'PENJUALAN', 1, 49, 10000, '900201', 'TRANSAKSI', '900201', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE + 0) + time '08:40') AT TIME ZONE 'Asia/Jakarta', now(), 10000),
  (900502, 1, 14, 'MASUK', 'BARANG_MASUK', 50, 50, 6000, NULL, 'BARANG_MASUK', 'BM-014', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 6000),
  (900519, 1, 14, 'KELUAR', 'PENJUALAN', 1, 49, 6000, '900202', 'TRANSAKSI', '900202', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE + 0) + time '09:20') AT TIME ZONE 'Asia/Jakarta', now(), 6000),
  (900503, 1, 15, 'MASUK', 'BARANG_MASUK', 6, 6, 5000, NULL, 'BARANG_MASUK', 'BM-015', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 5000),
  (900518, 1, 15, 'KELUAR', 'PENJUALAN', 1, 5, 5000, '900201', 'TRANSAKSI', '900201', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE + 0) + time '08:40') AT TIME ZONE 'Asia/Jakarta', now(), 5000),
  (900504, 1, 16, 'MASUK', 'BARANG_MASUK', 8, 8, 3500, NULL, 'BARANG_MASUK', 'BM-016', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 3500),
  (900520, 1, 16, 'KELUAR', 'PENJUALAN', 1, 7, 3500, '900202', 'TRANSAKSI', '900202', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE + 0) + time '09:20') AT TIME ZONE 'Asia/Jakarta', now(), 3500),
  (900505, 1, 17, 'MASUK', 'BARANG_MASUK', 50, 50, 1200, NULL, 'BARANG_MASUK', 'BM-017', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 1200),
  (900521, 1, 17, 'KELUAR', 'PENJUALAN', 1, 49, 1200, '900202', 'TRANSAKSI', '900202', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE + 0) + time '09:20') AT TIME ZONE 'Asia/Jakarta', now(), 1200),
  (900527, 1, 17, 'KELUAR', 'PENJUALAN', 2, 47, 1200, '900205', 'TRANSAKSI', '900205', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE + 0) + time '11:30') AT TIME ZONE 'Asia/Jakarta', now(), 1200),
  (900529, 1, 17, 'KELUAR', 'PENJUALAN', 1, 46, 1200, '900206', 'TRANSAKSI', '900206', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE + 0) + time '12:05') AT TIME ZONE 'Asia/Jakarta', now(), 1200),
  (900506, 1, 18, 'MASUK', 'BARANG_MASUK', 50, 50, 2500, NULL, 'BARANG_MASUK', 'BM-018', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 2500),
  (900517, 1, 18, 'KELUAR', 'PENJUALAN', 1, 49, 2500, '900201', 'TRANSAKSI', '900201', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE + 0) + time '08:40') AT TIME ZONE 'Asia/Jakarta', now(), 2500),
  (900507, 2, 102, 'MASUK', 'BARANG_MASUK', 50, 50, 9000, NULL, 'BARANG_MASUK', 'BM-102', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 9000),
  (900530, 2, 102, 'KELUAR', 'PENJUALAN', 1, 49, 9000, '900211', 'TRANSAKSI', '900211', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE + 0) + time '08:10') AT TIME ZONE 'Asia/Jakarta', now(), 9000),
  (900508, 2, 103, 'MASUK', 'BARANG_MASUK', 50, 50, 7000, NULL, 'BARANG_MASUK', 'BM-103', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 7000),
  (900532, 2, 103, 'KELUAR', 'PENJUALAN', 1, 49, 7000, '900212', 'TRANSAKSI', '900212', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE + 0) + time '09:05') AT TIME ZONE 'Asia/Jakarta', now(), 7000),
  (900509, 2, 104, 'MASUK', 'BARANG_MASUK', 50, 50, 3000, NULL, 'BARANG_MASUK', 'BM-104', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 3000),
  (900531, 2, 104, 'KELUAR', 'PENJUALAN', 1, 49, 3000, '900211', 'TRANSAKSI', '900211', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE + 0) + time '08:10') AT TIME ZONE 'Asia/Jakarta', now(), 3000),
  (900510, 2, 105, 'MASUK', 'BARANG_MASUK', 50, 50, 4500, NULL, 'BARANG_MASUK', 'BM-105', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 4500),
  (900511, 2, 106, 'MASUK', 'BARANG_MASUK', 50, 50, 5500, NULL, 'BARANG_MASUK', 'BM-106', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 5500),
  (900533, 2, 106, 'KELUAR', 'PENJUALAN', 1, 49, 5500, '900212', 'TRANSAKSI', '900212', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE + 0) + time '09:05') AT TIME ZONE 'Asia/Jakarta', now(), 5500),
  (900512, 2, 107, 'MASUK', 'BARANG_MASUK', 50, 50, 2000, NULL, 'BARANG_MASUK', 'BM-107', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 2000),
  (900534, 2, 107, 'KELUAR', 'PENJUALAN', 1, 49, 2000, '900213', 'TRANSAKSI', '900213', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE + 0) + time '09:45') AT TIME ZONE 'Asia/Jakarta', now(), 2000),
  (900535, 2, 107, 'MASUK', 'VOID_PENJUALAN', 1, 50, 2000, '900213', 'TRANSAKSI', '900213', 'Pembalik void', 2, 'Bendahara', ((CURRENT_DATE + 0) + time '13:00') AT TIME ZONE 'Asia/Jakarta', now(), 2000),
  (900513, 10, 1009, 'MASUK', 'BARANG_MASUK', 50, 50, 7000, NULL, 'BARANG_MASUK', 'BM-1009', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 7000),
  (900536, 10, 1009, 'KELUAR', 'PENJUALAN', 1, 49, 7000, '900221', 'TRANSAKSI', '900221', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE + 0) + time '09:40') AT TIME ZONE 'Asia/Jakarta', now(), 7000),
  (900514, 10, 1010, 'MASUK', 'BARANG_MASUK', 50, 50, 6000, NULL, 'BARANG_MASUK', 'BM-1010', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 6000),
  (900537, 10, 1010, 'KELUAR', 'PENJUALAN', 1, 49, 6000, '900221', 'TRANSAKSI', '900221', NULL, 4, 'Petugas Kasir', ((CURRENT_DATE + 0) + time '09:40') AT TIME ZONE 'Asia/Jakarta', now(), 6000),
  (900515, 10, 1011, 'MASUK', 'BARANG_MASUK', 50, 50, 3500, NULL, 'BARANG_MASUK', 'BM-1011', NULL, 3, 'Pengelola Kantin', ((CURRENT_DATE - 7) + time '07:00') AT TIME ZONE 'Asia/Jakarta', now(), 3500)
ON CONFLICT DO NOTHING;

INSERT INTO saldo_ledger (id, sekolah_id, subjek_tipe, subjek_id, arah, jenis,
                          nominal, saldo_setelah, transaksi_id, idempotency_key,
                          referensi_tipe, referensi_id, keterangan, aktor_id, waktu, created_at)
VALUES
  (900601, 1, 'KARTU_TAMU', 12, 'KREDIT', 'TOPUP_TUNAI', 200000, 200000, NULL, 'seed-add-900601', 'TOPUP', 'TOPUP-012', 'Top up tunai di TU', 2, ((CURRENT_DATE - 2) + time '08:00') AT TIME ZONE 'Asia/Jakarta', now()),
  (900611, 2, 'KARTU_TAMU', 14, 'KREDIT', 'TOPUP_TUNAI', 100000, 100000, NULL, 'seed-add-900611', 'TOPUP', 'S2-TOPUP-012', 'Top up tunai di TU', 6, ((CURRENT_DATE - 2) + time '08:05') AT TIME ZONE 'Asia/Jakarta', now()),
  (900604, 1, 'KARTU_TAMU', 13, 'KREDIT', 'TOPUP_ONLINE', 150000, 150000, NULL, 'seed-add-900604', 'TOPUP', 'TOPUP-013', 'Top up online via wali murid', 2, ((CURRENT_DATE - 2) + time '09:00') AT TIME ZONE 'Asia/Jakarta', now()),
  (900621, 10, 'KARTU_TAMU', 1004, 'KREDIT', 'TOPUP_TUNAI', 40000, 40000, NULL, 'seed-add-900621', 'TOPUP', 'TOPUP-FE10-004', 'Top up tunai di TU', 8, ((CURRENT_DATE - 1) + time '08:25') AT TIME ZONE 'Asia/Jakarta', now()),
  (900605, 1, 'KARTU_TAMU', 13, 'DEBIT', 'TRANSFER', 50000, 100000, NULL, 'seed-add-900605', 'KOREKSI', 'TR-013', 'Transfer saldo ke kartu siswa', 2, ((CURRENT_DATE - 1) + time '09:30') AT TIME ZONE 'Asia/Jakarta', now()),
  (900612, 2, 'KARTU_TAMU', 15, 'KREDIT', 'TOPUP_ONLINE', 60000, 60000, NULL, 'seed-add-900612', 'TOPUP', 'S2-TOPUP-013', 'Top up online via wali murid', 6, ((CURRENT_DATE - 1) + time '10:15') AT TIME ZONE 'Asia/Jakarta', now()),
  (900606, 1, 'KARTU_TAMU', 9, 'KREDIT', 'TOPUP_ONLINE', 20000, 20000, NULL, 'seed-add-900606', 'TOPUP', 'TOPUP-009', 'Top up online via wali murid', 2, ((CURRENT_DATE - 1) + time '13:20') AT TIME ZONE 'Asia/Jakarta', now()),
  (900211, 2, 'KARTU_TAMU', 14, 'DEBIT', 'PENJUALAN', 19000, 81000, 900211, 'seed-add-trx900211', 'TRANSAKSI', '900211', NULL, 4, ((CURRENT_DATE + 0) + time '08:10') AT TIME ZONE 'Asia/Jakarta', now()),
  (900201, 1, 'KARTU_TAMU', 12, 'DEBIT', 'PENJUALAN', 27500, 172500, 900201, 'seed-add-trx900201', 'TRANSAKSI', '900201', NULL, 4, ((CURRENT_DATE + 0) + time '08:40') AT TIME ZONE 'Asia/Jakarta', now()),
  (900212, 2, 'KARTU_TAMU', 15, 'DEBIT', 'PENJUALAN', 20000, 40000, 900212, 'seed-add-trx900212', 'TRANSAKSI', '900212', NULL, 4, ((CURRENT_DATE + 0) + time '09:05') AT TIME ZONE 'Asia/Jakarta', now()),
  (900202, 1, 'KARTU_TAMU', 13, 'DEBIT', 'PENJUALAN', 20000, 80000, 900202, 'seed-add-trx900202', 'TRANSAKSI', '900202', NULL, 4, ((CURRENT_DATE + 0) + time '09:20') AT TIME ZONE 'Asia/Jakarta', now()),
  (900221, 10, 'KARTU_TAMU', 1004, 'DEBIT', 'PENJUALAN', 20000, 20000, 900221, 'seed-add-trx900221', 'TRANSAKSI', '900221', NULL, 4, ((CURRENT_DATE + 0) + time '09:40') AT TIME ZONE 'Asia/Jakarta', now()),
  (900613, 2, 'KARTU_TAMU', 15, 'DEBIT', 'KOREKSI', 5000, 35000, NULL, 'seed-add-900613', 'KOREKSI', 'S2-BA-013', 'Koreksi kelebihan topup', 6, ((CURRENT_DATE + 0) + time '09:50') AT TIME ZONE 'Asia/Jakarta', now()),
  (900205, 1, 'KARTU_TAMU', 6, 'DEBIT', 'PENJUALAN', 12000, 72000, 900205, 'seed-add-trx900205', 'TRANSAKSI', '900205', NULL, 4, ((CURRENT_DATE + 0) + time '11:30') AT TIME ZONE 'Asia/Jakarta', now()),
  (900206, 1, 'KARTU_TAMU', 7, 'DEBIT', 'PENJUALAN', 15000, 74000, 900206, 'seed-add-trx900206', 'TRANSAKSI', '900206', NULL, 4, ((CURRENT_DATE + 0) + time '12:05') AT TIME ZONE 'Asia/Jakarta', now()),
  (900204, 1, 'KARTU_TAMU', 5, 'DEBIT', 'PENJUALAN', 8000, 40000, 900204, 'seed-add-trx900204', 'TRANSAKSI', '900204', NULL, 4, ((CURRENT_DATE + 0) + time '12:20') AT TIME ZONE 'Asia/Jakarta', now()),
  (900603, 1, 'KARTU_TAMU', 12, 'KREDIT', 'REFUND', 10000, 182500, NULL, 'seed-add-900603', 'KOREKSI', 'RF-012', 'Refund saldo mengendap ke wali murid', 2, ((CURRENT_DATE + 0) + time '14:00') AT TIME ZONE 'Asia/Jakarta', now()),
  (900602, 1, 'KARTU_TAMU', 12, 'KREDIT', 'PENYESUAIAN', 5000, 187500, NULL, 'seed-add-900602', 'KOREKSI', 'BA-012', 'Penyesuaian selisih hitung uang', 2, ((CURRENT_DATE + 0) + time '16:00') AT TIME ZONE 'Asia/Jakarta', now())
ON CONFLICT DO NOTHING;

INSERT INTO saldo_cache (subjek_tipe, subjek_id, sekolah_id, saldo, updated_at)
VALUES ('KARTU_TAMU', 1, 1, 85000, now()),
       ('KARTU_TAMU', 2, 1, 92000, now()),
       ('KARTU_TAMU', 3, 1, 92000, now()),
       ('KARTU_TAMU', 4, 1, 84000, now()),
       ('KARTU_TAMU', 5, 1, 40000, now()),
       ('KARTU_TAMU', 6, 1, 72000, now()),
       ('KARTU_TAMU', 7, 1, 74000, now()),
       ('KARTU_TAMU', 8, 1, 25000, now()),
       ('KARTU_TAMU', 9, 1, 20000, now()),
       ('KARTU_TAMU', 10, 1, 70000, now()),
       ('KARTU_TAMU', 11, 2, 63000, now()),
       ('KARTU_TAMU', 12, 1, 187500, now()),
       ('KARTU_TAMU', 13, 1, 80000, now()),
       ('KARTU_TAMU', 1001, 10, 85000, now()),
       ('KARTU_TAMU', 1002, 10, 42000, now()),
       ('KARTU_TAMU', 1003, 10, 14000, now()),
       ('KARTU_TAMU', 1004, 10, 20000, now()),
       ('KARTU_TAMU', 14, 2, 81000, now()),
       ('KARTU_TAMU', 15, 2, 35000, now())
ON CONFLICT DO NOTHING;

INSERT INTO setoran_tu (id, sekolah_id, tanggal, petugas_id, total_topup,
                        jumlah_disetor, referensi_id, catatan,
                        dikonfirmasi_oleh, waktu, created_at)
VALUES (3, 1, ((CURRENT_DATE - 2) + time '15:30') AT TIME ZONE 'Asia/Jakarta', 2, 915000, 915000, 'SETORAN-002', 'Setoran topup tunai dua hari lalu', 1, ((CURRENT_DATE - 2) + time '15:30') AT TIME ZONE 'Asia/Jakarta', now()),
       (1002, 10, ((CURRENT_DATE - 2) + time '15:30') AT TIME ZONE 'Asia/Jakarta', 8, 60000, 60000, 'SETORAN-FE10-002', 'Setoran topup tunai dua hari lalu', 1, ((CURRENT_DATE - 2) + time '15:30') AT TIME ZONE 'Asia/Jakarta', now())
ON CONFLICT DO NOTHING;

INSERT INTO posting_buku_kas (id, sekolah_id, referensi_id, entitas, mutasi_id,
                              ref_buku_kas, jumlah, status, waktu, created_at)
VALUES (3, 1, '900107', 'SESI_KASIR', NULL, 'BK-2026-003', 11000, 'SUKSES', ((CURRENT_DATE - 2) + time '14:00') AT TIME ZONE 'Asia/Jakarta', now()),
       (1002, 10, '910101', 'SESI_KASIR', NULL, 'BK-FE10-002', 15000, 'SUKSES', ((CURRENT_DATE + 0) + time '14:20') AT TIME ZONE 'Asia/Jakarta', now())
ON CONFLICT DO NOTHING;

INSERT INTO insiden_offline (id, sekolah_id, titik_kasir_id, mulai, selesai,
                             durasi_menit, keterangan, dilaporkan_oleh, created_at)
VALUES (3, 1, 1, ((CURRENT_DATE - 1) + time '10:00') AT TIME ZONE 'Asia/Jakarta', ((CURRENT_DATE - 1) + time '10:18') AT TIME ZONE 'Asia/Jakarta', 18, 'Printer struk macet', 4, now()),
       (1002, 10, 1001, ((CURRENT_DATE - 1) + time '11:00') AT TIME ZONE 'Asia/Jakarta', ((CURRENT_DATE - 1) + time '11:05') AT TIME ZONE 'Asia/Jakarta', 5, 'Koneksi terputus sebentar', 7, now())
ON CONFLICT DO NOTHING;

INSERT INTO webhook_event (id, sumber, event_id, event_type, payload_hash,
                           sekolah_id, status, keterangan, waktu, created_at)
VALUES (900801, 'WA_GW', 'seed-dev-wh-1', 'PESAN', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 1, 'DIABAIKAN', 'Gateway tidak terpasang', ((CURRENT_DATE - 1) + time '09:00') AT TIME ZONE 'Asia/Jakarta', now()),
       (900802, 'ADMIN_BE', 'seed-dev-wh-2', 'SINKRONISASI_SISWA', 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 1, 'DIPROSES', NULL, ((CURRENT_DATE + 0) + time '08:00') AT TIME ZONE 'Asia/Jakarta', now()),
       (900803, 'WA_GW', 'seed-dev-wh-3', 'PESAN', 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc', 10, 'DIPROSES', NULL, ((CURRENT_DATE + 0) + time '08:30') AT TIME ZONE 'Asia/Jakarta', now())
ON CONFLICT DO NOTHING;

INSERT INTO audit_log (id, sekolah_id, aktor_id, aksi, entitas, entitas_id,
                       nilai_lama, nilai_baru, alasan, waktu, created_at)
VALUES (900901, 1, 2, 'UBAH', 'KARTU_TAMU', '12', NULL, '{"batasHarian":100000}', 'Batas harian dinaikkan', ((CURRENT_DATE + 0) + time '08:45') AT TIME ZONE 'Asia/Jakarta', now()),
       (900902, 1, 4, 'BLOKIR', 'KARTU_TAMU', '10', NULL, '{"diblokir":true}', 'Kartu dilaporkan hilang', ((CURRENT_DATE + 0) + time '08:45') AT TIME ZONE 'Asia/Jakarta', now()),
       (900903, 10, 8, 'UBAH', 'MENU', '1009', '{"hargaJual":11000}', '{"hargaJual":12000}', 'Harga naik', ((CURRENT_DATE + 0) + time '08:45') AT TIME ZONE 'Asia/Jakarta', now())
ON CONFLICT DO NOTHING;

INSERT INTO limit_harian (id, sekolah_id, subjek_tipe, subjek_id, nominal,
                          diubah_oleh, diubah_pada, created_at)
VALUES (3, 1, 'KARTU_TAMU', 12, 100000, 2, now(), now()),
       (4, 1, 'KARTU_TAMU', 4, 40000, 2, now(), now()),
       (1003, 2, 'KARTU_TAMU', 14, 40000, 2, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO blokir_item (id, sekolah_id, subjek_tipe, subjek_id, menu_id, kategori_id,
                         diblokir, diubah_oleh, diubah_pada, created_at)
VALUES (3, 1, 'KARTU_TAMU', 12, '15', NULL, true, 2, now(), now()),
       (4, 1, 'KARTU_TAMU', 13, NULL, '2', true, 2, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO blokir_kartu (id, sekolah_id, subjek_tipe, subjek_id, diblokir, alasan,
                          diubah_oleh, diubah_pada, created_at)
VALUES (2, 1, 'KARTU_TAMU', 13, true, 'Kartu rusak', 2, now(), now()),
       (1002, 10, 'KARTU_TAMU', 1004, true, 'Menunggu kartu pengganti', 2, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO transaksi_menunggu_konfirmasi (id, sekolah_id, idempotency_key, rfid_uid,
                                           titik_kasir_id, subjek_tipe, subjek_id,
                                           petugas_id, total, items_json, status,
                                           dibuat_at, kedaluwarsa_at, created_at, updated_at)
VALUES (2, 1, 'seed-dev-pending-2', 'DE000013', 2, 'KARTU_TAMU', 13, 4, 9000, '[{"menuId":14,"qty":1}]', 'MENUNGGU', ((CURRENT_DATE + 0) + time '13:10') AT TIME ZONE 'Asia/Jakarta', ((CURRENT_DATE + 0) + time '13:25') AT TIME ZONE 'Asia/Jakarta', now(), now()),
       (3, 10, 'seed-dev-pending-3', 'DA000004', 1001, 'KARTU_TAMU', 1004, 7, 6000, '[{"menuId":1011,"qty":1}]', 'DIKONFIRMASI', ((CURRENT_DATE + 0) + time '12:40') AT TIME ZONE 'Asia/Jakarta', ((CURRENT_DATE + 0) + time '12:55') AT TIME ZONE 'Asia/Jakarta', now(), now())
ON CONFLICT DO NOTHING;

-- ── Perbaikan cache dari ledger (issue #255) ──────────────────
-- Seed harus SELF-HEALING: INSERT di atas memakai ON CONFLICT DO NOTHING,
-- jadi baris cache lama yang sudah DRIFT tidak pernah disentuh. Dua perintah
-- ini menurunkan ulang cache dari ledger — menjadikan seed sebagai jalan
-- perbaikan DB dev yang sudah tidak seimbang, sekaligus memastikan angka
-- cache di berkas ini tidak perlu dihafal (selalu = turunan ledger).
-- Trigger V24 menjamin hal yang sama untuk setiap tulisan setelahnya.
UPDATE saldo_cache c
   SET saldo      = GREATEST(COALESCE((SELECT sum(CASE WHEN l.arah = 'KREDIT' THEN l.nominal
                                                        ELSE -l.nominal END)
                                        FROM saldo_ledger l
                                       WHERE l.sekolah_id  = c.sekolah_id
                                         AND l.subjek_tipe = c.subjek_tipe
                                         AND l.subjek_id   = c.subjek_id), 0), 0),
       updated_at = now()
 WHERE c.saldo IS DISTINCT FROM COALESCE(
       (SELECT sum(CASE WHEN l.arah = 'KREDIT' THEN l.nominal ELSE -l.nominal END)
          FROM saldo_ledger l
         WHERE l.sekolah_id  = c.sekolah_id
           AND l.subjek_tipe = c.subjek_tipe
           AND l.subjek_id   = c.subjek_id), 0);

UPDATE stok_cache c
   SET stok       = GREATEST(COALESCE((SELECT sum(CASE WHEN m.arah = 'MASUK' THEN m.qty
                                                        ELSE -m.qty END)
                                        FROM mutasi_stok m
                                       WHERE m.menu_id    = c.menu_id
                                         AND m.sekolah_id = c.sekolah_id), 0), 0),
       updated_at = now()
 WHERE c.stok IS DISTINCT FROM COALESCE(
       (SELECT sum(CASE WHEN m.arah = 'MASUK' THEN m.qty ELSE -m.qty END)
          FROM mutasi_stok m
         WHERE m.menu_id    = c.menu_id
           AND m.sekolah_id = c.sekolah_id), 0);

-- ── Ringkas ──────────────────────────────────────────────────
-- Isi seed: 3 sekolah | 36 menu | 19 kartu tamu | 10 sesi kasir |
-- 22 transaksi | 49 mutasi saldo | 80 mutasi stok | 36 baris stok |
-- 9 akun login dev | pengaturan kantin.
-- Sekolah 10 (tenant FE) menyumbang: 11 menu, 4 kartu tamu, 3 sesi kasir,
-- 5 transaksi, 5 baris saldo_cache (4 kartu tamu + 1 siswa), 2 akun dev.
-- (Tanpa blok DO $$ agar berkas ini aman dijalankan psql, Flyway, maupun
--  ResourceDatabasePopulator yang memecah pernyataan pada titik koma.)
