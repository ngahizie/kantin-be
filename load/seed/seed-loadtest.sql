-- ============================================================
-- seed-loadtest.sql — Data seed untuk uji beban k6 (issue #145)
-- ------------------------------------------------------------
-- Tujuan: menyediakan data MINIMAL agar POST /api/kasir/tap menembus
-- jalur SUKSES pada skenario "N kasir tap bersamaan".
--
-- ⚠️ BUKAN migrasi Flyway dan BUKAN data produksi. Jalankan manual:
--     psql "$DB_URL" -f load/seed/seed-loadtest.sql
--
-- SKEMA v2 (V28__SkemaV2) — perbedaan penting dari seed v1:
--   * Kartu Tamu (`kartu_tamu`, `dev_pengguna`) sudah TIDAK ADA. Pemilik
--     kartu kini `siswa` + `kartu` di kantin-be sendiri (KartuLookupLokal,
--     bukan lagi menunggu Q7), dan akun login dev ada di tabel `pengguna`.
--   * `saldo` dan `stok` adalah CACHE yang dihitung ulang oleh TRIGGER dari
--     ledger (`mutasi_saldo`, `riwayat_stok`). Karena itu seed ini TIDAK
--     menulis angka saldo/stok langsung: ia menyisipkan baris ledger
--     (KREDIT / MASUK). Kalau angka cache ditulis langsung, tap pertama akan
--     memicu trigger dan me-reset nilai menjadi 0 → tap berikutnya gagal
--     "saldo tidak cukup" / "stok tidak cukup".
--
-- Idempoten: aman dijalankan berulang (ON CONFLICT / NOT EXISTS). Seluruh id
-- memakai rentang 9xxx agar tidak bertabrakan dengan seed dev (id 1..24).
--
-- Yang dibuat:
--   sekolah 1 (bila belum ada) · pengaturan_kantin (konfirmasi_manual=false)
--   titik_kasir 9001 · sesi_kasir TERBUKA hari ini di titik itu
--   kategori_menu 9001 · menu 9001 · stok 100 juta (via riwayat_stok)
--   20 siswa 9001..9020 · 20 kartu UID 04000001..04000020 · saldo 1 M
--   akun login loadtest/loadtest (peran PETUGAS, sekolah 1)
-- ============================================================

begin;

-- ── 1. Sekolah uji (tenant 1) ──────────────────────────────────────────────
insert into sekolah (id, nama, npsn, aktif)
values (1, 'SMP Negeri 1 Skoolia', '20260001', true)
on conflict (id) do nothing;

-- ── 2. Konfigurasi kantin (konfirmasi_manual=false → tap langsung potong) ──
insert into pengaturan_kantin (sekolah_id, nama_kantin, jam_tutup_otomatis, konfirmasi_manual)
values (1, 'Kantin Load Test', '23:59', false)
on conflict (sekolah_id) do update
    set konfirmasi_manual  = false,
        jam_tutup_otomatis = '23:59',
        updated_at         = now();

-- ── 3. Titik kasir uji (id 9xxx, milik seed ini) ───────────────────────────
insert into titik_kasir (id, sekolah_id, nama, kode, is_active)
values (9001, 1, 'Kasir Load Test', 'LOAD1', true)
on conflict (id) do nothing;

-- ── 4. Sesi kasir hari ini (TERBUKA) → hindari balapan "buka sesi" saat beban ──
-- Bila sesi hari ini sudah ada tetapi DITUTUP, tap akan ditolak
-- ("Sesi kasir hari ini sudah ditutup"), jadi statusnya dibuka kembali.
update sesi_kasir
   set status     = 'TERBUKA',
       ditutup_at = null,
       updated_at = now()
 where sekolah_id = 1
   and titik_kasir_id = 9001
   and tanggal = current_date;

insert into sesi_kasir (id, sekolah_id, titik_kasir_id, tanggal, status, dibuka_at)
select (select coalesce(max(id), 0) + 1 from sesi_kasir),
       1, 9001, current_date, 'TERBUKA', now()
 where not exists (select 1 from sesi_kasir
                    where titik_kasir_id = 9001 and tanggal = current_date)
on conflict (titik_kasir_id, tanggal) do nothing;

-- ── 5. Kategori & menu uji ─────────────────────────────────────────────────
insert into kategori_menu (id, sekolah_id, nama, urutan, is_active)
values (9001, 1, 'Kategori Load Test', 9001, true)
on conflict (id) do nothing;

insert into menu (id, sekolah_id, kategori_id, nama, harga_jual, satuan,
                  stok_minimum, is_active)
values (9001, 1, 9001, 'Menu Load Test', 8000, 'PCS', 0, true)
on conflict (id) do nothing;

-- ── 6. Stok besar lewat LEDGER (trigger trg_riwayat_stok_sinkron → stok) ────
-- stok_setelah dihitung dari ledger yang ada supaya konsisten dengan baris
-- lain; trigger hanya menjaga kolom `stok`, jadi HPP di-set setelahnya.
insert into riwayat_stok (id, sekolah_id, menu_id, arah, jenis, qty,
                          stok_setelah, hpp_snapshot, alasan, waktu)
select (select coalesce(max(id), 0) + 1 from riwayat_stok),
       1, 9001, 'MASUK', 'BARANG_MASUK', 100000000,
       coalesce((select sum(case when arah = 'MASUK' then qty else -qty end)
                   from riwayat_stok
                  where sekolah_id = 1 and menu_id = 9001), 0) + 100000000,
       4000, 'Seed uji beban (#145)', now()
 where not exists (select 1 from riwayat_stok
                    where sekolah_id = 1 and menu_id = 9001
                      and jenis = 'BARANG_MASUK' and qty >= 100000000);

update stok set hpp = 4000, updated_at = now() where menu_id = 9001;

-- ── 7. 20 siswa uji (pemilik kartu; skema v2 tidak punya Kartu Tamu) ───────
insert into siswa (id, sekolah_id, nis, nama, kelas, aktif)
select 9000 + g, 1,
       'LOAD-' || lpad(g::text, 3, '0'),
       'Load Tester ' || g,
       'LOAD', true
  from generate_series(1, 20) as g
on conflict (id) do nothing;

-- Pemegang kartu harus siswa AKTIF, kalau tidak KartuLookupLokal menolaknya.
update siswa set aktif = true
 where sekolah_id = 1 and id between 9001 and 9020;

-- ── 8. 20 kartu uji ────────────────────────────────────────────────────────
-- rfid_uid = '04' + 6 digit (desimal ter-pad) → 8 karakter, unik.
--   g=1  → '04000001'   g=20 → '04000020'
-- Nilai ini SAMA dengan yang dibangkitkan skrip k6 (load/k6/tap-slo.js).
-- Satu siswa hanya boleh punya SATU kartu aktif
-- (uq_kartu_siswa_aktif), jadi kartu lain milik siswa uji dinonaktifkan dulu.
update kartu
   set aktif = false
 where sekolah_id = 1
   and siswa_id between 9001 and 9020
   and uid not in (select '04' || lpad(g::text, 6, '0')
                     from generate_series(1, 20) as g);

insert into kartu (uid, sekolah_id, siswa_id, nomor_kartu, aktif)
select '04' || lpad(g::text, 6, '0'), 1, 9000 + g,
       'LOAD-' || (9000 + g), true
  from generate_series(1, 20) as g
on conflict (uid) do update
    set siswa_id    = excluded.siswa_id,
        nomor_kartu = excluded.nomor_kartu,
        aktif       = true;

-- ── 9. Saldo besar lewat LEDGER (trigger trg_mutasi_saldo_sinkron → saldo) ──
-- Baris `saldo` harus ada lebih dulu: trigger hanya meng-UPDATE, tidak
-- membuat baris baru.
insert into saldo (sekolah_id, siswa_id, saldo, updated_at)
select 1, 9000 + g, 0, now()
  from generate_series(1, 20) as g
on conflict (sekolah_id, siswa_id) do nothing;

insert into mutasi_saldo (id, sekolah_id, siswa_id, arah, jenis, nominal,
                          saldo_setelah, referensi_tipe, referensi_id,
                          keterangan, waktu)
select (select coalesce(max(id), 0) from mutasi_saldo) + s.id,
       1, s.id, 'KREDIT', 'PENYESUAIAN', 1000000000,
       coalesce((select sum(case when arah = 'KREDIT' then nominal else -nominal end)
                   from mutasi_saldo m
                  where m.sekolah_id = 1 and m.siswa_id = s.id), 0) + 1000000000,
       'SEED_LOADTEST', 'TOPUP-' || s.id,
       'Saldo awal uji beban (#145)', now()
  from siswa s
 where s.sekolah_id = 1
   and s.id between 9001 and 9020
   and not exists (select 1 from mutasi_saldo m
                    where m.sekolah_id = 1 and m.siswa_id = s.id
                      and m.referensi_tipe = 'SEED_LOADTEST');

-- ── 10. Akun login khusus uji beban (tabel `pengguna`, bukan dev_pengguna) ──
-- Dibuat di sini (bukan di seed dev) agar uji beban TIDAK bergantung pada
-- kredensial dev yang bisa berubah. Password: loadtest (BCrypt cost 10).
-- Hanya untuk lokal & runner CI. Username harus unik global karena shim login
-- dev mencari `WHERE lower(username) = lower(?)`.
insert into pengguna (id, sekolah_id, username, nama, role, password_hash, aktif)
values ((select coalesce(max(id), 0) + 1 from pengguna), 1, 'loadtest',
        'Load Test (Petugas POS)', 'PETUGAS',
        '$2a$10$O27Z3A5gSOPV1G0JvD/S4eoqPyFr7.ZKpcMFgwM2PI/WT3qz/NTVS', true)
on conflict (sekolah_id, username) do update
    set password_hash = excluded.password_hash,
        role          = excluded.role,
        nama          = excluded.nama,
        aktif         = true;

-- ── Ringkas ────────────────────────────────────────────────────────────────
do $$
begin
    raise notice 'Seed uji beban siap: sesi kasir 9001 TERBUKA, menu 9001 stok 100jt, 20 kartu (UID 04000001..04000020) saldo 1M, akun loadtest/loadtest.';
end $$;

commit;
