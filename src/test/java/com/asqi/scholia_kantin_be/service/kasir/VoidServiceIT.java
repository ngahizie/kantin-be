package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.dto.TapRequest;
import com.asqi.scholia_kantin_be.dto.TutupSesiRequest;
import com.asqi.scholia_kantin_be.dto.TapResponse;
import com.asqi.scholia_kantin_be.dto.TransaksiRefundResponse;
import com.asqi.scholia_kantin_be.helper.NomorStruk;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.MetodeBayar;
import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.Transaksi;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.stok.LedgerStokService;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.asqi.scholia_kantin_be.support.FixtureInduk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji integrasi void transaksi (PRD §6.3).
 *
 * <p>Menegakkan: saldo &amp; stok kembali, kompensasi lewat <b>mutasi pembalik
 * baru</b> (ledger asli tak diubah), alasan wajib, dan hanya sesi terbuka.
 */
@SpringBootTest(properties = "kantin.siswa.sumber=integrasi")
@Import({TestcontainersConfig.class, TapServiceIT.FakePortConfig.class})
@EnabledIfDockerAvailable
class VoidServiceIT {


    /** Permintaan tutup sesi dengan hitungan laci (C.3). */
    private static TutupSesiRequest tutupKas(long uangFisik, String catatan) {
        TutupSesiRequest r = new TutupSesiRequest();
        r.setUangFisik(uangFisik);
        r.setCatatan(catatan);
        return r;
    }

    private static final long SEKOLAH = 1L;
    private static final long SISWA = 7L;
    private static final long MENU_NASI = 10L;
    private static final long TITIK = 1L;
    private static final String UID = "A1B2C3D4";

    @Autowired
    private TapService tapService;

    @Autowired
    private VoidService voidService;

    @Autowired
    private SesiKasirService sesiKasir;

    @Autowired
    private LedgerSaldoService ledgerSaldo;

    @Autowired
    private LedgerStokService ledgerStok;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    private final IdentitasKantin petugas = IdentitasKantin.builder()
            .userId("555").sekolahId(SEKOLAH).build();

    @BeforeEach
    void bersihkan() {
        // VoidService/SesiKasirService memakai SekolahGuard → butuh TenantContext.
        TenantContext.set(IdentitasKantin.builder().userId("555").sekolahId(SEKOLAH).build());
        jdbc.execute("TRUNCATE TABLE transaksi_item, transaksi, sesi_kasir, titik_kasir, "
                + "mutasi_saldo, saldo, riwayat_stok, stok, catatan_audit CASCADE");

        FixtureInduk.sekolah(jdbc, SEKOLAH);

        FixtureInduk.penggunaUmum(jdbc, SEKOLAH);

        FixtureInduk.kategoriUmum(jdbc, SEKOLAH);

        FixtureInduk.kartu(jdbc, SEKOLAH, SISWA, UID);
        FixtureInduk.siswa(jdbc, SEKOLAH, SISWA);
        FixtureInduk.menu(jdbc, SEKOLAH, MENU_NASI);
        jdbc.update("INSERT INTO titik_kasir (id, sekolah_id, nama, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'Kasir 1', true, now(), now())", TITIK, SEKOLAH);

        tx.executeWithoutResult(s -> ledgerSaldo.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.SISWA).siswaId(SISWA)
                .jenis(JenisMutasiSaldo.TOPUP_TUNAI).nominal(50_000).idempotencyKey("seed").build()));
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_NASI, 20, 4_000, "BARANG_MASUK", "BM", 1L));
    }

    @AfterEach
    void bersihkanKonteks() {
        TenantContext.clear();
    }

    private TapResponse tap(String key, int qty) {
        TapRequest r = new TapRequest();
        r.setRfidUid(UID);
        r.setTitikKasirId(TITIK);
        r.setIdempotencyKey(key);
        var item = new TapRequest.ItemTap();
        item.setMenuId(MENU_NASI);
        item.setQty(qty);
        r.setItems(List.of(item));
        return tapService.tap(SEKOLAH, petugas, r);
    }

    /** Kode struk yang benar untuk transaksi hasil tap. */
    private String kode(TapResponse tap) {
        return NomorStruk.dari(tap.getTransaksiId(), tap.getWaktu());
    }

    @Test
    @DisplayName("kode struk salah ditolak — uang tidak boleh kembali")
    void kodeStrukSalahDitolak() {
        TapResponse tap = tap("tap-kode-salah", 1);
        long saldoSebelum = ledgerSaldo.saldo(SEKOLAH, SISWA);

        assertThatThrownBy(() -> voidService.refundTransaksi(
                SEKOLAH, tap.getTransaksiId(), 555L, "salah pilih", "TRX-19990101-1"))
                .hasMessageContaining("Kode struk tidak cocok");

        // Tidak ada uang yang kembali.
        assertThat(ledgerSaldo.saldo(SEKOLAH, SISWA)).isEqualTo(saldoSebelum);
    }

    @Test
    @DisplayName("kode struk kosong ditolak")
    void kodeStrukKosongDitolak() {
        TapResponse tap = tap("tap-kode-kosong", 1);
        assertThatThrownBy(() -> voidService.refundTransaksi(
                SEKOLAH, tap.getTransaksiId(), 555L, "alasan ada", "  "))
                .hasMessageContaining("Kode struk wajib");
    }

    @Test
    @DisplayName("refund mengembalikan saldo & stok lewat mutasi pembalik")
    void voidMengembalikan() {
        TapResponse tap = tap("tap-1", 2); // 2 × 8000 = 16.000
        assertThat(ledgerSaldo.saldo(SEKOLAH, SISWA)).isEqualTo(34_000L);
        assertThat(ledgerStok.stok(SEKOLAH, MENU_NASI)).isEqualTo(18);

        var trx = voidService.refundTransaksi(SEKOLAH, tap.getTransaksiId(), 555L, "Kartu dipakai bukan pemiliknya", kode(tap));

        assertThat(trx.getStatus()).isEqualTo(StatusTransaksi.VOID);
        assertThat(trx.getAlasanVoid()).isEqualTo("Kartu dipakai bukan pemiliknya");
        assertThat(ledgerSaldo.saldo(SEKOLAH, SISWA)).isEqualTo(50_000L);
        assertThat(ledgerStok.stok(SEKOLAH, MENU_NASI)).isEqualTo(20);

        // Baris asli TETAP ada (append-only); kompensasi = baris baru.
        Integer debitAsli = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mutasi_saldo WHERE arah = 'DEBIT' AND jenis = 'PENJUALAN'", Integer.class);
        Integer kreditVoid = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mutasi_saldo WHERE arah = 'KREDIT' AND jenis = 'VOID_PENJUALAN'", Integer.class);
        assertThat(debitAsli).isEqualTo(1);
        assertThat(kreditVoid).isEqualTo(1);
        assertThat(ledgerSaldo.hitungUlangDariLedger(SEKOLAH, SISWA)).isEqualTo(50_000L);

        // C.2e: transaksi saldo → struk pembatalan tanpa uang tunai/kembalian.
        var dto = TransaksiRefundResponse.dari(trx);
        assertThat(dto.metodeBayar()).isEqualTo(MetodeBayar.SALDO);
        assertThat(dto.uangDiterima()).isNull();
        assertThat(dto.kembalian()).isNull();
        assertThat(dto.subjekTipe()).isEqualTo("SISWA");
        assertThat(dto.siswaId()).isEqualTo(SISWA);
    }

    @Test
    @DisplayName("C.2a: void transaksi TUNAI — stok kembali, saldo TIDAK dikreditkan")
    void voidTunaiTidakMengkreditSaldo() {
        TapRequest r = new TapRequest();
        r.setRfidUid(null);
        r.setTitikKasirId(TITIK);
        r.setIdempotencyKey("tap-tunai-void");
        r.setMetodeBayar(MetodeBayar.TUNAI);
        r.setUangDiterima(20_000L);
        var item = new TapRequest.ItemTap();
        item.setMenuId(MENU_NASI);
        item.setQty(2);
        r.setItems(List.of(item));

        TapResponse tap = tapService.tap(SEKOLAH, petugas, r);
        assertThat(ledgerSaldo.saldo(SEKOLAH, SISWA)).isEqualTo(50_000L);

        var trx = voidService.refundTransaksi(SEKOLAH, tap.getTransaksiId(), 555L,
                "Salah input menu", kode(tap));

        assertThat(trx.getStatus()).isEqualTo(StatusTransaksi.VOID);
        // Stok kembali karena barang batal keluar...
        assertThat(ledgerStok.stok(SEKOLAH, MENU_NASI)).isEqualTo(20);
        // ...tetapi saldo tetap: tunai tidak pernah memotong saldo, jadi tidak
        // ada yang boleh dikreditkan (uang dikembalikan fisik oleh kasir).
        assertThat(ledgerSaldo.saldo(SEKOLAH, SISWA)).isEqualTo(50_000L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM mutasi_saldo WHERE jenis = 'VOID_PENJUALAN'", Integer.class)).isZero();

        // C.2e: struk pembatalan tetap menyebut cara bayar asli + uang/kembalian,
        // supaya cetak ulang tidak menampilkan "SISWA" untuk pembeli tunai.
        var dto = TransaksiRefundResponse.dari(trx);
        assertThat(dto.metodeBayar()).isEqualTo(MetodeBayar.TUNAI);
        assertThat(dto.uangDiterima()).isEqualTo(20_000L);
        assertThat(dto.kembalian()).isEqualTo(4_000L);
        assertThat(dto.subjekTipe()).isNull();
        assertThat(dto.siswaId()).isNull();
        assertThat(dto.nomorStruk()).isEqualTo(kode(tap));
    }

    @Test
    @DisplayName("void tanpa alasan ditolak")
    void voidTanpaAlasanDitolak() {
        TapResponse tap = tap("tap-2", 1);
        assertThatThrownBy(() -> voidService.refundTransaksi(SEKOLAH, tap.getTransaksiId(), 555L, "  ", kode(tap)))
                .isInstanceOf(com.asqi.scholia_kantin_be.component.exception.InvalidOperationException.class)
                .hasMessageContaining("Alasan refund wajib");
    }

    @Test
    @DisplayName("#258: void bersamaan — hanya satu yang menang, invarian saldo & stok tetap")
    void voidBersamaanSatuPemenang() throws Exception {
        TapResponse tap = tap("tap-void-race", 2); // 2 × 8_000 = 16_000
        assertThat(ledgerSaldo.saldo(SEKOLAH, SISWA)).isEqualTo(34_000L);

        int paralel = 8;
        ExecutorService pool = Executors.newFixedThreadPool(paralel);
        CountDownLatch mulai = new CountDownLatch(1);
        AtomicInteger sukses = new AtomicInteger();
        AtomicInteger ditolak = new AtomicInteger();

        var futures = new java.util.ArrayList<Future<Transaksi>>();
        for (int i = 0; i < paralel; i++) {
            final long seq = i;
            futures.add(pool.submit(() -> {
                // TenantContext/SekolahGuard = ThreadLocal, tidak diwariskan ke thread pool.
                TenantContext.set(petugas);
                try {
                    mulai.await();
                    try {
                        var trx = voidService.refundTransaksi(
                                SEKOLAH, tap.getTransaksiId(), 555L, "refund paralel #" + seq, kode(tap));
                        sukses.incrementAndGet();
                        return trx;
                    } catch (RuntimeException e) {
                        // InvalidOperationException ("sudah di-void") atau 409 DB
                        // kalau pembalik balapan di jalur UNIQUE idempotency.
                        ditolak.incrementAndGet();
                        return null;
                    }
                } finally {
                    TenantContext.clear();
                }
            }));
        }
        mulai.countDown();
        for (Future<Transaksi> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(sukses.get()).isEqualTo(1);
        assertThat(ditolak.get()).isEqualTo(paralel - 1);

        // Invarian: saldo & stok kembali persis sekali (satu mutasi pembalik).
        assertThat(ledgerSaldo.saldo(SEKOLAH, SISWA)).isEqualTo(50_000L);
        assertThat(ledgerStok.stok(SEKOLAH, MENU_NASI)).isEqualTo(20);
        Integer pembalik = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mutasi_saldo WHERE arah = 'KREDIT' AND jenis = 'VOID_PENJUALAN'",
                Integer.class);
        assertThat(pembalik).isEqualTo(1);
        assertThat(ledgerSaldo.hitungUlangDariLedger(SEKOLAH, SISWA))
                .isEqualTo(50_000L);
    }

    @Test
    @DisplayName("void dua kali ditolak")
    void voidDuaKaliDitolak() {
        TapResponse tap = tap("tap-3", 1);
        voidService.refundTransaksi(SEKOLAH, tap.getTransaksiId(), 555L, "salah", kode(tap));
        assertThatThrownBy(() -> voidService.refundTransaksi(SEKOLAH, tap.getTransaksiId(), 555L, "lagi", kode(tap)))
                .hasMessageContaining("sudah di-refund");
    }

    @Test
    @DisplayName("void pada sesi yang sudah ditutup ditolak (koreksi hanya bendahara)")
    void voidSesiTertutupDitolak() {
        TapResponse tap = tap("tap-4", 1);
        Long sesiId = jdbc.queryForObject(
                "SELECT sesi_kasir_id FROM transaksi WHERE id = ?", Long.class, tap.getTransaksiId());
        sesiKasir.tutupSesi(SEKOLAH, sesiId, 555L, false, tutupKas(0L, "uji tutup"));

        assertThatThrownBy(() -> voidService.refundTransaksi(SEKOLAH, tap.getTransaksiId(), 555L, "salah", kode(tap)))
                .hasMessageContaining("sudah ditutup");
    }

    @Test
    @DisplayName("void transaksi sekolah lain → 404")
    void voidTenantLain404() {
        TapResponse tap = tap("tap-5", 1);
        assertThatThrownBy(() -> voidService.refundTransaksi(999L, tap.getTransaksiId(), 555L, "salah", kode(tap)))
                .isInstanceOf(com.asqi.scholia_kantin_be.component.exception.NotFoundEntity.class);
    }

    // ────────────────────────────────────────────────────────────────
    // KOREKSI TRANSAKSI SESI TERTUTUP OLEH BENDAHARA (#119, PRD §6.3/§9.2)
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("#119: koreksi sesi tertutup mengembalikan saldo & stok via mutasi pembalik")
    void koreksiSesiTertutupMengembalikan() {
        TapResponse tap = tap("tap-kor1", 2); // 2 × 8000 = 16.000
        Long sesiId = jdbc.queryForObject(
                "SELECT sesi_kasir_id FROM transaksi WHERE id = ?", Long.class, tap.getTransaksiId());
        sesiKasir.tutupSesi(SEKOLAH, sesiId, 555L, false, tutupKas(0L, "uji tutup"));

        // Void petugas ditolak pada sesi tertutup.
        assertThatThrownBy(() -> voidService.refundTransaksi(SEKOLAH, tap.getTransaksiId(), 555L, "salah", kode(tap)))
                .hasMessageContaining("sudah ditutup");

        // Bendahara mengoreksi → saldo & stok kembali.
        var trx = voidService.koreksiTransaksiSesiTertutup(
                SEKOLAH, tap.getTransaksiId(), 777L, "Transaksi salah input pada sesi tertutup");

        assertThat(trx.getStatus()).isEqualTo(StatusTransaksi.VOID);
        assertThat(trx.getAlasanVoid()).isEqualTo("Transaksi salah input pada sesi tertutup");
        assertThat(trx.getVoidOleh()).isEqualTo(777L);
        assertThat(ledgerSaldo.saldo(SEKOLAH, SISWA)).isEqualTo(50_000L);
        assertThat(ledgerStok.stok(SEKOLAH, MENU_NASI)).isEqualTo(20);

        // Baris asli tetap ada (append-only); kompensasi = baris KOREKSI baru.
        Integer debitAsli = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mutasi_saldo WHERE arah = 'DEBIT' AND jenis = 'PENJUALAN'", Integer.class);
        Integer kreditKoreksi = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mutasi_saldo WHERE arah = 'KREDIT' AND jenis = 'KOREKSI'", Integer.class);
        assertThat(debitAsli).isEqualTo(1);
        assertThat(kreditKoreksi).isEqualTo(1);
        assertThat(ledgerSaldo.hitungUlangDariLedger(SEKOLAH, SISWA)).isEqualTo(50_000L);

        // Audit tercatat dengan aksi khusus koreksi.
        Integer audit = jdbc.queryForObject(
                "SELECT COUNT(*) FROM catatan_audit WHERE aksi = 'KOREKSI_TRANSAKSI_SESI_TERTUTUP'", Integer.class);
        assertThat(audit).isEqualTo(1);
    }

    @Test
    @DisplayName("#119: koreksi tanpa alasan ditolak")
    void koreksiTanpaAlasanDitolak() {
        TapResponse tap = tap("tap-kor2", 1);
        Long sesiId = jdbc.queryForObject(
                "SELECT sesi_kasir_id FROM transaksi WHERE id = ?", Long.class, tap.getTransaksiId());
        sesiKasir.tutupSesi(SEKOLAH, sesiId, 555L, false, tutupKas(0L, "uji tutup"));

        assertThatThrownBy(() -> voidService.koreksiTransaksiSesiTertutup(
                SEKOLAH, tap.getTransaksiId(), 777L, "  "))
                .hasMessageContaining("Alasan koreksi wajib");
    }

    @Test
    @DisplayName("#119: koreksi pada sesi yang masih terbuka ditolak (pakai void biasa)")
    void koreksiSesiTerbukaDitolak() {
        TapResponse tap = tap("tap-kor3", 1);

        assertThatThrownBy(() -> voidService.koreksiTransaksiSesiTertutup(
                SEKOLAH, tap.getTransaksiId(), 777L, "salah"))
                .hasMessageContaining("masih terbuka");
    }

    @Test
    @DisplayName("#119: koreksi dua kali ditolak (sudah di-void)")
    void koreksiDuaKaliDitolak() {
        TapResponse tap = tap("tap-kor4", 1);
        Long sesiId = jdbc.queryForObject(
                "SELECT sesi_kasir_id FROM transaksi WHERE id = ?", Long.class, tap.getTransaksiId());
        sesiKasir.tutupSesi(SEKOLAH, sesiId, 555L, false, tutupKas(0L, "uji tutup"));

        voidService.koreksiTransaksiSesiTertutup(SEKOLAH, tap.getTransaksiId(), 777L, "salah input");
        assertThatThrownBy(() -> voidService.koreksiTransaksiSesiTertutup(
                SEKOLAH, tap.getTransaksiId(), 777L, "lagi"))
                .hasMessageContaining("sudah di-refund");
    }

    @Test
    @DisplayName("#119: koreksi transaksi sekolah lain → 404")
    void koreksiTenantLain404() {
        TapResponse tap = tap("tap-kor5", 1);
        Long sesiId = jdbc.queryForObject(
                "SELECT sesi_kasir_id FROM transaksi WHERE id = ?", Long.class, tap.getTransaksiId());
        sesiKasir.tutupSesi(SEKOLAH, sesiId, 555L, false, tutupKas(0L, "uji tutup"));

        assertThatThrownBy(() -> voidService.koreksiTransaksiSesiTertutup(
                999L, tap.getTransaksiId(), 777L, "salah"))
                .isInstanceOf(com.asqi.scholia_kantin_be.component.exception.NotFoundEntity.class);
    }
}
