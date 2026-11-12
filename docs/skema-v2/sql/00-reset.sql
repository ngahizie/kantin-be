-- =====================================================================
-- RESET: hapus SEMUA objek lama, mulai dari nol
-- PERINGATAN: destruktif. Backup dulu (lihat README.md).
-- =====================================================================
drop schema if exists public cascade;
create schema public;
