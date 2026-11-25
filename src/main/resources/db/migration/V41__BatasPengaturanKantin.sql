-- =====================================================================
-- V41 — BATAS NILAI PENGATURAN KANTIN (issue #415)
--
-- OWASP A06:2025 Insecure Design / CWE-1284 (Improper Validation of
-- Specified Quantity). DTO `PengaturanKantinRequest` sudah dibatasi
-- @Min/@Max, tetapi constraint database masih hanya menjaga batas bawah
-- (`durasi_foto_detik >= 0`, dst). Akibatnya baris lama dan jalur SQL
-- langsung tetap bisa menyimpan nilai ekstrem:
--
--   * durasi_foto_detik = 0 → verifikasi visual wajah siswa di kasir POS
--     hilang, celah penipuan identitas kartu siswa.
--   * nilai sangat besar → timer foto membekukan layar POS, dan operasi
--     tanggal/keuangan berisiko overflow.
--
-- Normalisasi dulu (agar migrasi tidak gagal pada data warisan), lalu
-- pasang constraint dua sisi.
-- =====================================================================

-- 1. Normalisasi nilai di luar rentang.
UPDATE pengaturan_kantin SET durasi_foto_detik = 3   WHERE durasi_foto_detik < 1;
UPDATE pengaturan_kantin SET durasi_foto_detik = 60  WHERE durasi_foto_detik > 60;
UPDATE pengaturan_kantin SET ambang_hari       = 3650 WHERE ambang_hari > 3650;
UPDATE pengaturan_kantin SET min_topup         = 100000000 WHERE min_topup > 100000000;
UPDATE pengaturan_kantin SET maks_topup        = 100000000 WHERE maks_topup > 100000000;
UPDATE pengaturan_kantin SET batas_saldo_siswa = 500000000 WHERE batas_saldo_siswa > 500000000;

-- 2. Durasi foto: batas bawah dinaikkan ke 1 detik (0 = tanpa verifikasi).
ALTER TABLE pengaturan_kantin DROP CONSTRAINT IF EXISTS ck_config_durasi_foto;
ALTER TABLE pengaturan_kantin
    ADD CONSTRAINT ck_config_durasi_foto CHECK (durasi_foto_detik BETWEEN 1 AND 60);

-- 3. Batas atas nilai keuangan & ambang hari.
--    Batas bawah tetap 0 di database: minimum Rp1.000/Rp10.000 adalah aturan
--    API (DTO), bukan invarian penyimpanan — data warisan di bawah ambang itu
--    tetap terbaca, dan perubahan kebijakan tidak perlu migrasi baru.
ALTER TABLE pengaturan_kantin DROP CONSTRAINT IF EXISTS ck_config_min_topup;
ALTER TABLE pengaturan_kantin
    ADD CONSTRAINT ck_config_min_topup
        CHECK (min_topup IS NULL OR (min_topup >= 0 AND min_topup <= 100000000));

ALTER TABLE pengaturan_kantin DROP CONSTRAINT IF EXISTS ck_config_maks_topup;
ALTER TABLE pengaturan_kantin
    ADD CONSTRAINT ck_config_maks_topup
        CHECK (maks_topup IS NULL OR (maks_topup >= 0 AND maks_topup <= 100000000));

ALTER TABLE pengaturan_kantin DROP CONSTRAINT IF EXISTS ck_config_batas_saldo_siswa;
ALTER TABLE pengaturan_kantin
    ADD CONSTRAINT ck_config_batas_saldo_siswa
        CHECK (batas_saldo_siswa IS NULL OR (batas_saldo_siswa >= 0 AND batas_saldo_siswa <= 500000000));

ALTER TABLE pengaturan_kantin DROP CONSTRAINT IF EXISTS ck_pengaturan_ambang;
ALTER TABLE pengaturan_kantin
    ADD CONSTRAINT ck_pengaturan_ambang CHECK (ambang_hari BETWEEN 0 AND 3650);
