-- V31 — FK "rujukan maju" dijadikan DEFERRABLE INITIALLY DEFERRED.
--
-- Kenapa: alur tap kasir menulis baris stok (riwayat_stok) LEBIH DULU, lalu
-- menyimpan transaksi induknya di akhir transaksi DB yang sama; begitu pula
-- posting buku kas menulis jurnal_buku_kas sebelum baris mutasi_saldo-nya
-- di-flush. Karena V28 memasang FK biasa (diperiksa saat INSERT), PostgreSQL
-- menolak dengan "violates foreign key constraint" sehingga penjualan gagal
-- meski datanya konsisten di akhir transaksi.
--
-- DEFERRABLE INITIALLY DEFERRED memindahkan pemeriksaan ke COMMIT: integritas
-- tetap dijaga, urutan tulis tidak lagi jadi soal. Tidak ada kolom/tabel baru.

ALTER TABLE riwayat_stok
    DROP CONSTRAINT IF EXISTS fk_riwayat_stok_transaksi;
ALTER TABLE riwayat_stok
    ADD CONSTRAINT fk_riwayat_stok_transaksi
    FOREIGN KEY (transaksi_id) REFERENCES transaksi (id) ON DELETE SET NULL
    DEFERRABLE INITIALLY DEFERRED;

-- jurnal_buku_kas.mutasi_id bersifat POLIMORFIK: untuk entitas stok ia menunjuk
-- riwayat_stok(id), untuk entitas saldo ia menunjuk mutasi_saldo(id). Karena satu
-- kolom melayani dua induk, FK tunggal ke mutasi_saldo selalu salah untuk separuh
-- baris (posting barang masuk gagal). Integritasnya dijaga di service + laporan
-- rekonsiliasi, jadi constraint-nya dilepas.
ALTER TABLE jurnal_buku_kas
    DROP CONSTRAINT IF EXISTS fk_jurnal_buku_kas_mutasi;

ALTER TABLE mutasi_saldo
    DROP CONSTRAINT IF EXISTS fk_mutasi_saldo_transaksi;
ALTER TABLE mutasi_saldo
    ADD CONSTRAINT fk_mutasi_saldo_transaksi
    FOREIGN KEY (transaksi_id) REFERENCES transaksi (id) ON DELETE SET NULL
    DEFERRABLE INITIALLY DEFERRED;
