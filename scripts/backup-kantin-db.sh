#!/usr/bin/env bash
# ============================================================================
# backup-kantin-db.sh — Backup otomatis PostgreSQL kantin_db (pg_dump)
# ----------------------------------------------------------------------------
# KENAPA ADA: sampai audit ini, kantin_db TIDAK punya backup apa pun — tidak
# ada dump, tidak ada cron/systemd timer. Satu kesalahan (truncate/drop) atau
# kegagalan disk = data transaksi & ledger hilang permanen. NIST CSF 2.0
# fungsi RECOVER tidak terpenuhi. Script ini menutup celah itu.
#
# Yang dilakukan:
#   1. pg_dump kantin_db → berkas .dump (custom format, bisa pg_restore)
#   2. Rotasi: simpan N hari terakhir (default 7), sisanya dihapus
#   3. Tulis ringkasan ke log
#
# Pakai:
#   scripts/backup-kantin-db.sh                    # backup + rotasi
#   KANTIN_BACKUP_DIR=/custom/dir scripts/backup-kantin-db.sh
#   KANTIN_BACKUP_KEEP=14 scripts/backup-kantin-db.sh
#
# Penjadwalan (systemd user timer — tidak perlu sudo):
#   systemctl --user enable --now kantin-backup.timer
#   (unit ada di scripts/systemd/)
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIR="${KANTIN_BACKUP_DIR:-$ROOT/tmp/backups}"
KEEP="${KANTIN_BACKUP_KEEP:-7}"

# Kredensial DB — sama dengan application-local.properties (lokal dev).
# Untuk produksi, tim ops mengisi lewat environment, bukan hardcode.
DB_HOST="${KANTIN_DB_HOST:-localhost}"
DB_PORT="${KANTIN_DB_PORT:-5432}"
DB_NAME="${KANTIN_DB_NAME:-kantin_db}"
DB_USER="${KANTIN_DB_USER:-kantin}"
export PGPASSWORD="${KANTIN_DB_PASSWORD:-kantin_dev}"

mkdir -p "$DIR"

TS="$(date +%Y-%m-%d_%H-%M-%S)"
OUT="$DIR/${DB_NAME}_${TS}.dump"

echo "[$(date '+%F %T')] backup mulai → $OUT"
pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" \
        -Fc --no-owner --no-privileges \
        -f "$OUT" "$DB_NAME"

BYTES=$(stat -c%s "$OUT" 2>/dev/null || stat -f%z "$OUT")
SIZE="$(numfmt --to=iec "$BYTES" 2>/dev/null || echo "${BYTES}B")"

# Rotasi: hapus dump lebih tua dari KEEP hari.
DELETED=0
while IFS= read -r old; do
    rm -f "$old"
    DELETED=$((DELETED + 1))
done < <(find "$DIR" -maxdepth 1 -name "${DB_NAME}_*.dump" -type f -mtime +"$KEEP" 2>/dev/null)

echo "[$(date '+%F %T')] backup selesai: $SIZE, rotasi hapus $DELETED berkas (> ${KEEP} hari)"
echo "$TS $SIZE" >> "$DIR/backup.log"