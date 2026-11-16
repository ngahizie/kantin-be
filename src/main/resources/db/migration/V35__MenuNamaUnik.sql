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
-- Bila migrasi ini gagal dengan "could not create unique index ... Key
-- (sekolah_id, upper(nama::text))=(...) is duplicated", artinya masih ada
-- menu bernama sama yang aktif. Rapikan dulu (gabungkan stok atau isi
-- deleted_at pada baris duplikat), baru jalankan ulang migrasi. Jangan
-- menghapus baris yang masih dirujuk riwayat_stok/transaksi_item.
-- ============================================================

CREATE UNIQUE INDEX IF NOT EXISTS uq_menu_sekolah_nama
    ON menu (sekolah_id, upper(nama))
    WHERE deleted_at IS NULL;

COMMENT ON INDEX uq_menu_sekolah_nama IS
    'Nama menu unik per sekolah (tanpa peduli besar-kecil huruf); baris yang sudah dihapus (deleted_at terisi) tidak dihitung.';
