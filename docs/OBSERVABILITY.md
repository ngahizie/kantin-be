# Observability — Metrik & SLO (issue #146)

Dokumen ini menjelaskan metrik yang diekspos `kantin-be` dan cara memantau
**SLO tap p95 ≤ 1 detik** (PRD §12).

## 1. Ringkas

| Hal | Nilai |
|---|---|
| Endpoint scrape | `GET /actuator/prometheus` |
| Registry | Micrometer + `micrometer-registry-prometheus` |
| Format | Prometheus text exposition |
| Autentikasi | **tertutup** secara default (`kantin.metrics.public=false`); set `KANTIN_METRICS_PUBLIC=true` untuk membuka scrape tanpa JWT |
| Health | `GET /actuator/health` (+ `/liveness`, `/readiness` bila probes aktif) |

## 2. Metrik tap (SLO utama)

Meter: **`kantin_tap_duration_seconds`** (timer + histogram SLO).

Setiap tap kasir direkam di `TapService.tap()` dengan tag `outcome`:

| `outcome` | Arti |
|---|---|
| `sukses` | tap selesai normal |
| `menunggu_konfirmasi` | tap butuh konfirmasi petugas (PRD §6.1) |
| `gagal` | validasi/konflik (saldo kurang, kartu terkunci, dst.) |

SLO bucket yang dipasang: **250 ms, 500 ms, 1 dtk**. Ambang **1 dtk** adalah
batas SLO (PRD §12) — pelanggaran = tap yang durasinya > 1 detik.

### Contoh query PromQL

```promql
# p95 durasi tap (jalur sukses) — harus <= 1 dtk
histogram_quantile(
  0.95,
  sum by (le) (rate(kantin_tap_duration_seconds_bucket{outcome="sukses"}[5m]))
)

# rasio tap sukses yang melewati ambang SLO (bucket +Inf - le=1)
1 - (
  sum(rate(kantin_tap_duration_seconds_bucket{outcome="sukses",le="1.0"}[5m]))
  / sum(rate(kantin_tap_duration_seconds_count{outcome="sukses"}[5m]))
)

# laju tap gagal
sum(rate(kantin_tap_duration_seconds_count{outcome="gagal"}[5m]))
```

### Contoh alert (Prometheus rule)

```yaml
groups:
  - name: kantin-slo
    rules:
      - alert: TapP95MelebihiSLO
        expr: |
          histogram_quantile(
            0.95,
            sum by (le) (rate(kantin_tap_duration_seconds_bucket{outcome="sukses"}[5m]))
          ) > 1
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Tap p95 > 1 dtk selama 10 menit (langgar PRD §12)"
```

## 3. Metrik HTTP otomatis

Spring Boot juga mengekspos `http_server_requests_seconds_*`. Histogram
percentile diaktifkan (`management.distributions.percentiles-histogram.http.server.requests=true`)
sehingga latensi endpoint (termasuk `POST /api/kasir/tap`) dapat dihitung p95
tanpa instrumentasi tambahan.

## 4. Metrik Jalur Fail-Open (issue #439)

Beberapa kontrol pengaman beroperasi **fail-open secara sengaja** sesuai keputusan pilot (`docs/checklist-pra-rilis.md` §D & `architecture/SECURITY.md` §7.1) agar gangguan Redis atau integrasi tidak melumpuhkan kasir. Untuk mencegah kondisi bypass berlangsung tanpa diketahui (OWASP A10:2025, NIST DE.CM), kelas `MetrikFailOpen` mengekspos dua counter ber-kardinalitas terbatas (tanpa PII/ID siswa):

| Meter Prometheus | Tag & Nilai Dikenal | Sumber |
|---|---|---|
| `ratelimit_bypass_total` | `reason="redis_error"` \| `reason="disabled"` | `RateLimiterRedis` (Redis gagal saat fail-open) & `RateLimitFilter` (`enabled=false`) |
| `integrasi_fail_open_total` | `adapter="uid_siswa"` \| `adapter="aktivasi_modul"` \| `adapter="aktivasi_cache"` \| `adapter="token_blacklist"` | `UidSiswaRestClient`, `AktivasiModulRestClient`, `AktivasiModulFilter`, `TokenBlacklistRedis` |

Nilai label tak dikenal dinormalisasi menjadi `lainnya` agar kardinalitas tetap terjaga.

### Query PromQL & Aturan Alert (Prometheus Rule)

```promql
# Laju request yang melewati rate limit selama 5 menit terakhir
sum by (reason) (increase(ratelimit_bypass_total[5m]))

# Kejadian fail-open pada adapter integrasi & cache Redis selama 5 menit terakhir
sum by (adapter) (increase(integrasi_fail_open_total[5m]))
```

```yaml
groups:
  - name: kantin-fail-open
    rules:
      - alert: RateLimitBypassAktif
        expr: sum(increase(ratelimit_bypass_total{reason="redis_error"}[5m])) > 0
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Rate limit berjalan fail-open (Redis error) > 5 menit (issue #439)"
          description: "Redis tidak terjangkau atau timeout sehingga request masuk ke PostgreSQL tanpa pembatas laju. Periksa kesehatan Redis & pool DB."

      - alert: RateLimitDinonaktifkan
        expr: sum(increase(ratelimit_bypass_total{reason="disabled"}[5m])) > 0
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "KANTIN_RATE_LIMIT_ENABLED=false terdeteksi di traffic API"
          description: "Rate limit HTTP nonaktif; di lingkungan produksi hal ini melanggar checklist pra-rilis §D."

      - alert: IntegrasiFailOpenAktif
        expr: sum by (adapter) (increase(integrasi_fail_open_total[5m])) > 0
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Adapter/cache {{ $labels.adapter }} mengalami fail-open > 5 menit"
          description: "Kegagalan koneksi ke layanan eksternal atau Redis memicu fallback fail-open. Periksa konektivitas adapter terkait."
```

## 5. Konfigurasi

| Properti | Default | Fungsi |
|---|---|---|
| `management.endpoints.web.exposure.include` | `health,info,prometheus` | endpoint yang diekspos |
| `management.endpoint.health.probes.enabled` | `true` | `/liveness`, `/readiness` |
| `management.metrics.tags.application` | nama aplikasi | tag global tiap deret |
| `management.distributions.percentiles-histogram.http.server.requests` | `true` | histogram latensi HTTP |
| `kantin.metrics.public` (`KANTIN_METRICS_PUBLIC`) | `false` | `/actuator/prometheus` butuh token; set `true` untuk scrape tanpa JWT |

## 6. Kolam koneksi database (issue #435)
Micrometer otomatis mengekspos metrik Hikari. Yang relevan untuk mendeteksi
koneksi habis:

| Metrik | Arti |
|---|---|
| `hikaricp_connections_pending` | jumlah thread yang menunggu koneksi |
| `hikaricp_connections_active` | koneksi yang sedang dipakai |
| `hikaricp_connections_timeout_total` | percobaan ambil koneksi yang gagal |
Contoh alert:
```yaml
- alert: AntreanKoneksiDBPenuh
  expr: hikaricp_connections_pending{application="scholia-kantin"} > 5
  for: 2m
  labels:
    severity: warning
  annotations:
    summary: "Banyak request menunggu koneksi DB — cek query lambat / lock"
```
Bila alert ini bunyi, periksa query yang berjalan lama dan transaksi yang
menunggu `FOR UPDATE`. Batas waktunya diatur lewat `KANTIN_DB_STATEMENT_TIMEOUT`,
`KANTIN_DB_LOCK_TIMEOUT`, dan `KANTIN_DB_IDLE_TX_TIMEOUT` (transaksi yang
ditinggal idle diputus, issue #452).

### 6b. Metrik database sibuk (issue #452)

`hikaricp_connections_pending` hanya menunjukkan **antrean**; ia tidak bisa
membedakan "lambat" dari "gagal". Karena itu setiap respons 503 dari jalur
database juga dicatat sebagai counter:

| Metrik | Label `jenis` | Arti |
|---|---|---|
| `db_busy_total` | `query_timeout` | statement melewati `statement_timeout` (SQLState 57014) |
| `db_busy_total` | `lock` | menunggu kunci melewati `lock_timeout` (55P03) |
| `db_busy_total` | `deadlock` | deadlock PostgreSQL (40P01) |
| `db_busy_total` | `serialisasi` | serialization failure (40001) |
| `db_busy_total` | `koneksi` | kolam penuh **atau** DB tidak bisa dihubungi |
| `db_busy_total` | `lainnya` | kondisi transien lain (termasuk kapasitas ekspor penuh) |

Nilai label tak dikenal dinormalisasi ke `lainnya` agar kardinalitas terjaga.

```promql
# Laju 503 per jenis selama 5 menit terakhir
sum by (jenis) (increase(db_busy_total[5m]))
```

```yaml
- alert: DatabaseSibukMeningkat
  expr: sum(increase(db_busy_total[5m])) > 5
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "Request dijawab 503 karena database sibuk (issue #452)"
    description: "Lihat label jenis: query_timeout/lock = lambat atau kontensi; deadlock/serialisasi = logika transaksi; koneksi = kolam atau DB bermasalah."

- alert: DatabaseKoneksiBermasalah
  expr: sum(increase(db_busy_total{jenis="koneksi"}[5m])) > 0
  for: 2m
  labels:
    severity: critical
  annotations:
    summary: "Kolam koneksi habis atau DB tidak terjangkau"
    description: "Cek hikaricp_connections_pending, kesehatan DB, dan kredensial/TLS."
```

Setelah batas waktu aktif, kondisi berikut dijawab **503 + header `Retry-After`**
(bukan 500 generik). Semuanya aman diulang karena tap bersifat idempoten:

| Kondisi | Exception | Label `db_busy_total` |
|---|---|---|
| Query terlalu lama | `QueryTimeoutException` | `query_timeout` |
| Menunggu kunci terlalu lama | `CannotAcquireLockException` | `lock` |
| Deadlock | `DeadlockLoserDataAccessException` | `deadlock` |
| Serialization failure | `CannotSerializeTransactionException` | `serialisasi` |
| Kolam penuh / DB tidak terjangkau | `CannotGetJdbcConnectionException` | `koneksi` |
| Kapasitas ekspor penuh | `ServerBusyException` | `lainnya` |

Log server menulis `Database sibuk (<NamaException>): permintaan dikembalikan 503`
tanpa detail SQL. Ekspor laporan memakai `SET LOCAL statement_timeout` sendiri
(`KANTIN_DB_STATEMENT_TIMEOUT_LAPORAN_MS`, default 30 dtk) agar laporan berat
tidak ikut batas 10 dtk milik jalur tap. Migrasi Flyway memakai koneksi
terpisah (`KANTIN_FLYWAY_URL/_USERNAME/_PASSWORD`) agar tidak terpotong batas
aplikasi.

## 7. Scrape config Prometheus

```yaml
scrape_configs:
  - job_name: kantin-be
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['kantin-be:8082']
```

Karena default-nya `KANTIN_METRICS_PUBLIC=false`, tambahkan `authorization`
bearer token pada scrape config. Set `KANTIN_METRICS_PUBLIC=true` hanya bila
endpoint memang boleh di-scrape tanpa autentikasi.
