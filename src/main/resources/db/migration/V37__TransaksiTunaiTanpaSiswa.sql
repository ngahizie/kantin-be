-- C.2a: bayar tunai (Kartu Tamu / tanpa kartu) — transaksi TUNAI tidak terikat siswa.
--
-- Kolom `metode_bayar` sudah ada sejak V28 (CONSTRAINT ck_transaksi_metode_bayar
-- membolehkan SALDO|TUNAI), tetapi `siswa_id` masih NOT NULL sehingga transaksi
-- tunai tanpa kartu tidak dapat disimpan. Skema target (docs/skema-v2/sql/
-- 01-skema-baru.sql §12) memang membuat siswa_id nullable dengan CHECK
-- "bayar saldo wajib punya siswa, tunai boleh tanpa siswa" — migrasi ini
-- menyamakan Flyway dengan skema target tersebut.
--
-- Aman untuk data lama: semua baris existing berstatus SALDO (default kolom)
-- sehingga CHECK baru otomatis terpenuhi.

ALTER TABLE public.transaksi ALTER COLUMN siswa_id DROP NOT NULL;

-- Uang tunai diterima kasir: dipakai cetak-ulang struk & rekonsiliasi kas (C.2d).
ALTER TABLE public.transaksi ADD COLUMN uang_diterima bigint;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_transaksi_saldo_wajib_siswa'
    ) THEN
        ALTER TABLE public.transaksi
            ADD CONSTRAINT ck_transaksi_saldo_wajib_siswa
            CHECK (metode_bayar <> 'SALDO' OR siswa_id IS NOT NULL);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_transaksi_tunai_uang'
    ) THEN
        ALTER TABLE public.transaksi
            ADD CONSTRAINT ck_transaksi_tunai_uang
            CHECK (metode_bayar <> 'TUNAI' OR uang_diterima IS NOT NULL);
    END IF;
END $$;

COMMENT ON COLUMN public.transaksi.siswa_id IS 'Siswa pemilik saldo. NULL hanya untuk transaksi metode_bayar=TUNAI (tunai tanpa kartu, C.2a).';
COMMENT ON COLUMN public.transaksi.metode_bayar IS 'SALDO = dipotong dari saldo siswa (wajib siswa_id); TUNAI = uang tunai diterima kasir, boleh tanpa siswa (KEPUTUSAN 13 Nov: Kartu Tamu tanpa prabayar).';
COMMENT ON COLUMN public.transaksi.uang_diterima IS 'Uang tunai yang diterima kasir (kembalian = uang_diterima - total). NULL untuk metode_bayar=SALDO.';