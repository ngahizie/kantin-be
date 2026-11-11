-- ============================================================
-- V27 — Kode barcode produk opsional pada katalog (`menu.kode_barcode`)
-- ------------------------------------------------------------
-- Dipakai kasir dan pengelola untuk identifikasi/scan produk kemasan.
-- Boleh NULL (mis. makanan buatan kantin tanpa kemasan), namun bila terisi
-- harus unik per sekolah selama menu belum dihapus (`deleted_at IS NULL`).
-- ============================================================

ALTER TABLE menu
    ADD COLUMN IF NOT EXISTS kode_barcode VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_menu_sekolah_kode_barcode
    ON menu (sekolah_id, kode_barcode)
    WHERE kode_barcode IS NOT NULL AND deleted_at IS NULL;

COMMENT ON COLUMN menu.kode_barcode IS
    'Kode barcode produk opsional (EAN-13, Code 128, atau kode internal); unik per sekolah untuk item aktif/nonaktif yang belum dihapus.';

