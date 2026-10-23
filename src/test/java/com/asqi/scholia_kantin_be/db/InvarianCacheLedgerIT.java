package com.asqi.scholia_kantin_be.db;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.dto.RingkasanRekonsiliasi;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.repository.SaldoCacheRepository;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import com.asqi.scholia_kantin_be.service.kasir.PerintahMutasiSaldo;
import com.asqi.scholia_kantin_be.service.laporan.LaporanService;
import com.asqi.scholia_kantin_be.service.stok.LedgerStokService;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Invarian <b>"cache = turunan ledger"</b> (PRD §11.1, §11.3) — issue <b>#255</b>.
 *
 * <p>Menjawab dua hal:
 * <ol>
 *   <li><b>Acceptance #5</b> — 1000 transaksi acak lewat service nyata, lalu
 *       rekonsiliasi harus tetap <b>seimbang</b> (selisih 0) dan cache = Σ ledger.</li>
 *   <li><b>Acceptance #3</b> — invarian ditegakkan di <b>level DB</b>: baris
 *       ledger yang ditulis langsung (melewati service) tetap menurunkan cache
 *       dengan benar.</li>
 * </ol>
 *
 * <p>Akibat saldo kurang / stok kurang adalah {@code 409} yang sah — dilewati,
 * karena yang diuji adalah konsistensi setelah mutasi yang BERHASIL.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class InvarianCacheLedgerIT {

    private static final long SEKOLAH_ID = 77L;
    private static final long USER_ID = 42L;

    /**
     * ID khas kelas ini. `saldo_cache` ber-PK (subjek_tipe, subjek_id) — TANPA
     * sekolah_id — sehingga id yang dipakai kelas IT lain (mis. 1000L) sudah
     * menempati baris yang sama dan membuat lookup per-sekolah gagal.
     */
    private static final long SUBJEK_DASAR = 77_001L;
    private static final long MENU_DASAR = 77_001L;

    @Autowired
    private LedgerSaldoService ledgerSaldo;

    @Autowired
    private LedgerStokService ledgerStok;

    @Autowired
    private SaldoCacheRepository saldoCacheRepo;

    @Autowired
    private LaporanService laporan;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager tx;

    @Test
    @DisplayName("#255: 1000 transaksi acak tetap seimbang (cache = Σ ledger)")
    void seribuTransaksiAcakSeimbang() {
        TransactionTemplate t = new TransactionTemplate(tx);
        t.executeWithoutResult(s -> siapkan());

        // Tetap deterministik supaya kegagalan bisa direproduksi.
        Random acak = new Random(2026_10_23L);
        int berhasil = 0;

        for (int i = 0; i < 1000; i++) {
            int pilih = acak.nextInt(4);
            try {
                t.executeWithoutResult(s -> {
                    if (pilih == 0) {
                        ledgerSaldo.kredit(perintahSaldo(SUBJEK_DASAR + acak.nextInt(5), 1000 + acak.nextInt(5000),
                                JenisMutasiSaldo.TOPUP_TUNAI, "INV-" + System.nanoTime()));
                    } else if (pilih == 1) {
                        ledgerSaldo.debit(perintahSaldo(SUBJEK_DASAR + acak.nextInt(5), 1000 + acak.nextInt(3000),
                                JenisMutasiSaldo.PENJUALAN, null));
                    } else if (pilih == 2) {
                        ledgerStok.masukBarang(SEKOLAH_ID, MENU_DASAR + acak.nextInt(3),
                                1 + acak.nextInt(10), 5000L, "BARANG_MASUK",
                                "INV-STOK-" + System.nanoTime(), USER_ID);
                    } else {
                        ledgerStok.keluarPenjualan(SEKOLAH_ID, MENU_DASAR + acak.nextInt(3),
                                1 + acak.nextInt(3), 800_000L + acak.nextInt(5));
                    }
                });
                berhasil++;
            } catch (ConflictException dilewati) {
                // Saldo/stok kurang — sah; yang diuji adalah konsistensi setelah sukses.
            }
        }
        assertThat(berhasil).as("minimal ada mutasi yang berhasil").isPositive();

        // ── Acceptance #1 & #5: rekonsiliasi seimbang ────────────────────
        RingkasanRekonsiliasi hasil = laporan.rekonsiliasi(SEKOLAH_ID, null, null, null);
        assertThat(hasil.selisih()).as("selisih rekonsiliasi").isZero();
        assertThat(hasil.seimbang()).as("seimbang").isTrue();

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM saldo_cache c
                WHERE c.sekolah_id = ? AND c.saldo <> COALESCE((
                    SELECT sum(CASE WHEN l.arah = 'KREDIT' THEN l.nominal ELSE -l.nominal END)
                      FROM saldo_ledger l
                     WHERE l.sekolah_id = c.sekolah_id
                       AND l.subjek_tipe = c.subjek_tipe
                       AND l.subjek_id = c.subjek_id), 0)
                """, Integer.class, SEKOLAH_ID)).as("saldo_cache = Σ ledger").isZero();

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM stok_cache c
                WHERE c.sekolah_id = ? AND c.stok <> COALESCE((
                    SELECT sum(CASE WHEN m.arah = 'MASUK' THEN m.qty ELSE -m.qty END)
                      FROM mutasi_stok m
                     WHERE m.menu_id = c.menu_id
                       AND m.sekolah_id = c.sekolah_id), 0)
                """, Integer.class, SEKOLAH_ID)).as("stok_cache = Σ mutasi_stok").isZero();

        // ── Kartu stok: saldo berjalan berurutan (acceptance #2) ────────
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM mutasi_stok a
                JOIN LATERAL (
                    SELECT b.stok_setelah AS prev FROM mutasi_stok b
                     WHERE b.menu_id = a.menu_id AND (b.waktu, b.id) < (a.waktu, a.id)
                     ORDER BY b.waktu DESC, b.id DESC LIMIT 1) s ON true
                WHERE a.sekolah_id = ?
                  AND a.stok_setelah <> s.prev
                       + CASE WHEN a.arah = 'MASUK' THEN a.qty ELSE -a.qty END
                """, Integer.class, SEKOLAH_ID)).as("stok_setelah berurutan").isZero();
    }

    @Test
    @DisplayName("#255: baris ledger yang ditulis langsung ikut menurunkan cache")
    void ledgerLangsungMenurunkanCache() {
        TransactionTemplate t = new TransactionTemplate(tx);
        t.executeWithoutResult(s -> siapkan());

        long sebelum = jdbc.queryForObject(
                "SELECT saldo FROM saldo_cache WHERE sekolah_id = ? AND subjek_tipe = 'KARTU_TAMU' "
                        + "AND subjek_id = 77001", Long.class, SEKOLAH_ID);

        // Menulis ledger langsung (melewati service) — trigger yang harus menurunkan cache.
        jdbc.update("""
                INSERT INTO saldo_ledger (id, sekolah_id, subjek_tipe, subjek_id, arah, jenis,
                                          nominal, saldo_setelah, referensi_tipe, referensi_id,
                                          keterangan, aktor_id, waktu, created_at)
                VALUES (?, ?, 'KARTU_TAMU', 77001, 'KREDIT', 'TOPUP_TUNAI', 5000, ?, 'TOPUP',
                        'INV-LANGSUNG', 'ledger langsung', ?, now(), now())
                """, 9_770_001L, SEKOLAH_ID, sebelum + 5000, USER_ID);

        assertThat(jdbc.queryForObject(
                "SELECT saldo FROM saldo_cache WHERE sekolah_id = ? AND subjek_tipe = 'KARTU_TAMU' "
                        + "AND subjek_id = 77001", Long.class, SEKOLAH_ID))
                .as("cache turun mengikuti ledger yang ditulis langsung")
                .isEqualTo(sebelum + 5000);
    }

    // ---------------------------------------------------------------- helper

    private void siapkan() {
        jdbc.update("""
                INSERT INTO menu (id, sekolah_id, kategori_id, nama, harga_jual, satuan,
                                  stok_minimum, is_active, created_at, updated_at)
                VALUES (?, ?, NULL, 'Menu Invarian A', 10000, 'PCS', 0, true, now(), now()),
                       (?, ?, NULL, 'Menu Invarian B', 10000, 'PCS', 0, true, now(), now()),
                       (?, ?, NULL, 'Menu Invarian C', 10000, 'PCS', 0, true, now(), now())
                ON CONFLICT DO NOTHING
                """, MENU_DASAR, SEKOLAH_ID, MENU_DASAR + 1, SEKOLAH_ID, MENU_DASAR + 2, SEKOLAH_ID);

        for (int i = 0; i < 5; i++) {
            saldoCacheRepo.pastikanBarisAda(SubjekTipe.KARTU_TAMU.name(), SUBJEK_DASAR + i, SEKOLAH_ID);
        }
    }

    private PerintahMutasiSaldo perintahSaldo(Long subjekId, long nominal,
                                              JenisMutasiSaldo jenis, String idempotencyKey) {
        return PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH_ID)
                .subjekTipe(SubjekTipe.KARTU_TAMU)
                .subjekId(subjekId)
                .jenis(jenis)
                .nominal(nominal)
                .idempotencyKey(idempotencyKey)
                .referensiTipe("TRANSAKSI")
                .aktorId(USER_ID)
                .build();
    }
}
