-- =====================================================================
-- SEED 2 SEKOLAH: sekolah 1 (SMPN 1) dan sekolah 10 (SMPN 10)
-- Password semua akun: Kantin#2026
-- =====================================================================

begin;

-- 1. SEKOLAH
insert into sekolah (id, nama, npsn, alamat, aktif) values
  (1,  'SMP Negeri 1 Skoolia',  '20260001',
   'Jl. Merdeka No. 1, Kel. Sukamaju, Kec. Cilodong, Kota Depok, Jawa Barat 16415', true),
  (10, 'SMP Negeri 10 Skoolia', '20260010',
   'Jl. Diponegoro No. 10, Kel. Tugu, Kec. Cimanggis, Kota Depok, Jawa Barat 16451', true);

-- 2. PENGGUNA — 12 akun, password: Kantin#2026
insert into pengguna (id, sekolah_id, nama, username, peran, password_hash, aktif) values
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
insert into kategori_menu (id, sekolah_id, nama, urutan, aktif) values
  (1, 1, 'Makanan', 1, true),
  (2, 1, 'Minuman', 2, true),
  (3, 1, 'Snack', 3, true),
  (4, 1, 'Alat Tulis', 4, true),
  (11, 10, 'Makanan', 1, true),
  (12, 10, 'Minuman', 2, true),
  (13, 10, 'Snack', 3, true),
  (14, 10, 'Alat Tulis', 4, true);

-- 7. MENU — 12 menu per sekolah (foto_url dari data lama)
insert into menu (id, sekolah_id, kategori_id, nama, kode_barcode, satuan, foto_url, harga_jual, hpp, stok_minimum, aktif) values
  (1, 1, 1, 'Nasi Goreng', 'MNU-0001', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/34/Nasi_goreng_pattaya_20231028_120535.jpg/500px-Nasi_goreng_pattaya_20231028_120535.jpg', 12000, 7000, 5, true),
  (2, 1, 1, 'Mie Ayam', 'MNU-0002', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/82/Mi_ayam_jamur.JPG/500px-Mi_ayam_jamur.JPG', 10000, 6000, 5, true),
  (3, 1, 2, 'Es Teh Manis', 'MNU-0003', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d4/Iced_lychee_tea_and_Es_teh_manis_-_Bali_2025-09-25.jpg/500px-Iced_lychee_tea_and_Es_teh_manis_-_Bali_2025-09-25.jpg', 3000, 1000, 10, true),
  (4, 1, 2, 'Air Mineral', 'MNU-0004', 'BOTOL', 'https://upload.wikimedia.org/wikipedia/commons/0/02/Stilles_Mineralwasser.jpg', 4000, 2000, 10, true),
  (5, 1, 1, 'Ayam Geprek', 'MNU-0005', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/5c/Ayam_Geprek_Uyat.jpg/500px-Ayam_Geprek_Uyat.jpg', 13000, 9000, 5, true),
  (6, 1, 3, 'Keripik', 'MNU-0006', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/70/Keripik_sanjay.JPG/500px-Keripik_sanjay.JPG', 5000, 3000, 10, true),
  (7, 1, 3, 'Roti Bakar', 'MNU-0007', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/1a/Roti_bakar_kekinian.jpg/500px-Roti_bakar_kekinian.jpg', 8000, 5000, 5, true),
  (8, 1, 2, 'Susu Kotak', 'MNU-0008', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/f8/2025.04.10_Milk_Glass_Sugar_Bowl_and_Antique_Glass_Creamer.jpg/500px-2025.04.10_Milk_Glass_Sugar_Bowl_and_Antique_Glass_Creamer.jpg', 6000, 4000, 10, true),
  (9, 1, 4, 'Pulpen', 'MNU-0009', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/7b/Fountain_pen_writing_%28literacy%29.jpg/500px-Fountain_pen_writing_%28literacy%29.jpg', 3000, 2000, 20, true),
  (10, 1, 4, 'Buku Tulis', 'MNU-0010', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/f0/Notebook_writing.jpg/500px-Notebook_writing.jpg', 7000, 5000, 10, true),
  (11, 1, 1, 'Bakso', 'MNU-0011', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/6/6e/Bakso_Indonesian_Meatball_Soup_from_Solo.jpg/500px-Bakso_Indonesian_Meatball_Soup_from_Solo.jpg', 11000, 7000, 5, true),
  (12, 1, 2, 'Jus Jeruk', 'MNU-0012', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/6/67/Orange_juice_1_edit1.jpg/500px-Orange_juice_1_edit1.jpg', 7000, 5000, 5, true),
  (1000, 10, 11, 'Nasi Goreng', 'MNU-1000', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/34/Nasi_goreng_pattaya_20231028_120535.jpg/500px-Nasi_goreng_pattaya_20231028_120535.jpg', 12000, 7000, 5, true),
  (1001, 10, 11, 'Mie Ayam', 'MNU-1001', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/82/Mi_ayam_jamur.JPG/500px-Mi_ayam_jamur.JPG', 10000, 6000, 5, true),
  (1002, 10, 12, 'Es Teh Manis', 'MNU-1002', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d4/Iced_lychee_tea_and_Es_teh_manis_-_Bali_2025-09-25.jpg/500px-Iced_lychee_tea_and_Es_teh_manis_-_Bali_2025-09-25.jpg', 3000, 1000, 10, true),
  (1003, 10, 12, 'Air Mineral', 'MNU-1003', 'BOTOL', 'https://upload.wikimedia.org/wikipedia/commons/0/02/Stilles_Mineralwasser.jpg', 4000, 2000, 10, true),
  (1004, 10, 11, 'Ayam Geprek', 'MNU-1004', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/5c/Ayam_Geprek_Uyat.jpg/500px-Ayam_Geprek_Uyat.jpg', 13000, 9000, 5, true),
  (1005, 10, 13, 'Keripik', 'MNU-1005', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/70/Keripik_sanjay.JPG/500px-Keripik_sanjay.JPG', 5000, 3000, 10, true),
  (1006, 10, 13, 'Roti Bakar', 'MNU-1006', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/1a/Roti_bakar_kekinian.jpg/500px-Roti_bakar_kekinian.jpg', 8000, 5000, 5, true),
  (1007, 10, 12, 'Susu Kotak', 'MNU-1007', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/f8/2025.04.10_Milk_Glass_Sugar_Bowl_and_Antique_Glass_Creamer.jpg/500px-2025.04.10_Milk_Glass_Sugar_Bowl_and_Antique_Glass_Creamer.jpg', 6000, 4000, 10, true),
  (1008, 10, 14, 'Pulpen', 'MNU-1008', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/7b/Fountain_pen_writing_%28literacy%29.jpg/500px-Fountain_pen_writing_%28literacy%29.jpg', 3000, 2000, 20, true),
  (1009, 10, 14, 'Buku Tulis', 'MNU-1009', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/f0/Notebook_writing.jpg/500px-Notebook_writing.jpg', 7000, 5000, 10, true),
  (1010, 10, 11, 'Bakso', 'MNU-1010', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/6/6e/Bakso_Indonesian_Meatball_Soup_from_Solo.jpg/500px-Bakso_Indonesian_Meatball_Soup_from_Solo.jpg', 11000, 7000, 5, true),
  (1011, 10, 12, 'Jus Jeruk', 'MNU-1011', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/6/67/Orange_juice_1_edit1.jpg/500px-Orange_juice_1_edit1.jpg', 7000, 5000, 5, true);

-- 8. STOK — 1 baris per menu
insert into stok (menu_id, sekolah_id, jumlah, hpp, stok_minimum) values
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
  (1011, 10, 26, 5000, 5);

-- 9. TITIK KASIR
insert into titik_kasir (id, sekolah_id, kode, nama, aktif) values
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

-- 12. SALDO AWAL + MUTASI (hanya siswa bersaldo > 0)
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

insert into mutasi_saldo (sekolah_id, siswa_id, arah, jenis, nominal, saldo_setelah, keterangan, aktor_id) values
  (1, 101, 'KREDIT', 'TOPUP_TUNAI', 85000, 85000, 'Saldo awal seed', 2),
  (1, 102, 'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 2),
  (1, 103, 'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 2),
  (1, 104, 'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 2),
  (1, 105, 'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 2),
  (1, 106, 'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 2),
  (1, 107, 'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 2),
  (1, 108, 'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 2),
  (1, 109, 'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 2),
  (1, 110, 'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 2),
  (1, 12, 'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 2),
  (1, 13, 'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 2),
  (10, 1001, 'KREDIT', 'TOPUP_TUNAI', 85000, 85000, 'Saldo awal seed', 12),
  (10, 1005, 'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 12),
  (10, 1006, 'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 12),
  (10, 1007, 'KREDIT', 'TOPUP_TUNAI', 50000, 50000, 'Saldo awal seed', 12);

-- 13. SINKRONKAN SEQUENCE (karena id diisi manual)
select setval(pg_get_serial_sequence('pengguna','id'), (select max(id) from pengguna));
select setval(pg_get_serial_sequence('kategori_menu','id'), (select max(id) from kategori_menu));
select setval(pg_get_serial_sequence('menu','id'), (select max(id) from menu));
select setval(pg_get_serial_sequence('titik_kasir','id'), (select max(id) from titik_kasir));
select setval(pg_get_serial_sequence('akun_buku_kas','id'), (select max(id) from akun_buku_kas));

commit;

-- =====================================================================
-- RINGKASAN ISI
--   sekolah 2 | pengguna 12 | siswa 19 | orang_tua 38 | kartu 19
--   kategori_menu 8 | menu 24 | stok 24 | titik_kasir 4
--   pengaturan_kantin 2 | akun_buku_kas 8 | saldo 19 | mutasi_saldo 16
-- =====================================================================