-- ============================================================
-- V26 — Soft delete tersembunyi untuk item katalog (`menu.deleted_at`)
-- ------------------------------------------------------------
-- Memisahkan antara:
--   * Menonaktifkan sementara (`is_active = FALSE`): masih tampil di
--     halaman katalog `/menu` agar pengelola dapat mengaktifkannya lagi.
--   * Menghapus menu (`deleted_at IS NOT NULL`): disembunyikan dari
--     daftar katalog, kasir POS, dan peringatan stok menipis tanpa
--     merusak riwayat transaksi & mutasi stok (PRD §6.2, §7.1).
-- ============================================================

ALTER TABLE menu
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_menu_sekolah_aktif_tersedia
    ON menu (sekolah_id, is_active)
    WHERE deleted_at IS NULL;

COMMENT ON COLUMN menu.deleted_at IS
    'Timestamp soft-delete permanen dari katalog: NULL = masih terdaftar di katalog, NOT NULL = dihapus dari daftar katalog namun riwayat tetap tersimpan.';

