# 0014 — Peran DB least-privilege (owner/app) diterapkan, RLS ditunda

- **Status:** Diterima
- **Tanggal:** 2026-11-26
- **Pengusul:** Tim backend kantin (tindak lanjut audit keamanan PostgreSQL)
- **Terkait:** issue #433 (SEC-02), issue induk #442, [ADR-0010](./0010-topologi-postgresql-db-terpisah-server-sama.md), [ADR-0003](./0003-ledger-append-only-locking.md), `docs/database-role.md`

---

## Konteks

Audit keamanan PostgreSQL (OWASP Top 10:2025 / NIST CSF, 25 Nov 2026) menemukan
bahwa kantin-be:

1. memakai default `DB_USERNAME=postgres` (**superuser**) — bila environment
   lupa diisi di produksi, aplikasi punya hak penuh atas seluruh database;
2. tidak memisahkan role **migrasi** (DDL) dari role **runtime** (DML);
3. tidak memakai `GRANT`/`REVOKE`/RLS sama sekali, sehingga **satu-satunya**
   lapisan isolasi tenant adalah kode aplikasi.

Kode aplikasi sudah tenant-scoped (AGENTS.md §3.4, data sekolah lain → 404),
tetapi pertahanan berlapis di level database belum ada. Temuan ini tercatat
sebagai SEC-02 di issue #442 dan ditindaklanjuti di issue #433.

## Keputusan

1. **Diterapkan:** dua role PostgreSQL terpisah —
   `kantin_owner` (pemilik skema, dipakai Flyway untuk DDL) dan
   `kantin_app` (runtime, hanya `SELECT/INSERT/UPDATE/DELETE` + `USAGE` skema
   dan sequence), plus penjaga `PemeriksaPeranDatabase` yang **menggagalkan
   start** pada profil non-dev bila role runtime superuser/punya hak DDL,
   password kosong, atau role migrasi tidak terpisah.
2. **Ditunda:** Row Level Security (RLS) berbasis
   `current_setting('kantin.sekolah_id')`. Tidak diaktifkan pada iterasi ini.

## Alasan

**Kenapa pemisahan role langsung diterapkan:** murah, tidak mengubah logika
bisnis, dan menutup dampak terburuk (kompromi total DB) menjadi dampak terbatas
(DML pada data yang memang dibutuhkan aplikasi). Semua jalur sudah teruji:
Flyway sebagai owner, aplikasi sebagai app, dan `TRUNCATE`/`CREATE` ditolak.

**Kenapa RLS ditunda:** RLS mengubah **cara setiap transaksi menetapkan
tenant**. Ia hanya benar bila *semua* jalur menulis/membaca data menetapkan
`SET LOCAL kantin.sekolah_id` di transaksi yang sama, termasuk:

- request HTTP (filter tenant),
- penjadwal (`SesiKasirScheduler`, retry posting Buku Kas, auto-tutup jam tutup),
- webhook masuk & endpoint internal (tanpa JWT user, tenant dari payload),
- laporan lintas-sekolah untuk peran platform,
- migrasi & seed data.

Jalur-jalur itu belum punya mekanisme `SET LOCAL` yang teruji. Mengaktifkan RLS
lebih dulu berisiko **memblokir operasi sah** (kasir berhenti melayani, posting
Buku Kas gagal) — kegagalan yang jauh lebih berbahaya bagi kantin daripada
manfaat defense-in-depth yang belum terukur. RLS juga tidak bisa "setengah
aktif": tabel tanpa policy = tidak ada baris yang terlihat.

## Alternatif yang Dipertimbangkan

| Alternatif | Kenapa tidak dipilih |
|---|---|
| Tetap satu role, andalkan kode aplikasi | Persis kondisi yang dinilai SEC-02; tidak ada lapisan kedua |
| Satu role, tapi dicabut hak DDL-nya | Flyway butuh DDL; tanpa role kedua migrasi gagal |
| Aktifkan RLS sekarang dengan policy `USING (sekolah_id = current_setting(...))` | Belum semua jalur transaksi menetapkan tenant; risiko memblokir kasir/posting sah. Perlu desain + uji terpisah |
| RLS hanya pada tabel ledger (`saldo`, `mutasi_saldo`, `stok`, `riwayat_stok`) | Menarik, tetapi jalur ledger justru yang paling banyak dipakai scheduler/webhook tanpa konteks tenant |
| Managed-service superuser saja (tanpa role app) | Justru masalah yang ingin dihilangkan |

## Konsekuensi

**Positif:**
- Jalur runtime tidak bisa `CREATE`/`ALTER`/`DROP`/`TRUNCATE` → dampak
  injection/IDOR terbatas pada DML.
- Salah konfigurasi produksi (mis. lupa `DB_USERNAME`) **gagal saat start**,
  bukan diam-diam berjalan sebagai superuser.
- Postur role bisa dibuktikan kapan saja dengan `scripts/cek-peran-db.sh`.
- Tabel baru otomatis mendapat hak DML lewat `ALTER DEFAULT PRIVILEGES`.

**Negatif / risiko:**
- Operasi yang butuh DDL/`TRUNCATE` (seed dev, restore, perbaikan manual) harus
  memakai `kantin_owner`/role operasi, bukan role aplikasi — didokumentasikan di
  `docs/database-role.md §8`.
- Dua kredensial DB yang harus dikelola (runtime + migrasi) di secret manager.
- Test integrasi (Testcontainers) tetap memakai superuser container; postur role
  dibuktikan oleh script + unit test, bukan oleh IT.
- Tenant isolation **masih** bergantung pada kode aplikasi sampai RLS diterapkan.

## Tindak Lanjut

- [x] Role `kantin_owner`/`kantin_app` + script pembuatan & verifikasi
      (`scripts/peran-db/buat-peran.sql`, `scripts/cek-peran-db.sh`).
- [x] Penjaga `PemeriksaPeranDatabase` + unit test.
- [x] Default `DB_USERNAME` bukan superuser; `docker-compose.yml` membuat kedua
      role otomatis.
- [ ] **Prasyarat RLS** (issue terpisah bila akan dikerjakan):
      - [ ] helper `TenantContext` yang menetapkan `SET LOCAL kantin.sekolah_id`
            per transaksi, dipakai **semua** jalur (HTTP, scheduler, webhook,
            endpoint internal).
      - [ ] jalur lintas-sekolah (laporan platform) punya role/DSN terpisah
            dengan `BYPASSRLS` **atau** policy khusus.
      - [ ] policy + `FORCE ROW LEVEL SECURITY` per tabel ber-`sekolah_id`,
            diuji dengan Testcontainers (termasuk kasus "tenant salah → 0 baris").
      - [ ] uji beban tap ulang (RLS menambah overhead per baris; SLO p95 ≤ 1 dtk).
- [ ] Audit ulang: pastikan tidak ada `GRANT` berlebih di lingkungan produksi
      (jalankan `scripts/cek-peran-db.sh` pada staging/produksi).
