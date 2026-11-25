#!/usr/bin/env bash
# ============================================================================
# restore-kantin-db.sh — Uji/eksekusi restore dump kantin_db
# ----------------------------------------------------------------------------
# KENAPA ADA: backup tanpa uji restore hanya "harapan". Script ini membuktikan
# dump benar-benar bisa dipulihkan — ke database TERPISAH (bukan menimpa
# kantin_db), sehingga aman dijalankan kapan saja.
#
# Pakai:
#   scripts/restore-kantin-db.sh                      # uji dump terbaru → kantin_restore_test
#   scripts/restore-kantin-db.sh --file <path.dump>   # uji dump tertentu
#   scripts/restore-kantin-db.sh --target kantin_db   # ⚠️ RESTORE SUNGGUHAN (menimpa!)
#
# Untuk restore sungguhan, wajib pakai --yes:
#   scripts/restore-kantin-db.sh --target kantin_db --yes
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIR="${KANTIN_BACKUP_DIR:-$ROOT/tmp/backups}"

DB_HOST="${KANTIN_DB_HOST:-localhost}"
DB_PORT="${KANTIN_DB_PORT:-5432}"
DB_USER="${KANTIN_DB_USER:-kantin}"
export PGPASSWORD="${KANTIN_DB_PASSWORD:?KANTIN_DB_PASSWORD wajib diisi (tanpa default sejak issue #434)}"

FILE=""
TARGET="kantin_restore_test"
CONFIRM=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --file)   FILE="$2"; shift 2 ;;
        --target) TARGET="$2"; shift 2 ;;
        --yes)    CONFIRM="yes"; shift ;;
        -h|--help)
            sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
            exit 0 ;;
        *) echo "Opsi tidak dikenal: $1" >&2; exit 2 ;;
    esac
done

if [[ -z "$FILE" ]]; then
    FILE="$(find "$DIR" -maxdepth 1 -name '*.dump' -type f -printf '%T@ %p\n' 2>/dev/null \
            | sort -rn | head -1 | cut -d' ' -f2-)"
fi

if [[ -z "$FILE" || ! -f "$FILE" ]]; then
    echo "Tidak ada berkas dump di $DIR" >&2
    exit 1
fi

# Pengaman: menimpa database asli hanya bila diminta eksplisit.
if [[ "$TARGET" != "kantin_restore_test" && "$CONFIRM" != "yes" ]]; then
    echo "Menolak restore ke '$TARGET' tanpa --yes (mencegah data tertimpa)." >&2
    echo "Untuk uji aman, jalankan tanpa --target (memakai kantin_restore_test)." >&2
    exit 1
fi

echo "[$(date '+%F %T')] restore uji: $FILE → $TARGET"

# Database uji selalu dibuat ulang agar hasilnya bersih.
if [[ "$TARGET" == "kantin_restore_test" ]]; then
    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -q \
         -c "drop database if exists $TARGET;" \
         -c "create database $TARGET;"
else
    echo "[$(date '+%F %T')] ⚠️  MENIMPA database '$TARGET' dalam 5 detik (Ctrl+C untuk batal)"
    sleep 5
fi

pg_restore -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" \
           -d "$TARGET" --no-owner --no-privileges --exit-on-error "$FILE"

# Verifikasi: bandingkan jumlah tabel & baris kunci.
TABLES=$(psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$TARGET" -t -A \
         -c "select count(*) from pg_tables where schemaname='public';")
ROWS=$(psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$TARGET" -t -A \
       -c "select count(*) from siswa;" 2>/dev/null || echo "n/a")

echo "[$(date '+%F %T')] restore selesai — tabel: $TABLES, siswa: $ROWS"
if [[ "$TARGET" == "kantin_restore_test" ]]; then
    echo "Verifikasi isi: psql -d $TARGET -c '\\dt'"
fi