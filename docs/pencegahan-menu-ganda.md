# Kenapa Nama Menu Tidak Boleh Ganda (Penjelasan untuk Semua Orang)

## Masalahnya

Kalau dua menu punya nama sama di satu kantin, kasir bingung harus memilih yang mana.
Contoh nyata yang sudah terjadi: di kantin sekolah 10 ada dua baris "Jus Jeruk", keduanya
harga sama, karena menu itu ditambahkan lewat layar padahal sudah ada dari data awal.

Sistem sekarang **menerima** nama ganda tanpa protes. Akibatnya:

- Kasir menekan pilihan yang salah, laporan penjualan jadi tidak jelas.
- Data stok dan mutasi tercatat dua kali dengan nama sama, susah dilacak.
- Saat ekspor Excel, muncul dua baris identik yang membingungkan pembina.

## Aturannya

> **Nama menu tidak boleh sama di dalam satu sekolah.
> Antar sekolah boleh sama.**

Alasannya: "Ayam Geprek" yang dijual sekolah 1 dan "Ayam Geprek" yang dijual sekolah 10
adalah dua produk yang berbeda — pemiliknya beda, harganya bisa beda, stoknya beda.
Jadi keduanya sah. Yang tidak sah adalah **dua "Ayam Geprek" di kantin yang sama**.

## Analogi: petugas yang melihat buku tamu

Bayangkan kantin punya buku berisi daftar semua menu.

**Cara 1 — tanpa pemeriksaan (kondisi sekarang):**
Petugas langsung menulis nama apa pun yang diminta. Tidak melihat buku.
Kalau ada yang meminta "Jus Jeruk" padahal sudah ada, tetap ditulis. Dua baris sama.

**Cara 2 — dengan pemeriksaan (aturan baru):**
Sebelum menulis, petugas membuka buku dan bertanya:

1. Nama ini sudah ada di **kantin saya**? → kalau ya, tolak dengan pesan jelas.
2. Kalau belum ada → tulis.

Pesan yang muncul di layar: `Menu 'Jus Jeruk' sudah ada`.

Petugas juga merapikan tulisan lebih dulu: spasi berlebih dibuang, dan huruf besar/kecil
dianggap sama. Jadi "  es   teh manis  ", "Es Teh Manis", dan "ES TEH MANIS"
dihitung sebagai satu nama yang sama.

## Alur yang terjadi di layar

```
Admin klik "Tambah Menu"
        │
        ▼
Isi nama "Jus Jeruk", harga, satuan
        │
        ▼
Sistem rapikan nama        →  "Jus Jeruk"
        │
        ▼
Sistem lihat buku sekolah ini
        │
        ├── Nama belum ada ──► Simpan. Menu baru muncul di daftar.
        │
        └── Nama sudah ada ──► Tolak.
                                 Pesan: "Menu 'Jus Jeruk' sudah ada"
                                 Admin mengganti nama atau membatalkan.
```

Saat mengubah menu yang sudah ada, pemeriksaan tetap jalan — tapi menu itu **tidak
menghitung dirinya sendiri**. Jadi kalau admin hanya memperbaiki ejaan
("Nasi Goreng" → "Nasi Goreng Spesial"), tidak akan ditolak.

## Yang perlu dilakukan admin

| Situasi | Tindakan |
|---|---|
| Nama sudah dipakai, produknya memang sama | Jangan tambah lagi. Cukup ubah harga/stok menu yang ada |
| Nama sudah dipakai, produknya beda | Beri nama yang membedakan: merek + ukuran, mis. `Es Teh Manis 500 ml` |
| Dapat pesan "sudah ada" | Periksa daftar menu dulu, jangan ulangi simpan |

## Batas aturan ini

- **Hanya berlaku untuk input lewat aplikasi.** Kalau ada yang mengubah data langsung
  ke database, pemeriksaan ini tidak terpakai.
- **Tidak mencegah dua orang menyimpan di detik yang sama.** Risiko kecil karena
  yang mengelola katalog hanya admin, tapi secara teknis masih mungkin.
- **Tidak membereskan data lama yang sudah ganda.** Baris "Jus Jeruk" yang sudah ada
  sekarang harus dirapikan manual oleh admin.
- **Nama yang sudah dihapus tidak dihitung.** Kalau "Bakso" dinonaktifkan/hapus,
  nama itu bisa dipakai lagi.

## Kalau nanti mau lebih kuat

Saat data nyata sudah masuk, aturan ini bisa dipindahkan ke **database** supaya tidak
bisa dilewati siapa pun. Caranya: membuat kunci unik untuk pasangan (sekolah, nama).
Tapi itu baru bisa dilakukan **setelah data lama yang ganda dibersihkan** — kalau
dipasang sekarang, pemasangannya justru gagal karena masih ada dua "Jus Jeruk".

Untuk tahap sekarang, pemeriksaan di aplikasi sudah cukup: tidak perlu migrasi data,
tidak perlu bersih-bersih, dan pesan errornya bisa dibaca manusia.