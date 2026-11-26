-- ============================================================================
-- buat-peran.sql — role least-privilege PostgreSQL untuk kantin-be (issue #433)
-- ----------------------------------------------------------------------------
-- Membuat / memperbarui dua role terpisah, lalu memberi hak minimum:
--
--   kantin_owner  → pemilik skema. Dipakai FLYWAY (DDL: CREATE/ALTER/DROP).
--   kantin_app    → runtime aplikasi. HANYA DML (SELECT/INSERT/UPDATE/DELETE).
--
-- Kenapa: sebelumnya aplikasi konek sebagai `postgres` (superuser) dan migrasi
-- memakai kredensial yang sama dengan runtime. Satu jalur injection/IDOR yang
-- lolos = hak penuh atas seluruh database (OWASP A01/A07:2025).
--
-- SIFAT: IDEMPOTEN. Aman dijalankan berulang (role yang ada hanya di-ALTER).
-- BUTUH: psql >= 15 (memakai \getenv) — image postgres:18-alpine sudah memenuhi.
--
-- CARA PAKAI
--   1. Lokal / docker compose: file ini dijalankan OTOMATIS oleh image postgres
--      (docker-entrypoint-initdb.d) saat volume data pertama kali dibuat.
--   2. Database yang SUDAH ADA (staging/produksi):
--        DB_OWNER_PASSWORD=... DB_APP_PASSWORD=... \
--        psql -h <host> -U <superuser> -d kantin_db -v ON_ERROR_STOP=1 \
--             -f scripts/peran-db/buat-peran.sql
--      Butuh role superuser (atau pemilik DB) karena mengubah kepemilikan objek.
--      WAJIB backup dulu: scripts/backup-kantin-db.sh
--   3. Verifikasi hasilnya: scripts/cek-peran-db.sh
--
-- HASIL YANG DIHARAPKAN (diperiksa juga oleh PemeriksaPeranDatabase saat start):
--   * kantin_app bukan superuser, tanpa CREATEDB/CREATEROLE/BYPASSRLS
--   * kantin_app tanpa hak CREATE pada skema public  → jalur DDL tertutup
--   * kantin_app punya SELECT/INSERT/UPDATE/DELETE pada semua tabel (termasuk
--     tabel yang dibuat migrasi berikutnya, lewat ALTER DEFAULT PRIVILEGES)
-- ============================================================================

\set ON_ERROR_STOP on

-- ---------------------------------------------------------------- parameter
\getenv owner_password DB_OWNER_PASSWORD
\getenv app_password DB_APP_PASSWORD

\if :{?owner_password}
\else
  \echo '❌ DB_OWNER_PASSWORD wajib diisi (password role migrasi kantin_owner).'
  \quit 3
\endif

\if :{?app_password}
\else
  \echo '❌ DB_APP_PASSWORD wajib diisi (password role runtime kantin_app).'
  \quit 3
\endif

-- --------------------------------------------------------------- 1. role
-- CREATE ROLE tidak punya IF NOT EXISTS → bentuk dinamis + \gexec.
SELECT format(
         'CREATE ROLE kantin_owner LOGIN PASSWORD %L '
         'NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS',
         :'owner_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'kantin_owner')
\gexec

SELECT format(
         'CREATE ROLE kantin_app LOGIN PASSWORD %L '
         'NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS',
         :'app_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'kantin_app')
\gexec

-- Role yang sudah ada: pastikan atribut & password ikut diperbarui.
ALTER ROLE kantin_owner WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE
    NOREPLICATION NOBYPASSRLS PASSWORD :'owner_password';
ALTER ROLE kantin_app WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE
    NOREPLICATION NOBYPASSRLS PASSWORD :'app_password';

-- ------------------------------------------------- 2. kepemilikan database
-- kantin_owner menjadi pemilik DB & skema public → satu-satunya jalur DDL.
SELECT format('ALTER DATABASE %I OWNER TO kantin_owner', current_database())
\gexec
ALTER SCHEMA public OWNER TO kantin_owner;

-- Objek yang sudah ada (DB lama) dipindahkan ke kantin_owner agar Flyway
-- (yang berjalan sebagai kantin_owner) bisa mengubahnya.
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT c.relkind, c.relname
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relkind IN ('r', 'p', 'S', 'v', 'm', 'f')
    LOOP
        EXECUTE format('ALTER %s public.%I OWNER TO kantin_owner',
            CASE r.relkind
                WHEN 'S' THEN 'SEQUENCE'
                WHEN 'v' THEN 'VIEW'
                WHEN 'm' THEN 'MATERIALIZED VIEW'
                WHEN 'f' THEN 'FOREIGN TABLE'
                ELSE 'TABLE'
            END,
            r.relname);
    END LOOP;

    FOR r IN
        SELECT p.oid::regprocedure AS tanda
        FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public'
    LOOP
        EXECUTE format('ALTER FUNCTION %s OWNER TO kantin_owner', r.tanda);
    END LOOP;
END
$$;

-- ------------------------------------------------------------- 3. hak akses
-- Tutup jalur DDL & akses umum, lalu beri runtime DML saja.
REVOKE ALL ON DATABASE :"DBNAME" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"DBNAME" TO kantin_owner, kantin_app;

REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO kantin_app;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO kantin_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO kantin_app;

-- Tabel/sequence yang dibuat migrasi BERIKUTNYA (dijalankan sebagai
-- kantin_owner) otomatis memberi hak DML ke kantin_app.
ALTER DEFAULT PRIVILEGES FOR ROLE kantin_owner IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO kantin_app;
ALTER DEFAULT PRIVILEGES FOR ROLE kantin_owner IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO kantin_app;

-- ------------------------------------------------------------- 4. ringkasan
\echo ''
\echo '✅ Role siap:'
SELECT rolname AS role,
       rolsuper AS superuser,
       rolcreatedb AS createdb,
       rolcreaterole AS createrole,
       rolbypassrls AS bypassrls
FROM pg_roles
WHERE rolname IN ('kantin_owner', 'kantin_app')
ORDER BY rolname;
