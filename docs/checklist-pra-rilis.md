# ✅ Checklist Pra-Rilis Produksi — kantin-be

> Issue: **#173** · Referensi: PRD §8.2 & §11.10 · `architecture/SECURITY.md` §5.1 & §7
>
> **Tujuan:** memastikan **tidak ada rahasia / konfigurasi / env yang terlewat**
> saat rilis produksi — item-item ini konfigurasi, bukan kode, jadi mudah
> terlewat. Salin checklist ini ke PR rilis / tiket deploy, isi nilainya, lalu
> centang tiap item.

**Cara pakai:**

```bash
cp .env.example .env    # lalu isi nilai <GANTI_...>
# Jalankan daftar periksa di bawah, centang satu per satu.
```

Keterangan penanda:

- 🔴 **Fail-closed** — bila kosong, fitur mati / endpoint balas **503**. **Wajib** diisi.
- 🟡 **Keputusan** — perlu ditetapkan tim (tidak ada nilai default yang benar).
- 🟠 **Terblokir pihak lain** — menunggu kredensial/endpoint tim lain (lihat #164).
- ✅ **Diputuskan** (tinjauan 2026-10-30) — keputusan sudah diambil & didokumentasikan;
  nilai literal tetap diisi di tiket deploy. Rincian: issue #274.

---

## A. Profil & flag dev — WAJIB nonaktif di produksi

- [ ] `SPRING_PROFILES_ACTIVE` **bukan** `local` / `demo` / `loadtest`
- [ ] `KANTIN_DEV_LOGIN_ENABLED=false` (shim login dev)
- [ ] `KANTIN_DEV_LOGIN_PRIVATE_KEY` **kosong**
- [ ] `KANTIN_DEMO_ENABLED=false` (jalur demo E2E)
- [ ] `KANTIN_LOADTEST_ENABLED=false`
- [ ] `KANTIN_OPENAPI_PUBLIC=false` (atau dibatasi di balik JWT/gateway)
- [ ] `KANTIN_METRICS_PUBLIC=false` (atau allowlist scraper Prometheus)
- [ ] Semua `KANTIN_INTEGRASI_*_ENABLED=false` kecuali endpoint nyatanya **sudah siap** (lihat #164)

## B. Rahasia & kunci 🔴

- [ ] `KANTIN_WEBHOOK_SECRET` terisi — acak ≥ 32 byte. Kosong ⇒ `/api/webhook/**` **503**
- [ ] `KANTIN_INTERNAL_SECRET` terisi — acak ≥ 32 byte, **BEDA** dari webhook. Kosong ⇒ `/api/internal/**` **503**
- [ ] `KANTIN_INTERNAL_ENABLED=true` bila admin-be memakai endpoint internal
- [ ] `JWT_ADMIN_PUBLIC_KEY` = kunci **RS256 produksi** admin-be 🟠 (#164)
- [ ] `JWT_MOBILE_PUBLIC_KEY` = kunci **RS256 produksi** mobile-be 🟠 (#164)
- [ ] Password **PostgreSQL / Redis / MinIO** terisi & kuat (bukan default dev)

## C. CORS & akses jaringan ✅

**Perilaku kode yang diverifikasi langsung (2026-10-30):**

| Hal | Perilaku nyata |
|---|---|
| `kantin.cors.allowed-origins` | Daftar origin **eksplisit** dipisah koma. Default dev `http://localhost:5173,http://localhost:3000` — **wajib diganti** di produksi. |
| Allowlist IP webhook/internal | **Kosong = allow-all** — `IpAllowlist.kosong()` membuat pemeriksaan **dilewati sepenuhnya**. Mengosongkan adalah **pilihan sadar**, bukan lupa mengisi. |
| `X-Forwarded-For` | **Hanya** dipercaya bila koneksi datang dari IP di `*-trusted-proxies`; selain itu `remoteAddr` dipakai apa adanya (anti-spoof). |

- [ ] `KANTIN_CORS_ORIGINS` = origin FE produksi, **eksplisit**, **HTTPS**, **TANPA `*`**
      dan tanpa `localhost` ✅ → nilai: `______________________`
      (bila lebih dari satu, pisah koma; wildcard tidak diizinkan saat kredensial aktif)
- [ ] `KANTIN_WEBHOOK_ALLOWED_IPS` — **keputusan: kosong (allow-all)** ✅
      Kontrol utama webhook adalah **HMAC signature + timestamp + idempotency**, bukan
      allowlist. Isi **hanya** bila IP callback-be terbukti benar-benar tetap; allowlist
      yang salah justru memblokir top-up. Catat pilihan ini agar tidak dikira terlewat.
- [ ] `KANTIN_INTERNAL_ALLOWED_IPS` — **keputusan: kosong (allow-all)** ✅
      Sama seperti webhook: signature internal tetap kontrol utama; allowlist bersifat
      defense-in-depth **opsional**.
- [ ] `KANTIN_WEBHOOK_TRUSTED_PROXIES` / `KANTIN_INTERNAL_TRUSTED_PROXIES` /
      `KANTIN_RATE_LIMIT_TRUSTED_PROXIES` — **wajib diisi bila** ada reverse proxy/LB;
      bila **tidak ada** proxy, **biarkan kosong** (jangan isi `*`) ✅
      Alasan: bila ada proxy tetapi daftar ini kosong, seluruh request terlihat berasal dari
      IP proxy → allowlist IP tidak berfungsi dan rate limit memukul semua klien sekaligus.
- [ ] Konfirmasi arsitektur: **ada / tidak ada** reverse proxy di depan app → `__________`

## D. Rate limit — keputusan produksi ✅

**Keputusan tim kantin-be (2026-10-30): `fail-open` untuk pilot.**

Alasan: Redis outage **tidak boleh melumpuhkan kasir**. Saat Redis mati, proteksi
rate-limit hilang sementara, tetapi **JWT + RBAC + validasi bisnis tetap berjalan** — jadi
postur ini tidak membuka akses tanpa autentikasi. `fail-closed` hanya dipakai bila kebijakan
keamanan sekolah mensyaratkannya, dan konsekuensinya harus diterima: Redis mati ⇒ semua
request **429** ⇒ kantin berhenti melayani.

- [ ] `KANTIN_RATE_LIMIT_FAIL_CLOSED=false` (**fail-open**) ✅ — nilai default kode
      (`kantin.rate-limit.fail-closed=${KANTIN_RATE_LIMIT_FAIL_CLOSED:false}`)
- [ ] `KANTIN_RATE_LIMIT_ENABLED=true` ✅
- [ ] Ambang ditinjau & disetujui ✅:
      `KANTIN_RATE_LIMIT_AUTH` = 20/mnt · `KANTIN_RATE_LIMIT_SENSITIVE` = 60/mnt ·
      `KANTIN_RATE_LIMIT_DEFAULT` = 600/mnt · `KANTIN_RATE_LIMIT_WINDOW` = 60 dtk
- [ ] `KANTIN_RATE_LIMIT_TRUSTED_PROXIES` diisi **hanya bila** ada reverse proxy
      (lihat §C; kosong bila tanpa proxy)
- [ ] Catat postur final di tiket deploy: `fail-open` / `fail-closed` → `__________`

> ⚠️ **Jangan menyalin `KANTIN_RATE_LIMIT_ENABLED=false`.** Nilai itu hanya dipakai
> `.github/workflows/uji-beban-nightly.yml` untuk uji beban; di produksi rate limit
> **wajib aktif**.
>
> Perubahan postur dari default **wajib** dicatat di `architecture/SECURITY.md` §7.

## E. Database & migrasi

- [ ] Seluruh migrasi Flyway **V1 … V24** diterapkan (**24 migrasi**). Terakhir
      diverifikasi 2026-10-30 lewat `src/main/resources/db/migration/`; tambah angka ini
      bila ada migrasi baru sebelum rilis.
- [ ] `DB_URL` menunjuk **`kantin_db`** (DB terpisah) & `DB_USERNAME` **bukan** `postgres`
      di produksi — lihat ADR-0010 §Tinjauan Pra-Rilis
- [ ] **Backup** database sebelum deploy
- [ ] Ukuran pool Hikari sesuai kapasitas host — default `KANTIN_DB_POOL_MAX=30`.
      Bukti uji beban 100 tap/dtk: kolam 10 ⇒ antrean koneksi, p95 1,2 dtk;
      kolam 30 ⇒ p95 0,69 dtk (`docs/uji-beban-tap.md` §5.3, issue #180)
- [ ] `KANTIN_TOMCAT_MIN_SPARE` (default 100) sesuai kapasitas host

## F. Observability

- [ ] `/actuator/health` hijau
- [ ] Metrik Prometheus terekspos & ter-scrape (`docs/OBSERVABILITY.md`)
- [ ] Log **tidak** memuat rahasia (token, secret, PII)

## G. Verifikasi pasca-deploy

- [ ] Login admin/TU berhasil
- [ ] Tap kartu kasir berhasil (SLO p95 ≤ 1 dtk — `docs/uji-beban-tap.md`)
- [ ] Webhook top-up online terverifikasi **E2E** dengan secret produksi 🟠 (#164)
- [ ] Endpoint internal (admin-be cek UID Kartu Tamu) berhasil dengan signature sah

## H. Sign-off

- [ ] Tanggal rilis: `____`
- [ ] Penanggung jawab: `____`
- [ ] Catatan / pengecualian: `____`

---

> **Status item terblokir (🟠)** menunggu #164 (kunci RS256 produksi & endpoint
> tim lain). Checklist ini **dapat dipakai sekarang** untuk item non-blokir;
> lengkapi item 🟠 saat kredensial tersedia.
>
> **Keputusan lingkungan (🟡 → ✅, tinjauan 2026-10-30)** — CORS origin eksplisit,
> allowlist webhook/internal kosong (allow-all), `trusted-proxies` hanya bila ada
> reverse proxy, dan postur rate limit **fail-open** untuk pilot — dirinci beserta
> alasannya di **issue #274**. Nilai literal (domain FE, IP, daftar proxy) tetap
> diisi di tiket deploy.
>
> **Catatan cakupan:** checklist ini menguji **konfigurasi**, bukan perilaku
> fungsional. Bukti DoD tingkat rilis (test hijau, cakupan, UAT, SLO) ada di
> [`uat-dan-dod-rilis.md`](uat-dan-dod-rilis.md) §4.
