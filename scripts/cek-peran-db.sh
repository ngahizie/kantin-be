#!/usr/bin/env bash
# ============================================================================
# cek-peran-db.sh — Buktikan role database kantin-be least-privilege (issue #433)
# ----------------------------------------------------------------------------
# KENAPA ADA: aturan "runtime hanya DML, migrasi hanya DDL" mudah ditulis di
# dokumen tetapi mudah pula tidak sengaja dilanggar (mis. GRANT berlebih, atau
# role yang ternyata superuser). Script ini MEMBUKTIKAN postur role pada
# database nyata — lokal, staging, atau produksi — dengan 6 pemeriksaan.
#
# Yang diperiksa:
#   1. Role runtime BUKAN superuser (rolsuper=false)
#   2. Role runtime tanpa CREATEDB / CREATEROLE / BYPASSRLS
#   3. Role runtime tanpa hak CREATE pada skema public  → jalur DDL tertutup
#   4. Role runtime bisa membaca tabel aplikasi          → DML (SELECT) jalan
#   5. Role runtime TIDAK bisa CREATE TABLE              → DDL ditolak
#   6. Role migrasi (owner) BISA CREATE TABLE            → migrasi bisa jalan
#
# Pakai:
#   DB_USERNAME=kantin_app DB_PASSWORD=... \
#   SPRING_FLYWAY_USER=kantin_owner SPRING_FLYWAY_PASSWORD=... \
#   scripts/cek-peran-db.sh
#
#   # atau dengan variabel eksplisit:
#   KANTIN_DB_HOST=localhost KANTIN_DB_NAME=kantin_db \
#   KANTIN_DB_APP_USER=kantin_app KANTIN_DB_APP_PASSWORD=... \
#   KANTIN_DB_OWNER_USER=kantin_owner KANTIN_DB_OWNER_PASSWORD=... \
#   scripts/cek-peran-db.sh
#
# Butuh `psql` (PostgreSQL client). Exit 0 = semua pemeriksaan lolos.
# ============================================================================
set -uo pipefail

DB_HOST="${KANTIN_DB_HOST:-localhost}"
DB_PORT="${KANTIN_DB_PORT:-5432}"
DB_NAME="${KANTIN_DB_NAME:-kantin_db}"

APP_USER="${KANTIN_DB_APP_USER:-${DB_USERNAME:-kantin_app}}"
APP_PASS="${KANTIN_DB_APP_PASSWORD:-${DB_PASSWORD:-}}"
OWNER_USER="${KANTIN_DB_OWNER_USER:-${SPRING_FLYWAY_USER:-kantin_owner}}"
OWNER_PASS="${KANTIN_DB_OWNER_PASSWORD:-${SPRING_FLYWAY_PASSWORD:-}}"

if [[ -z "$APP_PASS" ]]; then
    echo "❌ DB_PASSWORD / KANTIN_DB_APP_PASSWORD wajib diisi (password role runtime)." >&2
    exit 2
fi
if [[ -z "$OWNER_PASS" ]]; then
    echo "❌ SPRING_FLYWAY_PASSWORD / KANTIN_DB_OWNER_PASSWORD wajib diisi (password role migrasi)." >&2
    exit 2
fi

psql_app() {
    PGPASSWORD="$APP_PASS" psql -h "$DB_HOST" -p "$DB_PORT" -U "$APP_USER" -d "$DB_NAME" \
        -tAq -v ON_ERROR_STOP=1 "$@"
}

psql_owner() {
    PGPASSWORD="$OWNER_PASS" psql -h "$DB_HOST" -p "$DB_PORT" -U "$OWNER_USER" -d "$DB_NAME" \
        -tAq -v ON_ERROR_STOP=1 "$@"
}

GAGAL=0
lolos() { printf '  ✅ %s\n' "$1"; }
gagal() { printf '  ❌ %s\n' "$1"; GAGAL=1; }

echo "=== Cek peran database (issue #433) ==="
echo "host=$DB_HOST db=$DB_NAME runtime=$APP_USER migrasi=$OWNER_USER"
echo

# --- 1 & 2: atribut role runtime -------------------------------------------
ATRIBUT="$(psql_app -c "select rolsuper, rolcreatedb, rolcreaterole, rolbypassrls
                          from pg_roles where rolname = current_user;" 2>/dev/null)"
if [[ -z "$ATRIBUT" ]]; then
    gagal "tidak bisa membaca pg_roles sebagai role runtime '$APP_USER' (kredensial/host?)"
else
    IFS='|' read -r SUPER CREATEDB CREATEROLE BYPASSRLS <<<"$ATRIBUT"
    [[ "$SUPER" == "f" ]] && lolos "role runtime bukan superuser" \
                          || gagal "role runtime SUPERUSER (harus false)"
    [[ "$CREATEDB$CREATEROLE$BYPASSRLS" == "fff" ]] \
        && lolos "role runtime tanpa CREATEDB/CREATEROLE/BYPASSRLS" \
        || gagal "role runtime punya CREATEDB=$CREATEDB CREATEROLE=$CREATEROLE BYPASSRLS=$BYPASSRLS"
fi

# --- 3: hak CREATE pada skema public --------------------------------------
BISA_CREATE="$(psql_app -c "select has_schema_privilege(current_user, 'public', 'CREATE');" 2>/dev/null)"
if [[ "$BISA_CREATE" == "f" ]]; then
    lolos "role runtime TIDAK punya CREATE pada skema public (jalur DDL tertutup)"
elif [[ -z "$BISA_CREATE" ]]; then
    gagal "tidak bisa memeriksa has_schema_privilege()"
else
    gagal "role runtime PUNYA CREATE pada skema public (harus dicabut)"
fi

# --- 4: DML baca benar-benar jalan ----------------------------------------
JUMLAH_TABEL="$(psql_app -c "select count(*) from information_schema.tables
                              where table_schema = 'public';" 2>/dev/null)"
if [[ "${JUMLAH_TABEL:-0}" -gt 0 ]]; then
    lolos "role runtime bisa membaca katalog & tabel public ($JUMLAH_TABEL tabel)"
else
    gagal "role runtime tidak melihat tabel di skema public (migrasi belum jalan?)"
fi

# --- 5: DDL harus DITOLAK untuk role runtime ------------------------------
if psql_app -c "create table __cek_peran_ddl (id int);" >/dev/null 2>&1; then
    gagal "role runtime BISA CREATE TABLE (harus ditolak)"
    psql_app -c "drop table if exists __cek_peran_ddl;" >/dev/null 2>&1 || true
else
    lolos "role runtime DITOLAK saat CREATE TABLE"
fi

# --- 6: role migrasi harus BISA DDL ---------------------------------------
if psql_owner -c "create table __cek_peran_owner (id int);" >/dev/null 2>&1; then
    lolos "role migrasi '$OWNER_USER' bisa CREATE TABLE (DDL untuk Flyway)"
    psql_owner -c "drop table if exists __cek_peran_owner;" >/dev/null 2>&1 || true
else
    gagal "role migrasi '$OWNER_USER' TIDAK bisa CREATE TABLE (Flyway akan gagal)"
fi

# --- 7: role runtime harus berbeda dari role migrasi ----------------------
if [[ "$APP_USER" == "$OWNER_USER" ]]; then
    gagal "role runtime == role migrasi ('$APP_USER'); wajib dipisah"
else
    lolos "role runtime ('$APP_USER') terpisah dari role migrasi ('$OWNER_USER')"
fi

echo
if [[ "$GAGAL" -eq 0 ]]; then
    echo "✅ SEMUA LOLOS — postur role sesuai issue #433."
    exit 0
fi
echo "❌ ADA PEMERIKSAAN GAGAL — lihat docs/database-role.md §5 (perbaikan)."
exit 1
