# Tata kelola migrasi Flyway (issue #436)

Dokumen ini mencatat kebijakan migrasi `kantin-be`: apa yang boleh, apa yang
dilarang, dan apa yang harus diperiksa sebelum deploy. Latar belakangnya temuan
audit OWASP A08:2025 (software & data integrity failures) atas tiga hal:
`baseline-on-migrate`, sifat destruktif `V28__SkemaV2.sql`, dan riwayat penomoran
migrasi.

## 1. Kebijakan `baseline-on-migrate`

```properties
# application.properties (default semua environment)
spring.flyway.baseline-on-migrate=false
spring.flyway.validate-on-migrate=true

# application-local.properties (HANYA profil local)
spring.flyway.baseline-on-migrate=true
```

`baseline-on-migrate=true` membuat Flyway **menandai** database non-kosong
sebagai baseline alih-alih gagal. Akibatnya skema yang tidak sesuai (tabel ada,
kolom beda) tidak pernah ketahuan — aplikasi berjalan di atas skema asing dan
gagal jauh kemudian, saat transaksi berjalan.

Karena itu default-nya `false`. Satu-satunya tempat yang membukanya adalah
profil `local`, karena database developer sering sudah berisi tabel dari
eksperimen. **Jangan** membuka di staging/produksi: ketidaksesuaian skema harus
terlihat sebagai kegagalan start, bukan ditutupi.

`validate-on-migrate=true` memeriksa checksum migrasi yang sudah diterapkan.
Konsekuensi penting: **migrasi yang sudah dirilis tidak boleh disunting** —
mengubah satu karakter saja mengubah checksum dan menggagalkan validasi di semua
environment yang sudah menerapkannya.

## 2. `V28__SkemaV2.sql` bersifat destruktif — dan itu disengaja

`V28` menghapus **seluruh** tabel schema `public` (`drop table if exists
public.%I cascade`) sebagai transisi ke skema v2. Keputusan itu diambil saat
belum ada data produksi dan sudah tertulis di header migrasi.

**Prasyarat menjalankan `V28`:**

1. Database kosong (belum ada tabel di schema `public`), **atau**
2. Anda sudah punya backup yang terbukti bisa dipulihkan:
   `pg_dump -Fc -f kantin-$(date +%F).dump "$DB_URL"` lalu uji restore di
   database terpisah.

**Penjaga otomatis.** `src/main/resources/db/migration/beforeMigrate.sql`
(callback Flyway) menghentikan `migrate` bila `V28` belum terpasang tetapi sudah
ada tabel lain di schema `public`:

```
ERROR: Migrasi dihentikan: V28__SkemaV2.sql menghapus SELURUH tabel schema public,
       tetapi database ini sudah berisi tabel tanpa riwayat V28.
HINT: Backup dulu (pg_dump), lalu kosongkan schema public bila memang database baru, ...
```

Callback dipakai, bukan guard di dalam `V28`, justru karena `V28` tidak boleh
disunting (alasan checksum di atas).

Perilaku yang sudah diuji langsung terhadap PostgreSQL 18:

| Kondisi database | Hasil `migrate` |
|---|---|
| Kosong (belum ada tabel) | jalan normal, 41 migrasi diterapkan |
| Ada tabel + **tidak ada** `flyway_schema_history` | ditolak Flyway sendiri: "Found non-empty schema(s) … but no schema history table" (karena `baseline-on-migrate=false`) |
| `flyway_schema_history` ada di versi 27 (mis. hasil `baseline`) + tabel `transaksi` berisi data | ditolak callback `beforeMigrate` dengan pesan di atas |
| `V28` sudah terpasang | callback lewat, migrasi berikutnya berjalan normal |

## 3. Riwayat penomoran `V29`/`V30`

Gap nomor **bukan** bukti drift, dan tidak ada environment yang pernah
menerapkan versi tersebut. Yang benar-benar terjadi (diverifikasi dari
`git log --all --name-only -- '*V29*' '*V30*'`):

| Kejadian | Bukti |
|---|---|
| `V29__KategoriDeletedAt.sql` dibuat | commit `b5ad4d2` (12 Nov 2026), fitur hapus kategori via `deleted_at` |
| Dinomori ulang menjadi `V33__KategoriDeletedAt.sql` karena `V31`/`V32` sudah terpakai | commit `9726f4a` — "chore(db): nomori ulang migrasi kategori jadi V33 (V31/V32 sudah terpakai)" |
| `V30` tidak pernah ada | tidak ada berkas `V30*` di seluruh riwayat repo |

Karena `V29` belum pernah dirilis ke database mana pun (belum ada deploy pada
rentang itu), penomoran ulang aman. Urutan `main` sekarang: `V28`, lalu `V31`–`V41`
(tanpa `V29`/`V30`). **Jangan** membuat migrasi baru dengan nomor `V29`/`V30` —
kalau suatu saat ada database yang ternyata pernah menerapkan `V29` lama,
`flyway validate` akan menolak dan deploy berhenti.

## 4. Sebelum deploy (wajib)

```bash
# 1. Lihat status migrasi target — JANGAN langsung migrate
./mvnw -B flyway:info \
  -Dflyway.url="$DB_URL" -Dflyway.user="$DB_USERNAME" -Dflyway.password="$DB_PASSWORD"

# 2. Baca hasilnya
#    - semua "Success"           → aman, aplikasi boleh start
#    - ada "Pending"             → deploy akan menerapkannya; pastikan tidak destruktif
#    - "Missing" / checksum beda → JANGAN migrate; periksa dulu (lihat bagian 5)
```

CI menjalankan langkah yang sama (`job migrasi` di `.github/workflows/ci.yml`)
terhadap PostgreSQL sekali pakai, sehingga berkas migrasi yang rusak atau tidak
terbaca gagal lebih awal, sebelum menyentuh database nyata.

## 5. Kalau `flyway validate` gagal

| Pesan | Arti | Tindakan |
|---|---|---|
| `Migration checksum mismatch` | migrasi yang sudah diterapkan disunting | **jangan** `repair` buta. Kembalikan berkas ke versi asli; kalau perbaikan memang perlu, buat migrasi baru |
| `Detected applied migration not resolved locally` | database punya versi yang tidak ada di repo | cek apakah berkas migrasi terhapus; `ignore-migration-patterns=*:missing` **hanya** untuk profil local |
| `Detected resolved migration not applied to database` | ada migrasi baru | normal; `migrate` akan menerapkannya |

`flyway:repair` hanya memperbaiki tabel riwayat (checksum/penanda gagal), bukan
skema. Pakai hanya setelah tahu persis penyebabnya, dan catat di PR.

## 6. Aturan singkat untuk kontributor

1. Migrasi baru = nomor berikutnya, satu perubahan logis per berkas.
2. Jangan menyunting migrasi yang sudah ada di `main`.
3. Destruktif (`drop`, `truncate`, `delete` massal) wajib: header peringatan,
   prasyarat tertulis di dokumen ini, dan rencana backup di deskripsi PR.
4. `beforeMigrate.sql` hanya untuk prasyarat lintas-migrasi; jangan menaruh DDL
   di sana.
