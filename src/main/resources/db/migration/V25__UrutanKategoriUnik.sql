-- ============================================================
-- V25 — Urutan kategori wajib unik per sekolah
-- ------------------------------------------------------------
-- Temuan review 2026-11-09: `urutan` bisa diisi angka yang sama untuk dua
-- kategori dalam satu sekolah (contoh nyata: sekolah 10 punya dua kategori
-- dengan urutan = 1). Akibatnya urutan tampil di kasir tidak deterministik
-- dan bisa berubah antar request.
--
-- Langkah:
--   1. Normalisasi: rapikan `urutan` menjadi 1..n per sekolah secara
--      deterministik (kategori AKTIF lebih dulu, lalu urutan lama, waktu
--      dibuat, id). Data yang sudah rapi tidak berubah nilainya; kategori
--      nonaktif/test yang menabrak nomor digeser ke belakang sehingga urutan
--      yang terlihat di kasir tetap.
--   2. Kunci dengan UNIQUE (sekolah_id, urutan) agar duplikat tidak bisa
--      terjadi lagi di level database.
--
-- Catatan: validasi ramah (pesan "urutan sudah dipakai") tetap perlu di
-- service; constraint ini hanya jaring terakhir agar data tidak rusak.
-- ============================================================

WITH ranked AS (
    SELECT id,
           (row_number() OVER (
               PARTITION BY sekolah_id
               ORDER BY is_active DESC, urutan, created_at, id
           ))::integer AS baru
    FROM kategori_menu
)
UPDATE kategori_menu k
SET urutan     = r.baru,
    updated_at = now()
FROM ranked r
WHERE k.id = r.id
  AND k.urutan IS DISTINCT FROM r.baru;

ALTER TABLE kategori_menu
    ADD CONSTRAINT uq_kategori_menu_sekolah_urutan UNIQUE (sekolah_id, urutan);

COMMENT ON CONSTRAINT uq_kategori_menu_sekolah_urutan ON kategori_menu IS
    'Satu nomor urut hanya boleh dipakai satu kategori per sekolah (review 2026-11-09).';
