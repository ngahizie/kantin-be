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

## 3. Batas waktu query & kolam koneksi (issue #435)

Kolam berisi 30 koneksi (`KANTIN_DB_POOL_MAX`). Satu query yang menggantung atau
satu transaksi yang menunggu `FOR UPDATE` tanpa batas akan menahan koneksinya;
begitu 30 koneksi habis, tap kasir tidak mendapat koneksi dan semua sekolah
terdampak. Karena itu dua batas dipasang **per koneksi** oleh Hikari:

```properties
spring.datasource.hikari.connection-init-sql=SELECT set_config('statement_timeout', '${KANTIN_DB_STATEMENT_TIMEOUT_MS:5000}', false), set_config('lock_timeout', '${KANTIN_DB_LOCK_TIMEOUT_MS:2000}', false)
```

| Env | Default | Arti |
|---|---|---|
| `KANTIN_DB_STATEMENT_TIMEOUT_MS` | `5000` | query yang berjalan lebih lama dipotong (error, koneksi kembali ke kolam) |
| `KANTIN_DB_LOCK_TIMEOUT_MS` | `2000` | transaksi yang menunggu kunci lebih lama dibatalkan |
| `KANTIN_DB_STATEMENT_TIMEOUT_LAPORAN_MS` | `30000` | batas khusus transaksi ekspor laporan (lihat bawah) |

`set_config(...)` dipakai, bukan `SET a=1; SET b=2` dalam satu baris, karena
Hikari menjalankan satu perintah dan perilaku dua `SET` bergantung pada driver.
Nilai `0` berarti tanpa batas — jangan dipakai di produksi. Verifikasi ada di
`BatasWaktuDatabaseIT` (`current_setting`, query lambat, tunggu kunci, rollback).

**Pengecualian 1 — migrasi.** Flyway memakai kolam yang sama, tetapi
`CREATE INDEX` pada tabel besar bisa melewati batas aplikasi. Batas dimatikan
khusus koneksi Flyway: `spring.flyway.init-sqls=SET statement_timeout = 0`.

**Pengecualian 2 — laporan berat.** `LaporanExportService` menaikkan batasnya
sendiri lewat `SET LOCAL statement_timeout = <KANTIN_DB_STATEMENT_TIMEOUT_LAPORAN_MS>`
di awal transaksi ekspor. `SET LOCAL` hanya berlaku sampai transaksi itu
berakhir, jadi sesi lain di kolam tetap memakai batas global.

**Metrik & alert.** Metrik Hikari tersedia otomatis di `/actuator/prometheus`:
`hikaricp_connections_pending` (permintaan yang menunggu koneksi),
`hikaricp_connections_active`, `hikaricp_connections_acquire_seconds`. Ambang
yang disarankan: `pending > 0` selama 2 menit = kolam mulai jenuh (tanda query
menggantung atau beban naik).

```promql
# permintaan yang antre koneksi
max by (pool) (hikaricp_connections_pending)

# koneksi terpakai vs ukuran kolam
max by (pool) (hikaricp_connections_active) / max by (pool) (hikaricp_connections_max)
```

```yaml
      - alert: KolamKoneksiJenuh
        expr: max by (pool) (hikaricp_connections_pending) > 0
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "Ada tap yang menunggu koneksi DB (kolam jenuh)"
```

## 4. Metrik HTTP otomatis

Spring Boot juga mengekspos `http_server_requests_seconds_*`. Histogram
percentile diaktifkan (`management.distributions.percentiles-histogram.http.server.requests=true`)
sehingga latensi endpoint (termasuk `POST /api/kasir/tap`) dapat dihitung p95
tanpa instrumentasi tambahan.

## 5. Konfigurasi

| Properti | Default | Fungsi |
|---|---|---|
| `management.endpoints.web.exposure.include` | `health,info,prometheus` | endpoint yang diekspos |
| `management.endpoint.health.probes.enabled` | `true` | `/liveness`, `/readiness` |
| `management.metrics.tags.application` | nama aplikasi | tag global tiap deret |
| `management.distributions.percentiles-histogram.http.server.requests` | `true` | histogram latensi HTTP |
| `kantin.metrics.public` (`KANTIN_METRICS_PUBLIC`) | `false` | `/actuator/prometheus` butuh token; set `true` untuk scrape tanpa JWT |

## 6. Scrape config Prometheus

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
