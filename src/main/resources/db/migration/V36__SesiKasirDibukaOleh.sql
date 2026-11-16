-- ============================================================
-- V36 — Jejak pembuka sesi kasir (dibuka_oleh) — issue #345
-- ------------------------------------------------------------
-- `sesi_kasir` sudah mencatat `ditutup_oleh` untuk audit tutup shift, tetapi
-- tidak mencatat siapa yang MEMBUKA sesi. Akibatnya pertanyaan "loket ini
-- dibuka oleh petugas yang mana?" tidak bisa dijawab — padahal pada
-- operasional multi-titik kasir (PRD §6.6) dua loket di sekolah yang sama
-- bisa dibuka oleh petugas berbeda pada hari yang sama.
--
-- Kolom dibuat NULLABLE karena:
--   * sesi lama (sebelum migrasi ini) tidak punya jejak pembuka;
--   * sesi yang dibuka otomatis oleh jalur tap tanpa identitas petugas tetap sah.
--
-- FK disamakan dengan pola `fk_sesi_kasir_ditutup_oleh` di V28. Tanpa ON DELETE
-- cascade, karena menghapus pengguna yang punya jejak audit bukan operasi yang
-- diizinkan (pengguna dinonaktifkan lewat kolom `aktif`, bukan dihapus).
-- ============================================================

ALTER TABLE sesi_kasir
    ADD COLUMN IF NOT EXISTS dibuka_oleh bigint;

COMMENT ON COLUMN sesi_kasir.dibuka_oleh IS
    'ID pengguna (petugas kasir) yang membuka sesi — audit shift, issue #345.';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_sesi_kasir_dibuka_oleh'
    ) THEN
        ALTER TABLE sesi_kasir
            ADD CONSTRAINT fk_sesi_kasir_dibuka_oleh
            FOREIGN KEY (dibuka_oleh) REFERENCES pengguna (id);
    END IF;
END $$;
