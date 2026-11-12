-- ============================================================
-- R__seed_dev.sql — Data dev/demo untuk skema v2 (23 tabel)
-- ------------------------------------------------------------
-- ⚠️ BUKAN data produksi.
--    Flyway REPEATABLE migration: hanya jalan bila `db/seed` ikut
--    didaftarkan di `spring.flyway.locations` (application-local).
--    CI & seluruh test tidak memuatnya secara default.
--
-- Password semua akun: Kantin#2026
--
-- Isi:
--   sekolah 2 · pengguna 12 (6 peran x 2 sekolah) · siswa 19 (foto terisi)
--   orang_tua 38 (2 wali per siswa) · kartu 19 · kategori_menu 8
--   menu 24 · stok 24 · titik_kasir 4 · pengaturan_kantin 2
--   akun_buku_kas 8 · saldo 19
--   sesi_kasir 6 · transaksi 36 · transaksi_item 77 · mutasi_saldo 53
--   riwayat_stok 103 · jurnal_buku_kas 39 · setoran 2
--   kejadian_offline 2 · catatan_audit 6 · pembatasan 3 · transaksi_tertunda 2
--
-- Riwayat 3 hari terakhir memakai `current_date - n`, jadi dashboard, laporan
-- harian, dan rekap sesi selalu ada isinya tanpa perlu transaksi manual.
--
-- Sifat: DESTRUKTIF untuk data dev — tabel dikosongkan dulu, lalu diisi
-- ulang, supaya hasilnya deterministik dan screenshot FE tidak berubah
-- antar-run. Jangan daftarkan folder db/seed di staging/produksi.
-- ============================================================

begin;

-- kosongkan dulu (deterministik). cascade karena banyak FK.
truncate table catatan_audit, kejadian_offline, jurnal_buku_kas, akun_buku_kas, transaksi_tertunda, pembatasan, setoran, mutasi_saldo, saldo, transaksi_item, transaksi, sesi_kasir, titik_kasir, riwayat_stok, stok, menu, kategori_menu, kartu, orang_tua, siswa, pengguna, pengaturan_kantin, sekolah restart identity cascade;

-- 1. SEKOLAH — logo_url berisi URL gambar (pola sama dengan siswa.foto_url)
insert into sekolah (id, nama, npsn, alamat, aktif, logo_url) values
  (1,  'SMP Negeri 1 Skoolia',  '20260001', 'Jl. Merdeka No. 1',      true,
   'https://ui-avatars.com/api/?name=SMPN+1+Skoolia&size=256&background=1D4ED8&color=ffffff&bold=true&format=png'),
  (10, 'SMP Negeri 10 Skoolia', '20260010', 'Jl. Diponegoro No. 10', true,
   'https://ui-avatars.com/api/?name=SMPN+10+Skoolia&size=256&background=0F766E&color=ffffff&bold=true&format=png');

-- 2. PENGGUNA — 12 akun, password: Kantin#2026
insert into pengguna (id, sekolah_id, nama, username, role, password_hash, aktif) values
  (1, 1, 'Wibisana Bama', 'kepsek1', 'KEPSEK', '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  (2, 1, 'Sri Wahyuni', 'tu1', 'TATA_USAHA', '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  (3, 1, 'Deryl Fabiensyah', 'pengelola1', 'PENGELOLA', '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  (4, 1, 'Ahmad Kasir', 'kasir1', 'PETUGAS', '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  (5, 1, 'Siti Rahma', 'bendahara1', 'BENDAHARA', '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  (6, 1, 'Budi Santoso', 'admin1', 'ADMIN_SEKOLAH', '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  (11, 10, 'Hendra Kusuma', 'kepsek10', 'KEPSEK', '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  (12, 10, 'Rina Marlina', 'tu10', 'TATA_USAHA', '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  (13, 10, 'Yusuf Hamdani', 'pengelola10', 'PENGELOLA', '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  (14, 10, 'Rizky Pratama', 'kasir10', 'PETUGAS', '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  (15, 10, 'Nurul Hidayah', 'bendahara10', 'BENDAHARA', '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  (16, 10, 'Dewi Anggraini', 'admin10', 'ADMIN_SEKOLAH', '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true);

-- 3. SISWA — 19 siswa, foto_url ikut pola menu.foto_url
insert into siswa (id, sekolah_id, nis, nisn, nama, kelas, foto_url, aktif) values
  (101, 1, '24250101', '001010000', 'Raka Aditya Pratama', '7A', 'https://ui-avatars.com/api/?name=Raka+Aditya+Pratama&size=256&background=0F766E&color=ffffff&bold=true&format=png', true),
  (102, 1, '24250102', '001020000', 'Nadia Safitri', '7A', 'https://ui-avatars.com/api/?name=Nadia+Safitri&size=256&background=1D4ED8&color=ffffff&bold=true&format=png', true),
  (103, 1, '24250103', '001030000', 'Bima Arya Nugraha', '7B', 'https://ui-avatars.com/api/?name=Bima+Arya+Nugraha&size=256&background=0F766E&color=ffffff&bold=true&format=png', true),
  (104, 1, '24250104', '001040000', 'Salsabila Putri', '7B', 'https://ui-avatars.com/api/?name=Salsabila+Putri&size=256&background=1D4ED8&color=ffffff&bold=true&format=png', true),
  (105, 1, '24250105', '001050000', 'Fajar Ramadhan', '8A', 'https://ui-avatars.com/api/?name=Fajar+Ramadhan&size=256&background=0F766E&color=ffffff&bold=true&format=png', true),
  (106, 1, '24250106', '001060000', 'Kirana Dewi Lestari', '8A', 'https://ui-avatars.com/api/?name=Kirana+Dewi+Lestari&size=256&background=1D4ED8&color=ffffff&bold=true&format=png', true),
  (107, 1, '24250107', '001070000', 'Abizard', '8B', 'https://ui-avatars.com/api/?name=Abizard&size=256&background=0F766E&color=ffffff&bold=true&format=png', true),
  (108, 1, '24250108', '001080000', 'Fajar Nugraha', '7B', 'https://ui-avatars.com/api/?name=Fajar+Nugraha&size=256&background=1D4ED8&color=ffffff&bold=true&format=png', true),
  (109, 1, '24250109', '001090000', 'Citra Kirana', '8A', 'https://ui-avatars.com/api/?name=Citra+Kirana&size=256&background=0F766E&color=ffffff&bold=true&format=png', true),
  (110, 1, '24250110', '001100000', 'Daffa Alfarizi', '9A', 'https://ui-avatars.com/api/?name=Daffa+Alfarizi&size=256&background=1D4ED8&color=ffffff&bold=true&format=png', true),
  (12, 1, '24250012', '00120000', 'Alya Rahmawati', '7C', 'https://ui-avatars.com/api/?name=Alya+Rahmawati&size=256&background=1D4ED8&color=ffffff&bold=true&format=png', true),
  (13, 1, '24250013', '00130000', 'Bagas Saputra', '7C', 'https://ui-avatars.com/api/?name=Bagas+Saputra&size=256&background=0F766E&color=ffffff&bold=true&format=png', true),
  (201, 1, '24250201', '002010000', 'Rangga Pratama', '7A', 'https://ui-avatars.com/api/?name=Rangga+Pratama&size=256&background=0F766E&color=ffffff&bold=true&format=png', true),
  (202, 1, '24250202', '002020000', 'Sinta Maharani', '7B', 'https://ui-avatars.com/api/?name=Sinta+Maharani&size=256&background=1D4ED8&color=ffffff&bold=true&format=png', true),
  (1001, 10, '24251001', '0010010000', 'Ahmad Fauzan', '7A', 'https://ui-avatars.com/api/?name=Ahmad+Fauzan&size=256&background=0F766E&color=ffffff&bold=true&format=png', true),
  (1005, 10, '24251005', '0010050000', 'Citra Ayu Lestari', '7A', 'https://ui-avatars.com/api/?name=Citra+Ayu+Lestari&size=256&background=0F766E&color=ffffff&bold=true&format=png', true),
  (1006, 10, '24251006', '0010060000', 'Dimas Prakoso', '7B', 'https://ui-avatars.com/api/?name=Dimas+Prakoso&size=256&background=1D4ED8&color=ffffff&bold=true&format=png', true),
  (1007, 10, '24251007', '0010070000', 'Eka Wulandari', '7B', 'https://ui-avatars.com/api/?name=Eka+Wulandari&size=256&background=0F766E&color=ffffff&bold=true&format=png', true),
  (3001, 10, '24253001', '0030010000', 'Anisa Rahma', '7A', 'https://ui-avatars.com/api/?name=Anisa+Rahma&size=256&background=0F766E&color=ffffff&bold=true&format=png', true);

-- 4. ORANG TUA — 2 wali per siswa (ayah + ibu)
insert into orang_tua (sekolah_id, siswa_id, nama, telepon, hubungan, punya_aplikasi, aktif) values
  (1, 101, 'Bapak Pratama', '081200000101', 'AYAH', true, true),
  (1, 101, 'Ibu Pratama',  '081300000101', 'IBU',  true, true),
  (1, 102, 'Bapak Safitri', '081200000102', 'AYAH', true, true),
  (1, 102, 'Ibu Safitri',  '081300000102', 'IBU',  true, true),
  (1, 103, 'Bapak Nugraha', '081200000103', 'AYAH', true, true),
  (1, 103, 'Ibu Nugraha',  '081300000103', 'IBU',  true, true),
  (1, 104, 'Bapak Putri', '081200000104', 'AYAH', true, true),
  (1, 104, 'Ibu Putri',  '081300000104', 'IBU',  true, true),
  (1, 105, 'Bapak Ramadhan', '081200000105', 'AYAH', true, true),
  (1, 105, 'Ibu Ramadhan',  '081300000105', 'IBU',  true, true),
  (1, 106, 'Bapak Lestari', '081200000106', 'AYAH', true, true),
  (1, 106, 'Ibu Lestari',  '081300000106', 'IBU',  true, true),
  (1, 107, 'Bapak Abizard', '081200000107', 'AYAH', true, true),
  (1, 107, 'Ibu Abizard',  '081300000107', 'IBU',  true, true),
  (1, 108, 'Bapak Nugraha', '081200000108', 'AYAH', true, true),
  (1, 108, 'Ibu Nugraha',  '081300000108', 'IBU',  true, true),
  (1, 109, 'Bapak Kirana', '081200000109', 'AYAH', true, true),
  (1, 109, 'Ibu Kirana',  '081300000109', 'IBU',  true, true),
  (1, 110, 'Bapak Alfarizi', '081200000110', 'AYAH', true, true),
  (1, 110, 'Ibu Alfarizi',  '081300000110', 'IBU',  true, true),
  (1, 12, 'Bapak Rahmawati', '081200000012', 'AYAH', true, true),
  (1, 12, 'Ibu Rahmawati',  '081300000012', 'IBU',  true, true),
  (1, 13, 'Bapak Saputra', '081200000013', 'AYAH', true, true),
  (1, 13, 'Ibu Saputra',  '081300000013', 'IBU',  true, true),
  (1, 201, 'Bapak Pratama', '081200000201', 'AYAH', true, true),
  (1, 201, 'Ibu Pratama',  '081300000201', 'IBU',  true, true),
  (1, 202, 'Bapak Maharani', '081200000202', 'AYAH', true, true),
  (1, 202, 'Ibu Maharani',  '081300000202', 'IBU',  true, true),
  (10, 1001, 'Bapak Fauzan', '081200001001', 'AYAH', true, true),
  (10, 1001, 'Ibu Fauzan',  '081300001001', 'IBU',  true, true),
  (10, 1005, 'Bapak Lestari', '081200001005', 'AYAH', true, true),
  (10, 1005, 'Ibu Lestari',  '081300001005', 'IBU',  true, true),
  (10, 1006, 'Bapak Prakoso', '081200001006', 'AYAH', true, true),
  (10, 1006, 'Ibu Prakoso',  '081300001006', 'IBU',  false, true),
  (10, 1007, 'Bapak Wulandari', '081200001007', 'AYAH', true, true),
  (10, 1007, 'Ibu Wulandari',  '081300001007', 'IBU',  false, true),
  (10, 3001, 'Bapak Rahma', '081200003001', 'AYAH', true, true),
  (10, 3001, 'Ibu Rahma',  '081300003001', 'IBU',  false, true);

-- 5. KARTU — 1 kartu per siswa
insert into kartu (uid, sekolah_id, siswa_id, nomor_kartu, aktif) values
  ('04A1B2C3D4', 1, 101, 'RFID-SISWA-101', true),
  ('04B2C3D4E5', 1, 102, 'RFID-SISWA-102', true),
  ('04C3D4E5F6', 1, 103, 'RFID-SISWA-103', true),
  ('04D4E5F6A1', 1, 104, 'RFID-SISWA-104', true),
  ('04E5F6A1B2', 1, 105, 'RFID-SISWA-105', true),
  ('04F1A2B3C4', 1, 106, 'RFID-SISWA-106', true),
  ('1831574427', 1, 107, 'RFID-SISWA-107', true),
  ('2225414394', 1, 108, 'RFID-SISWA-108', true),
  ('0830267682', 1, 109, 'RFID-SISWA-109', true),
  ('04F5A6B7C8', 1, 110, 'RFID-SISWA-110', true),
  ('04A2B3C4D5', 1, 12, 'RFID-SISWA-012', true),
  ('04B3C4D5E6', 1, 13, 'RFID-SISWA-013', true),
  ('04AA000201', 1, 201, 'UJI-RFID-201', true),
  ('04AA000202', 1, 202, 'UJI-RFID-202', true),
  ('DA100001', 10, 1001, 'RFID-SISWA-001', true),
  ('DA100005', 10, 1005, 'RFID-SISWA-005', true),
  ('DA100006', 10, 1006, 'RFID-SISWA-006', true),
  ('DA100007', 10, 1007, 'RFID-SISWA-007', true),
  ('04BB003001', 10, 3001, 'UJI-RFID-3001', true);

-- 6. KATEGORI MENU
insert into kategori_menu (id, sekolah_id, nama, urutan, is_active) values
  (1, 1, 'Makanan', 1, true),
  (2, 1, 'Minuman', 2, true),
  (3, 1, 'Snack', 3, true),
  (4, 1, 'Alat Tulis', 4, true),
  (11, 10, 'Makanan', 1, true),
  (12, 10, 'Minuman', 2, true),
  (13, 10, 'Snack', 3, true),
  (14, 10, 'Alat Tulis', 4, true);

-- 7. MENU — 12 menu per sekolah (foto_url dari data lama)
insert into menu (id, sekolah_id, kategori_id, nama, kode_barcode, satuan, foto_url, harga_jual, stok_minimum, is_active) values
  (1, 1, 1, 'Nasi Goreng', 'MNU-0001', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/34/Nasi_goreng_pattaya_20231028_120535.jpg/500px-Nasi_goreng_pattaya_20231028_120535.jpg', 12000, 5, true),
  (2, 1, 1, 'Mie Ayam', 'MNU-0002', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/82/Mi_ayam_jamur.JPG/500px-Mi_ayam_jamur.JPG', 10000, 5, true),
  (3, 1, 2, 'Es Teh Manis', 'MNU-0003', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d4/Iced_lychee_tea_and_Es_teh_manis_-_Bali_2025-09-25.jpg/500px-Iced_lychee_tea_and_Es_teh_manis_-_Bali_2025-09-25.jpg', 3000, 10, true),
  (4, 1, 2, 'Air Mineral', 'MNU-0004', 'BOTOL', 'https://upload.wikimedia.org/wikipedia/commons/0/02/Stilles_Mineralwasser.jpg', 4000, 10, true),
  (5, 1, 1, 'Ayam Geprek', 'MNU-0005', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/5c/Ayam_Geprek_Uyat.jpg/500px-Ayam_Geprek_Uyat.jpg', 13000, 5, true),
  (6, 1, 3, 'Keripik', 'MNU-0006', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/70/Keripik_sanjay.JPG/500px-Keripik_sanjay.JPG', 5000, 10, true),
  (7, 1, 3, 'Roti Bakar', 'MNU-0007', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/1a/Roti_bakar_kekinian.jpg/500px-Roti_bakar_kekinian.jpg', 8000, 5, true),
  (8, 1, 2, 'Susu Kotak', 'MNU-0008', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/f8/2025.04.10_Milk_Glass_Sugar_Bowl_and_Antique_Glass_Creamer.jpg/500px-2025.04.10_Milk_Glass_Sugar_Bowl_and_Antique_Glass_Creamer.jpg', 6000, 10, true),
  (9, 1, 4, 'Pulpen', 'MNU-0009', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/7b/Fountain_pen_writing_%28literacy%29.jpg/500px-Fountain_pen_writing_%28literacy%29.jpg', 3000, 20, true),
  (10, 1, 4, 'Buku Tulis', 'MNU-0010', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/f0/Notebook_writing.jpg/500px-Notebook_writing.jpg', 7000, 10, true),
  (11, 1, 1, 'Bakso', 'MNU-0011', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/6/6e/Bakso_Indonesian_Meatball_Soup_from_Solo.jpg/500px-Bakso_Indonesian_Meatball_Soup_from_Solo.jpg', 11000, 5, true),
  (12, 1, 2, 'Jus Jeruk', 'MNU-0012', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/6/67/Orange_juice_1_edit1.jpg/500px-Orange_juice_1_edit1.jpg', 7000, 5, true),
  (1000, 10, 11, 'Nasi Goreng', 'MNU-1000', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/34/Nasi_goreng_pattaya_20231028_120535.jpg/500px-Nasi_goreng_pattaya_20231028_120535.jpg', 12000, 5, true),
  (1001, 10, 11, 'Mie Ayam', 'MNU-1001', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/82/Mi_ayam_jamur.JPG/500px-Mi_ayam_jamur.JPG', 10000, 5, true),
  (1002, 10, 12, 'Es Teh Manis', 'MNU-1002', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d4/Iced_lychee_tea_and_Es_teh_manis_-_Bali_2025-09-25.jpg/500px-Iced_lychee_tea_and_Es_teh_manis_-_Bali_2025-09-25.jpg', 3000, 10, true),
  (1003, 10, 12, 'Air Mineral', 'MNU-1003', 'BOTOL', 'https://upload.wikimedia.org/wikipedia/commons/0/02/Stilles_Mineralwasser.jpg', 4000, 10, true),
  (1004, 10, 11, 'Ayam Geprek', 'MNU-1004', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/5c/Ayam_Geprek_Uyat.jpg/500px-Ayam_Geprek_Uyat.jpg', 13000, 5, true),
  (1005, 10, 13, 'Keripik', 'MNU-1005', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/70/Keripik_sanjay.JPG/500px-Keripik_sanjay.JPG', 5000, 10, true),
  (1006, 10, 13, 'Roti Bakar', 'MNU-1006', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/1a/Roti_bakar_kekinian.jpg/500px-Roti_bakar_kekinian.jpg', 8000, 5, true),
  (1007, 10, 12, 'Susu Kotak', 'MNU-1007', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/f8/2025.04.10_Milk_Glass_Sugar_Bowl_and_Antique_Glass_Creamer.jpg/500px-2025.04.10_Milk_Glass_Sugar_Bowl_and_Antique_Glass_Creamer.jpg', 6000, 10, true),
  (1008, 10, 14, 'Pulpen', 'MNU-1008', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/7b/Fountain_pen_writing_%28literacy%29.jpg/500px-Fountain_pen_writing_%28literacy%29.jpg', 3000, 20, true),
  (1009, 10, 14, 'Buku Tulis', 'MNU-1009', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/f0/Notebook_writing.jpg/500px-Notebook_writing.jpg', 7000, 10, true),
  (1010, 10, 11, 'Bakso', 'MNU-1010', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/6/6e/Bakso_Indonesian_Meatball_Soup_from_Solo.jpg/500px-Bakso_Indonesian_Meatball_Soup_from_Solo.jpg', 11000, 5, true),
  (1011, 10, 12, 'Jus Jeruk', 'MNU-1011', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/6/67/Orange_juice_1_edit1.jpg/500px-Orange_juice_1_edit1.jpg', 7000, 5, true);

-- 8. STOK — 1 baris per menu
insert into stok (menu_id, sekolah_id, stok, hpp, stok_minimum) values
  (1, 1, 21, 7000, 5),
  (2, 1, 39, 6000, 5),
  (3, 1, 19, 1000, 10),
  (4, 1, 14, 2000, 10),
  (5, 1, 37, 9000, 5),
  (6, 1, 39, 3000, 10),
  (7, 1, 23, 5000, 5),
  (8, 1, 36, 4000, 10),
  (9, 1, 44, 2000, 20),
  (10, 1, 19, 5000, 10),
  (11, 1, 37, 7000, 5),
  (12, 1, 26, 5000, 5),
  (1000, 10, 21, 7000, 5),
  (1001, 10, 39, 6000, 5),
  (1002, 10, 19, 1000, 10),
  (1003, 10, 14, 2000, 10),
  (1004, 10, 37, 9000, 5),
  (1005, 10, 39, 3000, 10),
  (1006, 10, 23, 5000, 5),
  (1007, 10, 36, 4000, 10),
  (1008, 10, 44, 2000, 20),
  (1009, 10, 19, 5000, 10),
  (1010, 10, 37, 7000, 5),
  (1011, 10, 26, 5000, 5)
on conflict (menu_id) do update
    set stok         = excluded.stok,
        hpp          = excluded.hpp,
        stok_minimum = excluded.stok_minimum;

-- 9. TITIK KASIR
insert into titik_kasir (id, sekolah_id, kode, nama, is_active) values
  (1,  1,  'DEMO1',  'Kasir Kantin Utama', true),
  (2,  1,  'DEMO2',  'Kasir Kelas 7',      true),
  (11, 10, 'FE10-1', 'Kasir Kantin 10 A',  true),
  (12, 10, 'FE10-2', 'Kasir Kantin 10 B',  true);

-- 10. PENGATURAN KANTIN
insert into pengaturan_kantin (sekolah_id, nama_kantin, jam_tutup_otomatis, konfirmasi_manual, durasi_foto_detik, min_topup, maks_topup, batas_saldo_siswa, kebijakan_saldo_mengendap, ambang_hari, diperbarui_oleh) values
  (1,  'Kantin Sekolah',     '15:00', false, 3, 5000, 500000, 1000000, 'REFUND', 90, 2),
  (10, 'Kantin Sekolah 10',  '14:30', false, 3, 5000, 300000,  750000, 'REFUND', 90, 12);

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

-- 12. SALDO — 19 baris (nilai di sini akan disinkronkan ulang oleh trigger
--     `trg_mutasi_saldo_sinkron` saat mutasi_saldo diisi di langkah 16)
insert into saldo (sekolah_id, siswa_id, saldo) values
  (1, 101, 85000),
  (1, 102, 50000),
  (1, 103, 50000),
  (1, 104, 50000),
  (1, 105, 50000),
  (1, 106, 50000),
  (1, 107, 50000),
  (1, 108, 50000),
  (1, 109, 50000),
  (1, 110, 50000),
  (1, 12, 50000),
  (1, 13, 50000),
  (1, 201, 0),
  (1, 202, 0),
  (10, 1001, 85000),
  (10, 1005, 50000),
  (10, 1006, 50000),
  (10, 1007, 50000),
  (10, 3001, 0);

-- 13. SESI KASIR — 3 hari terakhir (hari ini sengaja masih TERBUKA untuk demo kasir)
insert into sesi_kasir (id, sekolah_id, titik_kasir_id, tanggal, status, total_bruto, total_void, total_bersih, dibuka_at, ditutup_at, ditutup_oleh, auto_tutup, jurnal_buku_kas, referensi_buku_kas, uang_fisik, selisih, catatan) values
  (1, 1, 1, (current_date - 2), 'DITUTUP', 102000, 0, 102000, (current_date - 2 + time '07:30')::timestamptz, (current_date - 2 + time '15:05')::timestamptz, 4, false, true, 'SESI-1', 102000, 0, 'Tutup normal, uang fisik sesuai sistem'),
  (2, 1, 1, (current_date - 1), 'DITUTUP', 199000, 18000, 181000, (current_date - 1 + time '07:25')::timestamptz, (current_date - 1 + time '15:10')::timestamptz, 4, false, true, 'SESI-2', 181000, 0, 'Tutup normal, uang fisik sesuai sistem'),
  (3, 1, 1, (current_date - 0), 'TERBUKA', 57000, 0, 57000, (current_date - 0 + time '07:20')::timestamptz, null, null, false, false, null, null, null, null),
  (4, 1, 2, (current_date - 1), 'DITUTUP', 50000, 0, 50000, (current_date - 1 + time '07:40')::timestamptz, (current_date - 1 + time '15:00')::timestamptz, 4, false, true, 'SESI-4', 50000, 0, 'Tutup normal, uang fisik sesuai sistem'),
  (11, 10, 11, (current_date - 1), 'DITUTUP', 90000, 0, 90000, (current_date - 1 + time '07:15')::timestamptz, (current_date - 1 + time '14:40')::timestamptz, 14, false, true, 'SESI-11', 90000, 0, 'Tutup normal, uang fisik sesuai sistem'),
  (12, 10, 11, (current_date - 0), 'TERBUKA', 75000, 0, 75000, (current_date - 0 + time '07:10')::timestamptz, null, null, false, false, null, null, null, null);

-- 14. TRANSAKSI — 36 transaksi 3 hari terakhir (1 void untuk demo pembatalan)
insert into transaksi (id, idempotency_key, sekolah_id, sesi_kasir_id, titik_kasir_id, kartu_uid, petugas_id, total, total_hpp, status, alasan_void, void_at, void_oleh, waktu, siswa_id, metode_bayar) values
  (1, 'SEED-TRX-1', 1, 1, 1, '04A1B2C3D4', 4, 15000, 8000, 'SUKSES', null, null, null, (current_date - 2 + time '09:12')::timestamptz, 101, 'SALDO'),
  (2, 'SEED-TRX-2', 1, 1, 1, '04B2C3D4E5', 4, 14000, 8000, 'SUKSES', null, null, null, (current_date - 2 + time '09:40')::timestamptz, 102, 'SALDO'),
  (3, 'SEED-TRX-3', 1, 1, 1, '04C3D4E5F6', 4, 19000, 11000, 'SUKSES', null, null, null, (current_date - 2 + time '10:15')::timestamptz, 103, 'SALDO'),
  (4, 'SEED-TRX-4', 1, 1, 1, '04D4E5F6A1', 4, 11000, 7000, 'SUKSES', null, null, null, (current_date - 2 + time '11:02')::timestamptz, 104, 'SALDO'),
  (5, 'SEED-TRX-5', 1, 1, 1, '04E5F6A1B2', 4, 18000, 12000, 'SUKSES', null, null, null, (current_date - 2 + time '12:20')::timestamptz, 105, 'SALDO'),
  (6, 'SEED-TRX-6', 1, 1, 1, '04F1A2B3C4', 4, 25000, 17000, 'SUKSES', null, null, null, (current_date - 2 + time '13:05')::timestamptz, 106, 'SALDO'),
  (7, 'SEED-TRX-7', 1, 2, 1, '04A1B2C3D4', 4, 31000, 19000, 'SUKSES', null, null, null, (current_date - 1 + time '09:05')::timestamptz, 101, 'SALDO'),
  (8, 'SEED-TRX-8', 1, 2, 1, '1831574427', 4, 17000, 9000, 'SUKSES', null, null, null, (current_date - 1 + time '09:35')::timestamptz, 107, 'SALDO'),
  (9, 'SEED-TRX-9', 1, 2, 1, '2225414394', 4, 17000, 11000, 'SUKSES', null, null, null, (current_date - 1 + time '10:10')::timestamptz, 108, 'SALDO'),
  (10, 'SEED-TRX-10', 1, 2, 1, '0830267682', 4, 20000, 12000, 'SUKSES', null, null, null, (current_date - 1 + time '10:50')::timestamptz, 109, 'SALDO'),
  (11, 'SEED-TRX-11', 1, 2, 1, '04F5A6B7C8', 4, 15000, 8000, 'SUKSES', null, null, null, (current_date - 1 + time '11:30')::timestamptz, 110, 'SALDO'),
  (12, 'SEED-TRX-12', 1, 2, 1, '04A2B3C4D5', 4, 18000, 12000, 'SUKSES', null, null, null, (current_date - 1 + time '12:05')::timestamptz, 12, 'SALDO'),
  (13, 'SEED-TRX-13', 1, 2, 1, '04A2B3C4D5', 4, 7000, 3000, 'SUKSES', null, null, null, (current_date - 1 + time '12:40')::timestamptz, 12, 'SALDO'),
  (14, 'SEED-TRX-14', 1, 2, 1, '04B3C4D5E6', 4, 18000, 11000, 'VOID', 'Salah input menu (demo void)', (current_date - 1 + time '13:20')::timestamptz, 4, (current_date - 1 + time '13:10')::timestamptz, 13, 'SALDO'),
  (15, 'SEED-TRX-15', 1, 2, 1, '04C3D4E5F6', 4, 23000, 15000, 'SUKSES', null, null, null, (current_date - 1 + time '12:15')::timestamptz, 103, 'SALDO'),
  (16, 'SEED-TRX-16', 1, 2, 1, '04E5F6A1B2', 4, 7000, 3000, 'SUKSES', null, null, null, (current_date - 1 + time '10:35')::timestamptz, 105, 'SALDO'),
  (17, 'SEED-TRX-17', 1, 2, 1, '1831574427', 4, 8000, 5000, 'SUKSES', null, null, null, (current_date - 1 + time '11:05')::timestamptz, 107, 'SALDO'),
  (18, 'SEED-TRX-18', 1, 2, 1, '2225414394', 4, 8000, 4000, 'SUKSES', null, null, null, (current_date - 1 + time '11:45')::timestamptz, 108, 'SALDO'),
  (19, 'SEED-TRX-19', 1, 2, 1, '04F5A6B7C8', 4, 10000, 6000, 'SUKSES', null, null, null, (current_date - 1 + time '12:50')::timestamptz, 110, 'SALDO'),
  (20, 'SEED-TRX-20', 1, 4, 2, '04B2C3D4E5', 4, 11000, 5000, 'SUKSES', null, null, null, (current_date - 1 + time '09:20')::timestamptz, 102, 'SALDO'),
  (21, 'SEED-TRX-21', 1, 4, 2, '04D4E5F6A1', 4, 7000, 5000, 'SUKSES', null, null, null, (current_date - 1 + time '09:55')::timestamptz, 104, 'SALDO'),
  (22, 'SEED-TRX-22', 1, 4, 2, '04D4E5F6A1', 4, 32000, 19000, 'SUKSES', null, null, null, (current_date - 1 + time '10:30')::timestamptz, 104, 'SALDO'),
  (23, 'SEED-TRX-23', 1, 3, 1, '04A1B2C3D4', 4, 19000, 13000, 'SUKSES', null, null, null, (current_date - 0 + time '09:10')::timestamptz, 101, 'SALDO'),
  (24, 'SEED-TRX-24', 1, 3, 1, '04B2C3D4E5', 4, 25000, 16000, 'SUKSES', null, null, null, (current_date - 0 + time '09:45')::timestamptz, 102, 'SALDO'),
  (25, 'SEED-TRX-25', 1, 3, 1, '04C3D4E5F6', 4, 8000, 5000, 'SUKSES', null, null, null, (current_date - 0 + time '10:20')::timestamptz, 103, 'SALDO'),
  (26, 'SEED-TRX-26', 1, 3, 1, '0830267682', 4, 5000, 3000, 'SUKSES', null, null, null, (current_date - 0 + time '11:00')::timestamptz, 109, 'SALDO'),
  (27, 'SEED-TRX-27', 10, 11, 11, 'DA100001', 14, 15000, 8000, 'SUKSES', null, null, null, (current_date - 1 + time '09:15')::timestamptz, 1001, 'SALDO'),
  (28, 'SEED-TRX-28', 10, 11, 11, 'DA100005', 14, 17000, 11000, 'SUKSES', null, null, null, (current_date - 1 + time '10:00')::timestamptz, 1005, 'SALDO'),
  (29, 'SEED-TRX-29', 10, 11, 11, 'DA100006', 14, 11000, 7000, 'SUKSES', null, null, null, (current_date - 1 + time '11:10')::timestamptz, 1006, 'SALDO'),
  (30, 'SEED-TRX-30', 10, 11, 11, 'DA100005', 14, 15000, 9000, 'SUKSES', null, null, null, (current_date - 1 + time '11:40')::timestamptz, 1005, 'SALDO'),
  (31, 'SEED-TRX-31', 10, 11, 11, 'DA100005', 14, 18000, 9000, 'SUKSES', null, null, null, (current_date - 1 + time '12:20')::timestamptz, 1005, 'SALDO'),
  (32, 'SEED-TRX-32', 10, 11, 11, 'DA100006', 14, 14000, 8000, 'SUKSES', null, null, null, (current_date - 1 + time '12:55')::timestamptz, 1006, 'SALDO'),
  (33, 'SEED-TRX-33', 10, 12, 11, 'DA100001', 14, 31000, 19000, 'SUKSES', null, null, null, (current_date - 0 + time '09:20')::timestamptz, 1001, 'SALDO'),
  (34, 'SEED-TRX-34', 10, 12, 11, 'DA100007', 14, 20000, 12000, 'SUKSES', null, null, null, (current_date - 0 + time '10:05')::timestamptz, 1007, 'SALDO'),
  (35, 'SEED-TRX-35', 10, 12, 11, 'DA100001', 14, 19000, 13000, 'SUKSES', null, null, null, (current_date - 0 + time '10:45')::timestamptz, 1001, 'SALDO'),
  (36, 'SEED-TRX-36', 10, 12, 11, 'DA100007', 14, 5000, 3000, 'SUKSES', null, null, null, (current_date - 0 + time '11:20')::timestamptz, 1007, 'SALDO');

-- 15. TRANSAKSI ITEM
insert into transaksi_item (id, transaksi_id, menu_id, nama_menu, kategori_id, harga_jual, qty, hpp_snapshot, subtotal, created_at) values
  (1, 1, 1, 'Nasi Goreng', 1, 12000, 1, 7000, 12000, (current_date - 2 + time '09:12')::timestamptz),
  (2, 1, 3, 'Es Teh Manis', 2, 3000, 1, 1000, 3000, (current_date - 2 + time '09:12')::timestamptz),
  (3, 2, 2, 'Mie Ayam', 1, 10000, 1, 6000, 10000, (current_date - 2 + time '09:40')::timestamptz),
  (4, 2, 4, 'Air Mineral', 2, 4000, 1, 2000, 4000, (current_date - 2 + time '09:40')::timestamptz),
  (5, 3, 5, 'Ayam Geprek', 1, 13000, 1, 9000, 13000, (current_date - 2 + time '10:15')::timestamptz),
  (6, 3, 3, 'Es Teh Manis', 2, 3000, 2, 1000, 6000, (current_date - 2 + time '10:15')::timestamptz),
  (7, 4, 6, 'Keripik', 3, 5000, 1, 3000, 5000, (current_date - 2 + time '11:02')::timestamptz),
  (8, 4, 8, 'Susu Kotak', 2, 6000, 1, 4000, 6000, (current_date - 2 + time '11:02')::timestamptz),
  (9, 5, 11, 'Bakso', 1, 11000, 1, 7000, 11000, (current_date - 2 + time '12:20')::timestamptz),
  (10, 5, 12, 'Jus Jeruk', 2, 7000, 1, 5000, 7000, (current_date - 2 + time '12:20')::timestamptz),
  (11, 6, 5, 'Ayam Geprek', 1, 13000, 1, 9000, 13000, (current_date - 2 + time '13:05')::timestamptz),
  (12, 6, 12, 'Jus Jeruk', 2, 7000, 1, 5000, 7000, (current_date - 2 + time '13:05')::timestamptz),
  (13, 6, 6, 'Keripik', 3, 5000, 1, 3000, 5000, (current_date - 2 + time '13:05')::timestamptz),
  (14, 7, 1, 'Nasi Goreng', 1, 12000, 2, 7000, 24000, (current_date - 1 + time '09:05')::timestamptz),
  (15, 7, 12, 'Jus Jeruk', 2, 7000, 1, 5000, 7000, (current_date - 1 + time '09:05')::timestamptz),
  (16, 8, 2, 'Mie Ayam', 1, 10000, 1, 6000, 10000, (current_date - 1 + time '09:35')::timestamptz),
  (17, 8, 4, 'Air Mineral', 2, 4000, 1, 2000, 4000, (current_date - 1 + time '09:35')::timestamptz),
  (18, 8, 3, 'Es Teh Manis', 2, 3000, 1, 1000, 3000, (current_date - 1 + time '09:35')::timestamptz),
  (19, 9, 5, 'Ayam Geprek', 1, 13000, 1, 9000, 13000, (current_date - 1 + time '10:10')::timestamptz),
  (20, 9, 4, 'Air Mineral', 2, 4000, 1, 2000, 4000, (current_date - 1 + time '10:10')::timestamptz),
  (21, 10, 10, 'Buku Tulis', 4, 7000, 1, 5000, 7000, (current_date - 1 + time '10:50')::timestamptz),
  (22, 10, 8, 'Susu Kotak', 2, 6000, 1, 4000, 6000, (current_date - 1 + time '10:50')::timestamptz),
  (23, 10, 3, 'Es Teh Manis', 2, 3000, 1, 1000, 3000, (current_date - 1 + time '10:50')::timestamptz),
  (24, 10, 4, 'Air Mineral', 2, 4000, 1, 2000, 4000, (current_date - 1 + time '10:50')::timestamptz),
  (25, 11, 1, 'Nasi Goreng', 1, 12000, 1, 7000, 12000, (current_date - 1 + time '11:30')::timestamptz),
  (26, 11, 3, 'Es Teh Manis', 2, 3000, 1, 1000, 3000, (current_date - 1 + time '11:30')::timestamptz),
  (27, 12, 10, 'Buku Tulis', 4, 7000, 1, 5000, 7000, (current_date - 1 + time '12:05')::timestamptz),
  (28, 12, 9, 'Pulpen', 4, 3000, 1, 2000, 3000, (current_date - 1 + time '12:05')::timestamptz),
  (29, 12, 7, 'Roti Bakar', 3, 8000, 1, 5000, 8000, (current_date - 1 + time '12:05')::timestamptz),
  (30, 13, 4, 'Air Mineral', 2, 4000, 1, 2000, 4000, (current_date - 1 + time '12:40')::timestamptz),
  (31, 13, 3, 'Es Teh Manis', 2, 3000, 1, 1000, 3000, (current_date - 1 + time '12:40')::timestamptz),
  (32, 14, 1, 'Nasi Goreng', 1, 12000, 1, 7000, 12000, (current_date - 1 + time '13:10')::timestamptz),
  (33, 14, 8, 'Susu Kotak', 2, 6000, 1, 4000, 6000, (current_date - 1 + time '13:10')::timestamptz),
  (34, 15, 11, 'Bakso', 1, 11000, 1, 7000, 11000, (current_date - 1 + time '12:15')::timestamptz),
  (35, 15, 12, 'Jus Jeruk', 2, 7000, 1, 5000, 7000, (current_date - 1 + time '12:15')::timestamptz),
  (36, 15, 6, 'Keripik', 3, 5000, 1, 3000, 5000, (current_date - 1 + time '12:15')::timestamptz),
  (37, 16, 3, 'Es Teh Manis', 2, 3000, 1, 1000, 3000, (current_date - 1 + time '10:35')::timestamptz),
  (38, 16, 4, 'Air Mineral', 2, 4000, 1, 2000, 4000, (current_date - 1 + time '10:35')::timestamptz),
  (39, 17, 7, 'Roti Bakar', 3, 8000, 1, 5000, 8000, (current_date - 1 + time '11:05')::timestamptz),
  (40, 18, 6, 'Keripik', 3, 5000, 1, 3000, 5000, (current_date - 1 + time '11:45')::timestamptz),
  (41, 18, 3, 'Es Teh Manis', 2, 3000, 1, 1000, 3000, (current_date - 1 + time '11:45')::timestamptz),
  (42, 19, 8, 'Susu Kotak', 2, 6000, 1, 4000, 6000, (current_date - 1 + time '12:50')::timestamptz),
  (43, 19, 4, 'Air Mineral', 2, 4000, 1, 2000, 4000, (current_date - 1 + time '12:50')::timestamptz),
  (44, 20, 3, 'Es Teh Manis', 2, 3000, 2, 1000, 6000, (current_date - 1 + time '09:20')::timestamptz),
  (45, 20, 6, 'Keripik', 3, 5000, 1, 3000, 5000, (current_date - 1 + time '09:20')::timestamptz),
  (46, 21, 10, 'Buku Tulis', 4, 7000, 1, 5000, 7000, (current_date - 1 + time '09:55')::timestamptz),
  (47, 22, 5, 'Ayam Geprek', 1, 13000, 1, 9000, 13000, (current_date - 1 + time '10:30')::timestamptz),
  (48, 22, 1, 'Nasi Goreng', 1, 12000, 1, 7000, 12000, (current_date - 1 + time '10:30')::timestamptz),
  (49, 22, 3, 'Es Teh Manis', 2, 3000, 1, 1000, 3000, (current_date - 1 + time '10:30')::timestamptz),
  (50, 22, 4, 'Air Mineral', 2, 4000, 1, 2000, 4000, (current_date - 1 + time '10:30')::timestamptz),
  (51, 23, 5, 'Ayam Geprek', 1, 13000, 1, 9000, 13000, (current_date - 0 + time '09:10')::timestamptz),
  (52, 23, 8, 'Susu Kotak', 2, 6000, 1, 4000, 6000, (current_date - 0 + time '09:10')::timestamptz),
  (53, 24, 1, 'Nasi Goreng', 1, 12000, 1, 7000, 12000, (current_date - 0 + time '09:45')::timestamptz),
  (54, 24, 5, 'Ayam Geprek', 1, 13000, 1, 9000, 13000, (current_date - 0 + time '09:45')::timestamptz),
  (55, 25, 7, 'Roti Bakar', 3, 8000, 1, 5000, 8000, (current_date - 0 + time '10:20')::timestamptz),
  (56, 26, 6, 'Keripik', 3, 5000, 1, 3000, 5000, (current_date - 0 + time '11:00')::timestamptz),
  (57, 27, 1000, 'Nasi Goreng', 11, 12000, 1, 7000, 12000, (current_date - 1 + time '09:15')::timestamptz),
  (58, 27, 1002, 'Es Teh Manis', 12, 3000, 1, 1000, 3000, (current_date - 1 + time '09:15')::timestamptz),
  (59, 28, 1004, 'Ayam Geprek', 11, 13000, 1, 9000, 13000, (current_date - 1 + time '10:00')::timestamptz),
  (60, 28, 1003, 'Air Mineral', 12, 4000, 1, 2000, 4000, (current_date - 1 + time '10:00')::timestamptz),
  (61, 29, 1005, 'Keripik', 13, 5000, 1, 3000, 5000, (current_date - 1 + time '11:10')::timestamptz),
  (62, 29, 1007, 'Susu Kotak', 12, 6000, 1, 4000, 6000, (current_date - 1 + time '11:10')::timestamptz),
  (63, 30, 1001, 'Mie Ayam', 11, 10000, 1, 6000, 10000, (current_date - 1 + time '11:40')::timestamptz),
  (64, 30, 1005, 'Keripik', 13, 5000, 1, 3000, 5000, (current_date - 1 + time '11:40')::timestamptz),
  (65, 31, 1000, 'Nasi Goreng', 11, 12000, 1, 7000, 12000, (current_date - 1 + time '12:20')::timestamptz),
  (66, 31, 1002, 'Es Teh Manis', 12, 3000, 2, 1000, 6000, (current_date - 1 + time '12:20')::timestamptz),
  (67, 32, 1011, 'Jus Jeruk', 12, 7000, 1, 5000, 7000, (current_date - 1 + time '12:55')::timestamptz),
  (68, 32, 1003, 'Air Mineral', 12, 4000, 1, 2000, 4000, (current_date - 1 + time '12:55')::timestamptz),
  (69, 32, 1002, 'Es Teh Manis', 12, 3000, 1, 1000, 3000, (current_date - 1 + time '12:55')::timestamptz),
  (70, 33, 1000, 'Nasi Goreng', 11, 12000, 2, 7000, 24000, (current_date - 0 + time '09:20')::timestamptz),
  (71, 33, 1011, 'Jus Jeruk', 12, 7000, 1, 5000, 7000, (current_date - 0 + time '09:20')::timestamptz),
  (72, 34, 1004, 'Ayam Geprek', 11, 13000, 1, 9000, 13000, (current_date - 0 + time '10:05')::timestamptz),
  (73, 34, 1002, 'Es Teh Manis', 12, 3000, 1, 1000, 3000, (current_date - 0 + time '10:05')::timestamptz),
  (74, 34, 1003, 'Air Mineral', 12, 4000, 1, 2000, 4000, (current_date - 0 + time '10:05')::timestamptz),
  (75, 35, 1004, 'Ayam Geprek', 11, 13000, 1, 9000, 13000, (current_date - 0 + time '10:45')::timestamptz),
  (76, 35, 1007, 'Susu Kotak', 12, 6000, 1, 4000, 6000, (current_date - 0 + time '10:45')::timestamptz),
  (77, 36, 1005, 'Keripik', 13, 5000, 1, 3000, 5000, (current_date - 0 + time '11:20')::timestamptz);

-- 16. MUTASI SALDO — top up + penjualan + void (sumber kebenaran saldo)
insert into mutasi_saldo (id, sekolah_id, siswa_id, arah, jenis, nominal, saldo_setelah, transaksi_id, idempotency_key, referensi_tipe, referensi_id, keterangan, aktor_id, waktu) values
  (1, 1, 12, 'KREDIT', 'TOPUP_TUNAI', 75000, 75000, null, 'SEED-TOPUP-12', 'TOPUP', 'TOPUP-12', 'Top up tunai di TU (seed)', 2, (current_date - 3 + time '08:00')::timestamptz),
  (2, 1, 13, 'KREDIT', 'TOPUP_TUNAI', 50000, 50000, null, 'SEED-TOPUP-13', 'TOPUP', 'TOPUP-13', 'Top up tunai di TU (seed)', 2, (current_date - 3 + time '08:00')::timestamptz),
  (3, 1, 101, 'KREDIT', 'TOPUP_TUNAI', 150000, 150000, null, 'SEED-TOPUP-101', 'TOPUP', 'TOPUP-101', 'Top up tunai di TU (seed)', 2, (current_date - 3 + time '08:00')::timestamptz),
  (4, 1, 102, 'KREDIT', 'TOPUP_TUNAI', 100000, 100000, null, 'SEED-TOPUP-102', 'TOPUP', 'TOPUP-102', 'Top up tunai di TU (seed)', 2, (current_date - 3 + time '08:00')::timestamptz),
  (5, 1, 103, 'KREDIT', 'TOPUP_TUNAI', 100000, 100000, null, 'SEED-TOPUP-103', 'TOPUP', 'TOPUP-103', 'Top up tunai di TU (seed)', 2, (current_date - 3 + time '08:00')::timestamptz),
  (6, 1, 104, 'KREDIT', 'TOPUP_TUNAI', 100000, 100000, null, 'SEED-TOPUP-104', 'TOPUP', 'TOPUP-104', 'Top up tunai di TU (seed)', 2, (current_date - 3 + time '08:00')::timestamptz),
  (7, 1, 105, 'KREDIT', 'TOPUP_TUNAI', 75000, 75000, null, 'SEED-TOPUP-105', 'TOPUP', 'TOPUP-105', 'Top up tunai di TU (seed)', 2, (current_date - 3 + time '08:00')::timestamptz),
  (8, 1, 106, 'KREDIT', 'TOPUP_TUNAI', 75000, 75000, null, 'SEED-TOPUP-106', 'TOPUP', 'TOPUP-106', 'Top up tunai di TU (seed)', 2, (current_date - 3 + time '08:00')::timestamptz),
  (9, 1, 107, 'KREDIT', 'TOPUP_TUNAI', 75000, 75000, null, 'SEED-TOPUP-107', 'TOPUP', 'TOPUP-107', 'Top up tunai di TU (seed)', 2, (current_date - 3 + time '08:00')::timestamptz),
  (10, 1, 108, 'KREDIT', 'TOPUP_TUNAI', 75000, 75000, null, 'SEED-TOPUP-108', 'TOPUP', 'TOPUP-108', 'Top up tunai di TU (seed)', 2, (current_date - 3 + time '08:00')::timestamptz),
  (11, 1, 109, 'KREDIT', 'TOPUP_TUNAI', 75000, 75000, null, 'SEED-TOPUP-109', 'TOPUP', 'TOPUP-109', 'Top up tunai di TU (seed)', 2, (current_date - 3 + time '08:00')::timestamptz),
  (12, 1, 110, 'KREDIT', 'TOPUP_TUNAI', 75000, 75000, null, 'SEED-TOPUP-110', 'TOPUP', 'TOPUP-110', 'Top up tunai di TU (seed)', 2, (current_date - 3 + time '08:00')::timestamptz),
  (13, 10, 1001, 'KREDIT', 'TOPUP_TUNAI', 150000, 150000, null, 'SEED-TOPUP-1001', 'TOPUP', 'TOPUP-1001', 'Top up tunai di TU (seed)', 12, (current_date - 3 + time '08:00')::timestamptz),
  (14, 10, 1005, 'KREDIT', 'TOPUP_TUNAI', 100000, 100000, null, 'SEED-TOPUP-1005', 'TOPUP', 'TOPUP-1005', 'Top up tunai di TU (seed)', 12, (current_date - 3 + time '08:00')::timestamptz),
  (15, 10, 1006, 'KREDIT', 'TOPUP_TUNAI', 75000, 75000, null, 'SEED-TOPUP-1006', 'TOPUP', 'TOPUP-1006', 'Top up tunai di TU (seed)', 12, (current_date - 3 + time '08:00')::timestamptz),
  (16, 10, 1007, 'KREDIT', 'TOPUP_TUNAI', 75000, 75000, null, 'SEED-TOPUP-1007', 'TOPUP', 'TOPUP-1007', 'Top up tunai di TU (seed)', 12, (current_date - 3 + time '08:00')::timestamptz),
  (17, 1, 101, 'DEBIT', 'PENJUALAN', 19000, 131000, 23, 'SEED-TRX-23', 'TRANSAKSI', 'TRX-23', 'Pembelian di kasir', 4, (current_date - 0 + time '09:10')::timestamptz),
  (18, 10, 1001, 'DEBIT', 'PENJUALAN', 31000, 119000, 33, 'SEED-TRX-33', 'TRANSAKSI', 'TRX-33', 'Pembelian di kasir', 14, (current_date - 0 + time '09:20')::timestamptz),
  (19, 1, 102, 'DEBIT', 'PENJUALAN', 25000, 75000, 24, 'SEED-TRX-24', 'TRANSAKSI', 'TRX-24', 'Pembelian di kasir', 4, (current_date - 0 + time '09:45')::timestamptz),
  (20, 10, 1007, 'DEBIT', 'PENJUALAN', 20000, 55000, 34, 'SEED-TRX-34', 'TRANSAKSI', 'TRX-34', 'Pembelian di kasir', 14, (current_date - 0 + time '10:05')::timestamptz),
  (21, 1, 103, 'DEBIT', 'PENJUALAN', 8000, 92000, 25, 'SEED-TRX-25', 'TRANSAKSI', 'TRX-25', 'Pembelian di kasir', 4, (current_date - 0 + time '10:20')::timestamptz),
  (22, 10, 1001, 'DEBIT', 'PENJUALAN', 19000, 100000, 35, 'SEED-TRX-35', 'TRANSAKSI', 'TRX-35', 'Pembelian di kasir', 14, (current_date - 0 + time '10:45')::timestamptz),
  (23, 1, 109, 'DEBIT', 'PENJUALAN', 5000, 70000, 26, 'SEED-TRX-26', 'TRANSAKSI', 'TRX-26', 'Pembelian di kasir', 4, (current_date - 0 + time '11:00')::timestamptz),
  (24, 10, 1007, 'DEBIT', 'PENJUALAN', 5000, 50000, 36, 'SEED-TRX-36', 'TRANSAKSI', 'TRX-36', 'Pembelian di kasir', 14, (current_date - 0 + time '11:20')::timestamptz),
  (25, 1, 101, 'DEBIT', 'PENJUALAN', 31000, 100000, 7, 'SEED-TRX-7', 'TRANSAKSI', 'TRX-7', 'Pembelian di kasir', 4, (current_date - 1 + time '09:05')::timestamptz),
  (26, 10, 1001, 'DEBIT', 'PENJUALAN', 15000, 85000, 27, 'SEED-TRX-27', 'TRANSAKSI', 'TRX-27', 'Pembelian di kasir', 14, (current_date - 1 + time '09:15')::timestamptz),
  (27, 1, 102, 'DEBIT', 'PENJUALAN', 11000, 64000, 20, 'SEED-TRX-20', 'TRANSAKSI', 'TRX-20', 'Pembelian di kasir', 4, (current_date - 1 + time '09:20')::timestamptz),
  (28, 1, 107, 'DEBIT', 'PENJUALAN', 17000, 58000, 8, 'SEED-TRX-8', 'TRANSAKSI', 'TRX-8', 'Pembelian di kasir', 4, (current_date - 1 + time '09:35')::timestamptz),
  (29, 1, 104, 'DEBIT', 'PENJUALAN', 7000, 93000, 21, 'SEED-TRX-21', 'TRANSAKSI', 'TRX-21', 'Pembelian di kasir', 4, (current_date - 1 + time '09:55')::timestamptz),
  (30, 10, 1005, 'DEBIT', 'PENJUALAN', 17000, 83000, 28, 'SEED-TRX-28', 'TRANSAKSI', 'TRX-28', 'Pembelian di kasir', 14, (current_date - 1 + time '10:00')::timestamptz),
  (31, 1, 108, 'DEBIT', 'PENJUALAN', 17000, 58000, 9, 'SEED-TRX-9', 'TRANSAKSI', 'TRX-9', 'Pembelian di kasir', 4, (current_date - 1 + time '10:10')::timestamptz),
  (32, 1, 104, 'DEBIT', 'PENJUALAN', 32000, 61000, 22, 'SEED-TRX-22', 'TRANSAKSI', 'TRX-22', 'Pembelian di kasir', 4, (current_date - 1 + time '10:30')::timestamptz),
  (33, 1, 105, 'DEBIT', 'PENJUALAN', 7000, 68000, 16, 'SEED-TRX-16', 'TRANSAKSI', 'TRX-16', 'Pembelian di kasir', 4, (current_date - 1 + time '10:35')::timestamptz),
  (34, 1, 109, 'DEBIT', 'PENJUALAN', 20000, 50000, 10, 'SEED-TRX-10', 'TRANSAKSI', 'TRX-10', 'Pembelian di kasir', 4, (current_date - 1 + time '10:50')::timestamptz),
  (35, 1, 107, 'DEBIT', 'PENJUALAN', 8000, 50000, 17, 'SEED-TRX-17', 'TRANSAKSI', 'TRX-17', 'Pembelian di kasir', 4, (current_date - 1 + time '11:05')::timestamptz),
  (36, 10, 1006, 'DEBIT', 'PENJUALAN', 11000, 64000, 29, 'SEED-TRX-29', 'TRANSAKSI', 'TRX-29', 'Pembelian di kasir', 14, (current_date - 1 + time '11:10')::timestamptz),
  (37, 1, 110, 'DEBIT', 'PENJUALAN', 15000, 60000, 11, 'SEED-TRX-11', 'TRANSAKSI', 'TRX-11', 'Pembelian di kasir', 4, (current_date - 1 + time '11:30')::timestamptz),
  (38, 10, 1005, 'DEBIT', 'PENJUALAN', 15000, 68000, 30, 'SEED-TRX-30', 'TRANSAKSI', 'TRX-30', 'Pembelian di kasir', 14, (current_date - 1 + time '11:40')::timestamptz),
  (39, 1, 108, 'DEBIT', 'PENJUALAN', 8000, 50000, 18, 'SEED-TRX-18', 'TRANSAKSI', 'TRX-18', 'Pembelian di kasir', 4, (current_date - 1 + time '11:45')::timestamptz),
  (40, 1, 12, 'DEBIT', 'PENJUALAN', 18000, 57000, 12, 'SEED-TRX-12', 'TRANSAKSI', 'TRX-12', 'Pembelian di kasir', 4, (current_date - 1 + time '12:05')::timestamptz),
  (41, 1, 103, 'DEBIT', 'PENJUALAN', 23000, 69000, 15, 'SEED-TRX-15', 'TRANSAKSI', 'TRX-15', 'Pembelian di kasir', 4, (current_date - 1 + time '12:15')::timestamptz),
  (42, 10, 1005, 'DEBIT', 'PENJUALAN', 18000, 50000, 31, 'SEED-TRX-31', 'TRANSAKSI', 'TRX-31', 'Pembelian di kasir', 14, (current_date - 1 + time '12:20')::timestamptz),
  (43, 1, 12, 'DEBIT', 'PENJUALAN', 7000, 50000, 13, 'SEED-TRX-13', 'TRANSAKSI', 'TRX-13', 'Pembelian di kasir', 4, (current_date - 1 + time '12:40')::timestamptz),
  (44, 1, 110, 'DEBIT', 'PENJUALAN', 10000, 50000, 19, 'SEED-TRX-19', 'TRANSAKSI', 'TRX-19', 'Pembelian di kasir', 4, (current_date - 1 + time '12:50')::timestamptz),
  (45, 10, 1006, 'DEBIT', 'PENJUALAN', 14000, 50000, 32, 'SEED-TRX-32', 'TRANSAKSI', 'TRX-32', 'Pembelian di kasir', 14, (current_date - 1 + time '12:55')::timestamptz),
  (46, 1, 13, 'DEBIT', 'PENJUALAN', 18000, 32000, 14, 'SEED-TRX-14', 'TRANSAKSI', 'TRX-14', 'Pembelian di kasir', 4, (current_date - 1 + time '13:10')::timestamptz),
  (47, 1, 13, 'KREDIT', 'VOID_PENJUALAN', 18000, 50000, 14, 'SEED-VOID-14', 'TRANSAKSI', 'VOID-14', 'Pembatalan transaksi (void)', 4, (current_date - 1 + time '13:20')::timestamptz),
  (48, 1, 101, 'DEBIT', 'PENJUALAN', 15000, 85000, 1, 'SEED-TRX-1', 'TRANSAKSI', 'TRX-1', 'Pembelian di kasir', 4, (current_date - 2 + time '09:12')::timestamptz),
  (49, 1, 102, 'DEBIT', 'PENJUALAN', 14000, 50000, 2, 'SEED-TRX-2', 'TRANSAKSI', 'TRX-2', 'Pembelian di kasir', 4, (current_date - 2 + time '09:40')::timestamptz),
  (50, 1, 103, 'DEBIT', 'PENJUALAN', 19000, 50000, 3, 'SEED-TRX-3', 'TRANSAKSI', 'TRX-3', 'Pembelian di kasir', 4, (current_date - 2 + time '10:15')::timestamptz),
  (51, 1, 104, 'DEBIT', 'PENJUALAN', 11000, 50000, 4, 'SEED-TRX-4', 'TRANSAKSI', 'TRX-4', 'Pembelian di kasir', 4, (current_date - 2 + time '11:02')::timestamptz),
  (52, 1, 105, 'DEBIT', 'PENJUALAN', 18000, 50000, 5, 'SEED-TRX-5', 'TRANSAKSI', 'TRX-5', 'Pembelian di kasir', 4, (current_date - 2 + time '12:20')::timestamptz),
  (53, 1, 106, 'DEBIT', 'PENJUALAN', 25000, 50000, 6, 'SEED-TRX-6', 'TRANSAKSI', 'TRX-6', 'Pembelian di kasir', 4, (current_date - 2 + time '13:05')::timestamptz);

-- 17. RIWAYAT STOK — stok awal + penjualan + void (sumber kebenaran stok)
insert into riwayat_stok (id, sekolah_id, menu_id, arah, jenis, qty, stok_setelah, hpp_snapshot, transaksi_id, referensi_tipe, referensi_id, alasan, aktor_id, waktu, harga_beli_satuan, aktor_nama) values
  (1, 1, 1, 'MASUK', 'BARANG_MASUK', 27, 27, 7000, null, 'BARANG_MASUK', 'BM-1-1', 'Stok awal (seed)', 3, (current_date - 4 + time '06:00')::timestamptz, 7000, 'Deryl Fabiensyah'),
  (2, 1, 1, 'KELUAR', 'PENJUALAN', 1, 26, 7000, 24, 'TRANSAKSI', 'TRX-24', null, 4, (current_date - 0 + time '09:45')::timestamptz, null, null),
  (3, 1, 1, 'KELUAR', 'PENJUALAN', 2, 24, 7000, 7, 'TRANSAKSI', 'TRX-7', null, 4, (current_date - 1 + time '09:05')::timestamptz, null, null),
  (4, 1, 1, 'KELUAR', 'PENJUALAN', 1, 23, 7000, 22, 'TRANSAKSI', 'TRX-22', null, 4, (current_date - 1 + time '10:30')::timestamptz, null, null),
  (5, 1, 1, 'KELUAR', 'PENJUALAN', 1, 22, 7000, 11, 'TRANSAKSI', 'TRX-11', null, 4, (current_date - 1 + time '11:30')::timestamptz, null, null),
  (6, 1, 1, 'KELUAR', 'PENJUALAN', 1, 21, 7000, 14, 'TRANSAKSI', 'TRX-14', null, 4, (current_date - 1 + time '13:10')::timestamptz, null, null),
  (7, 1, 1, 'MASUK', 'VOID_PENJUALAN', 1, 22, 7000, 14, 'TRANSAKSI', 'VOID-14', null, 4, (current_date - 1 + time '13:20')::timestamptz, null, null),
  (8, 1, 1, 'KELUAR', 'PENJUALAN', 1, 21, 7000, 1, 'TRANSAKSI', 'TRX-1', null, 4, (current_date - 2 + time '09:12')::timestamptz, null, null),
  (9, 1, 2, 'MASUK', 'BARANG_MASUK', 41, 41, 6000, null, 'BARANG_MASUK', 'BM-1-2', 'Stok awal (seed)', 3, (current_date - 4 + time '06:00')::timestamptz, 6000, 'Deryl Fabiensyah'),
  (10, 1, 2, 'KELUAR', 'PENJUALAN', 1, 40, 6000, 8, 'TRANSAKSI', 'TRX-8', null, 4, (current_date - 1 + time '09:35')::timestamptz, null, null),
  (11, 1, 2, 'KELUAR', 'PENJUALAN', 1, 39, 6000, 2, 'TRANSAKSI', 'TRX-2', null, 4, (current_date - 2 + time '09:40')::timestamptz, null, null),
  (12, 1, 3, 'MASUK', 'BARANG_MASUK', 31, 31, 1000, null, 'BARANG_MASUK', 'BM-1-3', 'Stok awal (seed)', 3, (current_date - 4 + time '06:00')::timestamptz, 1000, 'Deryl Fabiensyah'),
  (13, 1, 3, 'KELUAR', 'PENJUALAN', 2, 29, 1000, 20, 'TRANSAKSI', 'TRX-20', null, 4, (current_date - 1 + time '09:20')::timestamptz, null, null),
  (14, 1, 3, 'KELUAR', 'PENJUALAN', 1, 28, 1000, 8, 'TRANSAKSI', 'TRX-8', null, 4, (current_date - 1 + time '09:35')::timestamptz, null, null),
  (15, 1, 3, 'KELUAR', 'PENJUALAN', 1, 27, 1000, 22, 'TRANSAKSI', 'TRX-22', null, 4, (current_date - 1 + time '10:30')::timestamptz, null, null),
  (16, 1, 3, 'KELUAR', 'PENJUALAN', 1, 26, 1000, 16, 'TRANSAKSI', 'TRX-16', null, 4, (current_date - 1 + time '10:35')::timestamptz, null, null),
  (17, 1, 3, 'KELUAR', 'PENJUALAN', 1, 25, 1000, 10, 'TRANSAKSI', 'TRX-10', null, 4, (current_date - 1 + time '10:50')::timestamptz, null, null),
  (18, 1, 3, 'KELUAR', 'PENJUALAN', 1, 24, 1000, 11, 'TRANSAKSI', 'TRX-11', null, 4, (current_date - 1 + time '11:30')::timestamptz, null, null),
  (19, 1, 3, 'KELUAR', 'PENJUALAN', 1, 23, 1000, 18, 'TRANSAKSI', 'TRX-18', null, 4, (current_date - 1 + time '11:45')::timestamptz, null, null),
  (20, 1, 3, 'KELUAR', 'PENJUALAN', 1, 22, 1000, 13, 'TRANSAKSI', 'TRX-13', null, 4, (current_date - 1 + time '12:40')::timestamptz, null, null),
  (21, 1, 3, 'KELUAR', 'PENJUALAN', 1, 21, 1000, 1, 'TRANSAKSI', 'TRX-1', null, 4, (current_date - 2 + time '09:12')::timestamptz, null, null),
  (22, 1, 3, 'KELUAR', 'PENJUALAN', 2, 19, 1000, 3, 'TRANSAKSI', 'TRX-3', null, 4, (current_date - 2 + time '10:15')::timestamptz, null, null),
  (23, 1, 4, 'MASUK', 'BARANG_MASUK', 22, 22, 2000, null, 'BARANG_MASUK', 'BM-1-4', 'Stok awal (seed)', 3, (current_date - 4 + time '06:00')::timestamptz, 2000, 'Deryl Fabiensyah'),
  (24, 1, 4, 'KELUAR', 'PENJUALAN', 1, 21, 2000, 8, 'TRANSAKSI', 'TRX-8', null, 4, (current_date - 1 + time '09:35')::timestamptz, null, null),
  (25, 1, 4, 'KELUAR', 'PENJUALAN', 1, 20, 2000, 9, 'TRANSAKSI', 'TRX-9', null, 4, (current_date - 1 + time '10:10')::timestamptz, null, null),
  (26, 1, 4, 'KELUAR', 'PENJUALAN', 1, 19, 2000, 22, 'TRANSAKSI', 'TRX-22', null, 4, (current_date - 1 + time '10:30')::timestamptz, null, null),
  (27, 1, 4, 'KELUAR', 'PENJUALAN', 1, 18, 2000, 16, 'TRANSAKSI', 'TRX-16', null, 4, (current_date - 1 + time '10:35')::timestamptz, null, null),
  (28, 1, 4, 'KELUAR', 'PENJUALAN', 1, 17, 2000, 10, 'TRANSAKSI', 'TRX-10', null, 4, (current_date - 1 + time '10:50')::timestamptz, null, null),
  (29, 1, 4, 'KELUAR', 'PENJUALAN', 1, 16, 2000, 13, 'TRANSAKSI', 'TRX-13', null, 4, (current_date - 1 + time '12:40')::timestamptz, null, null),
  (30, 1, 4, 'KELUAR', 'PENJUALAN', 1, 15, 2000, 19, 'TRANSAKSI', 'TRX-19', null, 4, (current_date - 1 + time '12:50')::timestamptz, null, null),
  (31, 1, 4, 'KELUAR', 'PENJUALAN', 1, 14, 2000, 2, 'TRANSAKSI', 'TRX-2', null, 4, (current_date - 2 + time '09:40')::timestamptz, null, null),
  (32, 1, 5, 'MASUK', 'BARANG_MASUK', 43, 43, 9000, null, 'BARANG_MASUK', 'BM-1-5', 'Stok awal (seed)', 3, (current_date - 4 + time '06:00')::timestamptz, 9000, 'Deryl Fabiensyah'),
  (33, 1, 5, 'KELUAR', 'PENJUALAN', 1, 42, 9000, 23, 'TRANSAKSI', 'TRX-23', null, 4, (current_date - 0 + time '09:10')::timestamptz, null, null),
  (34, 1, 5, 'KELUAR', 'PENJUALAN', 1, 41, 9000, 24, 'TRANSAKSI', 'TRX-24', null, 4, (current_date - 0 + time '09:45')::timestamptz, null, null),
  (35, 1, 5, 'KELUAR', 'PENJUALAN', 1, 40, 9000, 9, 'TRANSAKSI', 'TRX-9', null, 4, (current_date - 1 + time '10:10')::timestamptz, null, null),
  (36, 1, 5, 'KELUAR', 'PENJUALAN', 1, 39, 9000, 22, 'TRANSAKSI', 'TRX-22', null, 4, (current_date - 1 + time '10:30')::timestamptz, null, null),
  (37, 1, 5, 'KELUAR', 'PENJUALAN', 1, 38, 9000, 3, 'TRANSAKSI', 'TRX-3', null, 4, (current_date - 2 + time '10:15')::timestamptz, null, null),
  (38, 1, 5, 'KELUAR', 'PENJUALAN', 1, 37, 9000, 6, 'TRANSAKSI', 'TRX-6', null, 4, (current_date - 2 + time '13:05')::timestamptz, null, null),
  (39, 1, 6, 'MASUK', 'BARANG_MASUK', 45, 45, 3000, null, 'BARANG_MASUK', 'BM-1-6', 'Stok awal (seed)', 3, (current_date - 4 + time '06:00')::timestamptz, 3000, 'Deryl Fabiensyah'),
  (40, 1, 6, 'KELUAR', 'PENJUALAN', 1, 44, 3000, 26, 'TRANSAKSI', 'TRX-26', null, 4, (current_date - 0 + time '11:00')::timestamptz, null, null),
  (41, 1, 6, 'KELUAR', 'PENJUALAN', 1, 43, 3000, 20, 'TRANSAKSI', 'TRX-20', null, 4, (current_date - 1 + time '09:20')::timestamptz, null, null),
  (42, 1, 6, 'KELUAR', 'PENJUALAN', 1, 42, 3000, 18, 'TRANSAKSI', 'TRX-18', null, 4, (current_date - 1 + time '11:45')::timestamptz, null, null),
  (43, 1, 6, 'KELUAR', 'PENJUALAN', 1, 41, 3000, 15, 'TRANSAKSI', 'TRX-15', null, 4, (current_date - 1 + time '12:15')::timestamptz, null, null),
  (44, 1, 6, 'KELUAR', 'PENJUALAN', 1, 40, 3000, 4, 'TRANSAKSI', 'TRX-4', null, 4, (current_date - 2 + time '11:02')::timestamptz, null, null),
  (45, 1, 6, 'KELUAR', 'PENJUALAN', 1, 39, 3000, 6, 'TRANSAKSI', 'TRX-6', null, 4, (current_date - 2 + time '13:05')::timestamptz, null, null),
  (46, 1, 7, 'MASUK', 'BARANG_MASUK', 26, 26, 5000, null, 'BARANG_MASUK', 'BM-1-7', 'Stok awal (seed)', 3, (current_date - 4 + time '06:00')::timestamptz, 5000, 'Deryl Fabiensyah'),
  (47, 1, 7, 'KELUAR', 'PENJUALAN', 1, 25, 5000, 25, 'TRANSAKSI', 'TRX-25', null, 4, (current_date - 0 + time '10:20')::timestamptz, null, null),
  (48, 1, 7, 'KELUAR', 'PENJUALAN', 1, 24, 5000, 17, 'TRANSAKSI', 'TRX-17', null, 4, (current_date - 1 + time '11:05')::timestamptz, null, null),
  (49, 1, 7, 'KELUAR', 'PENJUALAN', 1, 23, 5000, 12, 'TRANSAKSI', 'TRX-12', null, 4, (current_date - 1 + time '12:05')::timestamptz, null, null),
  (50, 1, 8, 'MASUK', 'BARANG_MASUK', 40, 40, 4000, null, 'BARANG_MASUK', 'BM-1-8', 'Stok awal (seed)', 3, (current_date - 4 + time '06:00')::timestamptz, 4000, 'Deryl Fabiensyah'),
  (51, 1, 8, 'KELUAR', 'PENJUALAN', 1, 39, 4000, 23, 'TRANSAKSI', 'TRX-23', null, 4, (current_date - 0 + time '09:10')::timestamptz, null, null),
  (52, 1, 8, 'KELUAR', 'PENJUALAN', 1, 38, 4000, 10, 'TRANSAKSI', 'TRX-10', null, 4, (current_date - 1 + time '10:50')::timestamptz, null, null),
  (53, 1, 8, 'KELUAR', 'PENJUALAN', 1, 37, 4000, 19, 'TRANSAKSI', 'TRX-19', null, 4, (current_date - 1 + time '12:50')::timestamptz, null, null),
  (54, 1, 8, 'KELUAR', 'PENJUALAN', 1, 36, 4000, 14, 'TRANSAKSI', 'TRX-14', null, 4, (current_date - 1 + time '13:10')::timestamptz, null, null),
  (55, 1, 8, 'MASUK', 'VOID_PENJUALAN', 1, 37, 4000, 14, 'TRANSAKSI', 'VOID-14', null, 4, (current_date - 1 + time '13:20')::timestamptz, null, null),
  (56, 1, 8, 'KELUAR', 'PENJUALAN', 1, 36, 4000, 4, 'TRANSAKSI', 'TRX-4', null, 4, (current_date - 2 + time '11:02')::timestamptz, null, null),
  (57, 1, 9, 'MASUK', 'BARANG_MASUK', 45, 45, 2000, null, 'BARANG_MASUK', 'BM-1-9', 'Stok awal (seed)', 3, (current_date - 4 + time '06:00')::timestamptz, 2000, 'Deryl Fabiensyah'),
  (58, 1, 9, 'KELUAR', 'PENJUALAN', 1, 44, 2000, 12, 'TRANSAKSI', 'TRX-12', null, 4, (current_date - 1 + time '12:05')::timestamptz, null, null),
  (59, 1, 10, 'MASUK', 'BARANG_MASUK', 22, 22, 5000, null, 'BARANG_MASUK', 'BM-1-10', 'Stok awal (seed)', 3, (current_date - 4 + time '06:00')::timestamptz, 5000, 'Deryl Fabiensyah'),
  (60, 1, 10, 'KELUAR', 'PENJUALAN', 1, 21, 5000, 21, 'TRANSAKSI', 'TRX-21', null, 4, (current_date - 1 + time '09:55')::timestamptz, null, null),
  (61, 1, 10, 'KELUAR', 'PENJUALAN', 1, 20, 5000, 10, 'TRANSAKSI', 'TRX-10', null, 4, (current_date - 1 + time '10:50')::timestamptz, null, null),
  (62, 1, 10, 'KELUAR', 'PENJUALAN', 1, 19, 5000, 12, 'TRANSAKSI', 'TRX-12', null, 4, (current_date - 1 + time '12:05')::timestamptz, null, null),
  (63, 1, 11, 'MASUK', 'BARANG_MASUK', 39, 39, 7000, null, 'BARANG_MASUK', 'BM-1-11', 'Stok awal (seed)', 3, (current_date - 4 + time '06:00')::timestamptz, 7000, 'Deryl Fabiensyah'),
  (64, 1, 11, 'KELUAR', 'PENJUALAN', 1, 38, 7000, 15, 'TRANSAKSI', 'TRX-15', null, 4, (current_date - 1 + time '12:15')::timestamptz, null, null),
  (65, 1, 11, 'KELUAR', 'PENJUALAN', 1, 37, 7000, 5, 'TRANSAKSI', 'TRX-5', null, 4, (current_date - 2 + time '12:20')::timestamptz, null, null),
  (66, 1, 12, 'MASUK', 'BARANG_MASUK', 30, 30, 5000, null, 'BARANG_MASUK', 'BM-1-12', 'Stok awal (seed)', 3, (current_date - 4 + time '06:00')::timestamptz, 5000, 'Deryl Fabiensyah'),
  (67, 1, 12, 'KELUAR', 'PENJUALAN', 1, 29, 5000, 7, 'TRANSAKSI', 'TRX-7', null, 4, (current_date - 1 + time '09:05')::timestamptz, null, null),
  (68, 1, 12, 'KELUAR', 'PENJUALAN', 1, 28, 5000, 15, 'TRANSAKSI', 'TRX-15', null, 4, (current_date - 1 + time '12:15')::timestamptz, null, null),
  (69, 1, 12, 'KELUAR', 'PENJUALAN', 1, 27, 5000, 5, 'TRANSAKSI', 'TRX-5', null, 4, (current_date - 2 + time '12:20')::timestamptz, null, null),
  (70, 1, 12, 'KELUAR', 'PENJUALAN', 1, 26, 5000, 6, 'TRANSAKSI', 'TRX-6', null, 4, (current_date - 2 + time '13:05')::timestamptz, null, null),
  (71, 10, 1000, 'MASUK', 'BARANG_MASUK', 25, 25, 7000, null, 'BARANG_MASUK', 'BM-10-1000', 'Stok awal (seed)', 13, (current_date - 4 + time '06:00')::timestamptz, 7000, 'Yusuf Hamdani'),
  (72, 10, 1000, 'KELUAR', 'PENJUALAN', 2, 23, 7000, 33, 'TRANSAKSI', 'TRX-33', null, 14, (current_date - 0 + time '09:20')::timestamptz, null, null),
  (73, 10, 1000, 'KELUAR', 'PENJUALAN', 1, 22, 7000, 27, 'TRANSAKSI', 'TRX-27', null, 14, (current_date - 1 + time '09:15')::timestamptz, null, null),
  (74, 10, 1000, 'KELUAR', 'PENJUALAN', 1, 21, 7000, 31, 'TRANSAKSI', 'TRX-31', null, 14, (current_date - 1 + time '12:20')::timestamptz, null, null),
  (75, 10, 1001, 'MASUK', 'BARANG_MASUK', 40, 40, 6000, null, 'BARANG_MASUK', 'BM-10-1001', 'Stok awal (seed)', 13, (current_date - 4 + time '06:00')::timestamptz, 6000, 'Yusuf Hamdani'),
  (76, 10, 1001, 'KELUAR', 'PENJUALAN', 1, 39, 6000, 30, 'TRANSAKSI', 'TRX-30', null, 14, (current_date - 1 + time '11:40')::timestamptz, null, null),
  (77, 10, 1002, 'MASUK', 'BARANG_MASUK', 24, 24, 1000, null, 'BARANG_MASUK', 'BM-10-1002', 'Stok awal (seed)', 13, (current_date - 4 + time '06:00')::timestamptz, 1000, 'Yusuf Hamdani'),
  (78, 10, 1002, 'KELUAR', 'PENJUALAN', 1, 23, 1000, 34, 'TRANSAKSI', 'TRX-34', null, 14, (current_date - 0 + time '10:05')::timestamptz, null, null),
  (79, 10, 1002, 'KELUAR', 'PENJUALAN', 1, 22, 1000, 27, 'TRANSAKSI', 'TRX-27', null, 14, (current_date - 1 + time '09:15')::timestamptz, null, null),
  (80, 10, 1002, 'KELUAR', 'PENJUALAN', 2, 20, 1000, 31, 'TRANSAKSI', 'TRX-31', null, 14, (current_date - 1 + time '12:20')::timestamptz, null, null),
  (81, 10, 1002, 'KELUAR', 'PENJUALAN', 1, 19, 1000, 32, 'TRANSAKSI', 'TRX-32', null, 14, (current_date - 1 + time '12:55')::timestamptz, null, null),
  (82, 10, 1003, 'MASUK', 'BARANG_MASUK', 17, 17, 2000, null, 'BARANG_MASUK', 'BM-10-1003', 'Stok awal (seed)', 13, (current_date - 4 + time '06:00')::timestamptz, 2000, 'Yusuf Hamdani'),
  (83, 10, 1003, 'KELUAR', 'PENJUALAN', 1, 16, 2000, 34, 'TRANSAKSI', 'TRX-34', null, 14, (current_date - 0 + time '10:05')::timestamptz, null, null),
  (84, 10, 1003, 'KELUAR', 'PENJUALAN', 1, 15, 2000, 28, 'TRANSAKSI', 'TRX-28', null, 14, (current_date - 1 + time '10:00')::timestamptz, null, null),
  (85, 10, 1003, 'KELUAR', 'PENJUALAN', 1, 14, 2000, 32, 'TRANSAKSI', 'TRX-32', null, 14, (current_date - 1 + time '12:55')::timestamptz, null, null),
  (86, 10, 1004, 'MASUK', 'BARANG_MASUK', 40, 40, 9000, null, 'BARANG_MASUK', 'BM-10-1004', 'Stok awal (seed)', 13, (current_date - 4 + time '06:00')::timestamptz, 9000, 'Yusuf Hamdani'),
  (87, 10, 1004, 'KELUAR', 'PENJUALAN', 1, 39, 9000, 34, 'TRANSAKSI', 'TRX-34', null, 14, (current_date - 0 + time '10:05')::timestamptz, null, null),
  (88, 10, 1004, 'KELUAR', 'PENJUALAN', 1, 38, 9000, 35, 'TRANSAKSI', 'TRX-35', null, 14, (current_date - 0 + time '10:45')::timestamptz, null, null),
  (89, 10, 1004, 'KELUAR', 'PENJUALAN', 1, 37, 9000, 28, 'TRANSAKSI', 'TRX-28', null, 14, (current_date - 1 + time '10:00')::timestamptz, null, null),
  (90, 10, 1005, 'MASUK', 'BARANG_MASUK', 42, 42, 3000, null, 'BARANG_MASUK', 'BM-10-1005', 'Stok awal (seed)', 13, (current_date - 4 + time '06:00')::timestamptz, 3000, 'Yusuf Hamdani'),
  (91, 10, 1005, 'KELUAR', 'PENJUALAN', 1, 41, 3000, 36, 'TRANSAKSI', 'TRX-36', null, 14, (current_date - 0 + time '11:20')::timestamptz, null, null),
  (92, 10, 1005, 'KELUAR', 'PENJUALAN', 1, 40, 3000, 29, 'TRANSAKSI', 'TRX-29', null, 14, (current_date - 1 + time '11:10')::timestamptz, null, null),
  (93, 10, 1005, 'KELUAR', 'PENJUALAN', 1, 39, 3000, 30, 'TRANSAKSI', 'TRX-30', null, 14, (current_date - 1 + time '11:40')::timestamptz, null, null),
  (94, 10, 1006, 'MASUK', 'BARANG_MASUK', 23, 23, 5000, null, 'BARANG_MASUK', 'BM-10-1006', 'Stok awal (seed)', 13, (current_date - 4 + time '06:00')::timestamptz, 5000, 'Yusuf Hamdani'),
  (95, 10, 1007, 'MASUK', 'BARANG_MASUK', 38, 38, 4000, null, 'BARANG_MASUK', 'BM-10-1007', 'Stok awal (seed)', 13, (current_date - 4 + time '06:00')::timestamptz, 4000, 'Yusuf Hamdani'),
  (96, 10, 1007, 'KELUAR', 'PENJUALAN', 1, 37, 4000, 35, 'TRANSAKSI', 'TRX-35', null, 14, (current_date - 0 + time '10:45')::timestamptz, null, null),
  (97, 10, 1007, 'KELUAR', 'PENJUALAN', 1, 36, 4000, 29, 'TRANSAKSI', 'TRX-29', null, 14, (current_date - 1 + time '11:10')::timestamptz, null, null),
  (98, 10, 1008, 'MASUK', 'BARANG_MASUK', 44, 44, 2000, null, 'BARANG_MASUK', 'BM-10-1008', 'Stok awal (seed)', 13, (current_date - 4 + time '06:00')::timestamptz, 2000, 'Yusuf Hamdani'),
  (99, 10, 1009, 'MASUK', 'BARANG_MASUK', 19, 19, 5000, null, 'BARANG_MASUK', 'BM-10-1009', 'Stok awal (seed)', 13, (current_date - 4 + time '06:00')::timestamptz, 5000, 'Yusuf Hamdani'),
  (100, 10, 1010, 'MASUK', 'BARANG_MASUK', 37, 37, 7000, null, 'BARANG_MASUK', 'BM-10-1010', 'Stok awal (seed)', 13, (current_date - 4 + time '06:00')::timestamptz, 7000, 'Yusuf Hamdani'),
  (101, 10, 1011, 'MASUK', 'BARANG_MASUK', 28, 28, 5000, null, 'BARANG_MASUK', 'BM-10-1011', 'Stok awal (seed)', 13, (current_date - 4 + time '06:00')::timestamptz, 5000, 'Yusuf Hamdani'),
  (102, 10, 1011, 'KELUAR', 'PENJUALAN', 1, 27, 5000, 33, 'TRANSAKSI', 'TRX-33', null, 14, (current_date - 0 + time '09:20')::timestamptz, null, null),
  (103, 10, 1011, 'KELUAR', 'PENJUALAN', 1, 26, 5000, 32, 'TRANSAKSI', 'TRX-32', null, 14, (current_date - 1 + time '12:55')::timestamptz, null, null);

-- 18. JURNAL BUKU KAS — penanda posting per transaksi & per tutup sesi
insert into jurnal_buku_kas (id, sekolah_id, referensi_id, entitas, mutasi_id, ref_buku_kas, jumlah, status, waktu) values
  (1, 1, 'TRX-1', 'TRANSAKSI', null, null, 15000, 'SUKSES', (current_date - 2 + time '09:12')::timestamptz),
  (2, 1, 'TRX-2', 'TRANSAKSI', null, null, 14000, 'SUKSES', (current_date - 2 + time '09:40')::timestamptz),
  (3, 1, 'TRX-3', 'TRANSAKSI', null, null, 19000, 'SUKSES', (current_date - 2 + time '10:15')::timestamptz),
  (4, 1, 'TRX-4', 'TRANSAKSI', null, null, 11000, 'SUKSES', (current_date - 2 + time '11:02')::timestamptz),
  (5, 1, 'TRX-5', 'TRANSAKSI', null, null, 18000, 'SUKSES', (current_date - 2 + time '12:20')::timestamptz),
  (6, 1, 'TRX-6', 'TRANSAKSI', null, null, 25000, 'SUKSES', (current_date - 2 + time '13:05')::timestamptz),
  (7, 1, 'TRX-7', 'TRANSAKSI', null, null, 31000, 'SUKSES', (current_date - 1 + time '09:05')::timestamptz),
  (8, 1, 'TRX-8', 'TRANSAKSI', null, null, 17000, 'SUKSES', (current_date - 1 + time '09:35')::timestamptz),
  (9, 1, 'TRX-9', 'TRANSAKSI', null, null, 17000, 'SUKSES', (current_date - 1 + time '10:10')::timestamptz),
  (10, 1, 'TRX-10', 'TRANSAKSI', null, null, 20000, 'SUKSES', (current_date - 1 + time '10:50')::timestamptz),
  (11, 1, 'TRX-11', 'TRANSAKSI', null, null, 15000, 'SUKSES', (current_date - 1 + time '11:30')::timestamptz),
  (12, 1, 'TRX-12', 'TRANSAKSI', null, null, 18000, 'SUKSES', (current_date - 1 + time '12:05')::timestamptz),
  (13, 1, 'TRX-13', 'TRANSAKSI', null, null, 7000, 'SUKSES', (current_date - 1 + time '12:40')::timestamptz),
  (14, 1, 'TRX-15', 'TRANSAKSI', null, null, 23000, 'SUKSES', (current_date - 1 + time '12:15')::timestamptz),
  (15, 1, 'TRX-16', 'TRANSAKSI', null, null, 7000, 'SUKSES', (current_date - 1 + time '10:35')::timestamptz),
  (16, 1, 'TRX-17', 'TRANSAKSI', null, null, 8000, 'SUKSES', (current_date - 1 + time '11:05')::timestamptz),
  (17, 1, 'TRX-18', 'TRANSAKSI', null, null, 8000, 'SUKSES', (current_date - 1 + time '11:45')::timestamptz),
  (18, 1, 'TRX-19', 'TRANSAKSI', null, null, 10000, 'SUKSES', (current_date - 1 + time '12:50')::timestamptz),
  (19, 1, 'TRX-20', 'TRANSAKSI', null, null, 11000, 'SUKSES', (current_date - 1 + time '09:20')::timestamptz),
  (20, 1, 'TRX-21', 'TRANSAKSI', null, null, 7000, 'SUKSES', (current_date - 1 + time '09:55')::timestamptz),
  (21, 1, 'TRX-22', 'TRANSAKSI', null, null, 32000, 'SUKSES', (current_date - 1 + time '10:30')::timestamptz),
  (22, 1, 'TRX-23', 'TRANSAKSI', null, null, 19000, 'SUKSES', (current_date - 0 + time '09:10')::timestamptz),
  (23, 1, 'TRX-24', 'TRANSAKSI', null, null, 25000, 'SUKSES', (current_date - 0 + time '09:45')::timestamptz),
  (24, 1, 'TRX-25', 'TRANSAKSI', null, null, 8000, 'SUKSES', (current_date - 0 + time '10:20')::timestamptz),
  (25, 1, 'TRX-26', 'TRANSAKSI', null, null, 5000, 'SUKSES', (current_date - 0 + time '11:00')::timestamptz),
  (26, 10, 'TRX-27', 'TRANSAKSI', null, null, 15000, 'SUKSES', (current_date - 1 + time '09:15')::timestamptz),
  (27, 10, 'TRX-28', 'TRANSAKSI', null, null, 17000, 'SUKSES', (current_date - 1 + time '10:00')::timestamptz),
  (28, 10, 'TRX-29', 'TRANSAKSI', null, null, 11000, 'SUKSES', (current_date - 1 + time '11:10')::timestamptz),
  (29, 10, 'TRX-30', 'TRANSAKSI', null, null, 15000, 'SUKSES', (current_date - 1 + time '11:40')::timestamptz),
  (30, 10, 'TRX-31', 'TRANSAKSI', null, null, 18000, 'SUKSES', (current_date - 1 + time '12:20')::timestamptz),
  (31, 10, 'TRX-32', 'TRANSAKSI', null, null, 14000, 'SUKSES', (current_date - 1 + time '12:55')::timestamptz),
  (32, 10, 'TRX-33', 'TRANSAKSI', null, null, 31000, 'SUKSES', (current_date - 0 + time '09:20')::timestamptz),
  (33, 10, 'TRX-34', 'TRANSAKSI', null, null, 20000, 'SUKSES', (current_date - 0 + time '10:05')::timestamptz),
  (34, 10, 'TRX-35', 'TRANSAKSI', null, null, 19000, 'SUKSES', (current_date - 0 + time '10:45')::timestamptz),
  (35, 10, 'TRX-36', 'TRANSAKSI', null, null, 5000, 'SUKSES', (current_date - 0 + time '11:20')::timestamptz),
  (36, 1, 'SESI-1', 'SESI_KASIR', null, 'SESI-1', 102000, 'SUKSES', (current_date - 2 + time '15:05')::timestamptz),
  (37, 1, 'SESI-2', 'SESI_KASIR', null, 'SESI-2', 181000, 'SUKSES', (current_date - 1 + time '15:10')::timestamptz),
  (38, 1, 'SESI-4', 'SESI_KASIR', null, 'SESI-4', 50000, 'SUKSES', (current_date - 1 + time '15:00')::timestamptz),
  (39, 10, 'SESI-11', 'SESI_KASIR', null, 'SESI-11', 90000, 'SUKSES', (current_date - 1 + time '14:40')::timestamptz);

-- 19. SETORAN TU — uang top up yang disetor ke bendahara
insert into setoran (id, sekolah_id, tanggal, petugas_id, total_topup, jumlah_disetor, referensi_id, catatan, dikonfirmasi_oleh, waktu, status) values
  (1, 1,  (current_date - 3), 2, 1025000, 1025000, 'SETORAN-1', 'Setoran top up tunai hari itu', 5, (current_date - 3 + time '14:30')::timestamptz, 'DIKONFIRMASI'),
  (2, 10, (current_date - 3), 12, 400000, 400000, 'SETORAN-2', 'Setoran top up tunai hari itu', 15, (current_date - 3 + time '14:45')::timestamptz, 'DIKONFIRMASI');

-- 20. KEJADIAN OFFLINE — internet putus di kasir
insert into kejadian_offline (id, sekolah_id, titik_kasir_id, mulai, selesai, durasi_menit, keterangan, dilaporkan_oleh) values
  (1, 1,  1,  (current_date - 1 + time '10:05')::timestamptz, (current_date - 1 + time '10:17')::timestamptz, 12, 'Internet sekolah terputus, kasir sementara offline', 4),
  (2, 10, 11, (current_date - 1 + time '12:40')::timestamptz, (current_date - 1 + time '12:48')::timestamptz, 8,  'Router kasir restart, transaksi tertunda', 14);

-- 21. CATATAN AUDIT — jejak aksi penting
insert into catatan_audit (id, sekolah_id, aktor_id, aksi, entitas, entitas_id, nilai_lama, nilai_baru, alasan, waktu) values
  (1, 1, 2, 'TOPUP_SALDO',      'saldo',       '101',  '0',      '150000', 'Top up tunai di TU', (current_date - 3 + time '08:00')::timestamptz),
  (2, 1, 4, 'VOID_TRANSAKSI',   'transaksi',   '14',   '18000',  '0',      'Salah input menu', (current_date - 1 + time '13:20')::timestamptz),
  (3, 1, 4, 'TUTUP_SESI',       'sesi_kasir',  '1',    null,     '102000', 'Tutup sesi normal', (current_date - 2 + time '15:05')::timestamptz),
  (4, 1, 3, 'UBAH_HARGA',       'menu',        '5',    '12000',  '13000',  'Penyesuaian harga jual', (current_date - 2 + time '07:10')::timestamptz),
  (5, 1, 2, 'BLOKIR_SISWA',     'pembatasan',  '1',    null,     'LIMIT_HARIAN 20000', 'Permintaan orang tua', (current_date - 2 + time '08:30')::timestamptz),
  (6, 10, 12, 'TOPUP_SALDO',    'saldo',       '1001', '0',      '150000', 'Top up tunai di TU', (current_date - 3 + time '08:10')::timestamptz);

-- 22. PEMBATASAN — limit harian & blokir item (menggantikan blokir_kartu/limit_harian/blokir_item)
insert into pembatasan (id, sekolah_id, siswa_id, jenis, menu_id, kategori_id, nominal, alasan, aktif, diubah_oleh, waktu) values
  (1, 1, 107, 'LIMIT_HARIAN', null, null, 20000, 'Batas belanja harian dari orang tua', true,  2, (current_date - 2 + time '08:30')::timestamptz),
  (2, 1, 108, 'BLOKIR_ITEM',  6,    null, null,  'Alergi keripik (permintaan orang tua)', true,  2, (current_date - 2 + time '09:00')::timestamptz),
  (3, 1, 109, 'BLOKIR_ITEM',  null, 3,    null,  'Diet snack (sudah dicabut)',            false, 2, (current_date - 4 + time '09:00')::timestamptz);

-- 23. TRANSAKSI TERTUNDA — tap menunggu konfirmasi kasir
insert into transaksi_tertunda (id, sekolah_id, idempotency_key, rfid_uid, titik_kasir_id, petugas_id, total, items_json, status, transaksi_id, dibuat_at, kedaluwarsa_at, siswa_id) values
  (1, 1, 'SEED-PENDING-1', '04AA000202', 1, 4, 11000, '[{\"menuId\": 3, \"namaMenu\": \"Es Teh Manis\", \"hargaJual\": 3000, \"qty\": 2, \"subtotal\": 6000}, {\"menuId\": 6, \"namaMenu\": \"Keripik\", \"hargaJual\": 5000, \"qty\": 1, \"subtotal\": 5000}]', 'MENUNGGU',    null, (current_date - 0 + time '10:50')::timestamptz, (current_date - 0 + time '10:55')::timestamptz, 202),
  (2, 1, 'SEED-PENDING-2', '04AA000201', 1, 4, 7000,  '[{\"menuId\": 10, \"namaMenu\": \"Buku Tulis\", \"hargaJual\": 7000, \"qty\": 1, \"subtotal\": 7000}]', 'DIBATALKAN',  null, (current_date - 1 + time '11:15')::timestamptz, (current_date - 1 + time '11:20')::timestamptz, 201);

-- 24. TIDAK PERLU SINKRON SEQUENCE
--     Semua PK di skema v2 diisi eksplisit (seed) atau oleh `helper/IdGenerator`
--     di aplikasi (epoch-millis × 1000 + counter), jadi tidak ada sequence
--     PostgreSQL yang harus di-`setval`.

commit;

-- =====================================================================
-- RINGKASAN ISI (dipakai juga oleh SeedDevIT)
--   sekolah 2 | pengguna 12 | siswa 19 | orang_tua 38 | kartu 19
--   kategori_menu 8 | menu 24 | stok 24 | titik_kasir 4
--   pengaturan_kantin 2 | akun_buku_kas 8 | saldo 19
--   sesi_kasir 6 (4 ditutup, 2 terbuka) | transaksi 36 (35 sukses, 1 void)
--   transaksi_item 77 | mutasi_saldo 53 | riwayat_stok 103 | jurnal_buku_kas 39
--   setoran 2 | kejadian_offline 2 | catatan_audit 6 | pembatasan 3
--   transaksi_tertunda 2
--
-- Alur data yang dijaga konsisten:
--   saldo.saldo        = Σ (KREDIT − DEBIT) mutasi_saldo   (trigger)
--   stok.stok          = Σ (MASUK − KELUAR) riwayat_stok   (trigger)
--   transaksi.total    = Σ transaksi_item.subtotal
--   sesi_kasir.bersih  = total_bruto − total_void
-- =====================================================================
