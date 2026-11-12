# Skema Database v2 — Cetak Biru

> ✅ **Sudah jadi migrasi resmi.** Sejak `V28__SkemaV2.sql`, Flyway membangun skema v2
> dari nol: semua tabel di `public` (kecuali `flyway_schema_history`) dihapus lalu
> 23 tabel v2 dibuat ulang. Folder ini tetap jadi acuan desain & pembanding.
>
> **Database lama harus di-reset sekali** karena `V28` berubah (checksum berbeda):
> ```bash
> psql -h localhost -U kantin -d kantin_db -c 'drop schema public cascade; create schema public;'
> ./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # V1..V28 + seed dijalankan ulang
> ```

## Kenapa ada v2

Skema lama punya 25 tabel dengan masalah yang saling berkaitan:

| Masalah | Akibat |
|---|---|
| Tidak ada tabel `sekolah` | 24 tabel menyimpan `sekolah_id` tanpa induk — `999` pun diterima |
| Hanya 5 foreign key fisik | relasi cuma di kepala developer, bukan di database |
| `dev_siswa_rfid` mencampur 2 hal | data siswa dan kartu RFID tidak bisa dipisah |
| Tidak ada data orang tua | nama/HP wali dikarang di frontend |
| Nama tabel campur bahasa & awalan | `dev_`, `_cache`, `_log`, `pos_`, `posting_` |
| Fitur tamu & webhook tidak terpakai | 2 tabel + 7 constraint tanpa data nyata |

Skema v2 memperbaiki keenamnya tanpa menambah fitur baru: **25 → 23 tabel, 5 → 52 foreign key.**

## Isi folder

| File | Isi |
|---|---|
| [`dokumentasi-skema.md`](dokumentasi-skema.md) | **Dokumen utama** — 10 bab: ringkasan, alur data, 23 tabel per kelompok, aturan integritas + hasil uji, perbandingan lama→baru, isi seed, Q&A |
| [`erd-v2.mmd`](erd-v2.mmd) | ERD Mermaid — 23 tabel, 31 relasi. Buka di draw.io: *Arrange → Insert → Advanced → Mermaid* |
| [`sql/00-reset.sql`](sql/00-reset.sql) | hapus semua objek lama |
| [`sql/01-skema-baru.sql`](sql/01-skema-baru.sql) | 23 tabel + 52 FK + 59 index |
| [`sql/02-seed.sql`](sql/02-seed.sql) | 2 sekolah, 12 akun, 19 siswa, 38 wali, 24 menu |
| [`sql/03-verifikasi.sql`](sql/03-verifikasi.sql) | 9 uji konsistensi (read-only) |
| [`sql/README.md`](sql/README.md) | urutan perintah + cara restore |

## Menjalankan ulang di lokal

```bash
# backup dulu, selalu
pg_dump -h localhost -U kantin -d kantin_db \
  -f ~/kantin-backup/kantin_db_$(date +%Y%m%d_%H%M%S).sql

psql -h localhost -U kantin -d kantin_db -v ON_ERROR_STOP=1 -f docs/skema-v2/sql/00-reset.sql
psql -h localhost -U kantin -d kantin_db -v ON_ERROR_STOP=1 -f docs/skema-v2/sql/01-skema-baru.sql
psql -h localhost -U kantin -d kantin_db -v ON_ERROR_STOP=1 -f docs/skema-v2/sql/02-seed.sql
psql -h localhost -U kantin -d kantin_db -f docs/skema-v2/sql/03-verifikasi.sql
```

> Setelah reset, BE **belum bisa jalan** dengan skema ini: kode masih memanggil nama tabel
> lama (68 file). Jalankan dengan `--spring.flyway.enabled=false` hanya untuk inspeksi manual,
> sampai PR penyesuaian entity merge.

## Hasil uji terakhir (12 Nov 2026)

- 23 tabel, 52 FK, 16 unique, 173 check, 59 index
- 9 uji konsistensi → **0 temuan** (saldo = kredit − debit, siswa tanpa kartu/wali/saldo/foto = 0)
- 8 uji penolakan → **semua ditolak** (kartu lintas sekolah, 2 kartu aktif, saldo minus, transaksi SALDO tanpa siswa, dll)

## Urutan pekerjaan

| # | Isi | Status |
|---|---|---|
| 1 | Dokumentasi + cetak biru SQL (PR ini) | **sedang dikerjakan** |
| 2 | Migrasi Flyway `V28` + rename entity/repository + hapus tamu & webhook | belum |
| 3 | Isi 5 data yang dulu tidak tersimpan (uang laci, status setoran, NIS/foto, orang tua, nama petugas) | belum |
| 4 | Frontend menyesuaikan — issue [ngahizie/kantin-fe#135](https://github.com/ngahizie/kantin-fe/issues/135) | belum |
