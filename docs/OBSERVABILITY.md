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
