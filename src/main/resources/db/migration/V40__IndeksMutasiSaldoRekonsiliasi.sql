-- V40 — Indeks komposit untuk agregat rekonsiliasi saldo (#396, SEC-05)
--
-- MutasiSaldoRepository#totalPerArah menghitung SUM(nominal) GROUP BY arah untuk
-- satu sekolah tanpa batas periode. Indeks yang ada hanya (sekolah_id, waktu)
-- dan (sekolah_id, siswa_id, waktu DESC), sehingga agregat all-time memindai
-- seluruh baris tenant. Indeks (sekolah_id, arah, nominal) menutup GROUP BY
-- sekaligus menyediakan kolom yang dijumlahkan di satu struktur indeks.
CREATE INDEX IF NOT EXISTS idx_mutasi_saldo_rekonsiliasi_arah
    ON mutasi_saldo (sekolah_id, arah, nominal);

COMMENT ON INDEX idx_mutasi_saldo_rekonsiliasi_arah IS
    'Mendukung agregat rekonsiliasi all-time per sekolah dan arah (SEC-05, #396).';
