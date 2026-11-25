# TLS Koneksi PostgreSQL (issue #432)

Panduan singkat **mengapa**, **bagaimana**, dan **cara membuktikan** koneksi
kantin-be → PostgreSQL memakai TLS yang benar.

Temuan asal: **SEC-01** audit OWASP Top 10:2025 (**A04 Cryptographic Failures**),
issue induk #442.

---

## 1. Masalah yang dicegah

`DB_URL` tanpa parameter `sslmode` membuat driver pgjdbc memakai default
**`prefer`**: driver *mencoba* TLS, tetapi bila server tidak menyediakannya
**koneksi tetap jalan dalam plaintext** — tanpa error, tanpa log. Artinya
kredensial DB dan data pribadi siswa (nama, UID kartu, saldo) bisa disadap di
jaringan tanpa ada yang sadar.

`sslmode` yang tersedia di pgjdbc:

| Nilai | Enkripsi | Verifikasi CA | Verifikasi hostname | Dipakai kantin-be? |
|---|---|---|---|---|
| `disable` | ❌ | – | – | hanya dev lokal (profil dev + host loopback) |
| `allow` / `prefer` | opsional | ❌ | ❌ | **ditolak** |
| `require` | ✅ | ❌ | ❌ | **ditolak** (rawan MITM) |
| `verify-ca` | ✅ | ✅ | ❌ | **ditolak** (rawan DNS hijack) |
| `verify-full` | ✅ | ✅ | ✅ | ✅ **wajib** di staging/produksi |

## 2. Penjaga saat start: `PemeriksaTlsDatabase`

Berjalan pada `ApplicationReadyEvent` (fail-closed — lebih baik gagal start
daripada diam-diam plaintext):

| Kondisi | Perilaku |
|---|---|
| `kantin.db.tls.enforce=false` | hanya INFO (dev eksperimen) |
| profil dev (`local`,`test`,`demo`,`loadtest`) **dan** host DB loopback | WARN, start jalan |
| profil dev **tetapi** host DB non-loopback | **divalidasi** (mencegah "produksi lupa ganti profil") |
| profil lain (staging/produksi/apa pun) | `sslmode` **wajib** `verify-full` |
| `sslmode` kosong/`prefer`/`disable`/`require`/`verify-ca` | **start gagal** (`IllegalStateException`) |
| `verify-full` tanpa `sslrootcert` | start jalan + WARN (memakai truststore JVM) |

Properti terkait (`application.properties`):

```properties
kantin.db.tls.enforce=${KANTIN_DB_TLS_ENFORCE:true}
kantin.db.tls.dikecualikan-profil=${KANTIN_DB_TLS_DIKECUALIKAN_PROFIL:local,test,demo,loadtest}
```

Konfigurasi produksi:

```properties
DB_URL=jdbc:postgresql://<host>:5432/kantin_db?stringtype=unspecified&sslmode=verify-full&sslrootcert=/etc/kantin/pg-ca.pem
```

`sslrootcert` diambil dari secret k8s (bukan dari repo). Bila PostgreSQL
memakai sertifikat dari CA publik, `sslrootcert` boleh dikosongkan — verifikasi
memakai truststore default JVM (tetap `verify-full`).

## 3. Membuktikan (butuh Docker)

Semua perintah dijalankan dari root repo. Direktori kerja: `tmp/tls/`.

### 3.1 Siapkan CA + sertifikat server

```bash
mkdir -p tmp/tls && cd tmp/tls

# CA
openssl req -new -x509 -days 3650 -nodes -text -out ca.crt \
  -keyout ca.key -subj "/CN=Kantin Dev CA"

# Sertifikat server (CN/SAN harus cocok dengan host yang dipakai klien)
openssl req -new -nodes -text -out server.csr -keyout server.key \
  -subj "/CN=localhost"
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out server.crt -days 3650 \
  -extfile <(printf "subjectAltName=DNS:localhost,IP:127.0.0.1")

chmod 600 server.key
```

### 3.2 Jalankan PostgreSQL dengan TLS aktif

```bash
docker run -d --name pg-tls -p 55432:5432 \
  -e POSTGRES_DB=kantin_db -e POSTGRES_USER=kantin -e POSTGRES_PASSWORD=uji_tls \
  -v "$PWD:/certs:ro" postgres:18-alpine \
  -c ssl=on \
  -c ssl_cert_file=/certs/server.crt \
  -c ssl_key_file=/certs/server.key
```

### 3.3 Bukti **berhasil** — `verify-full` + CA yang benar

```bash
docker run --rm --network host -v "$PWD:/certs:ro" -e PGPASSWORD=uji_tls \
  postgres:18-alpine \
  psql "host=localhost port=55432 dbname=kantin_db user=kantin \
        sslmode=verify-full sslrootcert=/certs/ca.crt" \
  -c "select ssl, version from pg_stat_ssl where pid = pg_backend_pid();"
```

Harapan: keluaran `ssl = t` (koneksi terenkripsi) dan **tidak ada error**.

### 3.4 Bukti **gagal** — CA salah (fail-closed)

```bash
# CA lain (tidak menandatangani server.crt)
openssl req -new -x509 -days 1 -nodes -text -out ca-palsu.crt -keyout ca-palsu.key \
  -subj "/CN=CA Palsu"

docker run --rm --network host -v "$PWD:/certs:ro" -e PGPASSWORD=uji_tls \
  postgres:18-alpine \
  psql "host=localhost port=55432 dbname=kantin_db user=kantin \
        sslmode=verify-full sslrootcert=/certs/ca-palsu.crt" \
  -c "select 1;"
```

Harapan: **gagal** dengan pesan kurang-lebih
`SSL error: certificate verify failed` — inilah bukti MITM tidak lolos.

### 3.5 Bukti penjaga aplikasi (tanpa perlu DB hidup)

```bash
# (a) profil produksi + sslmode tidak diset → START GAGAL
SPRING_PROFILES_ACTIVE=prod \
DB_URL='jdbc:postgresql://db.internal:5432/kantin_db?stringtype=unspecified' \
./mvnw spring-boot:run
# → IllegalStateException: Koneksi PostgreSQL tidak terenkripsi dengan benar (OWASP A04:2025, issue #432)

# (b) profil produksi + verify-full → lolos penjaga (lanjut ke koneksi DB)
SPRING_PROFILES_ACTIVE=prod \
DB_URL='jdbc:postgresql://localhost:55432/kantin_db?stringtype=unspecified&sslmode=verify-full&sslrootcert=tmp/tls/ca.crt' \
./mvnw spring-boot:run
# → log: "TLS database terverifikasi: sslmode=verify-full ..."
```

### 3.6 Bersihkan

```bash
docker rm -f pg-tls && rm -rf tmp/tls
```

## 4. Uji otomatis (tanpa Docker)

Logika penjaga diuji di
`src/test/java/com/asqi/scholia_kantin_be/config/PemeriksaTlsDatabaseTest.java`
(12 kasus: parsing URL, semua nilai `sslmode` yang ditolak, profil dev vs
produksi, host loopback vs remote, `enforce=false`).

```bash
./mvnw -Dtest=PemeriksaTlsDatabaseTest test
```
