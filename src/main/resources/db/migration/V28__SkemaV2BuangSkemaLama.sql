-- =====================================================================
-- V28 — Buang skema lama (25 tabel) sebelum membangun skema v2
-- ---------------------------------------------------------------------
-- Skema v2 mengganti nama, menggabung, dan menghapus tabel lama
-- (lihat docs/skema-v2/dokumentasi-skema.md §6). Karena perubahannya
-- menyentuh primary key (saldo: subjek_tipe/subjek_id -> sekolah_id/siswa_id),
-- jalannya bukan ALTER bertahap melainkan bangun ulang.
--
-- Urutan: V28 hapus -> V29 buat 23 tabel v2 -> R__seed_dev isi data dev.
-- Data lama yang masih dibutuhkan: restore dari pg_dump sebelum migrasi.
-- =====================================================================

drop table if exists webhook_event cascade;
drop table if exists kartu_tamu cascade;
drop table if exists blokir_item cascade;
drop table if exists blokir_kartu cascade;
drop table if exists limit_harian cascade;
drop table if exists kebijakan_kantin cascade;
drop table if exists transaksi_menunggu_konfirmasi cascade;
drop table if exists posting_buku_kas cascade;
drop table if exists pos_buku_kas cascade;
drop table if exists insiden_offline cascade;
drop table if exists audit_log cascade;
drop table if exists setoran_tu cascade;
drop table if exists saldo_ledger cascade;
drop table if exists saldo_cache cascade;
drop table if exists mutasi_stok cascade;
drop table if exists stok_cache cascade;
drop table if exists transaksi_item cascade;
drop table if exists transaksi cascade;
drop table if exists sesi_kasir cascade;
drop table if exists titik_kasir cascade;
drop table if exists menu cascade;
drop table if exists kategori_menu cascade;
drop table if exists sekolah_kantin_config cascade;
drop table if exists dev_siswa_rfid cascade;
drop table if exists dev_pengguna cascade;

-- fungsi & trigger lama (trigger ikut terhapus bersama tabelnya)
drop function if exists sinkron_saldo_dari_ledger() cascade;
drop function if exists sinkron_stok_dari_mutasi() cascade;
drop function if exists sinkron_stok_minimum() cascade;
drop function if exists tolak_perubahan_ledger() cascade;
