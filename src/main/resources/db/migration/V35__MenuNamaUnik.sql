-- ============================================================
-- V35 — Nama menu unik per sekolah (#339)
-- ------------------------------------------------------------
-- Sebelum ini, keunikan nama menu hanya diperiksa di aplikasi
-- (KatalogService.pastikanNamaMenuUnik). Pemeriksaan "SELECT lalu INSERT"
-- tidak atomik: dua request yang tiba bersamaan sama-sama melihat "belum
-- ada", lalu sama-sama menyimpan — race condition yang tidak bisa ditutup
-- dari sisi kode saja.
--
-- Index ini memindahkan keputusan ke database, sehingga pelanggaran tetap
-- ditolak walau jalur pemeriksaan aplikasi terlewati (request paralel,
-- seed, atau SQL manual).
--
-- Kenapa ekspresinya `upper(nama)`:
--   * Aplikasi membandingkan tanpa peduli besar-kecil huruf
--     (MenuRepository.existsBy...NamaIgnoreCase → `upper(nama) = upper(?)`).
--     Index harus memakai ekspresi yang sama agar keduanya sepakat.
--   * Nama sudah dinormalisasi sebelum disimpan (trim + spasi tunggal),
--     jadi tidak perlu btrim/regexp_replace di sini.
--
-- Kenapa PARTIAL (`deleted_at IS NULL`), sama seperti V33 untuk kategori:
--   * Menghapus menu bersifat soft delete; nama menu yang sudah dihapus
--     boleh dipakai lagi untuk produk baru.
--
-- Kenapa perbandingan aman walau ada karakter non-ASCII: derived query
-- `...NamaIgnoreCase...` dirender Spring Data sebagai SQL
-- `upper(nama) = upper(?)` — bukan `String.equalsIgnoreCase` di Java. Jadi
-- pemeriksaan aplikasi dan index ini memakai fungsi serta collation database
-- yang sama; beda case folding antar-bahasa tidak masuk jalur ini.
--
-- PRA-CEK SEBELUM DEPLOY (hasilnya WAJIB kosong):
--   Flyway berjalan saat aplikasi start, sehingga duplikat yang tersisa
--   membuat aplikasi gagal start, bukan sekadar migrasi tertunda. Jalankan
--   di DB tujuan lebih dulu:
--
--     SELECT sekolah_id, upper(nama) AS nama, count(*) AS jumlah
--       FROM menu
--      WHERE deleted_at IS NULL
--      GROUP BY 1, 2
--     HAVING count(*) > 1;
--
-- Bila hasilnya tidak kosong, rapikan dulu (gabungkan stok atau isi
-- deleted_at pada baris duplikat), baru deploy. Jangan menghapus baris yang
-- masih dirujuk riwayat_stok/transaksi_item. Pesan kegagalan PostgreSQL
-- menyebutkan key duplikatnya, mis. "Key (sekolah_id, upper(nama::text))=(1,
-- JUS JERUK) is duplicated".
--
-- Catatan penguncian: CREATE UNIQUE INDEX non-CONCURRENTLY menahan tulis ke
-- tabel menu sampai selesai. Tabel katalog kecil sehingga waktunya singkat;
-- bila data sudah besar, jadwalkan deploy di luar jam operasional.
-- ============================================================

CREATE UNIQUE INDEX IF NOT EXISTS uq_menu_sekolah_nama
    ON menu (sekolah_id, upper(nama))
    WHERE deleted_at IS NULL;

COMMENT ON INDEX uq_menu_sekolah_nama IS
    'Nama menu unik per sekolah (tanpa peduli besar-kecil huruf); baris yang sudah dihapus (deleted_at terisi) tidak dihitung.';
