# Demo E2E Kasir (lokal) — issue #163

Panduan menjalankan **alur kasir end-to-end** di lokal — *tap → potong saldo &
stok → tutup sesi → cek laporan* — **tanpa** menunggu data/keys tim SKOOLIA
(OPEN-QUESTIONS **Q7**). Semua data demo **milik kantin-be sendiri** (tabel lokal).

> ⚠️ **Hanya untuk lokal/demo.** Profil `demo` mengaktifkan jalur lookup Kartu
> Tamu & shim login dev. **JANGAN** dipakai di staging/production.

---

## 0. Ringkas alur

```
docker compose up -d            # PostgreSQL + Redis
SPRING_PROFILES_ACTIVE=local,demo  (butuh private key JWT dummy)
                                # seed dev TIDAK otomatis — jalankan psql -f (lihat §3)
POST /api/v1/auth/login         # ambil token (shim dev)
POST /api/kasir/tap             # tap kartu → transaksi
POST /api/kasir/sesi/1/tutup    # tutup sesi
GET  /api/laporan/…             # cek laporan
```

Data yang di-seed (`db/seed/R__seed_dev.sql`): **2 sekolah** (sekolah **1** dan
sekolah **10** — tenant default form login FE), **24 menu**, **4 titik kasir**,
**14 sesi kasir** (12 ditutup, 2 masih terbuka untuk demo kasir), **56 transaksi
7 hari terakhir** (1 void untuk demo refund), **19 siswa + 19 kartu siswa**,
**14 akun pengguna** (2 kasir per sekolah, lihat §4), saldo & mutasinya, stok &
HPP, koreksi saldo, setoran TU, serta pengaturan kantin — cukup untuk semua layar
FE tanpa mock.

> **Integrasi eksternal disimulasikan (bukan API key).** Profil `demo` juga
> mengaktifkan adapter **demo** untuk 6 integrasi yang masih `blocked` (butuh
> kredensial tim SKOOLIA) — Buku Kas, Notifikasi, Lookup kartu, Status siswa,
> UID siswa, Aktivasi modul. Semua meniru sistem eksternal di lokal sehingga alur
> end-to-end (termasuk posting Buku Kas & notifikasi) benar-benar berjalan tanpa
> API key. Lihat §9.

---

## 1. Infrastruktur

```bash
docker compose up -d
docker compose ps            # pastikan postgres & redis "healthy"
```

## 2. Jalankan aplikasi (profil demo)

Profil `demo` butuh **private key JWT** agar shim login dev bisa menerbitkan
token (pasangan `jwt.admin-public-key`). Buat keypair dummy **sekali**:

```bash
# Private key (untuk shim login) + public key (untuk verifikasi kantin-be)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /tmp/priv.pem
export KANTIN_DEV_LOGIN_PRIVATE_KEY=$(openssl pkcs8 -topk8 -nocrypt -in /tmp/priv.pem -outform DER | base64 -w0)
export JWT_ADMIN_PUBLIC_KEY=$(openssl rsa -in /tmp/priv.pem -pubout -outform DER | base64 -w0)
```

Lalu:

```bash
SPRING_PROFILES_ACTIVE=local,demo \
KANTIN_DEV_LOGIN_ENABLED=true \
KANTIN_RATE_LIMIT_ENABLED=false \
  ./mvnw spring-boot:run
```

Aplikasi siap di `http://localhost:8082` (health: `/actuator/health`).

> `application-demo.properties` sudah menyetel `kantin.demo.enabled=true`,
> `kantin.dev-login.enabled=true`, dan mematikan rate limit — jadi env di atas
> hanya menegaskan.

## 3. Seed data demo (manual, tidak lagi otomatis)

Seed **tidak** lagi dimuat otomatis saat aplikasi start — supaya data dev tidak
terhapus dan transaksi dummy tidak dibuat ulang setiap hari. Jalankan sekali
secara manual saat butuh dataset demo:

```bash
psql -h localhost -U kantin -d kantin_db \
     -f src/main/resources/db/seed/R__seed_dev.sql
```

- file: `src/main/resources/db/seed/R__seed_dev.sql` (repeatable migration)
- jalan otomatis **hanya** bila kamu sendiri menambahkan `classpath:db/seed` ke
  `spring.flyway.locations` di `application-local.properties` (tidak default)

**Destruktif & deterministik** — tabel dikosongkan dulu lalu diisi ulang, supaya
hasilnya sama antar-run. Karena itu jangan dijalankan kalau ada data dev yang
ingin dipertahankan. Ingin DB kosong? Cukup jangan jalankan perintah di atas
(migrasi skema tetap jalan sendiri).

## 4. Ambil token (shim login dev)

Kredensial **diverifikasi** terhadap tabel `pengguna` hasil seed — username atau
password asal-asalan ditolak `401`. Password seluruh akun dev: `Kantin#2026`.

| Username | Role (klaim) | Sekolah | `currentRole` di FE |
|---|---|---|---|
| `kepsek1` | KEPSEK | 1 | kepsek |
| `tu1` | TATA_USAHA | 1 | tu |
| `bendahara1` | BENDAHARA | 1 | bendahara |
| `pengelola1` | PENGELOLA | 1 | pengelola |
| `admin1` | ADMIN_SEKOLAH | 1 | admin |
| `kasir1` | PETUGAS | 1 | kasir (titik kasir 1 / DEMO1) |
| `kasir2` | PETUGAS | 1 | kasir (titik kasir 2 / DEMO2 — uji multi-loket, #345) |
| `kepsek10` | KEPSEK | 10 | kepsek |
| `tu10` | TATA_USAHA | 10 | tu |
| `bendahara10` | BENDAHARA | 10 | bendahara |
| `pengelola10` | PENGELOLA | 10 | pengelola |
| `admin10` | ADMIN_SEKOLAH | 10 | admin |
| `kasir10` | PETUGAS | 10 | kasir (titik kasir 11 / FE10-1) |
| `kasir20` | PETUGAS | 10 | kasir (titik kasir 12 / FE10-2 — uji multi-loket, #345) |

> **Hak akses per akun mengikuti PRD §9.5/§9.6** (lihat `architecture/RBAC-MATRIX.md`
> sebagai sumber kebenaran). Setelah issue #250: `pengelola1` **tidak** lagi bisa
> top-up/refund/koreksi saldo, menambah menu/kategori, membuka sesi kasir, atau
> memblokir kartu siswa; `tu1`/`bendahara1` **tidak** lagi bisa mengubah limit
> harian/blokir item siswa (itu hak Admin sekolah) dan **tetap** boleh memblokir
> kartu siswa; `kepsek1` hanya boleh membaca laporan yang PRD §9.5 sebutkan.

Fixture siswa untuk tenant sekolah 10: nomor kartu `RFID-SISWA-001`, UID RFID
`DA100001`, siswa ID `1001` (**Ahmad Fauzan**, kelas 7A) — semuanya ada di seeder,
jadi tap dilayani lokal oleh `KartuLookupLokal`. Untuk mencobanya, login sebagai
`kasir10` dan kirim tap dengan UID tersebut, titik kasir `11` (FE10-1), menu
`1002` (Mie Ayam), kuantitas `1`, dan idempotency key baru.

Catatan: `sekolah_id` yang dikirim FE **diabaikan**. Tenant ditentukan oleh akun,
jadi untuk menguji tenant 10 (tenant default form login `kantin-fe`) pakai
`kasir10`/`bendahara10`, bukan akun sekolah 1.

```bash
TOKEN=$(curl -s -X POST http://localhost:8082/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"kasir1","password":"Kantin#2026"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
echo "$TOKEN" | head -c 20; echo "…"
```

Role, `userId`, dan `sekolah_id` diambil **dari akun**, bukan dari body request —
klien tidak bisa memilih identitas sendiri, dan `sekolah_id` pada isi permintaan FE lama
diabaikan. Bentuk respons:
`{"code":200,"message":"…","data":{"token":"…","user":{…}}}`.

## 5. Tap kartu (transaksi)

```bash
curl -s -X POST http://localhost:8082/api/kasir/tap \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
        "rfidUid": "04A1B2C3D4",
        "titikKasirId": 1,
        "idempotencyKey": "demo-tap-001",
        "items": [ {"menuId": 1, "qty": 1}, {"menuId": 3, "qty": 2} ]
      }' | python3 -m json.tool
```

Harapan: `code=200`, saldo terpotong (37.000 − 12.000 − 6.000 = **19.000**),
stok Nasi Goreng & Es Teh Manis berkurang, `transaksiId` terisi.
UID `04A1B2C3D4` adalah kartu siswa 101 (`RFID-SISWA-101`) di sekolah 1 — pakai
akun `kasir1` dan titik kasir 1.

> **Idempotency (PRD §11.3):** kirim ulang dengan `idempotencyKey` **sama** →
> hasil yang sama, **tidak** memotong saldo dua kali. Ganti key untuk tap baru.

## 6. Tutup sesi

```bash
curl -s -X POST http://localhost:8082/api/kasir/sesi/1/tutup \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

## 7. Cek laporan

```bash
# Ringkasan penjualan harian
curl -s "http://localhost:8082/api/laporan/penjualan?tanggal=$(date +%F)" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# Ekspor XLSX (Apache POI) — jenis laporan via query `jenis`
curl -s -o laporan.xlsx \
  "http://localhost:8082/api/laporan/ekspor?jenis=PENJUALAN&tanggal=$(date +%F)" \
  -H "Authorization: Bearer $TOKEN"
file laporan.xlsx     # → Microsoft Excel 2007+
```

Daftar lengkap endpoint laporan (penjualan/item/kategori, stok, setoran-tu,
per-siswa, dll) ada di [`architecture/API-ENDPOINTS.md`](../architecture/API-ENDPOINTS.md) §Laporan.

---

## 8. Reset data demo

Karena ledger & transaksi bersifat **append-only**, untuk mengulang dari nol:

```bash
docker compose down -v && docker compose up -d    # hapus volume → DB bersih
./mvnw spring-boot:run                            # migrasi skema saja
psql -h localhost -U kantin -d kantin_db \
     -f src/main/resources/db/seed/R__seed_dev.sql  # seed manual, bila perlu
```

## 9. Kenapa ini aman (tidak bocor ke produksi)

| Gerbang | Default | Efek |
|---|---|---|
| `kantin.demo.enabled` | `false` | Adapter **demo** (6 integrasi) hanya hidup bila `true` (profil `demo`) |
| `kantin.loadtest.enabled` | `false` | Menandai profil uji beban; lookup kartu **tidak** lagi lewat sini (sejak skema v2 pakai `KartuLookupLokal`) |
| `kantin.dev-login.enabled` | `false` | Shim login hanya hidup bila `true` + profil `local` |
| `kantin.integrasi.*.enabled` | `false` | Adapter REST **nyata** hanya hidup bila `true` (staging/produksi) |
| `kantin.integrasi.wajib-aktif` | `false` | Bila `true`, start DIGAGALKAN bila masih ada `*Fallback` (penjaga produksi #164) |
| `@Profile("local")` | — | Shim login dev **tidak** dimuat di staging/produksi |

**Adapter demo (semua digerbangi `kantin.demo.enabled`):**

| Port | Kelas demo | Meniru | Perilaku |
|---|---|---|---|
| `KartuLookupPort` | `KartuLookupLokal` | admin-be lookup siswa | Baca `kartu` + `siswa` + `pembatasan` dari DB lokal (skema v2); `@Primary` bila `kantin.siswa.sumber=lokal` (default) |
| `BukuKasPort` | `BukuKasDemo` | admin-be Buku Kas | Entri **SUKSES** (referensi tiruan) |
| `NotifikasiPort` | `NotifikasiDemo` | mobile-be push | **TERKIRIM** (log ringkasan) |
| `StatusSiswaPort` | `StatusSiswaDemo` | admin-be status siswa | Siswa **aktif**; blokir = log |
| `UidSiswaPort` | `UidSiswaDemo` | admin-be anti-tabrakan UID | UID **tidak** dipakai siswa |
| `AktivasiModulPort` | `AktivasiModulDemo` | internal-be aktivasi/fee | Modul **aktif** (`diketahui=true`) |

- Semua adapter demo `@Primary` — **jangan** aktifkan bersamaan dengan adapter
  REST nyata (`kantin.integrasi.*.enabled=true`) → dua `@Primary` ambigu.
- `KartuLookupLokal` (`@Primary`, `kantin.siswa.sumber=lokal`, `matchIfMissing=true`)
  dan `KartuLookupRestClient` (`kantin.integrasi.kartu-lookup.enabled=true`)
  sama-sama `@Primary` — **jangan** aktifkan bersamaan (ambigu). Pilih satu sumber.
- Identitas **siswa** dibaca lokal dari `kartu` + `siswa` + `pembatasan` lewat
  `KartuLookupLokal`; lookup ke admin-be tinggal menunggu Q7.
- Status blokir tetap dibaca **tanpa cache** lewat `KontrolKartuService` (PRD §11.11).

---

## 10. Untuk tim FE (kantin-fe)

- Base URL: `http://localhost:8082`, login: `POST /api/v1/auth/login` (shim dev).
- Akun dev (username → role): lihat tabel di §4. Ringkasnya `kasir1` & `kasir2`
  (sekolah 1), `kasir10` & `kasir20` (sekolah 10), plus `pengelola1`, `tu1`,
  `bendahara1`, `admin1`, dan pasangan `…10`. Password **diverifikasi**:
  `Kantin#2026` — bukan lagi "password apa saja diterima".
- Data seed: 24 menu, 4 titik kasir, 19 kartu siswa siap tap (mis. UID
  `04A1B2C3D4` untuk siswa 101 di sekolah 1, `DA100001` untuk siswa 1001 di
  sekolah 10).

> Terkait: [`docs/uji-beban-tap.md`](uji-beban-tap.md) (profil `loadtest`),
> [`architecture/OPEN-QUESTIONS.md`](../architecture/OPEN-QUESTIONS.md) (Q7).
