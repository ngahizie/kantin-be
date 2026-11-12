-- ============================================================
-- V30 — Logo sekolah (URL saja)
-- ------------------------------------------------------------
-- `sekolah.logo_url` menyimpan TAUTAN gambar logo sekolah, dipakai header
-- aplikasi (FE) dan kop laporan cetak. Yang disimpan hanya URL — bukan
-- berkasnya — sama seperti `siswa.foto_url` dan `menu.foto_url`: berkas
-- diunggah lewat `POST /api/storage/upload`, lalu URL hasilnya disimpan.
--
-- Nullable & tanpa default: sekolah lama tetap sah walau belum punya logo
-- (FE cukup menampilkan inisial nama sekolah sebagai fallback).
-- ============================================================

ALTER TABLE sekolah
    ADD COLUMN IF NOT EXISTS logo_url CHARACTER VARYING(500);

COMMENT ON COLUMN sekolah.logo_url IS
    'URL gambar logo sekolah (mis. hasil unggah /api/storage). NULL = belum ada logo; FE memakai inisial nama sekolah sebagai fallback.';
