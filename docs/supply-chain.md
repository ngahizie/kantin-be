# Supply Chain — SBOM & Pemindaian Dependency/Image (issue #440)

Kategori audit: **OWASP Top 10:2025 A03 Software Supply Chain Failures**,
NIST CSF **ID.RA-1** (Identify) & **PR.PS-4** (Protect).

> **Tech stack tidak berubah.** Ini hanya penambahan *tooling* pemindaian:
> tidak ada satu pun versi dependency/framework yang diubah.
> `cyclonedx-maven-plugin` adalah plugin build (tidak masuk image runtime) dan
> Trivy berjalan sebagai alat di CI/Jenkins, bukan dependency aplikasi.
> **Spring Boot tetap 4.0.2** sesuai `architecture/AGENTS.md` §2 + ADR-0001
> (parity `admin-be`). Kalau suatu saat versi perlu naik, jalurnya ADR +
> persetujuan atasan — bukan lewat PR ini.

## Apa yang berjalan

| Di mana | Apa | Gagal bila |
|---|---|---|
| `./mvnw verify` (lokal, CI, Jenkins) | SBOM CycloneDX → `target/bom.json` (176 komponen) | plugin error |
| CI job **SBOM & pindai dependency (Trivy)** | `trivy sbom target/bom.json` | ada CVE **HIGH/CRITICAL** yang sudah ada perbaikannya |
| CI job **Verifikasi image produksi** | `trivy image kantin-be:ci` + SBOM image `sbom-image.cdx.json` | idem, untuk paket OS + jar di dalam image |
| Jenkins stage **Pindai Supply Chain** | idem untuk `${IMAGE_NAME}:${IMAGE_TAG}`, **sebelum** `docker push` | idem |
| CI jadwal mingguan (Senin 03:00 UTC) | ulang seluruh gate | idem |

SBOM dipakai sebagai **sumber pindai** (bukan `pom.xml` mentah) supaya yang
diperiksa benar-benar dependency hasil resolusi Maven — termasuk transitif.

## Kenapa `--ignore-unfixed`

Gate hanya gagal untuk CVE yang **sudah ada patch**-nya. Base image
`azul/zulu-openjdk-alpine` dan paket OS di dalamnya rutin membawa CVE tanpa
perbaikan; tanpa `--ignore-unfixed` gate akan merah terus-menerus dan ujungnya
diabaikan orang. CVE tanpa patch tetap **terlihat** di laporan (tabel yang
sama) dan wajib dipantau lewat jadwal mingguan.

## Baseline utang lama (`.trivyignore.yaml`)

Saat gate ini pertama dipakai, dependency dan image **sudah** membawa temuan
HIGH/CRITICAL yang ada patch-nya. Baseline berisi **60 entri**: 59 CVE unik +
1 advisory `GHSA-2m67-wjpj-xhg9`.

Jumlah *baris* temuan lebih besar dari jumlah entri karena satu CVE bisa muncul
di beberapa library. Pada run CI terakhir:

| Pindai | Temuan tersisa | Di-suppress baseline |
|---|---|---|
| dependency (`trivy sbom`) | `Total: 0` | 68 |
| image (`trivy image`) | `Total: 0` | 65 |

Angka itu bukan konstanta — bisa berubah saat Trivy DB diperbarui. Sumber
kebenarannya adalah log job **SBOM & pindai dependency** dan **Verifikasi image
produksi** pada run CI yang bersangkutan, bukan dokumen ini.

Kalau gate dinyalakan tanpa baseline, seluruh pengembangan langsung terblokir
dan gate-nya akan dimatikan orang.

Karena itu temuan lama dicatat di `.trivyignore.yaml`:

- Setiap entri punya `statement` (alasan + nomor issue) dan
  **`expired_at: 2027-12-31`**. Setelah tanggal itu Trivy menolak baseline dan
  gate merah sampai dependency dinaikkan.
- Gate **tetap memblokir CVE baru** sejak sekarang.
- `--show-suppressed` dipakai di CI/Jenkins sehingga utang lama tetap
  **terlihat** di log & ringkasan run, bukan hilang.
- **Keputusan: tech stack tetap** — `architecture/AGENTS.md` §2 mematok
  **Spring Boot 4.0.2** (ADR-0001, parity dengan `admin-be`), dan versi
  **tidak dinaikkan**. Baseline ini adalah cara gate tetap aktif tanpa
  melanggar §2, bukan jalan menuju upgrade.
- Tinjau ulang: setiap rilis. Perpanjangan `expired_at` wajib disertai
  persetujuan tertulis (nama + tanggal) di deskripsi PR — bukan sekadar
  mengubah tanggal di file ini.
- Kalau kelak muncul CVE **baru** yang menuntut perubahan versi, jalurnya
  **ADR** (`architecture/adr/`) + persetujuan atasan, sesuai `AGENTS.md` §3.

Aturan menambah entri: hanya kalau CVE-nya **tidak bisa** diperbaiki sekarang,
wajib ada alasan + `expired_at` + nomor issue. Entri tanpa alasan dicabut saat
review. Kalau bisa diperbaiki, perbaiki — jangan tambahkan ke baseline.

## Di mana hasilnya terlihat

- **Log job**: tabel lengkap Trivy.
- **Ringkasan run/PR**: `GITHUB_STEP_SUMMARY` (tanpa perlu buka log).
- **Artefak CI** (30 hari): `sbom-dependency` (`target/bom.json` +
  `laporan-trivy-dependency.txt`) dan `sbom-image` (`sbom-image.cdx.json` +
  `laporan-trivy-image.txt`).
- **Jenkins**: `archiveArtifacts` → `target/bom.json`, `sbom-image.cdx.json`,
  `laporan-trivy-*.txt`.

## Kalau gate merah

1. **CVE baru pada dependency** → **jangan** langsung menaikkan versi: versi
   dipatok `AGENTS.md` §2 + ADR-0001 (parity `admin-be`). Laporkan ke tim;
   jalurnya ADR + persetujuan atasan.
2. **Belum ada versi aman** → CVE-nya *unfixed*, jadi gate tidak akan merah
   (`--ignore-unfixed`). Pantau lewat jadwal mingguan.
3. **CVE sudah ada patch tetapi tidak bisa dipakai** (mis. butuh Java 26) →
   tambahkan ke `.trivyignore.yaml` **dengan `statement` (CVE, alasan, nomor
   issue) dan `expired_at`**, plus persetujuan atasan bila ini perpanjangan
   baseline (AGENTS.md §2/ADR-0001 mematok versi framework). Entri tanpa
   alasan akan dicabut saat review:

   ```yaml
   vulnerabilities:
     - id: CVE-2026-12345
       statement: "Belum ada rilis yang mendukung Java 25; remediasi di #NNN"
       expired_at: 2027-12-31
   ```

## Menjalankan lokal

```bash
# SBOM
./mvnw -B -ntp -DskipTests org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom

# Pindai dependency (butuh trivy di PATH, atau lewat docker)
trivy sbom target/bom.json --scanners vuln --severity HIGH,CRITICAL \
    --ignore-unfixed --ignorefile .trivyignore.yaml --show-suppressed
docker run --rm -v "$PWD:/src" -w /src aquasec/trivy:0.76.2 \
    sbom --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed \
    --ignorefile /src/.trivyignore.yaml --show-suppressed target/bom.json

# Pindai image
docker build -t kantin-be:lokal .
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:0.76.2 \
    image --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed kantin-be:lokal
```

## Batasan yang diketahui

- **Jenkins stage belum diuji di agent nyata** (tidak ada akses ke Jenkins dari
  mesin pengembangan). Stage ini memakai `aquasec/trivy:0.76.2` lewat `docker
  run`, jadi butuh (a) akses Docker Hub dari agent, (b) socket Docker
  ter-mount. Escape hatch darurat: `TRIVY_SKIP=true` — saat dipakai, Jenkins
  menampilkan stage **Supply Chain DILEWATI** berisi peringatan, jadi
  pelewatan gate tidak pernah senyap.
- `aquasecurity/setup-trivy` dengan `cache: true` hanya meng-cache **biner**
  Trivy. **DB kerentanan tetap diunduh setiap run** (runner CI selalu bersih),
  jadi pemindaian butuh akses internet. Build tanpa internet akan gagal di step
  pemindaian, bukan lolos diam-diam.
- Versi Trivy dipatok (`v0.76.2`) agar hasil reproducible; naikkan berkala
  supaya DB & deteksinya mutakhir. Action GitHub di `ci.yml` juga dipatok ke
  commit SHA (komentar `# vX` menyimpan tag asalnya); workflow lain
  (`uji-beban-nightly`, `it-urutan-acak-nightly`) belum ikut dipatok.
- SBOM memuat dependency runtime aplikasi (`includeTestScope=false`); dependency
  test tidak dipindai karena tidak ikut ke image produksi.
