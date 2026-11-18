-- C.2a lanjutan: simpan nama pembeli untuk transaksi tunai tanpa kartu.
--
-- Kasir mengisi nama pembeli di layar POS dan FE sudah mengirim `namaPembeli`,
-- tetapi sebelumnya nilainya dibuang sehingga struk tunai tanpa kartu hanya
-- bisa menampilkan "Tamu (tunai)" dan nama yang diketik hilang tanpa jejak.
--
-- Kolom nullable: hanya diisi untuk TUNAI tanpa kartu (identitas siswa sudah
-- tercatat lewat siswa_id pada pembayaran saldo / tunai dengan kartu).

ALTER TABLE public.transaksi ADD COLUMN nama_pembeli varchar(100);

COMMENT ON COLUMN public.transaksi.nama_pembeli IS 'Nama pembeli yang diketik kasir untuk transaksi TUNAI tanpa kartu (C.2a). NULL untuk transaksi SALDO atau tunai dengan kartu.';