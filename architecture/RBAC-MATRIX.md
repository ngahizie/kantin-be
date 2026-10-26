# Matriks RBAC — kantin-be

> **Sumber kebenaran tunggal** untuk hak akses peran → endpoint.
> Dokumen ini dirujuk oleh `RbacMatriksTest` (`src/test/java/com/asqi/scholia_kantin_be/security/RbacMatriksTest.java`):
> setiap perubahan anotasi `@PerluPeran` **wajib** accompanied perubahan baris di tabel ini,
> kalau tidak CI gagal.

## Legenda peran

| Kode di tabel | `AktorKantin` | Makna |
|---|---|---|
| `kasir` | `PETUGAS_KANTIN` | Petugas kantin (POS) |
| `pengelola` | `PENGELOLA_KANTIN` | Pengelola kantin (katalog, stok) |
| `tu` | `TU_SEKOLAH` | **TU **dan** Bendahara** — `KlaimResolver` memetakan `TU`/`TATA_USAHA`/`BENDAHARA` ke satu aktor ini |
| `admin` | `ADMIN_SEKOLAH` | Admin sekolah |
| `kepsek` | `KEPSEK` | Kepala sekolah — **read-only**, hanya laporan yang PRD §9.5 sebutkan |
| `—` | — | Tanpa `@PerluPeran`; terbuka untuk semua token yang lolos autentikasi |

## Keputusan produk (issue #250)

Audit silang PRD §9.5/§9.6 vs anotasi `@PerluPeran` menemukan **over-grant** di 14 modul
(peran diberi hak yang tidak ada di PRD). Keputusan produk yang dipakai:

| # | Kelompok | Keputusan | Konsekuensi |
|---|---|---|---|
| 1 | Aksi uang — top-up, refund, pindah-saldo, koreksi saldo & koreksi sesi tertutup | **PRD diikuti** | Hanya `tu` (TU + Bendahara) |
| 2 | Aksi kasir — tap, konfirmasi/batal, buka/tutup sesi, void | **PRD diikuti** | Hanya `kasir` |
| 3 | Katalog & kategori (mutasi), Kartu Tamu (CRUD/refund/pindah-saldo), stok (barang masuk & opname) | **PRD diikuti** | Katalog & stok → `pengelola`; Kartu Tamu → `tu` |
| 4 | Titik kasir toggle & kontrol kartu | **PRD diikuti** | `toggle` → `admin`; kontrol kartu → `admin`/`tu` sesuai subjek |
| 5 | Laporan (read-only) | **PRD diikuti** | Kolom *Akses* §9.5 menjadi acuan |

**Pengecualian yang tetap perlu perataan** — `POST /api/kontrol-kartu/blokir` melayani dua
subjek dengan pemilik hak berbeda, jadi `@PerluPeran` tunggal tidak cukup:

- `KARTU_TAMU` → hanya `tu` (§9.4: "TU memblokir" kartu tamu)
- `SISWA` → hanya `admin` (§9.6 "Kontrol Siswa")

Batas per-subjek ditegakkan di dalam `KontrolKartuController#ubahBlokir`.

## Matriks

| Method | Path | Peran | Acuan PRD | Keputusan |
|---|---|---|---|---|
| `POST` | `/api/auth/cabut` | tu, admin | — | di luar keputusan #250 — dipertahankan |
| `GET` | `/api/auth/me` | — | — | di luar keputusan #250 — dipertahankan |
| `GET` | `/api/internal/kartu-tamu/cek-uid` | — | — | di luar keputusan #250 — dipertahankan |
| `GET` | `/api/kartu-tamu/` | tu | §9.4 & §9.6 — *Kartu Tamu: TU, Bendahara (CRUD, blokir, refund)* | PRD diikuti |
| `POST` | `/api/kartu-tamu/` | tu | §9.4 & §9.6 — *Kartu Tamu: TU, Bendahara (CRUD, blokir, refund)* | PRD diikuti |
| `GET` | `/api/kartu-tamu/nomor-berikutnya` | tu | §9.4 & §9.6 — *Kartu Tamu: TU, Bendahara (CRUD, blokir, refund)* | PRD diikuti |
| `POST` | `/api/kartu-tamu/pindah-saldo` | tu | §9.4 & §9.6 — *Kartu Tamu: TU, Bendahara (CRUD, blokir, refund)* | PRD diikuti |
| `POST` | `/api/kartu-tamu/refund` | tu | §9.4 & §9.6 — *Kartu Tamu: TU, Bendahara (CRUD, blokir, refund)* | PRD diikuti |
| `DELETE` | `/api/kartu-tamu/{kartuId}` | tu | §9.4 & §9.6 — *Kartu Tamu: TU, Bendahara (CRUD, blokir, refund)* | PRD diikuti |
| `GET` | `/api/kartu-tamu/{kartuId}` | tu | §9.4 & §9.6 — *Kartu Tamu: TU, Bendahara (CRUD, blokir, refund)* | PRD diikuti |
| `PUT` | `/api/kartu-tamu/{kartuId}` | tu | §9.4 & §9.6 — *Kartu Tamu: TU, Bendahara (CRUD, blokir, refund)* | PRD diikuti |
| `GET` | `/api/kartu-tamu/{kartuId}/riwayat` | tu | §9.4 & §9.6 — *Kartu Tamu: TU, Bendahara (CRUD, blokir, refund)* | PRD diikuti |
| `GET` | `/api/kasir/sesi/aktif` | kasir | §9.6 — *Kasir: Petugas kantin (create, read)* | PRD diikuti |
| `POST` | `/api/kasir/sesi/buka` | kasir | §9.6 — *Kasir: Petugas kantin (create, read)* | PRD diikuti |
| `GET` | `/api/kasir/sesi/{sesiId}` | kasir | §9.6 — *Kasir: Petugas kantin (create, read)* | PRD diikuti |
| `POST` | `/api/kasir/sesi/{sesiId}/posting-buku-kas` | pengelola, tu, admin | §5 (posting buku kas) | di luar keputusan #250 — dipertahankan |
| `GET` | `/api/kasir/sesi/{sesiId}/rekap` | kasir | §9.6 — *Kasir: Petugas kantin (create, read)* | PRD diikuti |
| `POST` | `/api/kasir/sesi/{sesiId}/tutup` | kasir | §9.6 — *Kasir: Petugas kantin (create, read)* | PRD diikuti |
| `POST` | `/api/kasir/tap` | kasir | §9.6 — *Kasir: Petugas kantin (create, read)* | PRD diikuti |
| `GET` | `/api/kasir/tap/menunggu` | kasir | §9.6 — *Kasir: Petugas kantin (create, read)* | PRD diikuti |
| `POST` | `/api/kasir/tap/{pendingId}/batal` | kasir | §9.6 — *Kasir: Petugas kantin (create, read)* | PRD diikuti |
| `POST` | `/api/kasir/tap/{pendingId}/konfirmasi` | kasir | §9.6 — *Kasir: Petugas kantin (create, read)* | PRD diikuti |
| `GET` | `/api/kasir/transaksi` | pengelola, tu, admin | §9.6 — *Kasir: Petugas kantin (create, read)* | di luar keputusan #250 — dipertahankan |
| `GET` | `/api/kasir/transaksi/sesi` | kasir, pengelola, tu, admin | §9.6 — *Kasir: Petugas kantin (create, read)* | di luar keputusan #250 — dipertahankan |
| `GET` | `/api/kasir/transaksi/sesi-tutup` | pengelola, tu, admin | §9.6 — *Kasir: Petugas kantin (create, read)* | di luar keputusan #250 — dipertahankan |
| `POST` | `/api/kasir/transaksi/{transaksiId}/koreksi` | tu | §9.2 & §9.6 — *Refund & Koreksi: Bendahara (create, read)* | PRD diikuti |
| `POST` | `/api/kasir/transaksi/{transaksiId}/void` | kasir | §9.6 — *Kasir: Petugas kantin (create, read)* | PRD diikuti |
| `GET` | `/api/katalog/kategori` | kasir, pengelola, tu, admin | §9.6 (baca katalog untuk POS) | di luar keputusan #250 — dipertahankan |
| `POST` | `/api/katalog/kategori` | pengelola | §9.6 — *Menu & Kategori: Pengelola (CRUD)* | PRD diikuti |
| `DELETE` | `/api/katalog/kategori/{kategoriId}` | pengelola | §9.6 — *Menu & Kategori: Pengelola (CRUD)* | PRD diikuti |
| `PUT` | `/api/katalog/kategori/{kategoriId}` | pengelola | §9.6 — *Menu & Kategori: Pengelola (CRUD)* | PRD diikuti |
| `GET` | `/api/katalog/menu` | kasir, pengelola, tu, admin | §9.6 (baca katalog untuk POS) | di luar keputusan #250 — dipertahankan |
| `POST` | `/api/katalog/menu` | pengelola | §9.6 — *Menu & Kategori: Pengelola (CRUD)* | PRD diikuti |
| `DELETE` | `/api/katalog/menu/{menuId}` | pengelola | §9.6 — *Menu & Kategori: Pengelola (CRUD)* | PRD diikuti |
| `GET` | `/api/katalog/menu/{menuId}` | kasir, pengelola, tu, admin | §9.6 (baca katalog untuk POS) | di luar keputusan #250 — dipertahankan |
| `PUT` | `/api/katalog/menu/{menuId}` | pengelola | §9.6 — *Menu & Kategori: Pengelola (CRUD)* | PRD diikuti |
| `GET` | `/api/konfigurasi/insiden-offline` | pengelola, tu, admin | — | di luar keputusan #250 — dipertahankan |
| `POST` | `/api/konfigurasi/insiden-offline` | kasir, pengelola, tu, admin | — | di luar keputusan #250 — dipertahankan |
| `GET` | `/api/konfigurasi/kebijakan` | pengelola, tu, admin | — | di luar keputusan #250 — dipertahankan |
| `PUT` | `/api/konfigurasi/kebijakan` | admin | — | di luar keputusan #250 — dipertahankan |
| `GET` | `/api/konfigurasi/pos-buku-kas` | kasir, pengelola, tu, admin | — | di luar keputusan #250 — dipertahankan |
| `POST` | `/api/konfigurasi/pos-buku-kas/aktivasi` | pengelola, tu, admin | — | di luar keputusan #250 — dipertahankan |
| `GET` | `/api/konfigurasi/profil` | kasir, pengelola, tu, admin | — | di luar keputusan #250 — dipertahankan |
| `POST` | `/api/kontrol-kartu/blokir` | tu, admin | §9.4 (blokir Kartu Tamu: TU/Bendahara) & §9.6 (kontrol siswa: Admin) | PRD diikuti |
| `POST` | `/api/kontrol-kartu/blokir-item` | admin, tu | §9.6 — *Kontrol Siswa: Admin (update)* | PRD diikuti |
| `PUT` | `/api/kontrol-kartu/limit-harian` | admin, tu | §9.6 — *Kontrol Siswa: Admin (update)* | PRD diikuti |
| `GET` | `/api/kontrol-kartu/{subjekTipe}/{subjekId}` | kasir, pengelola, tu, admin | §9.6 | di luar keputusan #250 — dipertahankan |
| `GET` | `/api/laporan/ekspor` | pengelola, tu, admin, kepsek | §9.5 — *Semua laporan dapat diekspor; akses mengikuti laporan asal* | PRD diikuti |
| `GET` | `/api/laporan/kartu-stok/{menuId}` | pengelola, tu | §9.5 — *Kartu stok per item: Pengelola, Bendahara* | PRD diikuti |
| `GET` | `/api/laporan/kartu-tamu` | tu | §9.5 — *Kartu Tamu: TU, Bendahara* | PRD diikuti |
| `GET` | `/api/laporan/kerugian-stok` | pengelola, tu, kepsek | §9.5 — *Kerugian stok: Pengelola, Bendahara, Kepsek* | PRD diikuti |
| `GET` | `/api/laporan/kerugian-stok/ringkasan` | pengelola, tu, kepsek | §9.5 — *Kerugian stok: Pengelola, Bendahara, Kepsek* | PRD diikuti |
| `GET` | `/api/laporan/pembatalan` | tu, admin | §9.5 — *Pembatalan kasir: Bendahara, Admin* | PRD diikuti |
| `GET` | `/api/laporan/penjualan` | pengelola, tu, admin, kepsek | §9.5 — *Penjualan: Bendahara, Kepsek, Admin, Pengelola* | PRD diikuti |
| `GET` | `/api/laporan/penjualan/item` | pengelola, tu, admin, kepsek | §9.5 — *Penjualan: Bendahara, Kepsek, Admin, Pengelola* | PRD diikuti |
| `GET` | `/api/laporan/penjualan/kasir` | pengelola, tu, admin, kepsek | §9.5 — *Penjualan: Bendahara, Kepsek, Admin, Pengelola* | PRD diikuti |
| `GET` | `/api/laporan/penjualan/kategori` | pengelola, tu, admin, kepsek | §9.5 — *Penjualan: Bendahara, Kepsek, Admin, Pengelola* | PRD diikuti |
| `GET` | `/api/laporan/penjualan/petugas` | pengelola, tu, admin, kepsek | §9.5 — *Penjualan: Bendahara, Kepsek, Admin, Pengelola* | PRD diikuti |
| `GET` | `/api/laporan/penjualan/titik` | pengelola, tu, admin, kepsek | §9.5 — *Penjualan: Bendahara, Kepsek, Admin, Pengelola* | PRD diikuti |
| `GET` | `/api/laporan/per-siswa` | tu, admin | §9.5 — *Per siswa: Bendahara, Admin* | PRD diikuti |
| `GET` | `/api/laporan/rekonsiliasi` | tu, admin, kepsek | §9.5 — *Rekonsiliasi: Bendahara, Kepsek, Admin* | PRD diikuti |
| `GET` | `/api/laporan/saldo-mengendap` | tu, admin, kepsek | §9.5 — *Saldo mengendap: Bendahara, Kepsek, Admin* | PRD diikuti |
| `GET` | `/api/laporan/saldo-mengendap/kartu-tamu` | tu | §9.5 — *Kartu Tamu: TU, Bendahara* | PRD diikuti |
| `GET` | `/api/laporan/saldo-mengendap/siswa` | tu, admin | §9.5 — *Saldo mengendap: Bendahara, Kepsek, Admin* | PRD diikuti |
| `GET` | `/api/laporan/setoran-tu` | tu | §9.5 — *Setoran kas TU: Bendahara* | PRD diikuti |
| `GET` | `/api/laporan/siswa/{siswaId}/riwayat` | tu, admin | §9.5 — *Per siswa: Bendahara, Admin* | PRD diikuti |
| `GET` | `/api/laporan/stok` | pengelola, tu | §9.5 — *Stok: Pengelola, Bendahara* | PRD diikuti |
| `GET` | `/api/pengaturan-kantin/` | kasir, pengelola, tu, admin | — | di luar keputusan #250 — dipertahankan |
| `PUT` | `/api/pengaturan-kantin/` | admin | — | di luar keputusan #250 — dipertahankan |
| `GET` | `/api/pengaturan-kantin/aktivasi-modul` | pengelola, tu, admin | — | di luar keputusan #250 — dipertahankan |
| `GET` | `/api/pengaturan-kantin/titik-kasir` | kasir, pengelola, tu, admin | §9.6 (baca titik kasir) | di luar keputusan #250 — dipertahankan |
| `POST` | `/api/pengaturan-kantin/titik-kasir` | admin | §9.6 — *Pengaturan & Titik Kasir: Admin (CRUD)* | PRD diikuti |
| `DELETE` | `/api/pengaturan-kantin/titik-kasir/{titikId}` | admin | §9.6 — *Pengaturan & Titik Kasir: Admin (CRUD)* | PRD diikuti |
| `GET` | `/api/pengaturan-kantin/titik-kasir/{titikId}` | kasir, pengelola, tu, admin | §9.6 (baca titik kasir) | di luar keputusan #250 — dipertahankan |
| `PUT` | `/api/pengaturan-kantin/titik-kasir/{titikId}` | admin | §9.6 — *Pengaturan & Titik Kasir: Admin (CRUD)* | PRD diikuti |
| `PATCH` | `/api/pengaturan-kantin/titik-kasir/{titikId}/toggle` | admin | §9.6 — *Pengaturan & Titik Kasir: Admin (CRUD)* | PRD diikuti |
| `GET` | `/api/saldo/` | kasir, pengelola, tu, admin | — | di luar keputusan #250 — dipertahankan |
| `GET` | `/api/saldo/koreksi` | tu, kepsek | §9.6 — *Refund & Koreksi: Bendahara (create, read), Kepsek (read)* | PRD diikuti |
| `POST` | `/api/saldo/koreksi` | tu | §9.2 & §9.6 — *Refund & Koreksi: Bendahara (create, read)* | PRD diikuti |
| `POST` | `/api/saldo/pindah-saldo` | tu | §9.3 & §9.6 — *Refund & Koreksi: Bendahara (create, read)* | PRD diikuti |
| `POST` | `/api/saldo/refund` | tu | §9.3 & §9.6 — *Refund & Koreksi: Bendahara (create, read)* | PRD diikuti |
| `GET` | `/api/saldo/refund/kandidat` | tu, kepsek | §9.3 — daftar siswa nonaktif bersisa saldo | PRD diikuti |
| `GET` | `/api/saldo/rekonsiliasi` | pengelola, tu, admin | — | di luar keputusan #250 — dipertahankan |
| `POST` | `/api/saldo/setoran-tu` | tu | §9.2 & §9.5 — *Setoran kas TU: Bendahara* | PRD diikuti |
| `GET` | `/api/saldo/setoran-tu/rekap` | tu | §9.2 & §9.5 — *Setoran kas TU: Bendahara* | PRD diikuti |
| `POST` | `/api/saldo/topup` | tu | §9.2 & §9.6 — *Top-up Tunai: TU, Bendahara (create, read)* | PRD diikuti |
| `GET` | `/api/siswa/` | pengelola, tu, admin, kepsek | — | di luar keputusan #250 — dipertahankan |
| `POST` | `/api/stok/barang-masuk` | pengelola | §9.6 — *Barang Masuk & Stok Opname: Pengelola (create, read), Bendahara (read)* | PRD diikuti |
| `POST` | `/api/stok/barang-masuk-pembalik` | pengelola | §9.6 — *Barang Masuk & Stok Opname: Pengelola (create, read), Bendahara (read)* | PRD diikuti |
| `GET` | `/api/stok/menipis` | kasir, pengelola, tu, admin | §9.6 (read stok) | di luar keputusan #250 — dipertahankan |
| `POST` | `/api/stok/opname` | pengelola | §9.6 — *Barang Masuk & Stok Opname: Pengelola (create, read), Bendahara (read)* | PRD diikuti |
| `POST` | `/api/stok/opname-batch` | pengelola | §9.6 — *Barang Masuk & Stok Opname: Pengelola (create, read), Bendahara (read)* | PRD diikuti |
| `GET` | `/api/stok/riwayat` | kasir, pengelola, tu, admin | §9.6 (read stok) | di luar keputusan #250 — dipertahankan |
| `GET` | `/api/stok/{menuId}` | kasir, pengelola, tu, admin | §9.6 (read stok) | di luar keputusan #250 — dipertahankan |
| `GET` | `/api/stok/{menuId}/rekonsiliasi` | pengelola, tu, admin | §9.6 (read stok) | di luar keputusan #250 — dipertahankan |
| `GET` | `/api/storage/file` | kasir, pengelola, tu, admin | — | di luar keputusan #250 — dipertahankan |
| `POST` | `/api/storage/upload` | pengelola, tu, admin | — | di luar keputusan #250 — dipertahankan |
| `POST` | `/api/webhook/{sumber}` | — | — | di luar keputusan #250 — dipertahankan |

## Catatan

- `GET` katalog, stok, titik kasir, dan `GET /api/kasir/transaksi*` **tidak** diubah —
  semuanya di luar keputusan #250 (baca). Menghapus akses baca katalog/stok akan mematikan
  layar POS (§9.6 "Kasir: Petugas (create, read)").
- **Under-grant yang belum diputuskan**: `GET /api/kasir/transaksi` (daftar transaksi tenant)
  tidak menerima `kasir`, padahal §9.6 menyebut "Petugas (create, read)". Dibiarkan apa
  adanya supaya tidak mengubah perilaku di luar keputusan — perlu keputusan terpisah.
- Ekspor laporan memakai satu endpoint untuk semua jenis; batas `kepsek` ditegakkan lewat
  `JENIS_EKSPOR_KEPSEK` di `LaporanController` (hanya jenis laporan yang §9.5 izinkan).
- Kalau endpoint baru ditambah, tambahkan **satu baris** di tabel ini. `RbacMatriksTest`
  akan gagal bila anotasi dan dokumen berbeda.
