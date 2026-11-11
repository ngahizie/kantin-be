# 📖 GLOSSARY.md — Glosarium Istilah Kantin SKOOLIA

> Gunakan istilah yang sama di kode, dokumen, dan obrolan tim. Jangan menerjemahkan istilah domain.

| Istilah | Arti |
|---|---|
| **Saldo siswa** | Dana titipan orang tua milik siswa, tercatat di ledger. Bukan pendapatan sekolah. |
| **Saldo Kartu Tamu** | Saldo yang terikat ke **nomor kartu** (bukan orang), untuk guru/staf/tamu. |
| **Kartu Tamu** | Kartu RFID milik kantin bernomor (mis. `KT-012`) untuk non-siswa; diisi tunai di TU. |
| **Petugas kantin** | Staf yang mengoperasikan kasir. Role baru SKOOLIA. |
| **Pengelola kantin** | Staf yang mengelola menu, harga, stok, barang masuk. Role baru SKOOLIA. |
| **Titik kasir** | Satu perangkat kasir (tablet/laptop + reader). Satu kantin bisa punya lebih dari satu. |
| **Sesi kasir** | Periode transaksi satu titik kasir dalam satu hari, diakhiri tutup kasir. |
| **Tutup kasir** | Aksi menutup sesi; total bersih diposting ke Buku Kas. Auto pada jam konfigurasi (default 23:59). |
| **Mutasi** | Satu baris pencatatan perubahan saldo **atau** stok (append-only). |
| **Ledger** | Kumpulan mutasi; sumber kebenaran saldo/stok. Append-only. |
| **Refund transaksi** | Pembatalan transaksi oleh petugas pada sesi yang masih terbuka; wajib kode struk. Status internal tetap `VOID`. |
| **HPP** | Harga Pokok Penjualan — biaya perolehan per unit item terjual. Metode: rata-rata tertimbang. |
| **HPP snapshot** | HPP yang berlaku **saat transaksi**, disimpan agar laba lama tidak berubah. |
| **Stok opname** | Penghitungan fisik stok untuk menyesuaikan stok sistem. Selisih wajib beralasan. |
| **Barang masuk** | Pencatatan pembelian stok dari pemasok; menaikkan stok & memperbarui HPP. |
| **Barang masuk pembalik** | Koreksi barang masuk yang salah (bukan edit/hapus). |
| **Closed-loop** | Saldo hanya bisa dibelanjakan di kantin sekolah yang sama; tak bisa tarik/transfer. |
| **Top-up** | Penambahan saldo. Online (PG, fee ditanggung ortu) atau tunai (di TU). |
| **Refund** | Pengembalian sisa saldo saat siswa lulus/pindah atau Kartu Tamu dikembalikan. |
| **Limit harian** | Batas belanja per hari per siswa; reset 00:00 waktu lokal sekolah. |
| **Blokir item/kategori** | Larangan membeli item/kategori tertentu oleh orang tua. |
| **Blokir kartu** | Menonaktifkan kartu (ortu/admin/TU). **Berlaku instan**, diperiksa tiap tap, tanpa cache TTL. |
| **Idempotency key** | ID unik dibuat klien kasir agar tap ganda/retry tidak memotong saldo dua kali. |
| **Tenant scoping** | Setiap query dibatasi ke sekolah milik user; data sekolah lain → **404**. |
| **Buku Kas** | Catatan keuangan sekolah di admin-be. Kantin posting pemasukan/pengeluaran ke sini. |
| **SekolahBalance** | Saldo sekolah tempat dana fisik top-up mengendap. |
| **RBAC** | Role-Based Access Control; dicek di backend tiap endpoint. |
| **ADR** | Architecture Decision Record — catatan keputusan arsitektur. Lihat [`adr/`](./adr/). |
| **DoD** | Definition of Done — syarat sebuah tugas dianggap selesai. |

## Padanan istilah teknis (dokumen → kode)

Dokumen memakai istilah Indonesia di bawah ini. **Nama kelas, field, config, dan
endpoint di kode tidak diterjemahkan** — kolom kanan hanya untuk memetakan kembali.

| Istilah di dokumen | Padanan di kode / istilah asing |
|---|---|
| **gagal-terbuka** | *fail-open* — dependensi bermasalah ⇒ layanan tetap jalan (mis. rate limit dilewati). Config: `KANTIN_RATE_LIMIT_FAIL_CLOSED=false`. |
| **gagal-tertutup** | *fail-closed* — dependensi bermasalah ⇒ permintaan ditolak (401/503). |
| **isi permintaan** | *payload* — badan data yang dikirim pada request webhook/API. |
| **pembungkus** | *wrapper* — skrip/kelas yang membungkus perintah lain (mis. `mvnw`). |
| **sebisanya** | *best-effort* — dicoba, tetapi kegagalannya tidak membatalkan proses utama. |
| **ditulis langsung di kode** | *hard-coded* — nilai tetap di kode, bukan dari konfigurasi/environment. |
| **usang** | *deprecated* — masih dilayani demi kompatibilitas, tidak dipakai untuk fitur baru. |
| **solusi sementara** | *workaround* — perbaikan sementara sebelum akar masalah dibereskan. |
