-- =====================================================================
-- CALLBACK beforeMigrate — penjaga prasyarat V28 (issue #436)
--
-- V28__SkemaV2.sql sengaja MENGHAPUS SELURUH tabel schema `public`
-- (`drop table if exists public.%I cascade`) sebagai transisi ke skema v2.
-- Keputusan itu sah ketika belum ada data produksi, tetapi berbahaya bila
-- tanpa sengaja dijalankan pada database berisi data: semua tabel hilang.
--
-- Callback ini dijalankan Flyway SEBELUM setiap `migrate`:
--   * V28 sudah terpasang  → lewat (migrasi berikutnya tidak destruktif);
--   * V28 belum terpasang DAN sudah ada tabel lain di schema public → BERHENTI
--     dengan pesan yang menyuruh backup/kosongkan schema lebih dulu.
--
-- Kenapa bukan guard di dalam V28 sendiri: mengubah isi migrasi yang sudah
-- diterapkan mengubah checksum-nya, dan `validate-on-migrate=true` akan
-- menggagalkan deploy di semua environment yang sudah menerapkan V28.
-- =====================================================================

DO $$
DECLARE
    ada_riwayat    boolean;
    v28_terpasang  boolean;
    ada_tabel_lain boolean;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'flyway_schema_history'
    ) INTO ada_riwayat;

    IF ada_riwayat THEN
        SELECT EXISTS (
            SELECT 1 FROM public.flyway_schema_history
            WHERE version = '28' AND success
        ) INTO v28_terpasang;

        IF v28_terpasang THEN
            -- V28 sudah lewat; migrasi sesudahnya tidak menghapus schema.
            RETURN;
        END IF;
    END IF;

    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_type = 'BASE TABLE'
          AND table_name <> 'flyway_schema_history'
    ) INTO ada_tabel_lain;

    IF ada_tabel_lain THEN
        RAISE EXCEPTION USING
            ERRCODE = 'P0001',
            MESSAGE = 'Migrasi dihentikan: V28__SkemaV2.sql menghapus SELURUH tabel schema public, '
                      'tetapi database ini sudah berisi tabel tanpa riwayat V28.',
            HINT = 'Backup dulu (pg_dump), lalu kosongkan schema public bila memang database baru, '
                   'atau pakai database lain. Untuk database lokal yang sudah punya tabel dari '
                   'eksperimen, jalankan dengan profil local (spring.flyway.baseline-on-migrate=true). '
                   'Lihat docs/migrasi-flyway.md.';
    END IF;
END $$;
