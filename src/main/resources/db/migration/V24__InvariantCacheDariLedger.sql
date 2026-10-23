-- ============================================================
-- V24 — Jamin invarian "cache = turunan ledger" di level DB
--       (PRD §11.1, §11.3 · issue #255)
--
-- Masalah: `saldo_cache.saldo` dan `stok_cache.stok` selama ini hanya
-- dijaga oleh service. Seed, migrasi, atau SQL langsung bisa membuat cache
-- DIVERGEN dari ledger-nya tanpa terdeteksi — dan seed yang idempoten
-- (`ON CONFLICT DO NOTHING`) tidak bisa memperbaiki baris yang sudah ada,
-- jadi DB dev yang sudah drift tetap drift.
--
-- Keputusan: ledger adalah sumber kebenaran, cache murni turunannya —
-- ditegakkan trigger di level DB, bukan oleh disiplin pemanggil:
--
--   saldo_cache.saldo = Σ (KREDIT − DEBIT) saldo_ledger  per (sekolah, subjek)
--   stok_cache.stok   = Σ (MASUK − KELUAR) mutasi_stok     per menu
--
-- Trigger dipasang di tabel LEDGER (`AFTER INSERT`), yaitu satu-satunya
-- jalur yang sah mengubah nilai: service, seed, migrasi, dan SQL langsung.
-- Sisi cache TIDAK diberi trigger dengan sengaja — beberapa jalur sah
-- (mis. seed menulis baris cache lebih dulu, dan uji `StokMinimumSinkronIT`
-- mengisi stok langsung untuk menguji sinkronisasi ambang) menulis cache
-- tanpa baris ledger, sehingga trigger di sana akan mengubah makna tulisan
-- tersebut. Perbaikan cache yang sudah ada dilakukan oleh backfill di bawah
-- dan oleh seed (lihat `R__seed_dev.sql`).
-- ============================================================

CREATE OR REPLACE FUNCTION sinkron_saldo_dari_ledger() RETURNS trigger AS $$
BEGIN
    -- GREATEST(..., 0): DB melarang saldo/stok negatif (ck_*_tidak_minus),
    -- jadi ledger yang TIDAK seimbang ditahan di 0, bukan diteruskan jadi
    -- pelanggaran CHECK. Ledger tak seimbang = data tak sah; diperbaiki di
    -- sumbernya, bukan dengan menulis nilai negatif ke cache.
    UPDATE saldo_cache c
       SET saldo      = GREATEST(COALESCE((SELECT sum(CASE WHEN l.arah = 'KREDIT' THEN l.nominal
                                                            ELSE -l.nominal END)
                                            FROM saldo_ledger l
                                           WHERE l.sekolah_id  = NEW.sekolah_id
                                             AND l.subjek_tipe = NEW.subjek_tipe
                                             AND l.subjek_id   = NEW.subjek_id), 0), 0),
           updated_at = now()
     WHERE c.subjek_tipe = NEW.subjek_tipe
       AND c.subjek_id   = NEW.subjek_id
       AND c.sekolah_id  = NEW.sekolah_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sinkron_stok_dari_mutasi() RETURNS trigger AS $$
BEGIN
    UPDATE stok_cache c
       SET stok        = GREATEST(COALESCE((SELECT sum(CASE WHEN m.arah = 'MASUK' THEN m.qty
                                                             ELSE -m.qty END)
                                             FROM mutasi_stok m
                                            WHERE m.menu_id    = NEW.menu_id
                                              AND m.sekolah_id = NEW.sekolah_id), 0), 0),
           updated_at  = now()
     WHERE c.menu_id    = NEW.menu_id
       AND c.sekolah_id = NEW.sekolah_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Setiap baris ledger baru langsung menurunkan cache-nya.
DROP TRIGGER IF EXISTS trg_saldo_ledger_sinkron ON saldo_ledger;
CREATE TRIGGER trg_saldo_ledger_sinkron
    AFTER INSERT ON saldo_ledger
    FOR EACH ROW EXECUTE FUNCTION sinkron_saldo_dari_ledger();

DROP TRIGGER IF EXISTS trg_mutasi_stok_sinkron ON mutasi_stok;
CREATE TRIGGER trg_mutasi_stok_sinkron
    AFTER INSERT ON mutasi_stok
    FOR EACH ROW EXECUTE FUNCTION sinkron_stok_dari_mutasi();

-- Backfill: samakan SEMUA baris cache lama dengan ledger-nya. Inilah yang
-- memperbaiki DB dev yang sudah drift — seed tidak bisa, karena
-- `ON CONFLICT DO NOTHING` tidak menyentuh baris lama.
UPDATE saldo_cache c
   SET saldo      = GREATEST(COALESCE((SELECT sum(CASE WHEN l.arah = 'KREDIT' THEN l.nominal
                                                        ELSE -l.nominal END)
                                        FROM saldo_ledger l
                                       WHERE l.sekolah_id  = c.sekolah_id
                                         AND l.subjek_tipe = c.subjek_tipe
                                         AND l.subjek_id   = c.subjek_id), 0), 0),
       updated_at = now()
 WHERE c.saldo IS DISTINCT FROM COALESCE(
       (SELECT sum(CASE WHEN l.arah = 'KREDIT' THEN l.nominal ELSE -l.nominal END)
          FROM saldo_ledger l
         WHERE l.sekolah_id  = c.sekolah_id
           AND l.subjek_tipe = c.subjek_tipe
           AND l.subjek_id   = c.subjek_id), 0);

UPDATE stok_cache c
   SET stok       = GREATEST(COALESCE((SELECT sum(CASE WHEN m.arah = 'MASUK' THEN m.qty
                                                        ELSE -m.qty END)
                                        FROM mutasi_stok m
                                       WHERE m.menu_id    = c.menu_id
                                         AND m.sekolah_id = c.sekolah_id), 0), 0),
       updated_at = now()
 WHERE c.stok IS DISTINCT FROM COALESCE(
       (SELECT sum(CASE WHEN m.arah = 'MASUK' THEN m.qty ELSE -m.qty END)
          FROM mutasi_stok m
         WHERE m.menu_id    = c.menu_id
           AND m.sekolah_id = c.sekolah_id), 0);

COMMENT ON FUNCTION sinkron_saldo_dari_ledger() IS
    'Jaga saldo_cache.saldo = Σ (KREDIT−DEBIT) saldo_ledger per (sekolah, subjek) — PRD §11.1/§11.3, issue #255. Dipanggil trigger trg_saldo_ledger_sinkron (AFTER INSERT ON saldo_ledger).';

COMMENT ON FUNCTION sinkron_stok_dari_mutasi() IS
    'Jaga stok_cache.stok = Σ (MASUK−KELUAR) mutasi_stok per menu — PRD §11.1/§11.3, issue #255. Dipanggil trigger trg_mutasi_stok_sinkron (AFTER INSERT ON mutasi_stok).';
