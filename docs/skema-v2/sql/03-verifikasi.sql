-- =====================================================================
-- VERIFIKASI SKEMA + SEED  (hanya baca, tidak mengubah data)
-- Jalankan: psql -U kantin -d kantin_db -f 03_verifikasi.sql
-- =====================================================================
\echo '=== 1. JUMLAH TABEL (harus 23) ==='
select count(*) as jumlah_tabel
from information_schema.tables
where table_schema='public' and table_type='BASE TABLE';

\echo '=== 2. ISI TIAP TABEL ==='
select 'sekolah' t, count(*) n from sekolah
union all select 'pengguna', count(*) from pengguna
union all select 'siswa', count(*) from siswa
union all select 'orang_tua', count(*) from orang_tua
union all select 'kartu', count(*) from kartu
union all select 'kategori_menu', count(*) from kategori_menu
union all select 'menu', count(*) from menu
union all select 'stok', count(*) from stok
union all select 'riwayat_stok', count(*) from riwayat_stok
union all select 'titik_kasir', count(*) from titik_kasir
union all select 'sesi_kasir', count(*) from sesi_kasir
union all select 'transaksi', count(*) from transaksi
union all select 'transaksi_item', count(*) from transaksi_item
union all select 'saldo', count(*) from saldo
union all select 'mutasi_saldo', count(*) from mutasi_saldo
union all select 'setoran', count(*) from setoran
union all select 'pengaturan_kantin', count(*) from pengaturan_kantin
union all select 'pembatasan', count(*) from pembatasan
union all select 'transaksi_tertunda', count(*) from transaksi_tertunda
union all select 'akun_buku_kas', count(*) from akun_buku_kas
union all select 'jurnal_buku_kas', count(*) from jurnal_buku_kas
union all select 'catatan_audit', count(*) from catatan_audit
union all select 'kejadian_offline', count(*) from kejadian_offline
order by 1;

\echo '=== 3. UJI KONSISTENSI (semua harus 0) ==='
select 'saldo beda dengan ledger' as uji,
       count(*) as temuan
from saldo s
where s.saldo <> coalesce((select sum(case when arah='KREDIT' then nominal else -nominal end)
                           from mutasi_saldo m
                           where m.sekolah_id=s.sekolah_id and m.siswa_id=s.siswa_id),0)
union all
select 'siswa tanpa kartu aktif', count(*) from siswa s
 where not exists (select 1 from kartu k where k.siswa_id=s.id and k.aktif)
union all
select 'siswa tanpa wali', count(*) from siswa s
 where not exists (select 1 from orang_tua o where o.siswa_id=s.id)
union all
select 'siswa tanpa baris saldo', count(*) from siswa s
 where not exists (select 1 from saldo sa where sa.siswa_id=s.id)
union all
select 'menu tanpa baris stok', count(*) from menu m
 where not exists (select 1 from stok st where st.menu_id=m.id)
union all
select 'siswa tanpa foto_url', count(*) from siswa
 where foto_url is null or foto_url=''
union all
select 'sekolah tanpa pengaturan', count(*) from sekolah s
 where not exists (select 1 from pengaturan_kantin p where p.sekolah_id=s.id)
union all
select 'sekolah tanpa titik kasir', count(*) from sekolah s
 where not exists (select 1 from titik_kasir t where t.sekolah_id=s.id)
union all
select 'sekolah tanpa akun buku kas', count(*) from sekolah s
 where not exists (select 1 from akun_buku_kas a where a.sekolah_id=s.id);

\echo '=== 4. AKUN PER SEKOLAH ==='
select sekolah_id, count(*) as jumlah_akun, string_agg(username, ', ' order by username) as akun
from pengguna group by sekolah_id order by sekolah_id;

\echo '=== 5. SISWA + KARTU + WALI PER SEKOLAH ==='
select s.sekolah_id,
       count(distinct s.id) as siswa,
       count(distinct k.uid) as kartu,
       count(distinct o.id) as wali
from siswa s
left join kartu k on k.siswa_id = s.id
left join orang_tua o on o.siswa_id = s.id
group by s.sekolah_id order by s.sekolah_id;

\echo '=== 6. CONSTRAINT YANG DIPASANG ==='
select constraint_type, count(*)
from information_schema.table_constraints
where table_schema='public'
group by constraint_type order by 1;
