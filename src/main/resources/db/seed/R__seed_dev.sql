-- ============================================================
-- R__seed_dev.sql — Data dev/demo untuk skema v2 (23 tabel)
-- ------------------------------------------------------------
-- ⚠️ BUKAN data produksi.
--    Flyway REPEATABLE migration. Sejak folder `db/seed` TIDAK lagi
--    didaftarkan di `application-local.properties.example`, file ini hanya
--    berjalan bila diminta manual:
--      psql -h localhost -U kantin -d kantin_db -f <file ini>
--    CI & seluruh test tidak memuatnya secara default (SeedDevIT mendaftarkan
--    lokasi seed sendiri).
--
-- Password semua akun: Kantin#2026
--
-- Isi:
--   sekolah 2 · pengguna 14 (7 peran x 2 sekolah, 2 kasir per sekolah) · siswa 21 (19 aktif + 2 nonaktif)
--   orang_tua 42 (2 wali per siswa) · kartu 21 · kategori_menu 8
--   menu 24 · stok 24 · titik_kasir 4 · pengaturan_kantin 2
--   akun_buku_kas 8 · saldo 21
--   sesi_kasir 16 (14 ditutup, 2 terbuka) · transaksi 62 (61 sukses: 55 saldo + 6 tunai; 1 void)
--   transaksi_item 107
--   mutasi_saldo 80 (topup tunai/online, penjualan, void, refund, transfer, koreksi)
--   riwayat_stok 138 (termasuk 5 kerugian: 3 barang rusak + 2 opname keluar)
--   jurnal_buku_kas 74 · setoran 2
--   kejadian_offline 2 · catatan_audit 10 · pembatasan 3 · transaksi_tertunda 2
--
-- Riwayat 7 hari terakhir memakai `current_date - n`, jadi dashboard, laporan
-- harian, dan rekap sesi selalu ada isinya tanpa perlu transaksi manual.
--
-- Sifat: DESTRUKTIF untuk data dev — tabel dikosongkan dulu, lalu diisi
-- ulang, supaya hasilnya deterministik dan screenshot FE tidak berubah
-- antar-run. Jangan daftarkan folder db/seed di staging/produksi.
-- ============================================================

begin;

-- kosongkan dulu (deterministik). cascade karena banyak FK.
truncate table catatan_audit, kejadian_offline, jurnal_buku_kas, akun_buku_kas, transaksi_tertunda, pembatasan, setoran, mutasi_saldo, saldo, transaksi_item, transaksi, sesi_kasir, titik_kasir, riwayat_stok, stok, menu, kategori_menu, kartu, orang_tua, siswa, pengguna, pengaturan_kantin, sekolah restart identity cascade;

-- 1. SEKOLAH — alamat lengkap (jalan, kelurahan, kecamatan, kota, provinsi, kode pos)
--    logo_url berisi URL gambar (pola sama dengan siswa.foto_url)
insert into sekolah (id, nama, npsn, alamat, aktif, logo_url) values
  (1,  'SMP Negeri 1 Skoolia',  '20260001',
   'Jl. Merdeka No. 1, Kel. Sukamaju, Kec. Cilodong, Kota Depok, Jawa Barat 16415', true,
   '/logo-sekolah-1.png'),
  (10, 'SMP Negeri 10 Skoolia', '20260010',
   'Jl. Diponegoro No. 10, Kel. Tugu, Kec. Cimanggis, Kota Depok, Jawa Barat 16451', true,
   '/logo-sekolah-10.png');

-- 2. PENGGUNA — 14 akun (2 kasir di tiap sekolah), password: Kantin#2026
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
  (16, 10, 'Dewi Anggraini', 'admin10', 'ADMIN_SEKOLAH', '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  -- Kasir kedua per sekolah untuk uji operasional multi-titik (issue #345):
  -- sekolah 1 → Titik 2 (DEMO2 "Kasir Kelas 7"), sekolah 10 → Titik 12 (FE10-2).
  -- Password sama dengan akun dev lain: Kantin#2026.
  (7,  1, 'Dimas Prasetyo', 'kasir2', 'PETUGAS', '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true),
  (17, 10, 'Wulan Sari', 'kasir20', 'PETUGAS', '$2a$10$LGt9ZvDWW97jh0ozfFEIrOlwkp.DQ7HSpq7Eg/VxbHLn3UDuD.Xem', true);

-- 3. SISWA — 19 siswa, foto_url potret nyata pelajar
insert into siswa (id, sekolah_id, nis, nisn, nama, kelas, foto_url, aktif) values
  (101, 1, '24250101', '001010000', 'Raka Aditya Pratama', '7A', 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400', true),
  (102, 1, '24250102', '001020000', 'Nadia Safitri', '7A', 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=400', true),
  (103, 1, '24250103', '001030000', 'Bima Arya Nugraha', '7B', 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=400', true),
  (104, 1, '24250104', '001040000', 'Salsabila Putri', '7B', 'https://images.unsplash.com/photo-1517841905240-472988babdf9?w=400', true),
  (105, 1, '24250105', '001050000', 'Fajar Ramadhan', '8A', 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=400', true),
  (106, 1, '24250106', '001060000', 'Kirana Dewi Lestari', '8A', 'https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=400', true),
  (107, 1, '24250107', '001070000', 'Abizard', '8B', 'https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=400', true),
  (108, 1, '24250108', '001080000', 'Fajar Nugraha', '7B', 'https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=400', true),
  (109, 1, '24250109', '001090000', 'Citra Kirana', '8A', 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=400', true),
  (110, 1, '24250110', '001100000', 'Daffa Alfarizi', '9A', 'https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=400', true),
  (12, 1, '24250012', '00120000', 'Alya Rahmawati', '7C', 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400', true),
  (13, 1, '24250013', '00130000', 'Bagas Saputra', '7C', 'https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=400', true),
  (201, 1, '24250201', '002010000', 'Rangga Pratama', '7A', 'https://images.unsplash.com/photo-1501196354995-cbb51c65aaea?w=400', true),
  (202, 1, '24250202', '002020000', 'Sinta Maharani', '7B', 'https://images.unsplash.com/photo-1488426862026-3ee34a7d66df?w=400', true),
  (1001, 10, '24251001', '0010010000', 'Ahmad Fauzan', '7A', 'https://images.unsplash.com/photo-1543610892-0b1f7e6d8ac1?w=400', true),
  (1005, 10, '24251005', '0010050000', 'Citra Ayu Lestari', '7A', 'https://images.unsplash.com/photo-1508214751196-bcfd4ca60f91?w=400', true),
  (1006, 10, '24251006', '0010060000', 'Dimas Prakoso', '7B', 'https://images.unsplash.com/photo-1499952127939-9bbf5af6c51c?w=400', true),
  (1007, 10, '24251007', '0010070000', 'Eka Wulandari', '7B', 'https://images.unsplash.com/photo-1529626455594-4ff0802cfb7e?w=400', true),
  (3001, 10, '24253001', '0030010000', 'Anisa Rahma', '7A', 'https://images.unsplash.com/photo-1531746020798-e6953c6e8e04?w=400', true);

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

-- 7. MENU — 12 menu per sekolah
--    Produk bermerek memakai packshot asli (URL hotlink, tanpa mengunduh berkas
--    ke repo) supaya foto cocok dengan merek pada nama menu. Menu lain memakai
--    foto bebas lisensi dari Wikimedia Commons.
--    Packshot dibungkus images.weserv.nl (proxy gambar publik) menjadi kanvas
--    4:3 latar putih (`w=600&h=450&fit=contain&bg=white&cbg=white`). Alasannya:
--    kartu menu di kasir memakai kotak `aspect-4/3` + `object-cover`, sehingga
--    foto packshot yang tinggi (±1:2) terlihat terpotong/terlalu zoom. Dengan
--    kanvas 4:3, gambar mengisi kartu tanpa ada bagian produk yang terpotong.
--    `bg=white` diperlukan untuk sumber PNG/WEBP transparan agar tidak menjadi
--    hitam saat dikonversi ke JPEG.
--    Sumber asli tiap packshot (semua packshot resmi di latar putih):
--      Air Mineral Aqua 600 ml        → image.astronauts.cloud (AQUA Air Mineral
--                                       Botol 600 ml)
--      Keripik Qtela Singkong 60 g    → indofood.com (halaman produk Qtela)
--      Susu Ultra Cokelat 250 ml      → cdn.bormadago.com (Ultra Milk Cokelat
--                                       250 ml biasa, bukan varian Low Fat)
--      Buku Tulis Sidu 38 Lembar      → foto produk penjual (osc.co.id)
--      Pulpen Standard AE7 Hitam      → foto produk penjual (gramedia.com)
--    Catatan: packshot merek adalah aset berhak cipta pemilik merek; dipakai
--    hanya untuk data contoh/demo. Untuk produksi, unggah foto sendiri lewat
--    menu Katalog (masuk storage MinIO/LocalStorage) dengan rasio mendekati 4:3.
insert into menu (id, sekolah_id, kategori_id, nama, kode_barcode, satuan, foto_url, harga_jual, stok_minimum, is_active) values
  (1, 1, 1, 'Nasi Goreng', 'MNU-0001', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/34/Nasi_goreng_pattaya_20231028_120535.jpg/500px-Nasi_goreng_pattaya_20231028_120535.jpg', 12000, 5, true),
  (2, 1, 1, 'Mie Ayam', 'MNU-0002', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/82/Mi_ayam_jamur.JPG/500px-Mi_ayam_jamur.JPG', 10000, 5, true),
  (3, 1, 2, 'Es Teh Manis', 'MNU-0003', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d4/Iced_lychee_tea_and_Es_teh_manis_-_Bali_2025-09-25.jpg/500px-Iced_lychee_tea_and_Es_teh_manis_-_Bali_2025-09-25.jpg', 3000, 10, true),
  (4, 1, 2, 'Air Mineral Aqua 600 ml', 'MNU-0004', 'BOTOL', 'https://images.weserv.nl/?url=image.astronauts.cloud/product-images/2026/7/AquaMineralWater600m_aa25284d-9af0-4bce-b442-4c82ed03002c_400x400.png&w=600&h=450&fit=contain&bg=white&cbg=white&output=jpg&q=90', 4000, 10, true),
  (5, 1, 1, 'Ayam Geprek', 'MNU-0005', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/5c/Ayam_Geprek_Uyat.jpg/500px-Ayam_Geprek_Uyat.jpg', 13000, 5, true),
  (6, 1, 3, 'Keripik Qtela Singkong 60 g', 'MNU-0006', 'PCS', 'https://images.weserv.nl/?url=www.indofood.com/images/microsite/product/280qtela-singkong-original.jpg&w=600&h=450&fit=contain&bg=white&cbg=white&output=jpg&q=88', 5000, 10, true),
  (7, 1, 3, 'Roti Bakar', 'MNU-0007', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/1a/Roti_bakar_kekinian.jpg/500px-Roti_bakar_kekinian.jpg', 8000, 5, true),
  (8, 1, 2, 'Susu Ultra Cokelat 250 ml', 'MNU-0008', 'PCS', 'https://images.weserv.nl/?url=cdn.bormadago.com/media/images/products/2020/12/41.1.png&w=600&h=450&fit=contain&bg=white&cbg=white&output=jpg&q=90', 6000, 10, true),
  (9, 1, 4, 'Pulpen Standard AE7 Hitam', 'MNU-0009', 'PCS', 'https://images.weserv.nl/?url=cdn.gramedia.com/uploads/items/DSC_0240.JPG&w=600&h=450&fit=contain&bg=white&cbg=white&output=jpg&q=88', 3000, 20, true),
  (10, 1, 4, 'Buku Tulis Sidu 38 Lembar', 'MNU-0010', 'PCS', 'https://images.weserv.nl/?url=osc.co.id/storage/products/8TIel1FYlO9R9vWAFmKf7gYnwboq1MYOBW6JAnhH.jpg&w=600&h=450&fit=contain&bg=white&cbg=white&output=jpg&q=88', 7000, 10, true),
  (11, 1, 1, 'Bakso', 'MNU-0011', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/6/6e/Bakso_Indonesian_Meatball_Soup_from_Solo.jpg/500px-Bakso_Indonesian_Meatball_Soup_from_Solo.jpg', 11000, 5, true),
  (12, 1, 2, 'Jus Jeruk', 'MNU-0012', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/6/67/Orange_juice_1_edit1.jpg/500px-Orange_juice_1_edit1.jpg', 7000, 5, true),
  (1000, 10, 11, 'Nasi Goreng', 'MNU-1000', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/34/Nasi_goreng_pattaya_20231028_120535.jpg/500px-Nasi_goreng_pattaya_20231028_120535.jpg', 12000, 5, true),
  (1001, 10, 11, 'Mie Ayam', 'MNU-1001', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/82/Mi_ayam_jamur.JPG/500px-Mi_ayam_jamur.JPG', 10000, 5, true),
  (1002, 10, 12, 'Es Teh Manis', 'MNU-1002', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d4/Iced_lychee_tea_and_Es_teh_manis_-_Bali_2025-09-25.jpg/500px-Iced_lychee_tea_and_Es_teh_manis_-_Bali_2025-09-25.jpg', 3000, 10, true),
  (1003, 10, 12, 'Air Mineral Aqua 600 ml', 'MNU-1003', 'BOTOL', 'https://images.weserv.nl/?url=image.astronauts.cloud/product-images/2026/7/AquaMineralWater600m_aa25284d-9af0-4bce-b442-4c82ed03002c_400x400.png&w=600&h=450&fit=contain&bg=white&cbg=white&output=jpg&q=90', 4000, 10, true),
  (1004, 10, 11, 'Ayam Geprek', 'MNU-1004', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/5c/Ayam_Geprek_Uyat.jpg/500px-Ayam_Geprek_Uyat.jpg', 13000, 5, true),
  (1005, 10, 13, 'Keripik Qtela Singkong 60 g', 'MNU-1005', 'PCS', 'https://images.weserv.nl/?url=www.indofood.com/images/microsite/product/280qtela-singkong-original.jpg&w=600&h=450&fit=contain&bg=white&cbg=white&output=jpg&q=88', 5000, 10, true),
  (1006, 10, 13, 'Roti Bakar', 'MNU-1006', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/1a/Roti_bakar_kekinian.jpg/500px-Roti_bakar_kekinian.jpg', 8000, 5, true),
  (1007, 10, 12, 'Susu Ultra Cokelat 250 ml', 'MNU-1007', 'PCS', 'https://images.weserv.nl/?url=cdn.bormadago.com/media/images/products/2020/12/41.1.png&w=600&h=450&fit=contain&bg=white&cbg=white&output=jpg&q=90', 6000, 10, true),
  (1008, 10, 14, 'Pulpen Standard AE7 Hitam', 'MNU-1008', 'PCS', 'https://images.weserv.nl/?url=cdn.gramedia.com/uploads/items/DSC_0240.JPG&w=600&h=450&fit=contain&bg=white&cbg=white&output=jpg&q=88', 3000, 20, true),
  (1009, 10, 14, 'Buku Tulis Sidu 38 Lembar', 'MNU-1009', 'PCS', 'https://images.weserv.nl/?url=osc.co.id/storage/products/8TIel1FYlO9R9vWAFmKf7gYnwboq1MYOBW6JAnhH.jpg&w=600&h=450&fit=contain&bg=white&cbg=white&output=jpg&q=88', 7000, 10, true),
  (1010, 10, 11, 'Bakso', 'MNU-1010', 'PORSI', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/6/6e/Bakso_Indonesian_Meatball_Soup_from_Solo.jpg/500px-Bakso_Indonesian_Meatball_Soup_from_Solo.jpg', 11000, 5, true),
  (1011, 10, 12, 'Jus Jeruk', 'MNU-1011', 'PCS', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/6/67/Orange_juice_1_edit1.jpg/500px-Orange_juice_1_edit1.jpg', 7000, 5, true);

-- 8. STOK — 1 baris per menu
insert into stok (menu_id, sekolah_id, stok, hpp, stok_minimum) values
  (1, 1, 17, 7000, 5),
  (2, 1, 39, 6000, 5),
  (3, 1, 19, 1000, 10),
  (4, 1, 6, 2000, 10),
  (5, 1, 37, 9000, 5),
  (6, 1, 35, 3000, 10),
  (7, 1, 23, 5000, 5),
  (8, 1, 36, 4000, 10),
  (9, 1, 44, 2000, 20),
  (10, 1, 19, 5000, 10),
  (11, 1, 37, 7000, 5),
  (12, 1, 26, 5000, 5),
  (1000, 10, 21, 7000, 5),
  (1001, 10, 35, 6000, 5),
  (1002, 10, 19, 1000, 10),
  (1003, 10, 14, 2000, 10),
  (1004, 10, 37, 9000, 5),
  (1005, 10, 35, 3000, 10),
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

-- 13. SESI KASIR — 7 hari terakhir (hari ini sengaja masih TERBUKA untuk demo kasir)
insert into sesi_kasir (id, sekolah_id, titik_kasir_id, tanggal, status, total_bruto, total_void, total_bersih, dibuka_at, ditutup_at, ditutup_oleh, auto_tutup, jurnal_buku_kas, referensi_buku_kas, uang_fisik, selisih, catatan) values
  (1, 1, 1, (current_date - 2), 'DITUTUP', 102000, 0, 102000, (current_date - 2 + time '07:30')::timestamptz, (current_date - 2 + time '15:05')::timestamptz, 4, false, true, 'SESI-1', 102000, 0, 'Tutup normal, uang fisik sesuai sistem'),
  (2, 1, 1, (current_date - 1), 'DITUTUP', 181000, 18000, 163000, (current_date - 1 + time '07:25')::timestamptz, (current_date - 1 + time '15:10')::timestamptz, 4, false, true, 'SESI-2', 163000, 0, 'Tutup normal, uang fisik sesuai sistem'),
  (3, 1, 1, (current_date - 0), 'TERBUKA', 57000, 0, 57000, (current_date - 0 + time '07:20')::timestamptz, null, null, false, false, null, null, null, null),
  (4, 1, 2, (current_date - 1), 'DITUTUP', 50000, 0, 50000, (current_date - 1 + time '07:40')::timestamptz, (current_date - 1 + time '15:00')::timestamptz, 4, false, true, 'SESI-4', 50000, 0, 'Tutup normal, uang fisik sesuai sistem'),
  (11, 10, 11, (current_date - 1), 'DITUTUP', 90000, 0, 90000, (current_date - 1 + time '07:15')::timestamptz, (current_date - 1 + time '14:40')::timestamptz, 4, false, true, 'SESI-11', 90000, 0, 'Tutup normal, uang fisik sesuai sistem'),
  (12, 10, 11, (current_date - 0), 'TERBUKA', 75000, 0, 75000, (current_date - 0 + time '07:10')::timestamptz, null, null, false, false, null, null, null, null),
  (23, 1, 1, (current_date - 3), 'DITUTUP', 25000, 0, 25000, (current_date - 3 + time '07:30')::timestamptz, (current_date - 3 + time '15:00')::timestamptz, 4, false, true, 'SESI-23', 25000, 0, 'Tutup normal, uang fisik sesuai sistem'),
  (24, 1, 1, (current_date - 4), 'DITUTUP', 25000, 0, 25000, (current_date - 4 + time '07:30')::timestamptz, (current_date - 4 + time '15:00')::timestamptz, 4, false, true, 'SESI-24', 25000, 0, 'Tutup normal, uang fisik sesuai sistem'),
  (25, 1, 1, (current_date - 5), 'DITUTUP', 25000, 0, 25000, (current_date - 5 + time '07:30')::timestamptz, (current_date - 5 + time '15:00')::timestamptz, 4, false, true, 'SESI-25', 25000, 0, 'Tutup normal, uang fisik sesuai sistem'),
  (26, 1, 1, (current_date - 6), 'DITUTUP', 25000, 0, 25000, (current_date - 6 + time '07:30')::timestamptz, (current_date - 6 + time '15:00')::timestamptz, 4, false, true, 'SESI-26', 25000, 0, 'Tutup normal, uang fisik sesuai sistem'),
  (33, 10, 11, (current_date - 3), 'DITUTUP', 15000, 0, 15000, (current_date - 3 + time '07:30')::timestamptz, (current_date - 3 + time '15:00')::timestamptz, 4, false, true, 'SESI-33', 15000, 0, 'Tutup normal, uang fisik sesuai sistem'),
  (34, 10, 11, (current_date - 4), 'DITUTUP', 15000, 0, 15000, (current_date - 4 + time '07:30')::timestamptz, (current_date - 4 + time '15:00')::timestamptz, 4, false, true, 'SESI-34', 15000, 0, 'Tutup normal, uang fisik sesuai sistem'),
  (35, 10, 11, (current_date - 5), 'DITUTUP', 15000, 0, 15000, (current_date - 5 + time '07:30')::timestamptz, (current_date - 5 + time '15:00')::timestamptz, 4, false, true, 'SESI-35', 15000, 0, 'Tutup normal, uang fisik sesuai sistem'),
  (36, 10, 11, (current_date - 6), 'DITUTUP', 15000, 0, 15000, (current_date - 6 + time '07:30')::timestamptz, (current_date - 6 + time '15:00')::timestamptz, 4, false, true, 'SESI-36', 15000, 0, 'Tutup normal, uang fisik sesuai sistem');

-- Jejak pembuka sesi (issue #345). Demo multi-titik: titik 1 sekolah 1 dibuka
-- kasir1 (4), titik 2 sekolah 1 dibuka kasir2 (7), semua sesi sekolah 10 dibuka
-- kasir10 (14).
update sesi_kasir
   set dibuka_oleh = case
       when sekolah_id = 1 and titik_kasir_id = 2 then 7
       when sekolah_id = 1 then 4
       else 14
   end
 where dibuka_oleh is null;

-- 14. TRANSAKSI — 56 transaksi 7 hari terakhir (1 void untuk demo pembatalan)
insert into transaksi (id, idempotency_key, sekolah_id, sesi_kasir_id, titik_kasir_id, kartu_uid, petugas_id, total, total_hpp, status, alasan_void, void_at, void_oleh, waktu, siswa_id, metode_bayar) values
  (151, 'SEED-TRX-151', 1, 26, 1, '04A1B2C3D4', 4, 12000, 7000, 'SUKSES', null, null, null, (current_date - 6 + time '09:15')::timestamptz, 101, 'SALDO'),
  (154, 'SEED-TRX-154', 10, 36, 11, 'DA100001', 14, 10000, 6000, 'SUKSES', null, null, null, (current_date - 6 + time '09:30')::timestamptz, 1001, 'SALDO'),
  (152, 'SEED-TRX-152', 1, 26, 1, '04B2C3D4E5', 4, 8000, 4000, 'SUKSES', null, null, null, (current_date - 6 + time '10:20')::timestamptz, 102, 'SALDO'),
  (155, 'SEED-TRX-155', 10, 36, 11, 'DA100005', 14, 5000, 3000, 'SUKSES', null, null, null, (current_date - 6 + time '10:40')::timestamptz, 1005, 'SALDO'),
  (153, 'SEED-TRX-153', 1, 26, 1, '04C3D4E5F6', 4, 5000, 3000, 'SUKSES', null, null, null, (current_date - 6 + time '11:35')::timestamptz, 103, 'SALDO'),
  (146, 'SEED-TRX-146', 1, 25, 1, '04A1B2C3D4', 4, 12000, 7000, 'SUKSES', null, null, null, (current_date - 5 + time '09:15')::timestamptz, 101, 'SALDO'),
  (149, 'SEED-TRX-149', 10, 35, 11, 'DA100001', 14, 10000, 6000, 'SUKSES', null, null, null, (current_date - 5 + time '09:30')::timestamptz, 1001, 'SALDO'),
  (147, 'SEED-TRX-147', 1, 25, 1, '04B2C3D4E5', 4, 8000, 4000, 'SUKSES', null, null, null, (current_date - 5 + time '10:20')::timestamptz, 102, 'SALDO'),
  (150, 'SEED-TRX-150', 10, 35, 11, 'DA100005', 14, 5000, 3000, 'SUKSES', null, null, null, (current_date - 5 + time '10:40')::timestamptz, 1005, 'SALDO'),
  (148, 'SEED-TRX-148', 1, 25, 1, '04C3D4E5F6', 4, 5000, 3000, 'SUKSES', null, null, null, (current_date - 5 + time '11:35')::timestamptz, 103, 'SALDO'),
  (141, 'SEED-TRX-141', 1, 24, 1, '04A1B2C3D4', 4, 12000, 7000, 'SUKSES', null, null, null, (current_date - 4 + time '09:15')::timestamptz, 101, 'SALDO'),
  (144, 'SEED-TRX-144', 10, 34, 11, 'DA100001', 14, 10000, 6000, 'SUKSES', null, null, null, (current_date - 4 + time '09:30')::timestamptz, 1001, 'SALDO'),
  (142, 'SEED-TRX-142', 1, 24, 1, '04B2C3D4E5', 4, 8000, 4000, 'SUKSES', null, null, null, (current_date - 4 + time '10:20')::timestamptz, 102, 'SALDO'),
  (145, 'SEED-TRX-145', 10, 34, 11, 'DA100005', 14, 5000, 3000, 'SUKSES', null, null, null, (current_date - 4 + time '10:40')::timestamptz, 1005, 'SALDO'),
  (143, 'SEED-TRX-143', 1, 24, 1, '04C3D4E5F6', 4, 5000, 3000, 'SUKSES', null, null, null, (current_date - 4 + time '11:35')::timestamptz, 103, 'SALDO'),
  (136, 'SEED-TRX-136', 1, 23, 1, '04A1B2C3D4', 4, 12000, 7000, 'SUKSES', null, null, null, (current_date - 3 + time '09:15')::timestamptz, 101, 'SALDO'),
  (139, 'SEED-TRX-139', 10, 33, 11, 'DA100001', 14, 10000, 6000, 'SUKSES', null, null, null, (current_date - 3 + time '09:30')::timestamptz, 1001, 'SALDO'),
  (137, 'SEED-TRX-137', 1, 23, 1, '04B2C3D4E5', 4, 8000, 4000, 'SUKSES', null, null, null, (current_date - 3 + time '10:20')::timestamptz, 102, 'SALDO'),
  (140, 'SEED-TRX-140', 10, 33, 11, 'DA100005', 14, 5000, 3000, 'SUKSES', null, null, null, (current_date - 3 + time '10:40')::timestamptz, 1005, 'SALDO'),
  (138, 'SEED-TRX-138', 1, 23, 1, '04C3D4E5F6', 4, 5000, 3000, 'SUKSES', null, null, null, (current_date - 3 + time '11:35')::timestamptz, 103, 'SALDO'),
  (1, 'SEED-TRX-1', 1, 1, 1, '04A1B2C3D4', 4, 15000, 8000, 'SUKSES', null, null, null, (current_date - 2 + time '09:12')::timestamptz, 101, 'SALDO'),
  (2, 'SEED-TRX-2', 1, 1, 1, '04B2C3D4E5', 4, 14000, 8000, 'SUKSES', null, null, null, (current_date - 2 + time '09:40')::timestamptz, 102, 'SALDO'),
  (3, 'SEED-TRX-3', 1, 1, 1, '04C3D4E5F6', 4, 19000, 11000, 'SUKSES', null, null, null, (current_date - 2 + time '10:15')::timestamptz, 103, 'SALDO'),
  (4, 'SEED-TRX-4', 1, 1, 1, '04D4E5F6A1', 4, 11000, 7000, 'SUKSES', null, null, null, (current_date - 2 + time '11:02')::timestamptz, 104, 'SALDO'),
  (5, 'SEED-TRX-5', 1, 1, 1, '04E5F6A1B2', 4, 18000, 12000, 'SUKSES', null, null, null, (current_date - 2 + time '12:20')::timestamptz, 105, 'SALDO'),
  (6, 'SEED-TRX-6', 1, 1, 1, '04F1A2B3C4', 4, 25000, 17000, 'SUKSES', null, null, null, (current_date - 2 + time '13:05')::timestamptz, 106, 'SALDO'),
  (7, 'SEED-TRX-7', 1, 2, 1, '04A1B2C3D4', 4, 31000, 19000, 'SUKSES', null, null, null, (current_date - 1 + time '09:05')::timestamptz, 101, 'SALDO'),
  (27, 'SEED-TRX-27', 10, 11, 11, 'DA100001', 14, 15000, 8000, 'SUKSES', null, null, null, (current_date - 1 + time '09:15')::timestamptz, 1001, 'SALDO'),
  (20, 'SEED-TRX-20', 1, 4, 2, '04B2C3D4E5', 4, 11000, 5000, 'SUKSES', null, null, null, (current_date - 1 + time '09:20')::timestamptz, 102, 'SALDO'),
  (8, 'SEED-TRX-8', 1, 2, 1, '1831574427', 4, 17000, 9000, 'SUKSES', null, null, null, (current_date - 1 + time '09:35')::timestamptz, 107, 'SALDO'),
  (21, 'SEED-TRX-21', 1, 4, 2, '04D4E5F6A1', 4, 7000, 5000, 'SUKSES', null, null, null, (current_date - 1 + time '09:55')::timestamptz, 104, 'SALDO'),
  (28, 'SEED-TRX-28', 10, 11, 11, 'DA100005', 14, 17000, 11000, 'SUKSES', null, null, null, (current_date - 1 + time '10:00')::timestamptz, 1005, 'SALDO'),
  (9, 'SEED-TRX-9', 1, 2, 1, '2225414394', 4, 17000, 11000, 'SUKSES', null, null, null, (current_date - 1 + time '10:10')::timestamptz, 108, 'SALDO'),
  (22, 'SEED-TRX-22', 1, 4, 2, '04D4E5F6A1', 4, 32000, 19000, 'SUKSES', null, null, null, (current_date - 1 + time '10:30')::timestamptz, 104, 'SALDO'),
  (16, 'SEED-TRX-16', 1, 2, 1, '04E5F6A1B2', 4, 7000, 3000, 'SUKSES', null, null, null, (current_date - 1 + time '10:35')::timestamptz, 105, 'SALDO'),
  (10, 'SEED-TRX-10', 1, 2, 1, '0830267682', 4, 20000, 12000, 'SUKSES', null, null, null, (current_date - 1 + time '10:50')::timestamptz, 109, 'SALDO'),
  (17, 'SEED-TRX-17', 1, 2, 1, '1831574427', 4, 8000, 5000, 'SUKSES', null, null, null, (current_date - 1 + time '11:05')::timestamptz, 107, 'SALDO'),
  (29, 'SEED-TRX-29', 10, 11, 11, 'DA100006', 14, 11000, 7000, 'SUKSES', null, null, null, (current_date - 1 + time '11:10')::timestamptz, 1006, 'SALDO'),
  (11, 'SEED-TRX-11', 1, 2, 1, '04F5A6B7C8', 4, 15000, 8000, 'SUKSES', null, null, null, (current_date - 1 + time '11:30')::timestamptz, 110, 'SALDO'),
  (30, 'SEED-TRX-30', 10, 11, 11, 'DA100005', 14, 15000, 9000, 'SUKSES', null, null, null, (current_date - 1 + time '11:40')::timestamptz, 1005, 'SALDO'),
  (18, 'SEED-TRX-18', 1, 2, 1, '2225414394', 4, 8000, 4000, 'SUKSES', null, null, null, (current_date - 1 + time '11:45')::timestamptz, 108, 'SALDO'),
  (12, 'SEED-TRX-12', 1, 2, 1, '04A2B3C4D5', 4, 18000, 12000, 'SUKSES', null, null, null, (current_date - 1 + time '12:05')::timestamptz, 12, 'SALDO'),
  (15, 'SEED-TRX-15', 1, 2, 1, '04C3D4E5F6', 4, 23000, 15000, 'SUKSES', null, null, null, (current_date - 1 + time '12:15')::timestamptz, 103, 'SALDO'),
  (31, 'SEED-TRX-31', 10, 11, 11, 'DA100005', 14, 18000, 9000, 'SUKSES', null, null, null, (current_date - 1 + time '12:20')::timestamptz, 1005, 'SALDO'),
  (13, 'SEED-TRX-13', 1, 2, 1, '04A2B3C4D5', 4, 7000, 3000, 'SUKSES', null, null, null, (current_date - 1 + time '12:40')::timestamptz, 12, 'SALDO'),
  (19, 'SEED-TRX-19', 1, 2, 1, '04F5A6B7C8', 4, 10000, 6000, 'SUKSES', null, null, null, (current_date - 1 + time '12:50')::timestamptz, 110, 'SALDO'),
  (32, 'SEED-TRX-32', 10, 11, 11, 'DA100006', 14, 14000, 8000, 'SUKSES', null, null, null, (current_date - 1 + time '12:55')::timestamptz, 1006, 'SALDO'),
  (14, 'SEED-TRX-14', 1, 2, 1, '04B3C4D5E6', 4, 18000, 11000, 'VOID', 'Salah input menu (demo void)', (current_date - 1 + time '13:20')::timestamptz, 4, (current_date - 1 + time '13:10')::timestamptz, 13, 'SALDO'),
  (23, 'SEED-TRX-23', 1, 3, 1, '04A1B2C3D4', 4, 19000, 13000, 'SUKSES', null, null, null, (current_date - 0 + time '09:10')::timestamptz, 101, 'SALDO'),
  (33, 'SEED-TRX-33', 10, 12, 11, 'DA100001', 14, 31000, 19000, 'SUKSES', null, null, null, (current_date - 0 + time '09:20')::timestamptz, 1001, 'SALDO'),
  (24, 'SEED-TRX-24', 1, 3, 1, '04B2C3D4E5', 4, 25000, 16000, 'SUKSES', null, null, null, (current_date - 0 + time '09:45')::timestamptz, 102, 'SALDO'),
  (34, 'SEED-TRX-34', 10, 12, 11, 'DA100007', 14, 20000, 12000, 'SUKSES', null, null, null, (current_date - 0 + time '10:05')::timestamptz, 1007, 'SALDO'),
  (25, 'SEED-TRX-25', 1, 3, 1, '04C3D4E5F6', 4, 8000, 5000, 'SUKSES', null, null, null, (current_date - 0 + time '10:20')::timestamptz, 103, 'SALDO'),
  (35, 'SEED-TRX-35', 10, 12, 11, 'DA100001', 14, 19000, 13000, 'SUKSES', null, null, null, (current_date - 0 + time '10:45')::timestamptz, 1001, 'SALDO'),
  (26, 'SEED-TRX-26', 1, 3, 1, '0830267682', 4, 5000, 3000, 'SUKSES', null, null, null, (current_date - 0 + time '11:00')::timestamptz, 109, 'SALDO'),
  (36, 'SEED-TRX-36', 10, 12, 11, 'DA100007', 14, 5000, 3000, 'SUKSES', null, null, null, (current_date - 0 + time '11:20')::timestamptz, 1007, 'SALDO');

-- 15. TRANSAKSI ITEM
insert into transaksi_item (id, transaksi_id, menu_id, nama_menu, kategori_id, harga_jual, qty, hpp_snapshot, subtotal, created_at) values
  (1, 151, 1, 'Nasi Goreng', 1, 12000, 1, 7000, 12000, (current_date - 6 + time '09:15')::timestamptz),
  (2, 154, 1001, 'Mie Ayam', 11, 10000, 1, 6000, 10000, (current_date - 6 + time '09:30')::timestamptz),
  (3, 152, 4, 'Air Mineral Aqua 600 ml', 2, 4000, 2, 2000, 8000, (current_date - 6 + time '10:20')::timestamptz),
  (4, 155, 1005, 'Keripik Qtela Singkong 60 g', 13, 5000, 1, 3000, 5000, (current_date - 6 + time '10:40')::timestamptz),
  (5, 153, 6, 'Keripik Qtela Singkong 60 g', 3, 5000, 1, 3000, 5000, (current_date - 6 + time '11:35')::timestamptz),
  (6, 146, 1, 'Nasi Goreng', 1, 12000, 1, 7000, 12000, (current_date - 5 + time '09:15')::timestamptz),
  (7, 149, 1001, 'Mie Ayam', 11, 10000, 1, 6000, 10000, (current_date - 5 + time '09:30')::timestamptz),
  (8, 147, 4, 'Air Mineral Aqua 600 ml', 2, 4000, 2, 2000, 8000, (current_date - 5 + time '10:20')::timestamptz),
  (9, 150, 1005, 'Keripik Qtela Singkong 60 g', 13, 5000, 1, 3000, 5000, (current_date - 5 + time '10:40')::timestamptz),
  (10, 148, 6, 'Keripik Qtela Singkong 60 g', 3, 5000, 1, 3000, 5000, (current_date - 5 + time '11:35')::timestamptz),
  (11, 141, 1, 'Nasi Goreng', 1, 12000, 1, 7000, 12000, (current_date - 4 + time '09:15')::timestamptz),
  (12, 144, 1001, 'Mie Ayam', 11, 10000, 1, 6000, 10000, (current_date - 4 + time '09:30')::timestamptz),
  (13, 142, 4, 'Air Mineral Aqua 600 ml', 2, 4000, 2, 2000, 8000, (current_date - 4 + time '10:20')::timestamptz),
  (14, 145, 1005, 'Keripik Qtela Singkong 60 g', 13, 5000, 1, 3000, 5000, (current_date - 4 + time '10:40')::timestamptz),
  (15, 143, 6, 'Keripik Qtela Singkong 60 g', 3, 5000, 1, 3000, 5000, (current_date - 4 + time '11:35')::timestamptz),
  (16, 136, 1, 'Nasi Goreng', 1, 12000, 1, 7000, 12000, (current_date - 3 + time '09:15')::timestamptz),
  (17, 139, 1001, 'Mie Ayam', 11, 10000, 1, 6000, 10000, (current_date - 3 + time '09:30')::timestamptz),
  (18, 137, 4, 'Air Mineral Aqua 600 ml', 2, 4000, 2, 2000, 8000, (current_date - 3 + time '10:20')::timestamptz),
  (19, 140, 1005, 'Keripik Qtela Singkong 60 g', 13, 5000, 1, 3000, 5000, (current_date - 3 + time '10:40')::timestamptz),
  (20, 138, 6, 'Keripik Qtela Singkong 60 g', 3, 5000, 1, 3000, 5000, (current_date - 3 + time '11:35')::timestamptz),
  (21, 1, 1, 'Nasi Goreng', 1, 12000, 1, 7000, 12000, (current_date - 2 + time '09:12')::timestamptz),
  (22, 1, 3, 'Es Teh Manis', 2, 3000, 1, 1000, 3000, (current_date - 2 + time '09:12')::timestamptz),
  (23, 2, 2, 'Mie Ayam', 1, 10000, 1, 6000, 10000, (current_date - 2 + time '09:40')::timestamptz),
  (24, 2, 4, 'Air Mineral Aqua 600 ml', 2, 4000, 1, 2000, 4000, (current_date - 2 + time '09:40')::timestamptz),
  (25, 3, 5, 'Ayam Geprek', 1, 13000, 1, 9000, 13000, (current_date - 2 + time '10:15')::timestamptz),
  (26, 3, 3, 'Es Teh Manis', 2, 3000, 2, 1000, 6000, (current_date - 2 + time '10:15')::timestamptz),
  (27, 4, 6, 'Keripik Qtela Singkong 60 g', 3, 5000, 1, 3000, 5000, (current_date - 2 + time '11:02')::timestamptz),
  (28, 4, 8, 'Susu Ultra Cokelat 250 ml', 2, 6000, 1, 4000, 6000, (current_date - 2 + time '11:02')::timestamptz),
  (29, 5, 11, 'Bakso', 1, 11000, 1, 7000, 11000, (current_date - 2 + time '12:20')::timestamptz),
  (30, 5, 12, 'Jus Jeruk', 2, 7000, 1, 5000, 7000, (current_date - 2 + time '12:20')::timestamptz),
  (31, 6, 5, 'Ayam Geprek', 1, 13000, 1, 9000, 13000, (current_date - 2 + time '13:05')::timestamptz),
  (32, 6, 12, 'Jus Jeruk', 2, 7000, 1, 5000, 7000, (current_date - 2 + time '13:05')::timestamptz),
  (33, 6, 6, 'Keripik Qtela Singkong 60 g', 3, 5000, 1, 3000, 5000, (current_date - 2 + time '13:05')::timestamptz),
  (34, 7, 1, 'Nasi Goreng', 1, 12000, 2, 7000, 24000, (current_date - 1 + time '09:05')::timestamptz),
  (35, 7, 12, 'Jus Jeruk', 2, 7000, 1, 5000, 7000, (current_date - 1 + time '09:05')::timestamptz),
  (36, 27, 1000, 'Nasi Goreng', 11, 12000, 1, 7000, 12000, (current_date - 1 + time '09:15')::timestamptz),
  (37, 27, 1002, 'Es Teh Manis', 12, 3000, 1, 1000, 3000, (current_date - 1 + time '09:15')::timestamptz),
  (38, 20, 3, 'Es Teh Manis', 2, 3000, 2, 1000, 6000, (current_date - 1 + time '09:20')::timestamptz),
  (39, 20, 6, 'Keripik Qtela Singkong 60 g', 3, 5000, 1, 3000, 5000, (current_date - 1 + time '09:20')::timestamptz),
  (40, 8, 2, 'Mie Ayam', 1, 10000, 1, 6000, 10000, (current_date - 1 + time '09:35')::timestamptz),
  (41, 8, 4, 'Air Mineral Aqua 600 ml', 2, 4000, 1, 2000, 4000, (current_date - 1 + time '09:35')::timestamptz),
  (42, 8, 3, 'Es Teh Manis', 2, 3000, 1, 1000, 3000, (current_date - 1 + time '09:35')::timestamptz),
  (43, 21, 10, 'Buku Tulis Sidu 38 Lembar', 4, 7000, 1, 5000, 7000, (current_date - 1 + time '09:55')::timestamptz),
  (44, 28, 1004, 'Ayam Geprek', 11, 13000, 1, 9000, 13000, (current_date - 1 + time '10:00')::timestamptz),
  (45, 28, 1003, 'Air Mineral Aqua 600 ml', 12, 4000, 1, 2000, 4000, (current_date - 1 + time '10:00')::timestamptz),
  (46, 9, 5, 'Ayam Geprek', 1, 13000, 1, 9000, 13000, (current_date - 1 + time '10:10')::timestamptz),
  (47, 9, 4, 'Air Mineral Aqua 600 ml', 2, 4000, 1, 2000, 4000, (current_date - 1 + time '10:10')::timestamptz),
  (48, 22, 5, 'Ayam Geprek', 1, 13000, 1, 9000, 13000, (current_date - 1 + time '10:30')::timestamptz),
  (49, 22, 1, 'Nasi Goreng', 1, 12000, 1, 7000, 12000, (current_date - 1 + time '10:30')::timestamptz),
  (50, 22, 3, 'Es Teh Manis', 2, 3000, 1, 1000, 3000, (current_date - 1 + time '10:30')::timestamptz),
  (51, 22, 4, 'Air Mineral Aqua 600 ml', 2, 4000, 1, 2000, 4000, (current_date - 1 + time '10:30')::timestamptz),
  (52, 16, 3, 'Es Teh Manis', 2, 3000, 1, 1000, 3000, (current_date - 1 + time '10:35')::timestamptz),
  (53, 16, 4, 'Air Mineral Aqua 600 ml', 2, 4000, 1, 2000, 4000, (current_date - 1 + time '10:35')::timestamptz),
  (54, 10, 10, 'Buku Tulis Sidu 38 Lembar', 4, 7000, 1, 5000, 7000, (current_date - 1 + time '10:50')::timestamptz),
  (55, 10, 8, 'Susu Ultra Cokelat 250 ml', 2, 6000, 1, 4000, 6000, (current_date - 1 + time '10:50')::timestamptz),
  (56, 10, 3, 'Es Teh Manis', 2, 3000, 1, 1000, 3000, (current_date - 1 + time '10:50')::timestamptz),
  (57, 10, 4, 'Air Mineral Aqua 600 ml', 2, 4000, 1, 2000, 4000, (current_date - 1 + time '10:50')::timestamptz),
  (58, 17, 7, 'Roti Bakar', 3, 8000, 1, 5000, 8000, (current_date - 1 + time '11:05')::timestamptz),
  (59, 29, 1005, 'Keripik Qtela Singkong 60 g', 13, 5000, 1, 3000, 5000, (current_date - 1 + time '11:10')::timestamptz),
  (60, 29, 1007, 'Susu Ultra Cokelat 250 ml', 12, 6000, 1, 4000, 6000, (current_date - 1 + time '11:10')::timestamptz),
  (61, 11, 1, 'Nasi Goreng', 1, 12000, 1, 7000, 12000, (current_date - 1 + time '11:30')::timestamptz),
  (62, 11, 3, 'Es Teh Manis', 2, 3000, 1, 1000, 3000, (current_date - 1 + time '11:30')::timestamptz),
  (63, 30, 1001, 'Mie Ayam', 11, 10000, 1, 6000, 10000, (current_date - 1 + time '11:40')::timestamptz),
  (64, 30, 1005, 'Keripik Qtela Singkong 60 g', 13, 5000, 1, 3000, 5000, (current_date - 1 + time '11:40')::timestamptz),
  (65, 18, 6, 'Keripik Qtela Singkong 60 g', 3, 5000, 1, 3000, 5000, (current_date - 1 + time '11:45')::timestamptz),
  (66, 18, 3, 'Es Teh Manis', 2, 3000, 1, 1000, 3000, (current_date - 1 + time '11:45')::timestamptz),
  (67, 12, 10, 'Buku Tulis Sidu 38 Lembar', 4, 7000, 1, 5000, 7000, (current_date - 1 + time '12:05')::timestamptz),
  (68, 12, 9, 'Pulpen Standard AE7 Hitam', 4, 3000, 1, 2000, 3000, (current_date - 1 + time '12:05')::timestamptz),
  (69, 12, 7, 'Roti Bakar', 3, 8000, 1, 5000, 8000, (current_date - 1 + time '12:05')::timestamptz),
  (70, 15, 11, 'Bakso', 1, 11000, 1, 7000, 11000, (current_date - 1 + time '12:15')::timestamptz),
  (71, 15, 12, 'Jus Jeruk', 2, 7000, 1, 5000, 7000, (current_date - 1 + time '12:15')::timestamptz),
  (72, 15, 6, 'Keripik Qtela Singkong 60 g', 3, 5000, 1, 3000, 5000, (current_date - 1 + time '12:15')::timestamptz),
  (73, 31, 1000, 'Nasi Goreng', 11, 12000, 1, 7000, 12000, (current_date - 1 + time '12:20')::timestamptz),
  (74, 31, 1002, 'Es Teh Manis', 12, 3000, 2, 1000, 6000, (current_date - 1 + time '12:20')::timestamptz),
  (75, 13, 4, 'Air Mineral Aqua 600 ml', 2, 4000, 1, 2000, 4000, (current_date - 1 + time '12:40')::timestamptz),
  (76, 13, 3, 'Es Teh Manis', 2, 3000, 1, 1000, 3000, (current_date - 1 + time '12:40')::timestamptz),
  (77, 19, 8, 'Susu Ultra Cokelat 250 ml', 2, 6000, 1, 4000, 6000, (current_date - 1 + time '12:50')::timestamptz),
  (78, 19, 4, 'Air Mineral Aqua 600 ml', 2, 4000, 1, 2000, 4000, (current_date - 1 + time '12:50')::timestamptz),
  (79, 32, 1011, 'Jus Jeruk', 12, 7000, 1, 5000, 7000, (current_date - 1 + time '12:55')::timestamptz),
  (80, 32, 1003, 'Air Mineral Aqua 600 ml', 12, 4000, 1, 2000, 4000, (current_date - 1 + time '12:55')::timestamptz),
  (81, 32, 1002, 'Es Teh Manis', 12, 3000, 1, 1000, 3000, (current_date - 1 + time '12:55')::timestamptz),
  (82, 14, 1, 'Nasi Goreng', 1, 12000, 1, 7000, 12000, (current_date - 1 + time '13:10')::timestamptz),
  (83, 14, 8, 'Susu Ultra Cokelat 250 ml', 2, 6000, 1, 4000, 6000, (current_date - 1 + time '13:10')::timestamptz),
  (84, 23, 5, 'Ayam Geprek', 1, 13000, 1, 9000, 13000, (current_date - 0 + time '09:10')::timestamptz),
  (85, 23, 8, 'Susu Ultra Cokelat 250 ml', 2, 6000, 1, 4000, 6000, (current_date - 0 + time '09:10')::timestamptz),
  (86, 33, 1000, 'Nasi Goreng', 11, 12000, 2, 7000, 24000, (current_date - 0 + time '09:20')::timestamptz),
  (87, 33, 1011, 'Jus Jeruk', 12, 7000, 1, 5000, 7000, (current_date - 0 + time '09:20')::timestamptz),
  (88, 24, 1, 'Nasi Goreng', 1, 12000, 1, 7000, 12000, (current_date - 0 + time '09:45')::timestamptz),
  (89, 24, 5, 'Ayam Geprek', 1, 13000, 1, 9000, 13000, (current_date - 0 + time '09:45')::timestamptz),
  (90, 34, 1004, 'Ayam Geprek', 11, 13000, 1, 9000, 13000, (current_date - 0 + time '10:05')::timestamptz),
  (91, 34, 1002, 'Es Teh Manis', 12, 3000, 1, 1000, 3000, (current_date - 0 + time '10:05')::timestamptz),
  (92, 34, 1003, 'Air Mineral Aqua 600 ml', 12, 4000, 1, 2000, 4000, (current_date - 0 + time '10:05')::timestamptz),
  (93, 25, 7, 'Roti Bakar', 3, 8000, 1, 5000, 8000, (current_date - 0 + time '10:20')::timestamptz),
  (94, 35, 1004, 'Ayam Geprek', 11, 13000, 1, 9000, 13000, (current_date - 0 + time '10:45')::timestamptz),
  (95, 35, 1007, 'Susu Ultra Cokelat 250 ml', 12, 6000, 1, 4000, 6000, (current_date - 0 + time '10:45')::timestamptz),
  (96, 26, 6, 'Keripik Qtela Singkong 60 g', 3, 5000, 1, 3000, 5000, (current_date - 0 + time '11:00')::timestamptz),
  (97, 36, 1005, 'Keripik Qtela Singkong 60 g', 13, 5000, 1, 3000, 5000, (current_date - 0 + time '11:20')::timestamptz);

-- 16. MUTASI SALDO — top up + penjualan + void (sumber kebenaran saldo)
insert into mutasi_saldo (id, sekolah_id, siswa_id, arah, jenis, nominal, saldo_setelah, transaksi_id, idempotency_key, referensi_tipe, referensi_id, keterangan, aktor_id, waktu) values
  (1, 1, 12, 'KREDIT', 'TOPUP_TUNAI', 75000, 75000, null, 'SEED-TOPUP-12', 'TOPUP', 'TOPUP-12', 'Top up tunai di TU (seed)', 2, (current_date - 6 + time '08:00')::timestamptz),
  (2, 1, 13, 'KREDIT', 'TOPUP_TUNAI', 50000, 50000, null, 'SEED-TOPUP-13', 'TOPUP', 'TOPUP-13', 'Top up tunai di TU (seed)', 2, (current_date - 6 + time '08:00')::timestamptz),
  (3, 1, 101, 'KREDIT', 'TOPUP_TUNAI', 150000, 150000, null, 'SEED-TOPUP-101', 'TOPUP', 'TOPUP-101', 'Top up tunai di TU (seed)', 2, (current_date - 6 + time '08:00')::timestamptz),
  (4, 1, 102, 'KREDIT', 'TOPUP_TUNAI', 100000, 100000, null, 'SEED-TOPUP-102', 'TOPUP', 'TOPUP-102', 'Top up tunai di TU (seed)', 2, (current_date - 6 + time '08:00')::timestamptz),
  (5, 1, 103, 'KREDIT', 'TOPUP_TUNAI', 100000, 100000, null, 'SEED-TOPUP-103', 'TOPUP', 'TOPUP-103', 'Top up tunai di TU (seed)', 2, (current_date - 6 + time '08:00')::timestamptz),
  (6, 1, 104, 'KREDIT', 'TOPUP_TUNAI', 100000, 100000, null, 'SEED-TOPUP-104', 'TOPUP', 'TOPUP-104', 'Top up tunai di TU (seed)', 2, (current_date - 6 + time '08:00')::timestamptz),
  (7, 1, 105, 'KREDIT', 'TOPUP_TUNAI', 75000, 75000, null, 'SEED-TOPUP-105', 'TOPUP', 'TOPUP-105', 'Top up tunai di TU (seed)', 2, (current_date - 6 + time '08:00')::timestamptz),
  (8, 1, 106, 'KREDIT', 'TOPUP_TUNAI', 75000, 75000, null, 'SEED-TOPUP-106', 'TOPUP', 'TOPUP-106', 'Top up tunai di TU (seed)', 2, (current_date - 6 + time '08:00')::timestamptz),
  (9, 1, 107, 'KREDIT', 'TOPUP_TUNAI', 75000, 75000, null, 'SEED-TOPUP-107', 'TOPUP', 'TOPUP-107', 'Top up tunai di TU (seed)', 2, (current_date - 6 + time '08:00')::timestamptz),
  (10, 1, 108, 'KREDIT', 'TOPUP_TUNAI', 75000, 75000, null, 'SEED-TOPUP-108', 'TOPUP', 'TOPUP-108', 'Top up tunai di TU (seed)', 2, (current_date - 6 + time '08:00')::timestamptz),
  (11, 1, 109, 'KREDIT', 'TOPUP_TUNAI', 75000, 75000, null, 'SEED-TOPUP-109', 'TOPUP', 'TOPUP-109', 'Top up tunai di TU (seed)', 2, (current_date - 6 + time '08:00')::timestamptz),
  (12, 1, 110, 'KREDIT', 'TOPUP_TUNAI', 75000, 75000, null, 'SEED-TOPUP-110', 'TOPUP', 'TOPUP-110', 'Top up tunai di TU (seed)', 2, (current_date - 6 + time '08:00')::timestamptz),
  (13, 10, 1001, 'KREDIT', 'TOPUP_TUNAI', 150000, 150000, null, 'SEED-TOPUP-1001', 'TOPUP', 'TOPUP-1001', 'Top up tunai di TU (seed)', 12, (current_date - 6 + time '08:00')::timestamptz),
  (14, 10, 1005, 'KREDIT', 'TOPUP_TUNAI', 100000, 100000, null, 'SEED-TOPUP-1005', 'TOPUP', 'TOPUP-1005', 'Top up tunai di TU (seed)', 12, (current_date - 6 + time '08:00')::timestamptz),
  (15, 10, 1006, 'KREDIT', 'TOPUP_TUNAI', 75000, 75000, null, 'SEED-TOPUP-1006', 'TOPUP', 'TOPUP-1006', 'Top up tunai di TU (seed)', 12, (current_date - 6 + time '08:00')::timestamptz),
  (16, 10, 1007, 'KREDIT', 'TOPUP_TUNAI', 75000, 75000, null, 'SEED-TOPUP-1007', 'TOPUP', 'TOPUP-1007', 'Top up tunai di TU (seed)', 12, (current_date - 6 + time '08:00')::timestamptz),
  (17, 1, 101, 'DEBIT', 'PENJUALAN', 12000, 138000, 151, 'SEED-TRX-151', 'TRANSAKSI', 'TRX-151', 'Pembelian di kasir', 4, (current_date - 6 + time '09:15')::timestamptz),
  (18, 10, 1001, 'DEBIT', 'PENJUALAN', 10000, 140000, 154, 'SEED-TRX-154', 'TRANSAKSI', 'TRX-154', 'Pembelian di kasir', 14, (current_date - 6 + time '09:30')::timestamptz),
  (19, 1, 102, 'DEBIT', 'PENJUALAN', 8000, 92000, 152, 'SEED-TRX-152', 'TRANSAKSI', 'TRX-152', 'Pembelian di kasir', 4, (current_date - 6 + time '10:20')::timestamptz),
  (20, 10, 1005, 'DEBIT', 'PENJUALAN', 5000, 95000, 155, 'SEED-TRX-155', 'TRANSAKSI', 'TRX-155', 'Pembelian di kasir', 14, (current_date - 6 + time '10:40')::timestamptz),
  (21, 1, 103, 'DEBIT', 'PENJUALAN', 5000, 95000, 153, 'SEED-TRX-153', 'TRANSAKSI', 'TRX-153', 'Pembelian di kasir', 4, (current_date - 6 + time '11:35')::timestamptz),
  (22, 1, 101, 'DEBIT', 'PENJUALAN', 12000, 126000, 146, 'SEED-TRX-146', 'TRANSAKSI', 'TRX-146', 'Pembelian di kasir', 4, (current_date - 5 + time '09:15')::timestamptz),
  (23, 10, 1001, 'DEBIT', 'PENJUALAN', 10000, 130000, 149, 'SEED-TRX-149', 'TRANSAKSI', 'TRX-149', 'Pembelian di kasir', 14, (current_date - 5 + time '09:30')::timestamptz),
  (24, 1, 102, 'DEBIT', 'PENJUALAN', 8000, 84000, 147, 'SEED-TRX-147', 'TRANSAKSI', 'TRX-147', 'Pembelian di kasir', 4, (current_date - 5 + time '10:20')::timestamptz),
  (25, 10, 1005, 'DEBIT', 'PENJUALAN', 5000, 90000, 150, 'SEED-TRX-150', 'TRANSAKSI', 'TRX-150', 'Pembelian di kasir', 14, (current_date - 5 + time '10:40')::timestamptz),
  (26, 1, 103, 'DEBIT', 'PENJUALAN', 5000, 90000, 148, 'SEED-TRX-148', 'TRANSAKSI', 'TRX-148', 'Pembelian di kasir', 4, (current_date - 5 + time '11:35')::timestamptz),
  (27, 1, 101, 'DEBIT', 'PENJUALAN', 12000, 114000, 141, 'SEED-TRX-141', 'TRANSAKSI', 'TRX-141', 'Pembelian di kasir', 4, (current_date - 4 + time '09:15')::timestamptz),
  (28, 10, 1001, 'DEBIT', 'PENJUALAN', 10000, 120000, 144, 'SEED-TRX-144', 'TRANSAKSI', 'TRX-144', 'Pembelian di kasir', 14, (current_date - 4 + time '09:30')::timestamptz),
  (29, 1, 102, 'DEBIT', 'PENJUALAN', 8000, 76000, 142, 'SEED-TRX-142', 'TRANSAKSI', 'TRX-142', 'Pembelian di kasir', 4, (current_date - 4 + time '10:20')::timestamptz),
  (30, 10, 1005, 'DEBIT', 'PENJUALAN', 5000, 85000, 145, 'SEED-TRX-145', 'TRANSAKSI', 'TRX-145', 'Pembelian di kasir', 14, (current_date - 4 + time '10:40')::timestamptz),
  (31, 1, 103, 'DEBIT', 'PENJUALAN', 5000, 85000, 143, 'SEED-TRX-143', 'TRANSAKSI', 'TRX-143', 'Pembelian di kasir', 4, (current_date - 4 + time '11:35')::timestamptz),
  (32, 1, 101, 'DEBIT', 'PENJUALAN', 12000, 102000, 136, 'SEED-TRX-136', 'TRANSAKSI', 'TRX-136', 'Pembelian di kasir', 4, (current_date - 3 + time '09:15')::timestamptz),
  (33, 10, 1001, 'DEBIT', 'PENJUALAN', 10000, 110000, 139, 'SEED-TRX-139', 'TRANSAKSI', 'TRX-139', 'Pembelian di kasir', 14, (current_date - 3 + time '09:30')::timestamptz),
  (34, 1, 102, 'DEBIT', 'PENJUALAN', 8000, 68000, 137, 'SEED-TRX-137', 'TRANSAKSI', 'TRX-137', 'Pembelian di kasir', 4, (current_date - 3 + time '10:20')::timestamptz),
  (35, 10, 1005, 'DEBIT', 'PENJUALAN', 5000, 80000, 140, 'SEED-TRX-140', 'TRANSAKSI', 'TRX-140', 'Pembelian di kasir', 14, (current_date - 3 + time '10:40')::timestamptz),
  (36, 1, 103, 'DEBIT', 'PENJUALAN', 5000, 80000, 138, 'SEED-TRX-138', 'TRANSAKSI', 'TRX-138', 'Pembelian di kasir', 4, (current_date - 3 + time '11:35')::timestamptz),
  (37, 1, 101, 'DEBIT', 'PENJUALAN', 15000, 87000, 1, 'SEED-TRX-1', 'TRANSAKSI', 'TRX-1', 'Pembelian di kasir', 4, (current_date - 2 + time '09:12')::timestamptz),
  (38, 1, 102, 'DEBIT', 'PENJUALAN', 14000, 54000, 2, 'SEED-TRX-2', 'TRANSAKSI', 'TRX-2', 'Pembelian di kasir', 4, (current_date - 2 + time '09:40')::timestamptz),
  (39, 1, 103, 'DEBIT', 'PENJUALAN', 19000, 61000, 3, 'SEED-TRX-3', 'TRANSAKSI', 'TRX-3', 'Pembelian di kasir', 4, (current_date - 2 + time '10:15')::timestamptz),
  (40, 1, 104, 'DEBIT', 'PENJUALAN', 11000, 89000, 4, 'SEED-TRX-4', 'TRANSAKSI', 'TRX-4', 'Pembelian di kasir', 4, (current_date - 2 + time '11:02')::timestamptz),
  (41, 1, 105, 'DEBIT', 'PENJUALAN', 18000, 57000, 5, 'SEED-TRX-5', 'TRANSAKSI', 'TRX-5', 'Pembelian di kasir', 4, (current_date - 2 + time '12:20')::timestamptz),
  (42, 1, 106, 'DEBIT', 'PENJUALAN', 25000, 50000, 6, 'SEED-TRX-6', 'TRANSAKSI', 'TRX-6', 'Pembelian di kasir', 4, (current_date - 2 + time '13:05')::timestamptz),
  (43, 1, 101, 'DEBIT', 'PENJUALAN', 31000, 56000, 7, 'SEED-TRX-7', 'TRANSAKSI', 'TRX-7', 'Pembelian di kasir', 4, (current_date - 1 + time '09:05')::timestamptz),
  (44, 10, 1001, 'DEBIT', 'PENJUALAN', 15000, 95000, 27, 'SEED-TRX-27', 'TRANSAKSI', 'TRX-27', 'Pembelian di kasir', 14, (current_date - 1 + time '09:15')::timestamptz),
  (45, 1, 102, 'DEBIT', 'PENJUALAN', 11000, 43000, 20, 'SEED-TRX-20', 'TRANSAKSI', 'TRX-20', 'Pembelian di kasir', 4, (current_date - 1 + time '09:20')::timestamptz),
  (46, 1, 107, 'DEBIT', 'PENJUALAN', 17000, 58000, 8, 'SEED-TRX-8', 'TRANSAKSI', 'TRX-8', 'Pembelian di kasir', 4, (current_date - 1 + time '09:35')::timestamptz),
  (47, 1, 104, 'DEBIT', 'PENJUALAN', 7000, 82000, 21, 'SEED-TRX-21', 'TRANSAKSI', 'TRX-21', 'Pembelian di kasir', 4, (current_date - 1 + time '09:55')::timestamptz),
  (48, 10, 1005, 'DEBIT', 'PENJUALAN', 17000, 63000, 28, 'SEED-TRX-28', 'TRANSAKSI', 'TRX-28', 'Pembelian di kasir', 14, (current_date - 1 + time '10:00')::timestamptz),
  (49, 1, 108, 'DEBIT', 'PENJUALAN', 17000, 58000, 9, 'SEED-TRX-9', 'TRANSAKSI', 'TRX-9', 'Pembelian di kasir', 4, (current_date - 1 + time '10:10')::timestamptz),
  (50, 1, 104, 'DEBIT', 'PENJUALAN', 32000, 50000, 22, 'SEED-TRX-22', 'TRANSAKSI', 'TRX-22', 'Pembelian di kasir', 4, (current_date - 1 + time '10:30')::timestamptz),
  (51, 1, 105, 'DEBIT', 'PENJUALAN', 7000, 50000, 16, 'SEED-TRX-16', 'TRANSAKSI', 'TRX-16', 'Pembelian di kasir', 4, (current_date - 1 + time '10:35')::timestamptz),
  (52, 1, 109, 'DEBIT', 'PENJUALAN', 20000, 55000, 10, 'SEED-TRX-10', 'TRANSAKSI', 'TRX-10', 'Pembelian di kasir', 4, (current_date - 1 + time '10:50')::timestamptz),
  (53, 1, 107, 'DEBIT', 'PENJUALAN', 8000, 50000, 17, 'SEED-TRX-17', 'TRANSAKSI', 'TRX-17', 'Pembelian di kasir', 4, (current_date - 1 + time '11:05')::timestamptz),
  (54, 10, 1006, 'DEBIT', 'PENJUALAN', 11000, 64000, 29, 'SEED-TRX-29', 'TRANSAKSI', 'TRX-29', 'Pembelian di kasir', 14, (current_date - 1 + time '11:10')::timestamptz),
  (55, 1, 110, 'DEBIT', 'PENJUALAN', 15000, 60000, 11, 'SEED-TRX-11', 'TRANSAKSI', 'TRX-11', 'Pembelian di kasir', 4, (current_date - 1 + time '11:30')::timestamptz),
  (56, 10, 1005, 'DEBIT', 'PENJUALAN', 15000, 48000, 30, 'SEED-TRX-30', 'TRANSAKSI', 'TRX-30', 'Pembelian di kasir', 14, (current_date - 1 + time '11:40')::timestamptz),
  (57, 1, 108, 'DEBIT', 'PENJUALAN', 8000, 50000, 18, 'SEED-TRX-18', 'TRANSAKSI', 'TRX-18', 'Pembelian di kasir', 4, (current_date - 1 + time '11:45')::timestamptz),
  (58, 1, 12, 'DEBIT', 'PENJUALAN', 18000, 57000, 12, 'SEED-TRX-12', 'TRANSAKSI', 'TRX-12', 'Pembelian di kasir', 4, (current_date - 1 + time '12:05')::timestamptz),
  (59, 1, 103, 'DEBIT', 'PENJUALAN', 23000, 38000, 15, 'SEED-TRX-15', 'TRANSAKSI', 'TRX-15', 'Pembelian di kasir', 4, (current_date - 1 + time '12:15')::timestamptz),
  (60, 10, 1005, 'DEBIT', 'PENJUALAN', 18000, 30000, 31, 'SEED-TRX-31', 'TRANSAKSI', 'TRX-31', 'Pembelian di kasir', 14, (current_date - 1 + time '12:20')::timestamptz),
  (61, 1, 12, 'DEBIT', 'PENJUALAN', 7000, 50000, 13, 'SEED-TRX-13', 'TRANSAKSI', 'TRX-13', 'Pembelian di kasir', 4, (current_date - 1 + time '12:40')::timestamptz),
  (62, 1, 110, 'DEBIT', 'PENJUALAN', 10000, 50000, 19, 'SEED-TRX-19', 'TRANSAKSI', 'TRX-19', 'Pembelian di kasir', 4, (current_date - 1 + time '12:50')::timestamptz),
  (63, 10, 1006, 'DEBIT', 'PENJUALAN', 14000, 50000, 32, 'SEED-TRX-32', 'TRANSAKSI', 'TRX-32', 'Pembelian di kasir', 14, (current_date - 1 + time '12:55')::timestamptz),
  (64, 1, 13, 'DEBIT', 'PENJUALAN', 18000, 32000, 14, 'SEED-TRX-14', 'TRANSAKSI', 'TRX-14', 'Pembelian di kasir', 4, (current_date - 1 + time '13:10')::timestamptz),
  (65, 1, 13, 'KREDIT', 'VOID_PENJUALAN', 18000, 50000, 14, 'SEED-VOID-14', 'TRANSAKSI', 'VOID-14', 'Pembatalan transaksi (void)', 4, (current_date - 1 + time '13:20')::timestamptz),
  (66, 1, 101, 'DEBIT', 'PENJUALAN', 19000, 37000, 23, 'SEED-TRX-23', 'TRANSAKSI', 'TRX-23', 'Pembelian di kasir', 4, (current_date - 0 + time '09:10')::timestamptz),
  (67, 10, 1001, 'DEBIT', 'PENJUALAN', 31000, 64000, 33, 'SEED-TRX-33', 'TRANSAKSI', 'TRX-33', 'Pembelian di kasir', 14, (current_date - 0 + time '09:20')::timestamptz),
  (68, 1, 102, 'DEBIT', 'PENJUALAN', 25000, 18000, 24, 'SEED-TRX-24', 'TRANSAKSI', 'TRX-24', 'Pembelian di kasir', 4, (current_date - 0 + time '09:45')::timestamptz),
  (69, 10, 1007, 'DEBIT', 'PENJUALAN', 20000, 55000, 34, 'SEED-TRX-34', 'TRANSAKSI', 'TRX-34', 'Pembelian di kasir', 14, (current_date - 0 + time '10:05')::timestamptz),
  (70, 1, 103, 'DEBIT', 'PENJUALAN', 8000, 30000, 25, 'SEED-TRX-25', 'TRANSAKSI', 'TRX-25', 'Pembelian di kasir', 4, (current_date - 0 + time '10:20')::timestamptz),
  (71, 10, 1001, 'DEBIT', 'PENJUALAN', 19000, 45000, 35, 'SEED-TRX-35', 'TRANSAKSI', 'TRX-35', 'Pembelian di kasir', 14, (current_date - 0 + time '10:45')::timestamptz),
  (72, 1, 109, 'DEBIT', 'PENJUALAN', 5000, 50000, 26, 'SEED-TRX-26', 'TRANSAKSI', 'TRX-26', 'Pembelian di kasir', 4, (current_date - 0 + time '11:00')::timestamptz),
  (73, 10, 1007, 'DEBIT', 'PENJUALAN', 5000, 50000, 36, 'SEED-TRX-36', 'TRANSAKSI', 'TRX-36', 'Pembelian di kasir', 14, (current_date - 0 + time '11:20')::timestamptz);

-- 17. RIWAYAT STOK — stok awal + penjualan + void (sumber kebenaran stok)
insert into riwayat_stok (id, sekolah_id, menu_id, arah, jenis, qty, stok_setelah, hpp_snapshot, transaksi_id, referensi_tipe, referensi_id, alasan, aktor_id, waktu, harga_beli_satuan, aktor_nama) values
  (1, 1, 1, 'MASUK', 'BARANG_MASUK', 27, 27, 7000, null, 'BARANG_MASUK', 'BM-1-1', 'Stok awal (seed)', 3, (current_date - 6 + time '06:00')::timestamptz, 7000, 'Deryl Fabiensyah'),
  (2, 1, 2, 'MASUK', 'BARANG_MASUK', 41, 41, 6000, null, 'BARANG_MASUK', 'BM-1-2', 'Stok awal (seed)', 3, (current_date - 6 + time '06:00')::timestamptz, 6000, 'Deryl Fabiensyah'),
  (3, 1, 3, 'MASUK', 'BARANG_MASUK', 31, 31, 1000, null, 'BARANG_MASUK', 'BM-1-3', 'Stok awal (seed)', 3, (current_date - 6 + time '06:00')::timestamptz, 1000, 'Deryl Fabiensyah'),
  (4, 1, 4, 'MASUK', 'BARANG_MASUK', 22, 22, 2000, null, 'BARANG_MASUK', 'BM-1-4', 'Stok awal (seed)', 3, (current_date - 6 + time '06:00')::timestamptz, 2000, 'Deryl Fabiensyah'),
  (5, 1, 5, 'MASUK', 'BARANG_MASUK', 43, 43, 9000, null, 'BARANG_MASUK', 'BM-1-5', 'Stok awal (seed)', 3, (current_date - 6 + time '06:00')::timestamptz, 9000, 'Deryl Fabiensyah'),
  (6, 1, 6, 'MASUK', 'BARANG_MASUK', 45, 45, 3000, null, 'BARANG_MASUK', 'BM-1-6', 'Stok awal (seed)', 3, (current_date - 6 + time '06:00')::timestamptz, 3000, 'Deryl Fabiensyah'),
  (7, 1, 7, 'MASUK', 'BARANG_MASUK', 26, 26, 5000, null, 'BARANG_MASUK', 'BM-1-7', 'Stok awal (seed)', 3, (current_date - 6 + time '06:00')::timestamptz, 5000, 'Deryl Fabiensyah'),
  (8, 1, 8, 'MASUK', 'BARANG_MASUK', 40, 40, 4000, null, 'BARANG_MASUK', 'BM-1-8', 'Stok awal (seed)', 3, (current_date - 6 + time '06:00')::timestamptz, 4000, 'Deryl Fabiensyah'),
  (9, 1, 9, 'MASUK', 'BARANG_MASUK', 45, 45, 2000, null, 'BARANG_MASUK', 'BM-1-9', 'Stok awal (seed)', 3, (current_date - 6 + time '06:00')::timestamptz, 2000, 'Deryl Fabiensyah'),
  (10, 1, 10, 'MASUK', 'BARANG_MASUK', 22, 22, 5000, null, 'BARANG_MASUK', 'BM-1-10', 'Stok awal (seed)', 3, (current_date - 6 + time '06:00')::timestamptz, 5000, 'Deryl Fabiensyah'),
  (11, 1, 11, 'MASUK', 'BARANG_MASUK', 39, 39, 7000, null, 'BARANG_MASUK', 'BM-1-11', 'Stok awal (seed)', 3, (current_date - 6 + time '06:00')::timestamptz, 7000, 'Deryl Fabiensyah'),
  (12, 1, 12, 'MASUK', 'BARANG_MASUK', 30, 30, 5000, null, 'BARANG_MASUK', 'BM-1-12', 'Stok awal (seed)', 3, (current_date - 6 + time '06:00')::timestamptz, 5000, 'Deryl Fabiensyah'),
  (13, 10, 1000, 'MASUK', 'BARANG_MASUK', 25, 25, 7000, null, 'BARANG_MASUK', 'BM-10-1000', 'Stok awal (seed)', 13, (current_date - 6 + time '06:00')::timestamptz, 7000, 'Yusuf Hamdani'),
  (14, 10, 1001, 'MASUK', 'BARANG_MASUK', 40, 40, 6000, null, 'BARANG_MASUK', 'BM-10-1001', 'Stok awal (seed)', 13, (current_date - 6 + time '06:00')::timestamptz, 6000, 'Yusuf Hamdani'),
  (15, 10, 1002, 'MASUK', 'BARANG_MASUK', 24, 24, 1000, null, 'BARANG_MASUK', 'BM-10-1002', 'Stok awal (seed)', 13, (current_date - 6 + time '06:00')::timestamptz, 1000, 'Yusuf Hamdani'),
  (16, 10, 1003, 'MASUK', 'BARANG_MASUK', 17, 17, 2000, null, 'BARANG_MASUK', 'BM-10-1003', 'Stok awal (seed)', 13, (current_date - 6 + time '06:00')::timestamptz, 2000, 'Yusuf Hamdani'),
  (17, 10, 1004, 'MASUK', 'BARANG_MASUK', 40, 40, 9000, null, 'BARANG_MASUK', 'BM-10-1004', 'Stok awal (seed)', 13, (current_date - 6 + time '06:00')::timestamptz, 9000, 'Yusuf Hamdani'),
  (18, 10, 1005, 'MASUK', 'BARANG_MASUK', 42, 42, 3000, null, 'BARANG_MASUK', 'BM-10-1005', 'Stok awal (seed)', 13, (current_date - 6 + time '06:00')::timestamptz, 3000, 'Yusuf Hamdani'),
  (19, 10, 1006, 'MASUK', 'BARANG_MASUK', 23, 23, 5000, null, 'BARANG_MASUK', 'BM-10-1006', 'Stok awal (seed)', 13, (current_date - 6 + time '06:00')::timestamptz, 5000, 'Yusuf Hamdani'),
  (20, 10, 1007, 'MASUK', 'BARANG_MASUK', 38, 38, 4000, null, 'BARANG_MASUK', 'BM-10-1007', 'Stok awal (seed)', 13, (current_date - 6 + time '06:00')::timestamptz, 4000, 'Yusuf Hamdani'),
  (21, 10, 1008, 'MASUK', 'BARANG_MASUK', 44, 44, 2000, null, 'BARANG_MASUK', 'BM-10-1008', 'Stok awal (seed)', 13, (current_date - 6 + time '06:00')::timestamptz, 2000, 'Yusuf Hamdani'),
  (22, 10, 1009, 'MASUK', 'BARANG_MASUK', 19, 19, 5000, null, 'BARANG_MASUK', 'BM-10-1009', 'Stok awal (seed)', 13, (current_date - 6 + time '06:00')::timestamptz, 5000, 'Yusuf Hamdani'),
  (23, 10, 1010, 'MASUK', 'BARANG_MASUK', 37, 37, 7000, null, 'BARANG_MASUK', 'BM-10-1010', 'Stok awal (seed)', 13, (current_date - 6 + time '06:00')::timestamptz, 7000, 'Yusuf Hamdani'),
  (24, 10, 1011, 'MASUK', 'BARANG_MASUK', 28, 28, 5000, null, 'BARANG_MASUK', 'BM-10-1011', 'Stok awal (seed)', 13, (current_date - 6 + time '06:00')::timestamptz, 5000, 'Yusuf Hamdani'),
  (25, 1, 1, 'KELUAR', 'PENJUALAN', 1, 26, 7000, 151, 'TRANSAKSI', 'TRX-151', null, 4, (current_date - 6 + time '09:15')::timestamptz, null, null),
  (26, 10, 1001, 'KELUAR', 'PENJUALAN', 1, 39, 6000, 154, 'TRANSAKSI', 'TRX-154', null, 14, (current_date - 6 + time '09:30')::timestamptz, null, null),
  (27, 1, 4, 'KELUAR', 'PENJUALAN', 2, 20, 2000, 152, 'TRANSAKSI', 'TRX-152', null, 4, (current_date - 6 + time '10:20')::timestamptz, null, null),
  (28, 10, 1005, 'KELUAR', 'PENJUALAN', 1, 41, 3000, 155, 'TRANSAKSI', 'TRX-155', null, 14, (current_date - 6 + time '10:40')::timestamptz, null, null),
  (29, 1, 6, 'KELUAR', 'PENJUALAN', 1, 44, 3000, 153, 'TRANSAKSI', 'TRX-153', null, 4, (current_date - 6 + time '11:35')::timestamptz, null, null),
  (30, 1, 1, 'KELUAR', 'PENJUALAN', 1, 25, 7000, 146, 'TRANSAKSI', 'TRX-146', null, 4, (current_date - 5 + time '09:15')::timestamptz, null, null),
  (31, 10, 1001, 'KELUAR', 'PENJUALAN', 1, 38, 6000, 149, 'TRANSAKSI', 'TRX-149', null, 14, (current_date - 5 + time '09:30')::timestamptz, null, null),
  (32, 1, 4, 'KELUAR', 'PENJUALAN', 2, 18, 2000, 147, 'TRANSAKSI', 'TRX-147', null, 4, (current_date - 5 + time '10:20')::timestamptz, null, null),
  (33, 10, 1005, 'KELUAR', 'PENJUALAN', 1, 40, 3000, 150, 'TRANSAKSI', 'TRX-150', null, 14, (current_date - 5 + time '10:40')::timestamptz, null, null),
  (34, 1, 6, 'KELUAR', 'PENJUALAN', 1, 43, 3000, 148, 'TRANSAKSI', 'TRX-148', null, 4, (current_date - 5 + time '11:35')::timestamptz, null, null),
  (35, 1, 1, 'KELUAR', 'PENJUALAN', 1, 24, 7000, 141, 'TRANSAKSI', 'TRX-141', null, 4, (current_date - 4 + time '09:15')::timestamptz, null, null),
  (36, 10, 1001, 'KELUAR', 'PENJUALAN', 1, 37, 6000, 144, 'TRANSAKSI', 'TRX-144', null, 14, (current_date - 4 + time '09:30')::timestamptz, null, null),
  (37, 1, 4, 'KELUAR', 'PENJUALAN', 2, 16, 2000, 142, 'TRANSAKSI', 'TRX-142', null, 4, (current_date - 4 + time '10:20')::timestamptz, null, null),
  (38, 10, 1005, 'KELUAR', 'PENJUALAN', 1, 39, 3000, 145, 'TRANSAKSI', 'TRX-145', null, 14, (current_date - 4 + time '10:40')::timestamptz, null, null),
  (39, 1, 6, 'KELUAR', 'PENJUALAN', 1, 42, 3000, 143, 'TRANSAKSI', 'TRX-143', null, 4, (current_date - 4 + time '11:35')::timestamptz, null, null),
  (40, 1, 1, 'KELUAR', 'PENJUALAN', 1, 23, 7000, 136, 'TRANSAKSI', 'TRX-136', null, 4, (current_date - 3 + time '09:15')::timestamptz, null, null),
  (41, 10, 1001, 'KELUAR', 'PENJUALAN', 1, 36, 6000, 139, 'TRANSAKSI', 'TRX-139', null, 14, (current_date - 3 + time '09:30')::timestamptz, null, null),
  (42, 1, 4, 'KELUAR', 'PENJUALAN', 2, 14, 2000, 137, 'TRANSAKSI', 'TRX-137', null, 4, (current_date - 3 + time '10:20')::timestamptz, null, null),
  (43, 10, 1005, 'KELUAR', 'PENJUALAN', 1, 38, 3000, 140, 'TRANSAKSI', 'TRX-140', null, 14, (current_date - 3 + time '10:40')::timestamptz, null, null),
  (44, 1, 6, 'KELUAR', 'PENJUALAN', 1, 41, 3000, 138, 'TRANSAKSI', 'TRX-138', null, 4, (current_date - 3 + time '11:35')::timestamptz, null, null),
  (45, 1, 1, 'KELUAR', 'PENJUALAN', 1, 22, 7000, 1, 'TRANSAKSI', 'TRX-1', null, 4, (current_date - 2 + time '09:12')::timestamptz, null, null),
  (46, 1, 3, 'KELUAR', 'PENJUALAN', 1, 30, 1000, 1, 'TRANSAKSI', 'TRX-1', null, 4, (current_date - 2 + time '09:12')::timestamptz, null, null),
  (47, 1, 2, 'KELUAR', 'PENJUALAN', 1, 40, 6000, 2, 'TRANSAKSI', 'TRX-2', null, 4, (current_date - 2 + time '09:40')::timestamptz, null, null),
  (48, 1, 4, 'KELUAR', 'PENJUALAN', 1, 13, 2000, 2, 'TRANSAKSI', 'TRX-2', null, 4, (current_date - 2 + time '09:40')::timestamptz, null, null),
  (49, 1, 5, 'KELUAR', 'PENJUALAN', 1, 42, 9000, 3, 'TRANSAKSI', 'TRX-3', null, 4, (current_date - 2 + time '10:15')::timestamptz, null, null),
  (50, 1, 3, 'KELUAR', 'PENJUALAN', 2, 28, 1000, 3, 'TRANSAKSI', 'TRX-3', null, 4, (current_date - 2 + time '10:15')::timestamptz, null, null),
  (51, 1, 6, 'KELUAR', 'PENJUALAN', 1, 40, 3000, 4, 'TRANSAKSI', 'TRX-4', null, 4, (current_date - 2 + time '11:02')::timestamptz, null, null),
  (52, 1, 8, 'KELUAR', 'PENJUALAN', 1, 39, 4000, 4, 'TRANSAKSI', 'TRX-4', null, 4, (current_date - 2 + time '11:02')::timestamptz, null, null),
  (53, 1, 11, 'KELUAR', 'PENJUALAN', 1, 38, 7000, 5, 'TRANSAKSI', 'TRX-5', null, 4, (current_date - 2 + time '12:20')::timestamptz, null, null),
  (54, 1, 12, 'KELUAR', 'PENJUALAN', 1, 29, 5000, 5, 'TRANSAKSI', 'TRX-5', null, 4, (current_date - 2 + time '12:20')::timestamptz, null, null),
  (55, 1, 5, 'KELUAR', 'PENJUALAN', 1, 41, 9000, 6, 'TRANSAKSI', 'TRX-6', null, 4, (current_date - 2 + time '13:05')::timestamptz, null, null),
  (56, 1, 12, 'KELUAR', 'PENJUALAN', 1, 28, 5000, 6, 'TRANSAKSI', 'TRX-6', null, 4, (current_date - 2 + time '13:05')::timestamptz, null, null),
  (57, 1, 6, 'KELUAR', 'PENJUALAN', 1, 39, 3000, 6, 'TRANSAKSI', 'TRX-6', null, 4, (current_date - 2 + time '13:05')::timestamptz, null, null),
  (58, 1, 1, 'KELUAR', 'PENJUALAN', 2, 20, 7000, 7, 'TRANSAKSI', 'TRX-7', null, 4, (current_date - 1 + time '09:05')::timestamptz, null, null),
  (59, 1, 12, 'KELUAR', 'PENJUALAN', 1, 27, 5000, 7, 'TRANSAKSI', 'TRX-7', null, 4, (current_date - 1 + time '09:05')::timestamptz, null, null),
  (60, 10, 1000, 'KELUAR', 'PENJUALAN', 1, 24, 7000, 27, 'TRANSAKSI', 'TRX-27', null, 14, (current_date - 1 + time '09:15')::timestamptz, null, null),
  (61, 10, 1002, 'KELUAR', 'PENJUALAN', 1, 23, 1000, 27, 'TRANSAKSI', 'TRX-27', null, 14, (current_date - 1 + time '09:15')::timestamptz, null, null),
  (62, 1, 3, 'KELUAR', 'PENJUALAN', 2, 26, 1000, 20, 'TRANSAKSI', 'TRX-20', null, 4, (current_date - 1 + time '09:20')::timestamptz, null, null),
  (63, 1, 6, 'KELUAR', 'PENJUALAN', 1, 38, 3000, 20, 'TRANSAKSI', 'TRX-20', null, 4, (current_date - 1 + time '09:20')::timestamptz, null, null),
  (64, 1, 2, 'KELUAR', 'PENJUALAN', 1, 39, 6000, 8, 'TRANSAKSI', 'TRX-8', null, 4, (current_date - 1 + time '09:35')::timestamptz, null, null),
  (65, 1, 4, 'KELUAR', 'PENJUALAN', 1, 12, 2000, 8, 'TRANSAKSI', 'TRX-8', null, 4, (current_date - 1 + time '09:35')::timestamptz, null, null),
  (66, 1, 3, 'KELUAR', 'PENJUALAN', 1, 25, 1000, 8, 'TRANSAKSI', 'TRX-8', null, 4, (current_date - 1 + time '09:35')::timestamptz, null, null),
  (67, 1, 10, 'KELUAR', 'PENJUALAN', 1, 21, 5000, 21, 'TRANSAKSI', 'TRX-21', null, 4, (current_date - 1 + time '09:55')::timestamptz, null, null),
  (68, 10, 1004, 'KELUAR', 'PENJUALAN', 1, 39, 9000, 28, 'TRANSAKSI', 'TRX-28', null, 14, (current_date - 1 + time '10:00')::timestamptz, null, null),
  (69, 10, 1003, 'KELUAR', 'PENJUALAN', 1, 16, 2000, 28, 'TRANSAKSI', 'TRX-28', null, 14, (current_date - 1 + time '10:00')::timestamptz, null, null),
  (70, 1, 5, 'KELUAR', 'PENJUALAN', 1, 40, 9000, 9, 'TRANSAKSI', 'TRX-9', null, 4, (current_date - 1 + time '10:10')::timestamptz, null, null),
  (71, 1, 4, 'KELUAR', 'PENJUALAN', 1, 11, 2000, 9, 'TRANSAKSI', 'TRX-9', null, 4, (current_date - 1 + time '10:10')::timestamptz, null, null),
  (72, 1, 5, 'KELUAR', 'PENJUALAN', 1, 39, 9000, 22, 'TRANSAKSI', 'TRX-22', null, 4, (current_date - 1 + time '10:30')::timestamptz, null, null),
  (73, 1, 1, 'KELUAR', 'PENJUALAN', 1, 19, 7000, 22, 'TRANSAKSI', 'TRX-22', null, 4, (current_date - 1 + time '10:30')::timestamptz, null, null),
  (74, 1, 3, 'KELUAR', 'PENJUALAN', 1, 24, 1000, 22, 'TRANSAKSI', 'TRX-22', null, 4, (current_date - 1 + time '10:30')::timestamptz, null, null),
  (75, 1, 4, 'KELUAR', 'PENJUALAN', 1, 10, 2000, 22, 'TRANSAKSI', 'TRX-22', null, 4, (current_date - 1 + time '10:30')::timestamptz, null, null),
  (76, 1, 3, 'KELUAR', 'PENJUALAN', 1, 23, 1000, 16, 'TRANSAKSI', 'TRX-16', null, 4, (current_date - 1 + time '10:35')::timestamptz, null, null),
  (77, 1, 4, 'KELUAR', 'PENJUALAN', 1, 9, 2000, 16, 'TRANSAKSI', 'TRX-16', null, 4, (current_date - 1 + time '10:35')::timestamptz, null, null),
  (78, 1, 10, 'KELUAR', 'PENJUALAN', 1, 20, 5000, 10, 'TRANSAKSI', 'TRX-10', null, 4, (current_date - 1 + time '10:50')::timestamptz, null, null),
  (79, 1, 8, 'KELUAR', 'PENJUALAN', 1, 38, 4000, 10, 'TRANSAKSI', 'TRX-10', null, 4, (current_date - 1 + time '10:50')::timestamptz, null, null),
  (80, 1, 3, 'KELUAR', 'PENJUALAN', 1, 22, 1000, 10, 'TRANSAKSI', 'TRX-10', null, 4, (current_date - 1 + time '10:50')::timestamptz, null, null),
  (81, 1, 4, 'KELUAR', 'PENJUALAN', 1, 8, 2000, 10, 'TRANSAKSI', 'TRX-10', null, 4, (current_date - 1 + time '10:50')::timestamptz, null, null),
  (82, 1, 7, 'KELUAR', 'PENJUALAN', 1, 25, 5000, 17, 'TRANSAKSI', 'TRX-17', null, 4, (current_date - 1 + time '11:05')::timestamptz, null, null),
  (83, 10, 1005, 'KELUAR', 'PENJUALAN', 1, 37, 3000, 29, 'TRANSAKSI', 'TRX-29', null, 14, (current_date - 1 + time '11:10')::timestamptz, null, null),
  (84, 10, 1007, 'KELUAR', 'PENJUALAN', 1, 37, 4000, 29, 'TRANSAKSI', 'TRX-29', null, 14, (current_date - 1 + time '11:10')::timestamptz, null, null),
  (85, 1, 1, 'KELUAR', 'PENJUALAN', 1, 18, 7000, 11, 'TRANSAKSI', 'TRX-11', null, 4, (current_date - 1 + time '11:30')::timestamptz, null, null),
  (86, 1, 3, 'KELUAR', 'PENJUALAN', 1, 21, 1000, 11, 'TRANSAKSI', 'TRX-11', null, 4, (current_date - 1 + time '11:30')::timestamptz, null, null),
  (87, 10, 1001, 'KELUAR', 'PENJUALAN', 1, 35, 6000, 30, 'TRANSAKSI', 'TRX-30', null, 14, (current_date - 1 + time '11:40')::timestamptz, null, null),
  (88, 10, 1005, 'KELUAR', 'PENJUALAN', 1, 36, 3000, 30, 'TRANSAKSI', 'TRX-30', null, 14, (current_date - 1 + time '11:40')::timestamptz, null, null),
  (89, 1, 6, 'KELUAR', 'PENJUALAN', 1, 37, 3000, 18, 'TRANSAKSI', 'TRX-18', null, 4, (current_date - 1 + time '11:45')::timestamptz, null, null),
  (90, 1, 3, 'KELUAR', 'PENJUALAN', 1, 20, 1000, 18, 'TRANSAKSI', 'TRX-18', null, 4, (current_date - 1 + time '11:45')::timestamptz, null, null),
  (91, 1, 10, 'KELUAR', 'PENJUALAN', 1, 19, 5000, 12, 'TRANSAKSI', 'TRX-12', null, 4, (current_date - 1 + time '12:05')::timestamptz, null, null),
  (92, 1, 9, 'KELUAR', 'PENJUALAN', 1, 44, 2000, 12, 'TRANSAKSI', 'TRX-12', null, 4, (current_date - 1 + time '12:05')::timestamptz, null, null),
  (93, 1, 7, 'KELUAR', 'PENJUALAN', 1, 24, 5000, 12, 'TRANSAKSI', 'TRX-12', null, 4, (current_date - 1 + time '12:05')::timestamptz, null, null),
  (94, 1, 11, 'KELUAR', 'PENJUALAN', 1, 37, 7000, 15, 'TRANSAKSI', 'TRX-15', null, 4, (current_date - 1 + time '12:15')::timestamptz, null, null),
  (95, 1, 12, 'KELUAR', 'PENJUALAN', 1, 26, 5000, 15, 'TRANSAKSI', 'TRX-15', null, 4, (current_date - 1 + time '12:15')::timestamptz, null, null),
  (96, 1, 6, 'KELUAR', 'PENJUALAN', 1, 36, 3000, 15, 'TRANSAKSI', 'TRX-15', null, 4, (current_date - 1 + time '12:15')::timestamptz, null, null),
  (97, 10, 1000, 'KELUAR', 'PENJUALAN', 1, 23, 7000, 31, 'TRANSAKSI', 'TRX-31', null, 14, (current_date - 1 + time '12:20')::timestamptz, null, null),
  (98, 10, 1002, 'KELUAR', 'PENJUALAN', 2, 21, 1000, 31, 'TRANSAKSI', 'TRX-31', null, 14, (current_date - 1 + time '12:20')::timestamptz, null, null),
  (99, 1, 4, 'KELUAR', 'PENJUALAN', 1, 7, 2000, 13, 'TRANSAKSI', 'TRX-13', null, 4, (current_date - 1 + time '12:40')::timestamptz, null, null),
  (100, 1, 3, 'KELUAR', 'PENJUALAN', 1, 19, 1000, 13, 'TRANSAKSI', 'TRX-13', null, 4, (current_date - 1 + time '12:40')::timestamptz, null, null),
  (101, 1, 8, 'KELUAR', 'PENJUALAN', 1, 37, 4000, 19, 'TRANSAKSI', 'TRX-19', null, 4, (current_date - 1 + time '12:50')::timestamptz, null, null),
  (102, 1, 4, 'KELUAR', 'PENJUALAN', 1, 6, 2000, 19, 'TRANSAKSI', 'TRX-19', null, 4, (current_date - 1 + time '12:50')::timestamptz, null, null),
  (103, 10, 1011, 'KELUAR', 'PENJUALAN', 1, 27, 5000, 32, 'TRANSAKSI', 'TRX-32', null, 14, (current_date - 1 + time '12:55')::timestamptz, null, null),
  (104, 10, 1003, 'KELUAR', 'PENJUALAN', 1, 15, 2000, 32, 'TRANSAKSI', 'TRX-32', null, 14, (current_date - 1 + time '12:55')::timestamptz, null, null),
  (105, 10, 1002, 'KELUAR', 'PENJUALAN', 1, 20, 1000, 32, 'TRANSAKSI', 'TRX-32', null, 14, (current_date - 1 + time '12:55')::timestamptz, null, null),
  (106, 1, 1, 'KELUAR', 'PENJUALAN', 1, 17, 7000, 14, 'TRANSAKSI', 'TRX-14', null, 4, (current_date - 1 + time '13:10')::timestamptz, null, null),
  (107, 1, 8, 'KELUAR', 'PENJUALAN', 1, 36, 4000, 14, 'TRANSAKSI', 'TRX-14', null, 4, (current_date - 1 + time '13:10')::timestamptz, null, null),
  (108, 1, 1, 'MASUK', 'VOID_PENJUALAN', 1, 18, 7000, 14, 'TRANSAKSI', 'VOID-14', null, 4, (current_date - 1 + time '13:20')::timestamptz, null, null),
  (109, 1, 8, 'MASUK', 'VOID_PENJUALAN', 1, 37, 4000, 14, 'TRANSAKSI', 'VOID-14', null, 4, (current_date - 1 + time '13:20')::timestamptz, null, null),
  (110, 1, 5, 'KELUAR', 'PENJUALAN', 1, 38, 9000, 23, 'TRANSAKSI', 'TRX-23', null, 4, (current_date - 0 + time '09:10')::timestamptz, null, null),
  (111, 1, 8, 'KELUAR', 'PENJUALAN', 1, 36, 4000, 23, 'TRANSAKSI', 'TRX-23', null, 4, (current_date - 0 + time '09:10')::timestamptz, null, null),
  (112, 10, 1000, 'KELUAR', 'PENJUALAN', 2, 21, 7000, 33, 'TRANSAKSI', 'TRX-33', null, 14, (current_date - 0 + time '09:20')::timestamptz, null, null),
  (113, 10, 1011, 'KELUAR', 'PENJUALAN', 1, 26, 5000, 33, 'TRANSAKSI', 'TRX-33', null, 14, (current_date - 0 + time '09:20')::timestamptz, null, null),
  (114, 1, 1, 'KELUAR', 'PENJUALAN', 1, 17, 7000, 24, 'TRANSAKSI', 'TRX-24', null, 4, (current_date - 0 + time '09:45')::timestamptz, null, null),
  (115, 1, 5, 'KELUAR', 'PENJUALAN', 1, 37, 9000, 24, 'TRANSAKSI', 'TRX-24', null, 4, (current_date - 0 + time '09:45')::timestamptz, null, null),
  (116, 10, 1004, 'KELUAR', 'PENJUALAN', 1, 38, 9000, 34, 'TRANSAKSI', 'TRX-34', null, 14, (current_date - 0 + time '10:05')::timestamptz, null, null),
  (117, 10, 1002, 'KELUAR', 'PENJUALAN', 1, 19, 1000, 34, 'TRANSAKSI', 'TRX-34', null, 14, (current_date - 0 + time '10:05')::timestamptz, null, null),
  (118, 10, 1003, 'KELUAR', 'PENJUALAN', 1, 14, 2000, 34, 'TRANSAKSI', 'TRX-34', null, 14, (current_date - 0 + time '10:05')::timestamptz, null, null),
  (119, 1, 7, 'KELUAR', 'PENJUALAN', 1, 23, 5000, 25, 'TRANSAKSI', 'TRX-25', null, 4, (current_date - 0 + time '10:20')::timestamptz, null, null),
  (120, 10, 1004, 'KELUAR', 'PENJUALAN', 1, 37, 9000, 35, 'TRANSAKSI', 'TRX-35', null, 14, (current_date - 0 + time '10:45')::timestamptz, null, null),
  (121, 10, 1007, 'KELUAR', 'PENJUALAN', 1, 36, 4000, 35, 'TRANSAKSI', 'TRX-35', null, 14, (current_date - 0 + time '10:45')::timestamptz, null, null),
  (122, 1, 6, 'KELUAR', 'PENJUALAN', 1, 35, 3000, 26, 'TRANSAKSI', 'TRX-26', null, 4, (current_date - 0 + time '11:00')::timestamptz, null, null),
  (123, 10, 1005, 'KELUAR', 'PENJUALAN', 1, 35, 3000, 36, 'TRANSAKSI', 'TRX-36', null, 14, (current_date - 0 + time '11:20')::timestamptz, null, null);

-- 18. JURNAL BUKU KAS — penanda posting per transaksi & per tutup sesi
insert into jurnal_buku_kas (id, sekolah_id, referensi_id, entitas, mutasi_id, ref_buku_kas, jumlah, status, waktu) values
  (1, 1, 'TRX-151', 'TRANSAKSI', null, null, 12000, 'SUKSES', (current_date - 6 + time '09:15')::timestamptz),
  (2, 10, 'TRX-154', 'TRANSAKSI', null, null, 10000, 'SUKSES', (current_date - 6 + time '09:30')::timestamptz),
  (3, 1, 'TRX-152', 'TRANSAKSI', null, null, 8000, 'SUKSES', (current_date - 6 + time '10:20')::timestamptz),
  (4, 10, 'TRX-155', 'TRANSAKSI', null, null, 5000, 'SUKSES', (current_date - 6 + time '10:40')::timestamptz),
  (5, 1, 'TRX-153', 'TRANSAKSI', null, null, 5000, 'SUKSES', (current_date - 6 + time '11:35')::timestamptz),
  (6, 1, 'SESI-26', 'SESI_KASIR', null, 'SESI-26', 25000, 'SUKSES', (current_date - 6 + time '15:00')::timestamptz),
  (7, 10, 'SESI-36', 'SESI_KASIR', null, 'SESI-36', 15000, 'SUKSES', (current_date - 6 + time '15:00')::timestamptz),
  (8, 1, 'TRX-146', 'TRANSAKSI', null, null, 12000, 'SUKSES', (current_date - 5 + time '09:15')::timestamptz),
  (9, 10, 'TRX-149', 'TRANSAKSI', null, null, 10000, 'SUKSES', (current_date - 5 + time '09:30')::timestamptz),
  (10, 1, 'TRX-147', 'TRANSAKSI', null, null, 8000, 'SUKSES', (current_date - 5 + time '10:20')::timestamptz),
  (11, 10, 'TRX-150', 'TRANSAKSI', null, null, 5000, 'SUKSES', (current_date - 5 + time '10:40')::timestamptz),
  (12, 1, 'TRX-148', 'TRANSAKSI', null, null, 5000, 'SUKSES', (current_date - 5 + time '11:35')::timestamptz),
  (13, 1, 'SESI-25', 'SESI_KASIR', null, 'SESI-25', 25000, 'SUKSES', (current_date - 5 + time '15:00')::timestamptz),
  (14, 10, 'SESI-35', 'SESI_KASIR', null, 'SESI-35', 15000, 'SUKSES', (current_date - 5 + time '15:00')::timestamptz),
  (15, 1, 'TRX-141', 'TRANSAKSI', null, null, 12000, 'SUKSES', (current_date - 4 + time '09:15')::timestamptz),
  (16, 10, 'TRX-144', 'TRANSAKSI', null, null, 10000, 'SUKSES', (current_date - 4 + time '09:30')::timestamptz),
  (17, 1, 'TRX-142', 'TRANSAKSI', null, null, 8000, 'SUKSES', (current_date - 4 + time '10:20')::timestamptz),
  (18, 10, 'TRX-145', 'TRANSAKSI', null, null, 5000, 'SUKSES', (current_date - 4 + time '10:40')::timestamptz),
  (19, 1, 'TRX-143', 'TRANSAKSI', null, null, 5000, 'SUKSES', (current_date - 4 + time '11:35')::timestamptz),
  (20, 1, 'SESI-24', 'SESI_KASIR', null, 'SESI-24', 25000, 'SUKSES', (current_date - 4 + time '15:00')::timestamptz),
  (21, 10, 'SESI-34', 'SESI_KASIR', null, 'SESI-34', 15000, 'SUKSES', (current_date - 4 + time '15:00')::timestamptz),
  (22, 1, 'TRX-136', 'TRANSAKSI', null, null, 12000, 'SUKSES', (current_date - 3 + time '09:15')::timestamptz),
  (23, 10, 'TRX-139', 'TRANSAKSI', null, null, 10000, 'SUKSES', (current_date - 3 + time '09:30')::timestamptz),
  (24, 1, 'TRX-137', 'TRANSAKSI', null, null, 8000, 'SUKSES', (current_date - 3 + time '10:20')::timestamptz),
  (25, 10, 'TRX-140', 'TRANSAKSI', null, null, 5000, 'SUKSES', (current_date - 3 + time '10:40')::timestamptz),
  (26, 1, 'TRX-138', 'TRANSAKSI', null, null, 5000, 'SUKSES', (current_date - 3 + time '11:35')::timestamptz),
  (27, 1, 'SESI-23', 'SESI_KASIR', null, 'SESI-23', 25000, 'SUKSES', (current_date - 3 + time '15:00')::timestamptz),
  (28, 10, 'SESI-33', 'SESI_KASIR', null, 'SESI-33', 15000, 'SUKSES', (current_date - 3 + time '15:00')::timestamptz),
  (29, 1, 'TRX-1', 'TRANSAKSI', null, null, 15000, 'SUKSES', (current_date - 2 + time '09:12')::timestamptz),
  (30, 1, 'TRX-2', 'TRANSAKSI', null, null, 14000, 'SUKSES', (current_date - 2 + time '09:40')::timestamptz),
  (31, 1, 'TRX-3', 'TRANSAKSI', null, null, 19000, 'SUKSES', (current_date - 2 + time '10:15')::timestamptz),
  (32, 1, 'TRX-4', 'TRANSAKSI', null, null, 11000, 'SUKSES', (current_date - 2 + time '11:02')::timestamptz),
  (33, 1, 'TRX-5', 'TRANSAKSI', null, null, 18000, 'SUKSES', (current_date - 2 + time '12:20')::timestamptz),
  (34, 1, 'TRX-6', 'TRANSAKSI', null, null, 25000, 'SUKSES', (current_date - 2 + time '13:05')::timestamptz),
  (35, 1, 'SESI-1', 'SESI_KASIR', null, 'SESI-1', 102000, 'SUKSES', (current_date - 2 + time '15:05')::timestamptz),
  (36, 1, 'TRX-7', 'TRANSAKSI', null, null, 31000, 'SUKSES', (current_date - 1 + time '09:05')::timestamptz),
  (37, 10, 'TRX-27', 'TRANSAKSI', null, null, 15000, 'SUKSES', (current_date - 1 + time '09:15')::timestamptz),
  (38, 1, 'TRX-20', 'TRANSAKSI', null, null, 11000, 'SUKSES', (current_date - 1 + time '09:20')::timestamptz),
  (39, 1, 'TRX-8', 'TRANSAKSI', null, null, 17000, 'SUKSES', (current_date - 1 + time '09:35')::timestamptz),
  (40, 1, 'TRX-21', 'TRANSAKSI', null, null, 7000, 'SUKSES', (current_date - 1 + time '09:55')::timestamptz),
  (41, 10, 'TRX-28', 'TRANSAKSI', null, null, 17000, 'SUKSES', (current_date - 1 + time '10:00')::timestamptz),
  (42, 1, 'TRX-9', 'TRANSAKSI', null, null, 17000, 'SUKSES', (current_date - 1 + time '10:10')::timestamptz),
  (43, 1, 'TRX-22', 'TRANSAKSI', null, null, 32000, 'SUKSES', (current_date - 1 + time '10:30')::timestamptz),
  (44, 1, 'TRX-16', 'TRANSAKSI', null, null, 7000, 'SUKSES', (current_date - 1 + time '10:35')::timestamptz),
  (45, 1, 'TRX-10', 'TRANSAKSI', null, null, 20000, 'SUKSES', (current_date - 1 + time '10:50')::timestamptz),
  (46, 1, 'TRX-17', 'TRANSAKSI', null, null, 8000, 'SUKSES', (current_date - 1 + time '11:05')::timestamptz),
  (47, 10, 'TRX-29', 'TRANSAKSI', null, null, 11000, 'SUKSES', (current_date - 1 + time '11:10')::timestamptz),
  (48, 1, 'TRX-11', 'TRANSAKSI', null, null, 15000, 'SUKSES', (current_date - 1 + time '11:30')::timestamptz),
  (49, 10, 'TRX-30', 'TRANSAKSI', null, null, 15000, 'SUKSES', (current_date - 1 + time '11:40')::timestamptz),
  (50, 1, 'TRX-18', 'TRANSAKSI', null, null, 8000, 'SUKSES', (current_date - 1 + time '11:45')::timestamptz),
  (51, 1, 'TRX-12', 'TRANSAKSI', null, null, 18000, 'SUKSES', (current_date - 1 + time '12:05')::timestamptz),
  (52, 1, 'TRX-15', 'TRANSAKSI', null, null, 23000, 'SUKSES', (current_date - 1 + time '12:15')::timestamptz),
  (53, 10, 'TRX-31', 'TRANSAKSI', null, null, 18000, 'SUKSES', (current_date - 1 + time '12:20')::timestamptz),
  (54, 1, 'TRX-13', 'TRANSAKSI', null, null, 7000, 'SUKSES', (current_date - 1 + time '12:40')::timestamptz),
  (55, 1, 'TRX-19', 'TRANSAKSI', null, null, 10000, 'SUKSES', (current_date - 1 + time '12:50')::timestamptz),
  (56, 10, 'TRX-32', 'TRANSAKSI', null, null, 14000, 'SUKSES', (current_date - 1 + time '12:55')::timestamptz),
  (57, 1, 'TRX-14', 'TRANSAKSI', null, null, 18000, 'SUKSES', (current_date - 1 + time '13:10')::timestamptz),
  (58, 10, 'SESI-11', 'SESI_KASIR', null, 'SESI-11', 90000, 'SUKSES', (current_date - 1 + time '14:40')::timestamptz),
  (59, 1, 'SESI-4', 'SESI_KASIR', null, 'SESI-4', 50000, 'SUKSES', (current_date - 1 + time '15:00')::timestamptz),
  (60, 1, 'SESI-2', 'SESI_KASIR', null, 'SESI-2', 163000, 'SUKSES', (current_date - 1 + time '15:10')::timestamptz),
  (61, 1, 'TRX-23', 'TRANSAKSI', null, null, 19000, 'SUKSES', (current_date - 0 + time '09:10')::timestamptz),
  (62, 10, 'TRX-33', 'TRANSAKSI', null, null, 31000, 'SUKSES', (current_date - 0 + time '09:20')::timestamptz),
  (63, 1, 'TRX-24', 'TRANSAKSI', null, null, 25000, 'SUKSES', (current_date - 0 + time '09:45')::timestamptz),
  (64, 10, 'TRX-34', 'TRANSAKSI', null, null, 20000, 'SUKSES', (current_date - 0 + time '10:05')::timestamptz),
  (65, 1, 'TRX-25', 'TRANSAKSI', null, null, 8000, 'SUKSES', (current_date - 0 + time '10:20')::timestamptz),
  (66, 10, 'TRX-35', 'TRANSAKSI', null, null, 19000, 'SUKSES', (current_date - 0 + time '10:45')::timestamptz),
  (67, 1, 'TRX-26', 'TRANSAKSI', null, null, 5000, 'SUKSES', (current_date - 0 + time '11:00')::timestamptz),
  (68, 10, 'TRX-36', 'TRANSAKSI', null, null, 5000, 'SUKSES', (current_date - 0 + time '11:20')::timestamptz);
-- 19. SETORAN TU — uang top up yang disetor ke bendahara
insert into setoran (id, sekolah_id, tanggal, petugas_id, total_topup, jumlah_disetor, referensi_id, catatan, dikonfirmasi_oleh, waktu, status) values
  (1, 1,  (current_date - 6), 2, 1025000, 1025000, 'SETORAN-1', 'Setoran top up tunai hari itu', 5, (current_date - 6 + time '14:30')::timestamptz, 'DIKONFIRMASI'),
  (2, 10, (current_date - 6), 12, 400000, 400000, 'SETORAN-2', 'Setoran top up tunai hari itu', 15, (current_date - 6 + time '14:45')::timestamptz, 'DIKONFIRMASI');

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
  (1, 1, 'SEED-PENDING-1', '04AA000202', 1, 4, 11000, '[{\"menuId\": 3, \"namaMenu\": \"Es Teh Manis\", \"hargaJual\": 3000, \"qty\": 2, \"subtotal\": 6000}, {\"menuId\": 6, \"namaMenu\": \"Keripik Qtela Singkong 60 g\", \"hargaJual\": 5000, \"qty\": 1, \"subtotal\": 5000}]', 'MENUNGGU',    null, (current_date - 0 + time '10:50')::timestamptz, (current_date - 0 + time '10:55')::timestamptz, 202),
  (2, 1, 'SEED-PENDING-2', '04AA000201', 1, 4, 7000,  '[{\"menuId\": 10, \"namaMenu\": \"Buku Tulis Sidu 38 Lembar\", \"hargaJual\": 7000, \"qty\": 1, \"subtotal\": 7000}]', 'DIBATALKAN',  null, (current_date - 1 + time '11:15')::timestamptz, (current_date - 1 + time '11:20')::timestamptz, 201);

-- =====================================================================
-- 24. PELENGKAP DEMO — menutup layar yang tadinya kosong
--     (C.2a bayar tunai, C.3 kas fisik & selisih, laporan kerugian stok,
--      refund / pindah saldo / koreksi saldo, topup online)
--
--     Catatan penting: mutasi_saldo & riwayat_stok bersifat APPEND-ONLY
--     (trigger tolak_perubahan_ledger), jadi saldo_setelah/stok_setelah
--     TIDAK boleh dikoreksi lewat UPDATE. Karena itu:
--       · saldo_setelah memakai Σ ledger saat itu (bukan literal);
--       · stok_setelah memakai stok akhir dikurangi qty, dan setiap menu
--         hanya dipakai SEKALI di blok ini, semuanya berwaktu sore hari
--         (setelah seluruh baris seed lain) agar rantai stok_setelah utuh.
-- =====================================================================

-- 24a. Dua siswa NONAKTIF — syarat layar /saldo/refund:
--      satu masih bersaldo (kandidat refund), satu sudah direfund penuh.
insert into siswa (id, sekolah_id, nis, nisn, nama, kelas, foto_url, aktif) values
  (1201, 1,  '24251201', '001210000', 'Dimas Prakoso',      '9C', 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=400', false),
  (1202, 10, '24251202', '001220000', 'Laras Ayuningtyas',  '9A', 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=400', false);

insert into orang_tua (sekolah_id, siswa_id, nama, telepon, hubungan, punya_aplikasi, aktif) values
  (1,  1201, 'Bapak Prakoso',     '081200001201', 'AYAH', true, true),
  (1,  1201, 'Ibu Prakoso',       '081300001201', 'IBU',  true, true),
  (10, 1202, 'Bapak Ayuningtyas', '081200001202', 'AYAH', true, true),
  (10, 1202, 'Ibu Ayuningtyas',   '081300001202', 'IBU',  true, true);

-- Kartu siswa nonaktif: sengaja aktif=false agar tidak bisa dipakai tap.
insert into kartu (uid, sekolah_id, siswa_id, nomor_kartu, aktif) values
  ('04AA001201', 1,  1201, 'RFID-SISWA-1201', false),
  ('DA1001202',  10, 1202, 'RFID-SISWA-1202', false);

-- Baris cache saldo awal 0; trigger hanya meng-UPDATE baris yang sudah ada.
insert into saldo (sekolah_id, siswa_id, saldo) values
  (1,  1201, 0),
  (10, 1202, 0);

-- 24b. Mutasi saldo pelengkap: topup, refund, pindah saldo, koreksi, topup online.
--      Waktu sengaja sore hari supaya menjadi baris TERAKHIR tiap siswa —
--      invariant "saldo_setelah berurutan" tetap terpenuhi.
insert into mutasi_saldo (id, sekolah_id, siswa_id, arah, jenis, nominal, saldo_setelah, transaksi_id, idempotency_key, referensi_tipe, referensi_id, keterangan, aktor_id, waktu) values
  (74, 1,  1201, 'KREDIT', 'TOPUP_TUNAI', 35000, 35000, null, 'SEED-TOPUP-1201', 'TOPUP', 'TOPUP-1201', 'Top up tunai terakhir sebelum siswa lulus', 2, (current_date + time '16:30')::timestamptz),
  (75, 10, 1202, 'KREDIT', 'TOPUP_TUNAI', 28000, 28000, null, 'SEED-TOPUP-1202', 'TOPUP', 'TOPUP-1202', 'Top up tunai sebelum siswa pindah sekolah', 12, (current_date + time '16:30')::timestamptz);

insert into mutasi_saldo (id, sekolah_id, siswa_id, arah, jenis, nominal, saldo_setelah, transaksi_id, idempotency_key, referensi_tipe, referensi_id, keterangan, aktor_id, waktu)
select 76, 10, 1202, 'DEBIT', 'REFUND', 28000,
       (select coalesce(sum(case when m.arah = 'KREDIT' then m.nominal else -m.nominal end), 0)
          from mutasi_saldo m where m.sekolah_id = 10 and m.siswa_id = 1202) - 28000,
       null, 'SEED-REFUND-1202', 'REFUND', 'REFUND-1202',
       'Refund sisa saldo — siswa pindah sekolah (PRD §9.3)', 15, (current_date + time '16:35')::timestamptz;

insert into mutasi_saldo (id, sekolah_id, siswa_id, arah, jenis, nominal, saldo_setelah, transaksi_id, idempotency_key, referensi_tipe, referensi_id, keterangan, aktor_id, waktu)
select 77, 1, 101, 'DEBIT', 'TRANSFER', 10000,
       (select coalesce(sum(case when m.arah = 'KREDIT' then m.nominal else -m.nominal end), 0)
          from mutasi_saldo m where m.sekolah_id = 1 and m.siswa_id = 101) - 10000,
       null, 'SEED-TRANSFER-101', 'TRANSFER', 'TRANSFER-101',
       'Pindah saldo ke saudara kandung (PRD §9.3)', 5, (current_date + time '16:40')::timestamptz;

insert into mutasi_saldo (id, sekolah_id, siswa_id, arah, jenis, nominal, saldo_setelah, transaksi_id, idempotency_key, referensi_tipe, referensi_id, keterangan, aktor_id, waktu)
select 78, 1, 12, 'KREDIT', 'TRANSFER', 10000,
       (select coalesce(sum(case when m.arah = 'KREDIT' then m.nominal else -m.nominal end), 0)
          from mutasi_saldo m where m.sekolah_id = 1 and m.siswa_id = 12) + 10000,
       null, 'SEED-TRANSFER-12', 'TRANSFER', 'TRANSFER-101',
       'Menerima pindahan saldo dari saudara (PRD §9.3)', 5, (current_date + time '16:40')::timestamptz;

insert into mutasi_saldo (id, sekolah_id, siswa_id, arah, jenis, nominal, saldo_setelah, transaksi_id, idempotency_key, referensi_tipe, referensi_id, keterangan, aktor_id, waktu)
select 79, 1, 104, 'KREDIT', 'KOREKSI', 5000,
       (select coalesce(sum(case when m.arah = 'KREDIT' then m.nominal else -m.nominal end), 0)
          from mutasi_saldo m where m.sekolah_id = 1 and m.siswa_id = 104) + 5000,
       null, 'SEED-KOREKSI-104', 'KOREKSI', 'KOREKSI-104',
       'Koreksi saldo — selisih hitung setoran TU', 5, (current_date + time '16:45')::timestamptz;

insert into mutasi_saldo (id, sekolah_id, siswa_id, arah, jenis, nominal, saldo_setelah, transaksi_id, idempotency_key, referensi_tipe, referensi_id, keterangan, aktor_id, waktu)
select 80, 1, 105, 'KREDIT', 'TOPUP_ONLINE', 50000,
       (select coalesce(sum(case when m.arah = 'KREDIT' then m.nominal else -m.nominal end), 0)
          from mutasi_saldo m where m.sekolah_id = 1 and m.siswa_id = 105) + 50000,
       null, 'SEED-TOPUP-ONLINE-105', 'TOPUP_ONLINE', 'TOPUP-ONLINE-105',
       'Top up online via payment gateway (PRD §8.2)', 2, (current_date + time '16:50')::timestamptz;

-- 24c. Dua sesi tambahan HARI INI di loket kedua (sudah ditutup) — tempat
--      contoh kas fisik & selisih (C.3) tanpa mengganggu sesi kasir demo.
insert into sesi_kasir (id, sekolah_id, titik_kasir_id, tanggal, status, total_bruto, total_void, total_bersih, dibuka_at, dibuka_oleh, ditutup_at, ditutup_oleh, auto_tutup, jurnal_buku_kas, referensi_buku_kas, uang_fisik, selisih, catatan) values
  (5,  1,  2,  current_date, 'DITUTUP', 0, 0, 0, (current_date + time '07:40')::timestamptz, 7,  (current_date + time '16:45')::timestamptz, 7,  false, true, 'SESI-5',  null, null, null),
  (13, 10, 12, current_date, 'DITUTUP', 0, 0, 0, (current_date + time '07:15')::timestamptz, 17, (current_date + time '16:50')::timestamptz, 17, false, true, 'SESI-13', null, null, null);

-- 24d. TRANSAKSI TUNAI (C.2a) — pembeli tanpa kartu / Kartu Tamu.
--      Semua sore hari supaya baris stok-nya berada di ujung rantai.
insert into transaksi (id, idempotency_key, sekolah_id, sesi_kasir_id, titik_kasir_id, kartu_uid, petugas_id, total, total_hpp, status, alasan_void, void_at, void_oleh, waktu, siswa_id, metode_bayar, uang_diterima, nama_pembeli) values
  (156, 'SEED-TRX-156', 1,  3,  1,  null, 4,  14000, 7000,  'SUKSES', null, null, null, (current_date + time '16:05')::timestamptz, null, 'TUNAI', 20000, 'Bu Rina (Guru)'),
  (157, 'SEED-TRX-157', 1,  3,  1,  null, 4,  12000, 7000,  'SUKSES', null, null, null, (current_date + time '16:10')::timestamptz, null, 'TUNAI', 15000, 'Pak Andi (Guru)'),
  (158, 'SEED-TRX-158', 1,  5,  2,  null, 7,  9000,  5000,  'SUKSES', null, null, null, (current_date + time '16:15')::timestamptz, null, 'TUNAI', 10000, 'Tamu Tanpa Kartu'),
  (159, 'SEED-TRX-159', 10, 12, 11, null, 14, 17000, 11000, 'SUKSES', null, null, null, (current_date + time '16:20')::timestamptz, null, 'TUNAI', 20000, 'Ibu Sari (Guru)'),
  (160, 'SEED-TRX-160', 1,  5,  2,  null, 7,  16000, 11000, 'SUKSES', null, null, null, (current_date + time '16:25')::timestamptz, null, 'TUNAI', 16000, 'Pak Budi (Karyawan)'),
  (161, 'SEED-TRX-161', 10, 13, 12, null, 17, 20000, 12000, 'SUKSES', null, null, null, (current_date + time '16:30')::timestamptz, null, 'TUNAI', 20000, 'Bu Ani (Guru)');

insert into transaksi_item (id, transaksi_id, menu_id, nama_menu, kategori_id, harga_jual, qty, hpp_snapshot, subtotal, created_at) values
  (98,  156, 3,    'Es Teh Manis',                  2,  3000,  2, 1000, 6000,  (current_date + time '16:05')::timestamptz),
  (99,  156, 7,    'Roti Bakar',                    3,  8000,  1, 5000, 8000,  (current_date + time '16:05')::timestamptz),
  (100, 157, 1,    'Nasi Goreng',                   1,  12000, 1, 7000, 12000, (current_date + time '16:10')::timestamptz),
  (101, 158, 4,    'Air Mineral Aqua 600 ml',       2,  4000,  1, 2000, 4000,  (current_date + time '16:15')::timestamptz),
  (102, 158, 6,    'Keripik Qtela Singkong 60 g',   3,  5000,  1, 3000, 5000,  (current_date + time '16:15')::timestamptz),
  (103, 159, 1010, 'Bakso',                         11, 11000, 1, 7000, 11000, (current_date + time '16:20')::timestamptz),
  (104, 159, 1007, 'Susu Ultra Cokelat 250 ml',     12, 6000,  1, 4000, 6000,  (current_date + time '16:20')::timestamptz),
  (105, 160, 5,    'Ayam Geprek',                   1,  13000, 1, 9000, 13000, (current_date + time '16:25')::timestamptz),
  (106, 160, 9,    'Pulpen Standard AE7 Hitam',     4,  3000,  1, 2000, 3000,  (current_date + time '16:25')::timestamptz),
  (107, 161, 1001, 'Mie Ayam',                      11, 10000, 2, 6000, 20000, (current_date + time '16:30')::timestamptz);

-- Mutasi stok penjualan tunai. Setiap menu hanya dipakai sekali dan berwaktu
-- paling akhir untuk menu itu, jadi stok_setelah = stok akhir − qty.
insert into riwayat_stok (id, sekolah_id, menu_id, arah, jenis, qty, stok_setelah, hpp_snapshot, transaksi_id, referensi_tipe, referensi_id, alasan, aktor_id, waktu, harga_beli_satuan, aktor_nama) values
  (124, 1,  3,    'KELUAR', 'PENJUALAN', 2, 17, 1000, 156, 'TRANSAKSI', 'TRX-156', 'Penjualan tunai di kasir', 4,  (current_date + time '16:05')::timestamptz, 1000, 'Kasir 1'),
  (125, 1,  7,    'KELUAR', 'PENJUALAN', 1, 22, 5000, 156, 'TRANSAKSI', 'TRX-156', 'Penjualan tunai di kasir', 4,  (current_date + time '16:05')::timestamptz, 5000, 'Kasir 1'),
  (126, 1,  1,    'KELUAR', 'PENJUALAN', 1, 16, 7000, 157, 'TRANSAKSI', 'TRX-157', 'Penjualan tunai di kasir', 4,  (current_date + time '16:10')::timestamptz, 7000, 'Kasir 1'),
  (127, 1,  4,    'KELUAR', 'PENJUALAN', 1, 5,  2000, 158, 'TRANSAKSI', 'TRX-158', 'Penjualan tunai di kasir', 7,  (current_date + time '16:15')::timestamptz, 2000, 'Kasir 2'),
  (128, 1,  6,    'KELUAR', 'PENJUALAN', 1, 34, 3000, 158, 'TRANSAKSI', 'TRX-158', 'Penjualan tunai di kasir', 7,  (current_date + time '16:15')::timestamptz, 3000, 'Kasir 2'),
  (129, 10, 1010, 'KELUAR', 'PENJUALAN', 1, 36, 7000, 159, 'TRANSAKSI', 'TRX-159', 'Penjualan tunai di kasir', 14, (current_date + time '16:20')::timestamptz, 7000, 'Kasir 10'),
  (130, 10, 1007, 'KELUAR', 'PENJUALAN', 1, 35, 4000, 159, 'TRANSAKSI', 'TRX-159', 'Penjualan tunai di kasir', 14, (current_date + time '16:20')::timestamptz, 4000, 'Kasir 10'),
  (131, 1,  5,    'KELUAR', 'PENJUALAN', 1, 36, 9000, 160, 'TRANSAKSI', 'TRX-160', 'Penjualan tunai di kasir', 7,  (current_date + time '16:25')::timestamptz, 9000, 'Kasir 2'),
  (132, 1,  9,    'KELUAR', 'PENJUALAN', 1, 43, 2000, 160, 'TRANSAKSI', 'TRX-160', 'Penjualan tunai di kasir', 7,  (current_date + time '16:25')::timestamptz, 2000, 'Kasir 2'),
  (133, 10, 1001, 'KELUAR', 'PENJUALAN', 2, 33, 6000, 161, 'TRANSAKSI', 'TRX-161', 'Penjualan tunai di kasir', 17, (current_date + time '16:30')::timestamptz, 6000, 'Kasir 20');

-- 24e. KERUGIAN STOK — laporan /laporan/kerugian-stok (PRD §9.5).
--      BARANG_RUSAK = rusak/basi/bocor; OPNAME_KELUAR = selisih hitung opname.
--      Semua sore hari (setelah seluruh penjualan) agar rantai stok utuh.
insert into riwayat_stok (id, sekolah_id, menu_id, arah, jenis, qty, stok_setelah, hpp_snapshot, transaksi_id, referensi_tipe, referensi_id, alasan, aktor_id, waktu, harga_beli_satuan, aktor_nama) values
  (134, 1,  12,   'KELUAR', 'BARANG_RUSAK',  2, 24, 5000, null, 'OPNAME', 'BA-RUSAK-1',  'Jus tumpah saat jam istirahat',  4,  (current_date + time '17:00')::timestamptz, 5000, 'Kasir 1'),
  (135, 1,  8,    'KELUAR', 'OPNAME_KELUAR', 1, 35, 4000, null, 'OPNAME', 'BA-OPNAME-1', 'Selisih hitung opname mingguan', 3,  (current_date + time '17:05')::timestamptz, 4000, 'Deryl Fabiensyah'),
  (136, 1,  2,    'KELUAR', 'BARANG_RUSAK',  1, 38, 6000, null, 'OPNAME', 'BA-RUSAK-2',  'Basi, tidak layak dijual',       4,  (current_date + time '17:10')::timestamptz, 6000, 'Kasir 1'),
  (137, 10, 1011, 'KELUAR', 'BARANG_RUSAK',  3, 23, 5000, null, 'OPNAME', 'BA-RUSAK-3',  'Kemasan bocor saat pengiriman',  14, (current_date + time '17:15')::timestamptz, 5000, 'Kasir 10'),
  (138, 10, 1006, 'KELUAR', 'OPNAME_KELUAR', 1, 22, 5000, null, 'OPNAME', 'BA-OPNAME-2', 'Selisih hitung opname mingguan', 13, (current_date + time '17:20')::timestamptz, 5000, 'Deryl Fabiensyah');

-- 24f. Jurnal Buku Kas untuk transaksi tunai baru (pola sama dengan #18).
insert into jurnal_buku_kas (id, sekolah_id, referensi_id, entitas, mutasi_id, ref_buku_kas, jumlah, status, waktu) values
  (69, 1,  'TRX-156', 'TRANSAKSI', null, null, 14000, 'SUKSES', (current_date + time '16:05')::timestamptz),
  (70, 1,  'TRX-157', 'TRANSAKSI', null, null, 12000, 'SUKSES', (current_date + time '16:10')::timestamptz),
  (71, 1,  'TRX-158', 'TRANSAKSI', null, null, 9000,  'SUKSES', (current_date + time '16:15')::timestamptz),
  (72, 10, 'TRX-159', 'TRANSAKSI', null, null, 17000, 'SUKSES', (current_date + time '16:20')::timestamptz),
  (73, 1,  'TRX-160', 'TRANSAKSI', null, null, 16000, 'SUKSES', (current_date + time '16:25')::timestamptz),
  (74, 10, 'TRX-161', 'TRANSAKSI', null, null, 20000, 'SUKSES', (current_date + time '16:30')::timestamptz);

-- 24g. Catatan audit untuk aksi baru (refund, pindah saldo, koreksi, selisih kas).
insert into catatan_audit (id, sekolah_id, aktor_id, aksi, entitas, entitas_id, nilai_lama, nilai_baru, alasan, waktu) values
  (7,  10, 12, 'TOPUP_SALDO',    'saldo',       '1202', '0',      '28000',  'Top up sebelum siswa pindah sekolah',   (current_date + time '16:30')::timestamptz),
  (8,  10, 15, 'REFUND_SALDO',   'saldo',       '1202', '28000',  '0',      'Refund sisa saldo — siswa pindah sekolah', (current_date + time '16:35')::timestamptz),
  (9,  1,  5,  'TRANSFER_SALDO', 'saldo',       '101',  '116000', '106000', 'Pindah saldo ke saudara kandung',       (current_date + time '16:40')::timestamptz),
  (10, 1,  5,  'KOREKSI_SALDO',  'saldo',       '104',  '100000', '105000', 'Selisih hitung setoran TU',             (current_date + time '16:45')::timestamptz);

-- 24h. Normalisasi sesi: total mengikuti transaksi, kas fisik mengikuti
--      totalTunai (C.2d), plus satu contoh selisih kas untuk demo C.3.
update sesi_kasir s
   set total_bruto  = x.bruto,
       total_void   = x.void_total,
       total_bersih = x.bruto - x.void_total
  from (
        select sesi_kasir_id,
               coalesce(sum(total), 0) as bruto,
               coalesce(sum(total) filter (where status = 'VOID'), 0) as void_total
          from transaksi
         group by sesi_kasir_id
       ) x
 where s.id = x.sesi_kasir_id;

update sesi_kasir s
   set uang_fisik = x.tunai,
       selisih    = 0,
       catatan    = 'Tutup normal, uang fisik sesuai sistem (kas = totalTunai)'
  from (
        select sesi_kasir_id,
               coalesce(sum(total) filter (where status = 'SUKSES' and metode_bayar = 'TUNAI'), 0) as tunai
          from transaksi
         group by sesi_kasir_id
       ) x
 where s.id = x.sesi_kasir_id
   and s.status = 'DITUTUP';

-- Demo selisih kas (C.3): sesi 5 kas tunai seharusnya 25.000, fisik 24.000.
update sesi_kasir
   set uang_fisik = uang_fisik - 1000,
       selisih    = -1000,
       catatan    = 'Kas kurang Rp 1.000 — uang kembalian salah hitung (demo selisih)'
 where id = 5;

-- 25. TIDAK PERLU SINKRON SEQUENCE
--     Semua PK di skema v2 diisi eksplisit (seed) atau oleh `helper/IdGenerator`
--     di aplikasi (epoch-millis × 1000 + counter), jadi tidak ada sequence
--     PostgreSQL yang harus di-`setval`.

commit;

-- =====================================================================
-- RINGKASAN ISI (dipakai juga oleh SeedDevIT)
--   sekolah 2 | pengguna 14 | siswa 21 (19 aktif + 2 nonaktif) | orang_tua 42 | kartu 21
--   kategori_menu 8 | menu 24 | stok 24 | titik_kasir 4
--   pengaturan_kantin 2 | akun_buku_kas 8 | saldo 21
--   sesi_kasir 16 (14 ditutup, 2 terbuka) | transaksi 62 (61 sukses, 1 void)
--   transaksi_item 107 | mutasi_saldo 80 | riwayat_stok 138 | jurnal_buku_kas 74
--   setoran 2 | kejadian_offline 2 | catatan_audit 10 | pembatasan 3
--   transaksi_tertunda 2
--
--   Cakupan demo yang dijaga: bayar tunai (6 transaksi), kas fisik + selisih
--   (sesi 5 kurang Rp 1.000), kerugian stok (3 barang rusak + 2 opname keluar),
--   refund saldo, pindah saldo, koreksi bendahara, topup online, kandidat refund.
--
-- Alur data yang dijaga konsisten:
--   saldo.saldo        = Σ (KREDIT − DEBIT) mutasi_saldo   (trigger)
--   stok.stok          = Σ (MASUK − KELUAR) riwayat_stok   (trigger)
--   transaksi.total    = Σ transaksi_item.subtotal
--   sesi_kasir.bersih  = total_bruto − total_void
-- =====================================================================
