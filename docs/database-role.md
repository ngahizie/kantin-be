# Peran Database Least-Privilege (issue #433)

Panduan **mengapa**, **bagaimana**, dan **cara membuktikan** bahwa kantin-be
memakai dua role PostgreSQL terpisah: satu untuk migrasi (DDL), satu untuk
runtime (DML saja) — dan **menolak** koneksi sebagai superuser.

Temuan asal: **SEC-02** audit OWASP Top 10:2025 (**A01 Broken Access Control**,
**A07 Authentication Failures**), issue induk #442.

---

## 1. Masalah yang dicegah

Sebelum perubahan ini:

| Hal | Kondisi lama | Akibat |
|---|---|---|
| Default user DB | `postgres` (superuser) | Lupa mengisi `DB_USERNAME` di produksi = aplikasi punya hak penuh atas seluruh DB |
| Role migrasi | sama dengan role runtime | Runtime punya jalur DDL (`CREATE`/`ALTER`/`DROP`) |
| Lapisan isolasi kedua | tidak ada (RLS tidak aktif) | Satu jalur injection/IDOR yang lolos langsung berbuah kompromi total |

Kode aplikasi memang sudah tenant-scoped, tetapi **satu-satunya** lapisan
isolasi adalah kode itu sendiri. Pemisahan role memberi lapisan kedua di level
database: jalur runtime **tidak bisa** mengubah struktur skema, dan tidak bisa
menghapus data secara massal (`TRUNCATE`/`DROP`).

## 2. Model dua role

| Role | Dipakai oleh | Hak | Password env |
|---|---|---|---|
| `kantin_owner` | **Flyway** (migrasi) | Pemilik database & skema `public`: `CREATE`/`ALTER`/`DROP` | `SPRING_FLYWAY_PASSWORD` |
| `kantin_app` | **Aplikasi** (Hikari pool) | `USAGE` skema + `SELECT, INSERT, UPDATE, DELETE` pada semua tabel, `USAGE, SELECT` pada sequence | `DB_PASSWORD` |
| `kantin` (bootstrap) | Image `postgres` / operasi manual | Superuser container | `DB_PASSWORD` untuk `POSTGRES_PASSWORD` |

Hak yang **sengaja tidak** diberikan ke `kantin_app`:

- `CREATE` pada skema `public` → jalur DDL tertutup
- `TRUNCATE`, `DROP`, `ALTER` → tidak bisa menghapus/mengubah data massal
- `CREATEDB`, `CREATEROLE`, `BYPASSRLS`, `SUPERUSER` → tidak bisa naik hak
- `flyway_schema_history` tetap milik `kantin_owner` → runtime tidak bisa
  memalsukan riwayat migrasi

Hak untuk tabel **baru** dijamin `ALTER DEFAULT PRIVILEGES FOR ROLE kantin_owner`,
jadi migrasi berikutnya tidak perlu mengurus `GRANT` lagi.

## 3. Penjaga saat start: `PemeriksaPeranDatabase`

Berjalan **saat inisialisasi bean** — sebelum Tomcat menerima trafik, sehingga
konfigurasi yang salah membuat aplikasi **tidak pernah melayani request**
(fail-closed). Berbeda dari penjaga TLS/topologi yang memakai
`ApplicationReadyEvent`; menolak superuser tidak berguna bila koneksi sudah
dilayani lebih dulu.

| Kondisi | Perilaku |
|---|---|
| `kantin.db.peran.enforce=false` | hanya INFO (dev eksperimen) |
| profil dev (`local`,`test`,`demo`,`loadtest`) **dan** host DB loopback | WARN, start jalan |
| profil dev **tetapi** host DB non-loopback | **divalidasi** (mencegah "produksi lupa ganti profil") |
| `DB_USERNAME` kosong / `postgres` / `superuser` / `rdsadmin` / `azure_superuser` / `cloudsqlsuperuser` | **start gagal** |
| `DB_PASSWORD` kosong | **start gagal** |
| `spring.flyway.user` kosong atau **sama** dengan role runtime | **start gagal** |
| `spring.flyway.password` kosong | **start gagal** |
| Role runtime ternyata `rolsuper`/`rolcreaterole`/`rolcreatedb`/`rolbypassrls` atau punya `CREATE` pada `public` | **start gagal** (dibaca dari `pg_roles`, bukan dari nama) |

Properti terkait (`application.properties`):

```properties
kantin.db.peran.enforce=${KANTIN_DB_PERAN_ENFORCE:true}
kantin.db.peran.dikecualikan-profil=${KANTIN_DB_PERAN_DIKECUALIKAN_PROFIL:local,test,demo,loadtest}
kantin.db.peran.user-terlarang=${KANTIN_DB_PERAN_USER_TERLARANG:postgres,superuser,rdsadmin,azure_superuser,cloudsqlsuperuser}
kantin.db.peran.wajib-pisah-migrasi=${KANTIN_DB_PERAN_WAJIB_PISAH_MIGRASI:true}
kantin.db.peran.cek-atribut=${KANTIN_DB_PERAN_CEK_ATRIBUT:true}
```

> **Catatan:** pemeriksaan atribut butuh koneksi nyata. Bila atribut tidak bisa
> diverifikasi (DB tak terjangkau), start **digagalkan** — tanpa DB sehat
> aplikasi ini tidak bisa melayani apa pun.

## 4. Menerapkan

### 4a. Docker compose (dev, volume baru)

`docker-compose.yml` sudah memasang `scripts/peran-db/buat-peran.sql` sebagai
`docker-entrypoint-initdb.d`. Script berjalan **sekali**, saat volume data
pertama kali dibuat:

```bash
cp .env.example .env      # isi DB_PASSWORD, SPRING_FLYWAY_PASSWORD, REDIS_PASSWORD
docker compose up -d
docker compose logs postgres | grep -A3 "Role siap"
```

`.env` minimal untuk DB:

```properties
DB_URL=jdbc:postgresql://localhost:5432/kantin_db?sslmode=disable
DB_USERNAME=kantin_app
DB_PASSWORD=<password role runtime>
SPRING_FLYWAY_USER=kantin_owner
SPRING_FLYWAY_PASSWORD=<password role migrasi>
```

### 4b. Database lokal yang sudah ada isinya (tanpa Docker)

Volume lama **tidak** menjalankan ulang script init. Terapkan manual:

```bash
# 1. Backup dulu (WAJIB)
KANTIN_DB_USER=postgres KANTIN_DB_PASSWORD=... scripts/backup-kantin-db.sh

# 2. Buat/segarkan role + pindahkan kepemilikan objek (butuh superuser)
DB_OWNER_PASSWORD='...' DB_APP_PASSWORD='...' \
  psql -h localhost -U postgres -d kantin_db -v ON_ERROR_STOP=1 \
       -f scripts/peran-db/buat-peran.sql

# 3. Ubah config aplikasi (application-local.properties)
#    spring.datasource.username=kantin_app
#    spring.datasource.password=<DB_APP_PASSWORD>
#    spring.flyway.user=kantin_owner
#    spring.flyway.password=<DB_OWNER_PASSWORD>

# 4. Buktikan
DB_PASSWORD='...' SPRING_FLYWAY_PASSWORD='...' scripts/cek-peran-db.sh
```

### 4c. Staging / produksi

1. **Backup** database (`scripts/backup-kantin-db.sh`).
2. Jalankan `scripts/peran-db/buat-peran.sql` sebagai superuser / DBA
   (butuh hak mengubah kepemilikan objek). Script **idempoten** — aman diulang.
3. Set environment aplikasi:

   ```properties
   DB_USERNAME=kantin_app
   DB_PASSWORD=<dari secret manager>
   SPRING_FLYWAY_USER=kantin_owner
   SPRING_FLYWAY_PASSWORD=<dari secret manager>
   ```

4. Deploy. Start yang benar mencetak:

   ```
   Peran database terverifikasi: runtime='kantin_app' (DML-only),
   migrasi='kantin_owner' (DDL), host='db.internal', profil=[prod] (issue #433).
   ```

5. Jalankan `scripts/cek-peran-db.sh` terhadap DB tersebut (butuh jaringan ke DB).

> **Kenapa `SPRING_FLYWAY_USER` dan bukan properti di `application.properties`?**
> Properti role migrasi sengaja **tidak** diberi nilai default di file properties
> agar dev/test/Testcontainers tetap memakai datasource utama (satu role).
> `SPRING_FLYWAY_USER`/`SPRING_FLYWAY_PASSWORD` adalah nama standar Spring, jadi
> berfungsi di container, k8s, systemd, dan `.env` tanpa pemetaan tambahan.

## 5. Membuktikan

```bash
DB_PASSWORD='...' SPRING_FLYWAY_PASSWORD='...' scripts/cek-peran-db.sh
```

Contoh keluaran sehat:

```
=== Cek peran database (issue #433) ===
host=localhost db=kantin_db runtime=kantin_app migrasi=kantin_owner

  ✅ role runtime bukan superuser
  ✅ role runtime tanpa CREATEDB/CREATEROLE/BYPASSRLS
  ✅ role runtime TIDAK punya CREATE pada skema public (jalur DDL tertutup)
  ✅ role runtime bisa membaca katalog & tabel public (24 tabel)
  ✅ role runtime DITOLAK saat CREATE TABLE
  ✅ role migrasi 'kantin_owner' bisa CREATE TABLE (DDL untuk Flyway)
  ✅ role runtime ('kantin_app') terpisah dari role migrasi ('kantin_owner')

✅ SEMUA LOLOS — postur role sesuai issue #433.
```

Bukti tambahan yang pernah dijalankan (lokal, PostgreSQL 18.6):

| Skenario | Hasil |
|---|---|
| Boot profil `prod`, `DB_USERNAME=kantin_app`, Flyway sebagai `kantin_owner` | 38 migrasi diterapkan sebagai `kantin_owner`, app start, `/actuator/health` = `UP` |
| Boot profil `prod`, `DB_USERNAME=postgres` | start gagal: *"DB_USERNAME='postgres' adalah user superuser/bawaan dan DITOLAK"* |
| Boot profil `prod`, role migrasi tidak diisi | start gagal: *"spring.flyway.user (SPRING_FLYWAY_USER) kosong"* |
| Boot profil `prod`, role superuser bernama lain (mis. `uji_super`) | start gagal: *"Role runtime 'uji_super' TIDAK least-privilege … superuser=true"* |
| `kantin_app` menjalankan `CREATE TABLE` / `TRUNCATE` | ditolak: *permission denied for schema public* / *for table* |

## 6. Row Level Security (RLS) — ditunda

Issue #433 juga menyebut RLS sebagai *defense in depth* opsional. Keputusan:
**DITUNDA**, dicatat di [ADR-0014](../architecture/adr/0014-rls-ditunda-peran-db-least-privilege.md).

Alasan ringkas: RLS mengubah cara **setiap** transaksi menetapkan tenant
(`SET LOCAL kantin.sekolah_id`), dan belum semua jalur transaksi
(scheduler, webhook, retry posting, laporan lintas-sekolah) punya mekanisme itu
yang teruji. Mengaktifkan RLS sebelum itu berisiko memblokir operasi sah —
lebih berbahaya daripada manfaatnya. Prasyaratnya didaftar di ADR tersebut.

## 7. Troubleshooting

| Pesan saat start | Penyebab | Perbaikan |
|---|---|---|
| `DB_USERNAME='postgres' … DITOLAK` | masih memakai superuser | set `DB_USERNAME=kantin_app` |
| `DB_PASSWORD … kosong` | secret tidak terpasang | isi `DB_PASSWORD` |
| `spring.flyway.user … kosong` | role migrasi belum diisi | set `SPRING_FLYWAY_USER`/`SPRING_FLYWAY_PASSWORD` |
| `Role migrasi Flyway … sama dengan role runtime` | satu role untuk dua peran | pisahkan jadi `kantin_owner` + `kantin_app` |
| `Role runtime … TIDAK least-privilege` | role punya `SUPERUSER`/`CREATEROLE`/`CREATE` | jalankan ulang `scripts/peran-db/buat-peran.sql` |
| Flyway: `permission denied for schema public` | Flyway masih memakai role runtime | isi `SPRING_FLYWAY_USER` |
| App: `permission denied for table …` | tabel dibuat sebelum `ALTER DEFAULT PRIVILEGES` | jalankan ulang `buat-peran.sql` (memberi `GRANT` pada tabel yang ada) |
| App: `permission denied for sequence …` | sequence baru tanpa `GRANT USAGE` | idem |

## 8. Batasan yang perlu diketahui

- **Test integrasi (Testcontainers)** tetap memakai role superuser container —
  test perlu `TRUNCATE` antar-test dan membuat skema. Yang diuji di IT adalah
  logika aplikasi, bukan postur role; postur role dibuktikan
  `scripts/cek-peran-db.sh` + `PemeriksaPeranDatabaseTest`.
- **Backup/restore** (`scripts/backup-kantin-db.sh`) butuh role dengan `SELECT`
  penuh — gunakan `kantin_owner` atau role backup khusus, **bukan** `kantin_app`.
- **`TRUNCATE` tidak diberikan** ke runtime. Skrip seed dev
  (`db/seed/R__seed_dev.sql`) karena itu harus dijalankan sebagai `kantin_owner`
  / superuser, bukan `kantin_app`.
- Role `kantin` (bootstrap) **tetap superuser** di container dev; ia tidak
  dipakai aplikasi. Di produksi, role bootstrap DBA dikelola di luar repo
  (SECURITY.md).
