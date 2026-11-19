-- V39 — Penjualan melebihi catatan stok (ADR-0013)
--
-- Keputusan 19 Nov 2026 (butir C.1 ceklis review): bila stok sistem habis padahal
-- barangnya masih ada di gudang/cadangan, kasir boleh menjual dengan konfirmasi
-- eksplisit. Penjualan seperti itu dicatat sebagai jenis mutasi tersendiri
-- (PENJUALAN_MINUS) supaya laporan bisa memisahkannya dari penjualan normal.
--
-- Penjagaan "stok tidak boleh minus" TETAP berlaku untuk semua jenis mutasi lain:
-- barang masuk, opname, void, dan pembalik tidak bisa menghasilkan stok negatif.

-- Catatan: nama tabel mengikuti baseline V28 (SkemaV2) — riwayat_stok & stok.

ALTER TABLE riwayat_stok DROP CONSTRAINT IF EXISTS ck_riwayat_stok_tidak_minus;

ALTER TABLE riwayat_stok
    ADD CONSTRAINT ck_riwayat_stok_tidak_minus
    CHECK (stok_setelah >= 0 OR jenis = 'PENJUALAN_MINUS');

COMMENT ON CONSTRAINT ck_riwayat_stok_tidak_minus ON riwayat_stok IS
    'Stok minus hanya boleh berasal dari PENJUALAN_MINUS (ADR-0013); jenis lain wajib >= 0.';

-- Cache stok adalah turunan ledger, jadi boleh negatif selama minusnya tercatat.
ALTER TABLE stok DROP CONSTRAINT IF EXISTS ck_stok_tidak_minus;

COMMENT ON COLUMN stok.stok IS
    'Stok berjalan per menu. Boleh negatif HANYA selama ada PENJUALAN_MINUS yang belum ditutup opname (ADR-0013).';

-- Tap pending (konfirmasi manual) menyimpan ulang permintaan kasir; flag jual
-- melebihi catatan harus ikut tersimpan agar konfirmasi tidak gagal validasi stok.
ALTER TABLE transaksi_tertunda
    ADD COLUMN IF NOT EXISTS izinkan_stok_minus BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS alasan_stok_minus VARCHAR(200);

COMMENT ON COLUMN transaksi_tertunda.izinkan_stok_minus IS
    'Kasir mengizinkan jual melebihi catatan stok (ADR-0013); dibawa ke konfirmasi.';
