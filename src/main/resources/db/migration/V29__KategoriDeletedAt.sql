-- ============================================================
-- V29 — Soft delete tersembunyi untuk kategori (`kategori_menu.deleted_at`)
-- ------------------------------------------------------------
-- Memisahkan dua aksi yang sebelumnya tercampur:
--   * Menonaktifkan sementara (`is_active = FALSE`): masih tampil di
--     halaman katalog agar pengelola bisa mengaktifkan lagi (PUT).
--   * Menghapus kategori (`deleted_at IS NOT NULL`): hilang dari
--     GET /api/katalog/kategori dan tidak bisa dipakai menu baru,
--     tetapi riwayat transaksi & laporan historis tetap terbaca.
--
-- Unik nama & urutan diubah menjadi PARTIAL INDEX (hanya baris hidup)
-- supaya nama/urutan kategori yang sudah dihapus boleh dipakai ulang.
-- ============================================================

ALTER TABLE kategori_menu
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE kategori_menu DROP CONSTRAINT IF EXISTS uq_kategori_menu_sekolah_nama;
ALTER TABLE kategori_menu DROP CONSTRAINT IF EXISTS uq_kategori_menu_sekolah_urutan;

CREATE UNIQUE INDEX IF NOT EXISTS uq_kategori_menu_sekolah_nama_hidup
    ON kategori_menu (sekolah_id, nama)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_kategori_menu_sekolah_urutan_hidup
    ON kategori_menu (sekolah_id, urutan)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_kategori_menu_sekolah_hidup
    ON kategori_menu (sekolah_id, is_active, urutan)
    WHERE deleted_at IS NULL;

COMMENT ON COLUMN kategori_menu.deleted_at IS
    'Timestamp soft-delete permanen kategori: NULL = masih terdaftar di katalog, NOT NULL = dihapus dari daftar katalog namun riwayat transaksi tetap tersimpan.';
