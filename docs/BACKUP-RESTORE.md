# Backup & Restore `kantin_db`

> Menutup temuan audit keamanan **NIST CSF 2.0 — RECOVER**: sebelum ini `kantin_db`
> tidak punya backup sama sekali (tanpa dump, tanpa cron/timer). Satu `truncate`
> atau kegagalan disk = data transaksi, saldo, dan ledger hilang permanen.

## Berkas

| Berkas | Fungsi |
|---|---|
| `scripts/backup-kantin-db.sh` | `pg_dump` format custom + rotasi harian |
| `scripts/restore-kantin-db.sh` | Uji restore ke DB terpisah, atau restore sungguhan |
| `scripts/systemd/kantin-backup.service` | Unit oneshot pemanggil script backup |
| `scripts/systemd/kantin-backup.timer` | Jadwal harian 02:30 (dengan `Persistent=true`) |

Dump ditulis ke `tmp/backups/` (sudah di-gitignore — **tidak** ikut ter-commit).

## Pemasangan (sekali)

```bash
cp scripts/systemd/kantin-backup.{service,timer} ~/.config/systemd/user/
systemctl --user daemon-reload
systemctl --user enable --now kantin-backup.timer
systemctl --user list-timers kantin-backup.timer
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
4. **Kredensial default hanya untuk lokal** (`kantin_dev`). Di server, isi lewat
   environment `KANTIN_DB_*`, jangan hardcode.
