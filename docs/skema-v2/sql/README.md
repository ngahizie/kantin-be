# SQL Skema Baru Kantin Skoolia

Urutan menjalankan:

```bash
cd ~/kantin-erd/sql

# 0. BACKUP DULU (wajib)
pg_dump -h localhost -U kantin -d kantin_db -f ~/kantin-backup/kantin_db_$(date +%Y%m%d_%H%M%S).sql

# 1. hapus semua objek lama
psql -h localhost -U kantin -d kantin_db -v ON_ERROR_STOP=1 -f 00-reset.sql

# 2. buat 23 tabel baru
psql -h localhost -U kantin -d kantin_db -v ON_ERROR_STOP=1 -f 01-skema-baru.sql

# 3. isi 2 sekolah
psql -h localhost -U kantin -d kantin_db -v ON_ERROR_STOP=1 -f 02-seed.sql

# 4. cek hasil
psql -h localhost -U kantin -d kantin_db -f 03-verifikasi.sql
```

## Kalau perlu kembali ke skema lama

```bash
psql -h localhost -U kantin -d kantin_db -c 'drop schema public cascade; create schema public;'
psql -h localhost -U kantin -d kantin_db -f ~/kantin-backup/<file-backup>.sql
```

## Isi file

| File | Isi |
|---|---|
| `00-reset.sql` | `drop schema public cascade` — menghapus SEMUA tabel lama |
| `01-skema-baru.sql` | 23 tabel + constraint + index |
| `02-seed.sql` | 2 sekolah, 12 akun, 19 siswa, 38 wali, 24 menu, saldo awal |
| `03-verifikasi.sql` | 9 uji konsistensi + rekap isi tabel (read-only) |
