# Backup & Restore `kantin_db`

> Menutup temuan audit keamanan **NIST CSF 2.0 — RECOVER**: sebelum ini `kantin_db`
> tidak punya backup sama sekali (tanpa dump, tanpa cron/timer). Satu `truncate`
> atau kegagalan disk = data transaksi, saldo, dan ledger hilang permanen.

## Berkas

| Berkas | Fungsi |
|---|---|
| `scripts/backup-kantin-db.sh` | `pg_dump` format custom + rotasi harian |
| `scripts/restore-kantin-db.sh` | Uji restore ke DB terpisah, atau restore sungguhan |
| `scripts/install-backup-timer.sh` | Pemasang timer portabel (mengganti `__ROOT__` sesuai lokasi repo) |
| `scripts/systemd/kantin-backup.service` | Unit oneshot pemanggil script backup |
| `scripts/systemd/kantin-backup.timer` | Jadwal harian 02:30 (dengan `Persistent=true`) |

Dump ditulis ke `tmp/backups/` (sudah di-gitignore — **tidak** ikut ter-commit).

## Pemasangan (sekali, portabel)

```bash
scripts/install-backup-timer.sh
```

Script ini mengganti placeholder `__ROOT__` di unit systemd dengan path repo
saat ini, menyalinnya ke `~/.config/systemd/user/`, lalu mengaktifkan timer.
Tidak perlu sudo, dan **tidak** mengasumsikan lokasi folder repo — jadi bisa
dipakai di mesin siapa pun setelah `git clone`.

Mencopot pemasangan:

```bash
scripts/install-backup-timer.sh --uninstall
```

`Persistent=true` membuat backup tetap dijalankan bila mesin mati saat jadwal.
Timer berjalan selama user session aktif; untuk tetap jalan setelah logout,
pastikan `loginctl show-user $USER -p Linger` bernilai `Linger=yes`.

## Menjalankan manual

```bash
scripts/backup-kantin-db.sh                 # backup + rotasi
KANTIN_BACKUP_KEEP=14 scripts/backup-kantin-db.sh   # simpan 14 hari
systemctl --user start kantin-backup.service        # lewat systemd
```

## Uji restore (aman, tidak menimpa apa pun)

```bash
scripts/restore-kantin-db.sh                # dump terbaru → kantin_restore_test
```

Script membuat ulang `kantin_restore_test`, memulihkan dump, lalu mencetak jumlah
tabel dan baris `siswa` sebagai bukti. **Uji ini wajib dijalankan berkala** —
backup yang tidak pernah diuji restore hanya "harapan".

## Restore sungguhan (menimpa `kantin_db`)

```bash
scripts/restore-kantin-db.sh --target kantin_db --yes
```

Tanpa `--yes`, script menolak menimpa database asli.

## Batasan yang perlu diketahui

1. **Hanya lokal.** Dump ada di disk yang sama dengan database. Disk rusak =
   backup ikut hilang. Langkah berikutnya: salin ke luar mesin (rclone/rsync).
2. **Tanpa enkripsi.** Dump berisi data pribadi (nama siswa, telepon orang tua)
   begitu data nyata masuk. Untuk produksi, enkripsi dump (mis. `age`/GPG) dan
   batasi izin berkas.
3. **Retensi berbasis waktu**, bukan jumlah berkas. Dengan `KEEP=7`, ukuran
   folder dibatasi oleh ukuran DB × 7.
4. **Tidak ada kredensial default** (sejak issue #434). Skrip backup/restore
   berhenti bila `KANTIN_DB_PASSWORD` kosong. Di server, isi lewat environment
   `KANTIN_DB_*`, jangan hardcode.
