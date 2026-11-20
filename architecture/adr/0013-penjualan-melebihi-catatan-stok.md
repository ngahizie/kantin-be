# ADR-0013 — Penjualan melebihi catatan stok (`PENJUALAN_MINUS`) bila barang masih ada di gudang

- **Status:** Diterima (2026-11-19)
- **Tanggal:** 2026-11-19 · **Tinjauan terakhir:** 2026-11-19
- **Pengusul:** Tim kantin (keputusan pemilik produk, sesi review presentasi 9 Nov 2026 — butir C.1)
- **Terkait:** PRD §6.2, §7.3, §7.4, §11.2; ADR-0003 (ledger append-only + locking); issue C.1 ceklis review

---

## Konteks

PRD §6.2 menyatakan **"saldo dan stok tidak boleh minus dalam kondisi apa pun"**, dan hari ini
aturan itu ditegakkan tiga lapis:

1. `TapValidator` tahap 4 menolak tap bila `stok <= 0` atau `stok < qty`;
2. `LedgerStokService.keluarPenjualan` melempar `ConflictException` bila stok tidak cukup;
3. DB: `CHECK (stok_setelah >= 0)` pada `riwayat_stok` dan `CHECK (stok >= 0)` pada `stok` (nama tabel mengikuti baseline `V28__SkemaV2`).

Masalah operasionalnya: **stok sistem bisa lebih kecil daripada stok fisik**. Barang cadangan
di gudang belum sempat dicatat sebagai barang masuk, atau barang masuk hari itu belum
diinput. Akibatnya kasir menolak pembeli padahal barangnya ada di rak/gudang — pembeli gagal
beli, dan kasir tidak punya jalan keluar selain mengarang transaksi.

Keputusan pemilik produk (butir C.1 ceklis review): **pembeli harus tetap bisa membeli**.
Ini mengubah aturan inti PRD, jadi dicatat sebagai ADR.

## Keputusan

**Penjualan melebihi catatan diizinkan sebagai mutasi bertanda, bukan sebagai pelonggaran
umum.**

1. Jenis mutasi baru `PENJUALAN_MINUS` (arah `KELUAR`) khusus untuk penjualan yang membuat
   `stok_setelah < 0`. Penjualan normal tetap `PENJUALAN` dan tetap wajib `>= 0`.
2. Klien kasir harus menyatakan maksudnya secara eksplisit: `TapRequest.izinkanStokMinus=true`
   (disertai `alasanStokMinus` opsional, maks 200 karakter). Tanpa flag itu, perilaku lama
   berlaku — tap ditolak dengan pesan "habis (stok 0)" / "tidak cukup (sisa N)".
3. Penjagaan DB dilonggarkan **hanya untuk jenis itu**:
   `CHECK (stok_setelah >= 0 OR jenis = 'PENJUALAN_MINUS')`. Semua mutasi lain tetap wajib
   `>= 0`, jadi opname, barang masuk, void, dan pembalik tidak bisa menghasilkan minus.
4. `stok.stok` boleh negatif, karena cache adalah turunan ledger dan minus itu memang
   tercatat. Cache tetap dapat dihitung ulang dari `riwayat_stok` (`hitungUlangDariLedger`),
   dan uji rekonsiliasi memastikan cache tidak menyimpang dari ledger.
5. Setiap penjualan minus **wajib berjejak**: jenis mutasi, `transaksi_id`, aktor (dari konteks
   request), dan `alasan` bila diisi. Opname masuk (`OPNAME_MASUK`) adalah satu-satunya cara
   resmi menutup minus.
6. HPP: bila `stok <= 0`, barang masuk berikutnya memakai **harga beli barang masuk itu**
   sebagai HPP baru (aturan PRD §7.4 yang sudah ada). Penjaga `HppService` yang menolak
   `stokSekarang < 0` dilonggarkan agar barang masuk setelah penjualan minus tidak gagal.
7. **Flag berlaku per transaksi, bukan per item.** Bila kasir menyalakan `izinkanStokMinus`,
   seluruh item pada transaksi itu boleh melewati catatan (hanya item yang memang kurang yang
   menghasilkan `PENJUALAN_MINUS`). Ini disengaja: satu tap = satu keputusan kasir di depan
   pembeli, dan memecah flag per item akan membuat dialog konfirmasi berlapis di meja kasir.

## Alternatif yang ditolak

- **Opname cepat otomatis** (membuat `OPNAME_MASUK` sebesar kekurangan tepat sebelum menjual):
  menutup celah tanpa minus di DB, tetapi **mengarang angka stok** tanpa hitung fisik dan
  membuat selisih gudang tidak pernah terlihat sebagai selisih. Jejak auditnya menyesatkan.
- **Membiarkan tanpa flag/jejak**: kasir bisa menjual apa pun tanpa batas, dan laporan tidak
  bisa memisahkan penjualan normal dari penjualan yang melebihi catatan.
- **Hanya peran PENGELOLA**: tujuan keputusan ini adalah pembeli tetap terlayani; kalau
  pengelola tidak ada di tempat, pembeli kembali gagal beli.

## Konsekuensi

- **Laporan**: laporan stok/inventaris harus menampilkan stok negatif apa adanya dan
  menyediakan hitungan `PENJUALAN_MINUS` per item supaya selisih gudang terlihat
  (menyusul di PR FE + laporan).
- **Opname**: opname masuk tetap jalur penutup; tidak ada perubahan pada alur opname.
- **Uji**: `LedgerStokServiceIT` yang lama ("stok tidak cukup → ConflictException") tetap sah
  untuk jalur tanpa flag; ditambah uji jalur ber-flag (stok jadi negatif, jenis
  `PENJUALAN_MINUS`, lalu barang masuk memakai harga beli baru).
- **PRD**: §6.2 diamendemen dengan pengecualian eksplisit (lihat catatan di dokumen PRD).
