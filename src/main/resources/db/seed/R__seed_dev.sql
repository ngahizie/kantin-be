-- ============================================================
-- R__seed_dev.sql — Data dev/demo untuk skema v2 (23 tabel)
-- ------------------------------------------------------------
-- ⚠️ BUKAN data produksi.
--    Flyway REPEATABLE migration: hanya jalan bila folder `db/seed`
--    ikut didaftarkan di `spring.flyway.locations` (application-local).
--    CI & seluruh test TIDAK memuatnya.
--
-- Password semua akun: Kantin#2026
--
-- Isi:
--   sekolah 2 · pengguna 12 · siswa 19 · orang_tua 38 · kartu 19
--   kategori_menu 8 · menu 24 · stok 24 · titik_kasir 4
--   pengaturan_kantin 2 · akun_buku_kas 8
--   saldo 19 · mutasi_saldo 18
--   sesi_kasir 3 (2 terbuka hari ini, 1 tertutup kemarin)
--   transaksi 2 + transaksi_item 2 + riwayat_stok 2
--
-- Sifat: DESTRUKTIF untuk data dev — tabel dikosongkan dulu lalu diisi
-- ulang supaya deterministik. Jangan daftarkan `db/seed` di staging/produksi.
-- ============================================================

begin;

-- 0. KOSONGKAN (anak → induk; cascade karena banyak FK)
truncate table catatan_audit, kejadian_offline, jurnal_buku_kas, akun_buku_kas,
    transaksi_tertunda, pembatasan, setoran, mutasi_saldo, saldo,
    transaksi_item, transaksi, sesi_kasir, titik_kasir, riwayat_stok, stok,
    menu, kategori_menu, kartu, orang_tua, siswa, pengguna,
    pengaturan_kantin, sekolah
    restart identity cascade;

-- 1. SEKOLAH
insert into sekolah (id, nama, npsn, alamat, aktif) values
  (1,  'SMP Negeri 1 Skoolia',  '20260001', 'Jl. Merdeka No. 1',      true),
  (10, 'SMP Negeri 10 Skoolia', '20260010', 'Jl. Diponegoro No. 10', true);

-- 2. PENGGUNA — 12 akun (6 peran x 2 sekolah), password: Kantin#2026
insert into pengguna (id, sekolah_id, nama, username, role, password_hash, aktif) values
  (1,  1,  'Wibisana Bama',     'kepsek1',     'KEPSEK',        '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  (2,  1,  'Sri Wahyuni',       'tu1',         'TATA_USAHA',    '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  (3,  1,  'Deryl Fabiensyah',  'pengelola1',  'PENGELOLA',     '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  (4,  1,  'Ahmad Kasir',       'kasir1',      'PETUGAS',       '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  (5,  1,  'Siti Rahma',        'bendahara1',  'BENDAHARA',     '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  (6,  1,  'Budi Santoso',      'admin1',      'ADMIN_SEKOLAH', '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  (11, 10, 'Hendra Kusuma',     'kepsek10',    'KEPSEK',        '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  (12, 10, 'Rina Marlina',      'tu10',        'TATA_USAHA',    '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  (13, 10, 'Yusuf Hamdani',     'pengelola10', 'PENGELOLA',     '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  (14, 10, 'Rizky Pratama',     'kasir10',     'PETUGAS',       '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  (15, 10, 'Nurul Hidayah',     'bendahara10', 'BENDAHARA',     '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  (16, 10, 'Dewi Anggraini',    'admin10',     'ADMIN_SEKOLAH', '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true);

-- 3. SISWA — 19 siswa
insert into siswa (id, sekolah_id, nis, nisn, nama, kelas, foto_url, aktif) values
  (101,  1,  '24250101', '001010000',  'Raka Aditya Pratama',  '7A', 'https://ui-avatars.com/api/?name=Raka+Aditya+Pratama&size=256&background=0F766E&color=ffffff&bold=true&format=png', true),
  (102,  1,  '24250102', '001020000',  'Nadia Safitri',        '7A', 'https://ui-avatars.com/api/?name=Nadia+Safitri&size=256&background=1D4ED8&color=ffffff&bold=true&format=png', true),
  (103,  1,  '24250103', '001030000',  'Bima Arya Nugraha',    '7B', 'https://ui-avatars.com/api/?name=Bima+Arya+Nugraha&size=256&background=0F766E&color=ffffff&bold=true&format=png', true),
  (104,  1,  '24250104', '001040000',  'Salsabila Putri',      '7B', 'https://ui-avatars.com/api/?name=Salsabila+Putri&size=256&background=1D4ED8&color=ffffff&bold=true&format=png', true),
  (105,  1,  '24250105', '001050000',  'Fajar Ramadhan',       '8A', 'https://ui-avatars.com/api/?name=Fajar+Ramadhan&size=256&background=0F766E&color=ffffff&bold=true&format=png', true),
  (106,  1,  '24250106', '001060000',  'Kirana Dewi Lestari',  '8A', 'https://ui-avatars.com/api/?name=Kirana+Dewi+Lestari&size=256&background=1D4ED8&color=ffffff&bold=true&format=png', true),
  (107,  1,  '24250107', '001070000',  'Abizard',              '8B', 'https://ui-avatars.com/api/?name=Abizard&size=256&background=0F766E&color=ffffff&bold=true&format=png', true),
  (108,  1,  '24250108', '001080000',  'Fajar Nugraha',        '7B', 'https://ui-avatars.com/api/?name=Fajar+Nugraha&size=256&background=1D4ED8&color=ffffff&bold=true&format=png', true),
  (109,  1,  '24250109', '001090000',  'Citra Kirana',         '8A', 'https://ui-avatars.com/api/?name=Citra+Kirana&size=256&background=0F766E&color=ffffff&bold=true&format=png', true),
  (110,  1,  '24250110', '001100000',  'Daffa Alfarizi',       '9A', 'https://ui-avatars.com/api/?name=Daffa+Alfarizi&size=256&background=1D4ED8&color=ffffff&bold=true&format=png', true),
  (12,   1,  '24250012', '001200000',  'Alya Rahmawati',       '7C', 'https://ui-avatars.com/api/?name=Alya+Rahmawati&size=256&background=1D4ED8&color=ffffff&bold=true&format=png', true),
  (13,   1,  '24250013', '001300000',  'Bagas Saputra',        '7C', 'https://ui-avatars.com/api/?name=Bagas+Saputra&size=256&background=0F766E&color=ffffff&bold=true&format=png', true),
  (201,  1,  '24250201', '002010000',  'Rangga Pratama',       '7A', 'https://ui-avatars.com/api/?name=Rangga+Pratama&size=256&background=0F766E&color=ffffff&bold=true&format=png', true),
  (202,  1,  '24250202', '002020000',  'Sinta Maharani',       '7B', 'https://ui-avatars.com/api/?name=Sinta+Maharani&size=256&background=1D4ED8&color=ffffff&bold=true&format=png', true),
  (1001, 10, '24251001', '0010010000', 'Ahmad Fauzan',         '7A', 'https://ui-avatars.com/api/?name=Ahmad+Fauzan&size=256&background=0F766E&color=ffffff&bold=true&format=png', true),
  (1005, 10, '24251005', '0010050000', 'Citra Ayu Lestari',    '7A', 'https://ui-avatars.com/api/?name=Citra+Ayu+Lestari&size=256&background=0F766E&color=ffffff&bold=true&format=png', true),
  (1006, 10, '24251006', '0010060000', 'Dimas Prakoso',        '7B', 'https://ui-avatars.com/api/?name=Dimas+Prakoso&size=256&background=1D4ED8&color=ffffff&bold=true&format=png', true),
  (1007, 10, '24251007', '0010070000', 'Eka Wulandari',        '7B', 'https://ui-avatars.com/api/?name=Eka+Wulandari&size=256&background=0F766E&color=ffffff&bold=true&format=png', true),
  (3001, 10, '24253001', '0030010000', 'Anisa Rahma',          '7A', 'https://ui-avatars.com/api/?name=Anisa+Rahma&size=256&background=0F766E&color=ffffff&bold=true&format=png', true);

-- 4. ORANG TUA — 2 wali per siswa (ayah + ibu)
insert into orang_tua (sekolah_id, siswa_id, nama, telepon, hubungan, punya_aplikasi, aktif)
select s.sekolah_id, s.id, 'Bapak ' || split_part(s.nama, ' ', 1),
       '0812' || lpad(s.id::text, 8, '0'), 'AYAH', true, true
from siswa s
union all
select s.sekolah_id, s.id, 'Ibu ' || split_part(s.nama, ' ', 1),
       '0813' || lpad(s.id::text, 8, '0'), 'IBU', true, true
from siswa s;

-- 5. KARTU — 1 kartu per siswa
insert into kartu (uid, sekolah_id, siswa_id, nomor_kartu, aktif) values
  ('04A1B2C3D4', 1,  101,  'RFID-SISWA-101', true),
  ('04B2C3D4E5', 1,  102,  'RFID-SISWA-102', true),
  ('04C3D4E5F6', 1,  103,  'RFID-SISWA-103', true),
  ('04D4E5F6A1', 1,  104,  'RFID-SISWA-104', true),
  ('04E5F6A1B2', 1,  105,  'RFID-SISWA-105', true),
  ('04F1A2B3C4', 1,  106,  'RFID-SISWA-106', true),
  ('1831574427', 1,  107,  'RFID-SISWA-107', true),
  ('2225414394', 1,  108,  'RFID-SISWA-108', true),
  ('0830267682', 1,  109,  'RFID-SISWA-109', true),
  ('04F5A6B7C8', 1,  110,  'RFID-SISWA-110', true),
  ('04A2B3C4D5', 1,  12,   'RFID-SISWA-012', true),
  ('04B3C4D5E6', 1,  13,   'RFID-SISWA-013', true),
  ('04AA000201', 1,  201,  'UJI-RFID-201',    true),
  ('04AA000202', 1,  202,  'UJI-RFID-202',    true),
  ('DA100001',   10, 1001, 'RFID-SISWA-001', true),
  ('DA100005',   10, 1005, 'RFID-SISWA-005', true),
  ('DA100006',   10, 1006, 'RFID-SISWA-006', true),
  ('DA100007',   10, 1007, 'RFID-SISWA-007', true),
  ('04BB003001', 10, 3001, 'UJI-RFID-3001',   true);

-- 6. KATEGORI MENU
insert into kategori_menu (id, sekolah_id, nama, urutan, is_active) values
  (1,  1,  'Makanan',    1, true),
  (2,  1,  'Minuman',    2, true),
  (3,  1,  'Snack',      3, true),
  (4,  1,  'Alat Tulis', 4, true),
  (11, 10, 'Makanan',    1, true),
  (12, 10, 'Minuman',    2, true),
  (13, 10, 'Snack',      3, true),
  (14, 10, 'Alat Tulis', 4, true);

-- 7. MENU — 12 menu per sekolah
insert into menu (id, sekolah_id, kategori_id, nama, kode_barcode, satuan, foto_url, harga_jual, stok_minimum, is_active) values
  (1,    1,  1,  'Nasi Goreng',  'MNU-0001', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/34/Nasi_goreng_pattaya_20231028_120535.jpg/500px-Nasi_goreng_pattaya_20231028_120535.jpg', 12000, 5,  true),
  (2,    1,  1,  'Mie Ayam',     'MNU-0002', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/82/Mi_ayam_jamur.JPG/500px-Mi_ayam_jamur.JPG', 10000, 5,  true),
  (3,    1,  2,  'Es Teh Manis', 'MNU-0003', 'PCS',   'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d4/Iced_lychee_tea_and_Es_teh_manis_-_Bali_2025-09-25.jpg/500px-Iced_lychee_tea_and_Es_teh_manis_-_Bali_2025-09-25.jpg', 3000, 10, true),
  (4,    1,  2,  'Air Mineral',  'MNU-0004', 'BOTOL', 'https://upload.wikimedia.org/wikipedia/commons/0/02/Stilles_Mineralwasser.jpg', 4000, 10, true),
  (5,    1,  1,  'Ayam Geprek',  'MNU-0005', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/5c/Ayam_Geprek_Uyat.jpg/500px-Ayam_Geprek_Uyat.jpg', 13000, 5,  true),
  (6,    1,  3,  'Keripik',      'MNU-0006', 'PCS',   'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/70/Keripik_sanjay.JPG/500px-Keripik_sanjay.JPG', 5000, 10, true),
  (7,    1,  3,  'Roti Bakar',   'MNU-0007', 'PCS',   'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/1a/Roti_bakar_kekinian.jpg/500px-Roti_bakar_kekinian.jpg', 8000, 5,  true),
  (8,    1,  2,  'Susu Kotak',   'MNU-0008', 'PCS',   'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/f8/2025.04.10_Milk_Glass_Sugar_Bowl_and_Antique_Glass_Creamer.jpg/500px-2025.04.10_Milk_Glass_Sugar_Bowl_and_Antique_Glass_Creamer.jpg', 6000, 10, true),
  (9,    1,  4,  'Pulpen',       'MNU-0009', 'PCS',   'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/7b/Fountain_pen_writing_%28literacy%29.jpg/500px-Fountain_pen_writing_%28literacy%29.jpg', 3000, 20, true),
  (10,   1,  4,  'Buku Tulis',   'MNU-0010', 'PCS',   'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/f0/Notebook_writing.jpg/500px-Notebook_writing.jpg', 7000, 10, true),
  (11,   1,  1,  'Bakso',        'MNU-0011', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/6/6e/Bakso_Indonesian_Meatball_Soup_from_Solo.jpg/500px-Bakso_Indonesian_Meatball_Soup_from_Solo.jpg', 11000, 5,  true),
  (12,   1,  2,  'Jus Jeruk',    'MNU-0012', 'PCS',   'https://thumb.wikimedia.org/wikipedia/commons/thumb/6/67/Orange_juice_1_edit1.jpg/500px-Orange_juice_1_edit1.jpg', 7000, 5,  true),
  (1000, 10, 11, 'Nasi Goreng',  'MNU-1000', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/34/Nasi_goreng_pattaya_20231028_120535.jpg/500px-Nasi_goreng_pattaya_20231028_120535.jpg', 12000, 5,  true),
  (1001, 10, 11, 'Mie Ayam',     'MNU-1001', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/82/Mi_ayam_jamur.JPG/500px-Mi_ayam_jamur.JPG', 10000, 5,  true),
  (1002, 10, 12, 'Es Teh Manis', 'MNU-1002', 'PCS',   'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d4/Iced_lychee_tea_and_Es_teh_manis_-_Bali_2025-09-25.jpg/500px-Iced_lychee_tea_and_Es_teh_manis_-_Bali_2025-09-25.jpg', 3000, 10, true),
  (1003, 10, 12, 'Air Mineral',  'MNU-1003', 'BOTOL', 'https://upload.wikimedia.org/wikipedia/commons/0/02/Stilles_Mineralwasser.jpg', 4000, 10, true),
  (1004, 10, 11, 'Ayam Geprek',  'MNU-1004', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/5c/Ayam_Geprek_Uyat.jpg/500px-Ayam_Geprek_Uyat.jpg', 13000, 5,  true),
  (1005, 10, 13, 'Keripik',      'MNU-1005', 'PCS',   'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/70/Keripik_sanjay.JPG/500px-Keripik_sanjay.JPG', 5000, 10, true),
  (1006, 10, 13, 'Roti Bakar',   'MNU-1006', 'PCS',   'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/1a/Roti_bakar_kekinian.jpg/500px-Roti_bakar_kekinian.jpg', 8000, 5,  true),
  (1007, 10, 12, 'Susu Kotak',   'MNU-1007', 'PCS',   'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/f8/2025.04.10_Milk_Glass_Sugar_Bowl_and_Antique_Glass_Creamer.jpg/500px-2025.04.10_Milk_Glass_Sugar_Bowl_and_Antique_Glass_Creamer.jpg', 6000, 10, true),
  (1008, 10, 14, 'Pulpen',       'MNU-1008', 'PCS',   'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/7b/Fountain_pen_writing_%28literacy%29.jpg/500px-Fountain_pen_writing_%28literacy%29.jpg', 3000, 20, true),
  (1009, 10, 14, 'Buku Tulis',   'MNU-1009', 'PCS',   'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/f0/Notebook_writing.jpg/500px-Notebook_writing.jpg', 7000, 10, true),
  (1010, 10, 11, 'Bakso',        'MNU-1010', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/6/6e/Bakso_Indonesian_Meatball_Soup_from_Solo.jpg/500px-Bakso_Indonesian_Meatball_Soup_from_Solo.jpg', 11000, 5,  true),
  (1011, 10, 12, 'Jus Jeruk',    'MNU-1011', 'PCS',   'https://thumb.wikimedia.org/wikipedia/commons/thumb/6/67/Orange_juice_1_edit1.jpg/500px-Orange_juice_1_edit1.jpg', 7000, 5,  true);

-- 8. STOK — baris dibuat trigger menu; di sini hanya HPP & stok minimum.
--    Nilai `stok` dihitung ulang oleh trigger sinkron_stok_dari_mutasi()
--    dari ledger riwayat_stok (bagian 16), jadi angka di sini bukan sumber kebenaran.
insert into stok (menu_id, sekolah_id, stok, hpp, stok_minimum) values
  (1,    1,  21, 7000, 5),  (2,    1,  39, 6000, 5),  (3,    1,  19, 1000, 10),
  (4,    1,  14, 2000, 10), (5,    1,  37, 9000, 5),  (6,    1,  39, 3000, 10),
  (7,    1,  23, 5000, 5),  (8,    1,  36, 4000, 10), (9,    1,  44, 2000, 20),
  (10,   1,  19, 5000, 10), (11,   1,  37, 7000, 5),  (12,   1,  26, 5000, 5),
  (1000, 10, 21, 7000, 5),  (1001, 10, 39, 6000, 5),  (1002, 10, 19, 1000, 10),
  (1003, 10, 14, 2000, 10), (1004, 10, 37, 9000, 5),  (1005, 10, 39, 3000, 10),
  (1006, 10, 23, 5000, 5),  (1007, 10, 36, 4000, 10), (1008, 10, 44, 2000, 20),
  (1009, 10, 19, 5000, 10), (1010, 10, 37, 7000, 5),  (1011, 10, 26, 5000, 5)
on conflict (menu_id) do update
    set hpp          = excluded.hpp,
        stok_minimum = excluded.stok_minimum;

-- 9. TITIK KASIR
insert into titik_kasir (id, sekolah_id, kode, nama, is_active) values
  (1,  1,  'DEMO1',  'Kasir Kantin Utama', true),
  (2,  1,  'DEMO2',  'Kasir Kelas 7',      true),
  (11, 10, 'FE10-1', 'Kasir Kantin 10 A',  true),
  (12, 10, 'FE10-2', 'Kasir Kantin 10 B',  true);

-- 10. PENGATURAN KANTIN
insert into pengaturan_kantin (sekolah_id, nama_kantin, jam_tutup_otomatis, konfirmasi_manual,
                              durasi_foto_detik, min_topup, maks_topup, batas_saldo_siswa,
                              kebijakan_saldo_mengendap, ambang_hari, diperbarui_oleh) values
  (1,  'Kantin Sekolah',    '15:00', false, 3, 5000, 500000, 1000000, 'REFUND', 90, 2),
  (10, 'Kantin Sekolah 10', '14:30', false, 3, 5000, 300000,  750000, 'REFUND', 90, 12);

-- 11. AKUN BUKU KAS
insert into akun_buku_kas (id, sekolah_id, nama, tipe, aktif) values
  (1,  1,  'Penjualan Kantin', 'MASUK',  true),
  (2,  1,  'Top Up Saldo',     'MASUK',  true),
  (3,  1,  'Belanja Bahan',    'KELUAR', true),
  (4,  1,  'Operasional',      'KELUAR', true),
  (11, 10, 'Penjualan Kantin', 'MASUK',  true),
  (12, 10, 'Top Up Saldo',     'MASUK',  true),
  (13, 10, 'Belanja Bahan',    'KELUAR', true),
  (14, 10, 'Operasional',      'KELUAR', true);

-- 12. SALDO SISWA (konsisten dengan mutasi di bawah)
insert into saldo (sekolah_id, siswa_id, saldo) values
  (1, 101, 85000), (1, 102, 50000), (1, 103, 50000), (1, 104, 50000),
  (1, 105, 50000), (1, 106, 50000), (1, 107, 50000), (1, 108, 50000),
  (1, 109, 50000), (1, 110, 50000), (1, 12,  50000), (1, 13,  50000),
  (1, 201, 0),     (1, 202, 0),
  (10, 1001, 73000),
  (10, 1005, 47000),
  (10, 1006, 50000), (10, 1007, 50000), (10, 3001, 0);

-- 13. MUTASI SALDO — top-up awal + pembelanjaan di sesi tertutup
insert into mutasi_saldo (id, sekolah_id, siswa_id, arah, jenis, nominal, saldo_setelah,
                          keterangan, aktor_id, waktu) values
  (1,  1,  101,  'KREDIT', 'TOPUP_TUNAI', 85000, 85000, 'Saldo awal seed', 2,  now() - interval '7 days'),
  (2,  1,  102,  'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 2,  now() - interval '7 days'),
  (3,  1,  103,  'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 2,  now() - interval '7 days'),
  (4,  1,  104,  'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 2,  now() - interval '7 days'),
  (5,  1,  105,  'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 2,  now() - interval '7 days'),
  (6,  1,  106,  'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 2,  now() - interval '7 days'),
  (7,  1,  107,  'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 2,  now() - interval '7 days'),
  (8,  1,  108,  'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 2,  now() - interval '7 days'),
  (9,  1,  109,  'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 2,  now() - interval '7 days'),
  (10, 1,  110,  'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 2,  now() - interval '7 days'),
  (11, 1,  12,   'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 2,  now() - interval '7 days'),
  (12, 1,  13,   'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 2,  now() - interval '7 days'),
  (13, 10, 1001, 'KREDIT', 'TOPUP_TUNAI', 85000, 85000, 'Saldo awal seed', 12, now() - interval '7 days'),
  (14, 10, 1005, 'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 12, now() - interval '7 days'),
  (15, 10, 1006, 'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 12, now() - interval '7 days'),
  (16, 10, 1007, 'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 12, now() - interval '7 days'),
  (17, 10, 1001, 'DEBIT',  'PENJUALAN',   12000, 73000, 'Belanja Nasi Goreng x1', 14, now() - interval '1 day'),
  (18, 10, 1005, 'DEBIT',  'PENJUALAN',    3000, 47000, 'Belanja Es Teh Manis x1', 14, now() - interval '1 day');

-- 14. SESI KASIR — 2 terbuka hari ini, 1 tertutup kemarin
insert into sesi_kasir (id, sekolah_id, titik_kasir_id, tanggal, status,
                        total_bruto, total_void, total_bersih,
                        dibuka_at, ditutup_at, ditutup_oleh, auto_tutup, jurnal_buku_kas) values
  (9001, 1,  1,  current_date,     'TERBUKA', 0,     0, 0,
   now() - interval '2 hours', null, null, false, false),
  (9002, 10, 11, current_date,     'TERBUKA', 0,     0, 0,
   now() - interval '2 hours', null, null, false, false),
  (9003, 10, 11, current_date - 1, 'DITUTUP', 15000, 0, 15000,
   now() - interval '1 day' - interval '6 hours', now() - interval '1 day', 14, false, false);

-- 15. TRANSAKSI contoh pada sesi tertutup
insert into transaksi (id, idempotency_key, sekolah_id, sesi_kasir_id, titik_kasir_id,
                       kartu_uid, petugas_id, siswa_id, total, total_hpp, status, metode_bayar, waktu) values
  (9101, 'seed-trx-9101', 10, 9003, 11, 'DA100001', 14, 1001, 12000, 7000, 'SUKSES', 'SALDO', now() - interval '1 day' - interval '5 hours'),
  (9102, 'seed-trx-9102', 10, 9003, 11, 'DA100005', 14, 1005,  3000, 1000, 'SUKSES', 'SALDO', now() - interval '1 day' - interval '4 hours');

insert into transaksi_item (id, transaksi_id, menu_id, nama_menu, kategori_id,
                            harga_jual, qty, hpp_snapshot, subtotal) values
  (9201, 9101, 1000, 'Nasi Goreng',  11, 12000, 1, 7000, 12000),
  (9202, 9102, 1002, 'Es Teh Manis', 12,  3000, 1, 1000,  3000);

-- 16. RIWAYAT STOK — sumber kebenaran stok (trigger menghitung ulang `stok`)
--     16a. stok awal tiap menu (BARANG_MASUK) — total = stok akhir + yang terjual
insert into riwayat_stok (id, sekolah_id, menu_id, arah, jenis, qty, stok_setelah,
                          hpp_snapshot, harga_beli_satuan, referensi_tipe, aktor_id, aktor_nama, waktu)
select 9300 + row_number() over (order by v.sekolah_id, v.menu_id),
       v.sekolah_id, v.menu_id, 'MASUK', 'BARANG_MASUK', v.qty, v.qty,
       v.hpp, v.hpp, 'SEED',
       case when v.sekolah_id = 1 then 3 else 13 end,
       'Seed stok awal', now() - interval '30 days'
from (values
    (1, 1, 21, 7000), (2, 1, 39, 6000), (3, 1, 19, 1000),
    (4, 1, 14, 2000), (5, 1, 37, 9000), (6, 1, 39, 3000),
    (7, 1, 23, 5000), (8, 1, 36, 4000), (9, 1, 44, 2000),
    (10, 1, 19, 5000), (11, 1, 37, 7000), (12, 1, 26, 5000),
    (1000, 10, 21, 7000), (1001, 10, 39, 6000), (1002, 10, 19, 1000),
    (1003, 10, 14, 2000), (1004, 10, 37, 9000), (1005, 10, 39, 3000),
    (1006, 10, 23, 5000), (1007, 10, 36, 4000), (1008, 10, 44, 2000),
    (1009, 10, 19, 5000), (1010, 10, 37, 7000), (1011, 10, 26, 5000)
) as v(menu_id, sekolah_id, qty, hpp);

--     16b. penjualan di sesi tertutup (KELUAR) — mengurangi stok 1000 & 1002
insert into riwayat_stok (id, sekolah_id, menu_id, arah, jenis, qty, stok_setelah,
                          hpp_snapshot, harga_beli_satuan, transaksi_id, aktor_id, aktor_nama, waktu) values
  (9401, 10, 1000, 'KELUAR', 'PENJUALAN', 1, 20, 7000, 7000, 9101, 14, 'Rizky Pratama', now() - interval '1 day' - interval '5 hours'),
  (9402, 10, 1002, 'KELUAR', 'PENJUALAN', 1, 18, 1000, 1000, 9102, 14, 'Rizky Pratama', now() - interval '1 day' - interval '4 hours');

-- 17. SINKRONKAN SEQUENCE (id diisi manual di atas)
select setval(pg_get_serial_sequence('pengguna', 'id'),       (select max(id) from pengguna));
select setval(pg_get_serial_sequence('kategori_menu', 'id'),  (select max(id) from kategori_menu));
select setval(pg_get_serial_sequence('menu', 'id'),           (select max(id) from menu));
select setval(pg_get_serial_sequence('titik_kasir', 'id'),    (select max(id) from titik_kasir));
select setval(pg_get_serial_sequence('akun_buku_kas', 'id'),  (select max(id) from akun_buku_kas));
select setval(pg_get_serial_sequence('orang_tua', 'id'),      (select max(id) from orang_tua));
select setval(pg_get_serial_sequence('mutasi_saldo', 'id'),   (select max(id) from mutasi_saldo));
select setval(pg_get_serial_sequence('sesi_kasir', 'id'),     (select max(id) from sesi_kasir));
select setval(pg_get_serial_sequence('transaksi', 'id'),      (select max(id) from transaksi));
select setval(pg_get_serial_sequence('transaksi_item', 'id'), (select max(id) from transaksi_item));
select setval(pg_get_serial_sequence('riwayat_stok', 'id'),   (select max(id) from riwayat_stok));

commit;

-- =====================================================================
-- RINGKASAN: sekolah 2 | pengguna 12 | siswa 19 | orang_tua 38 | kartu 19
--   kategori_menu 8 | menu 24 | stok 24 | titik_kasir 4
--   pengaturan_kantin 2 | akun_buku_kas 8 | saldo 19 | mutasi_saldo 18
--   sesi_kasir 3 | transaksi 2 | transaksi_item 2 | riwayat_stok 2
-- =====================================================================
