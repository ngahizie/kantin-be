package com.asqi.scholia_kantin_be.service.integrasi;

import com.asqi.scholia_kantin_be.dto.TapRequest;
import com.asqi.scholia_kantin_be.dto.TapResponse;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import com.asqi.scholia_kantin_be.service.kasir.PerintahMutasiSaldo;
import com.asqi.scholia_kantin_be.service.kasir.TapService;
import com.asqi.scholia_kantin_be.service.stok.LedgerStokService;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.FixtureInduk;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UID kartu harus dikenali apa pun besar-kecil hurufnya (issue #346).
 *
 * <p>Memakai implementasi produksi {@code KartuLookupLokal} (bukan fake), karena
 * bug aslinya ada di lookup lokal: nilai klien diteruskan apa adanya ke query
 * sementara kolom {@code kartu.uid} case-sensitive.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class KartuLookupUidCaseIT {

    private static final long SEKOLAH = 1L;
    private static final long SISWA = 7L;
    private static final long MENU_NASI = 10L;
    private static final long TITIK = 1L;
    private static final String UID_KAPITAL = "A1B2C3D4";

    @Autowired
    private KartuLookupPort kartuLookup;

    @Autowired
    private TapService tapService;

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
    void siapkan() {
        TenantContext.set(petugas);
        jdbc.execute("TRUNCATE TABLE transaksi_item, transaksi, sesi_kasir, titik_kasir, "
                + "mutasi_saldo, saldo, riwayat_stok, stok, catatan_audit CASCADE");
        FixtureInduk.sekolah(jdbc, SEKOLAH);
        FixtureInduk.penggunaUmum(jdbc, SEKOLAH);
        FixtureInduk.kategoriUmum(jdbc, SEKOLAH);
        FixtureInduk.siswa(jdbc, SEKOLAH, SISWA);
        FixtureInduk.kartu(jdbc, SEKOLAH, SISWA, UID_KAPITAL);
        FixtureInduk.menu(jdbc, SEKOLAH, MENU_NASI);
        jdbc.update("INSERT INTO titik_kasir (id, sekolah_id, nama, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'Kasir 1', true, now(), now())", TITIK, SEKOLAH);
        tx.executeWithoutResult(s -> ledgerSaldo.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.SISWA).siswaId(SISWA)
                .jenis(JenisMutasiSaldo.TOPUP_TUNAI).nominal(50_000).idempotencyKey("seed").build()));
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_NASI, 20, 4_000, "BARANG_MASUK", "BM", 1L));
    }

    @AfterEach
    void bersihkan() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("lookup UID huruf kecil, kapital, dan campur → kartu yang sama")
    void lookupSemuaBentukHuruf() {
        InfoKartu kapital = kartuLookup.cariBerdasarkanUid(SEKOLAH, UID_KAPITAL);
        InfoKartu kecil = kartuLookup.cariBerdasarkanUid(SEKOLAH, "a1b2c3d4");
        InfoKartu campur = kartuLookup.cariBerdasarkanUid(SEKOLAH, "a1B2c3D4");
        InfoKartu berspasi = kartuLookup.cariBerdasarkanUid(SEKOLAH, "  a1b2c3d4  ");

        assertThat(kapital.isDikenal()).isTrue();
        assertThat(kecil.isDikenal()).as("UID huruf kecil harus dikenali").isTrue();
        assertThat(campur.isDikenal()).as("UID huruf campur harus dikenali").isTrue();
        assertThat(berspasi.isDikenal()).as("UID dengan spasi ujung harus dikenali").isTrue();

        assertThat(kecil.getSiswaId()).isEqualTo(kapital.getSiswaId());
        assertThat(campur.getSiswaId()).isEqualTo(kapital.getSiswaId());
        assertThat(berspasi.getSiswaId()).isEqualTo(kapital.getSiswaId());
        assertThat(kecil.getNama()).isEqualTo(kapital.getNama());
    }

    @Test
    @DisplayName("UID tidak terdaftar tetap ditolak (tidak asal cocok)")
    void uidAsingDitolak() {
        assertThat(kartuLookup.cariBerdasarkanUid(SEKOLAH, "deadbeef").isDikenal()).isFalse();
        assertThat(kartuLookup.cariBerdasarkanUid(SEKOLAH, "").isDikenal()).isFalse();
        assertThat(kartuLookup.cariBerdasarkanUid(SEKOLAH, null).isDikenal()).isFalse();
    }

    @Test
    @DisplayName("tap dengan rfidUid huruf kecil berhasil & tersimpan kanonik")
    void tapHurufKecilBerhasil() {
        long saldoSebelum = ledgerSaldo.saldo(SEKOLAH, SISWA);

        TapRequest r = new TapRequest();
        r.setRfidUid("a1b2c3d4");
        r.setTitikKasirId(TITIK);
        r.setIdempotencyKey("tap-uid-kecil");
        TapRequest.ItemTap item = new TapRequest.ItemTap();
        item.setMenuId(MENU_NASI);
        item.setQty(1);
        r.setItems(List.of(item));

        TapResponse hasil = tapService.tap(SEKOLAH, petugas, r);

        assertThat(hasil.getTransaksiId()).as("tap huruf kecil harus membuat transaksi").isNotNull();
        assertThat(ledgerSaldo.saldo(SEKOLAH, SISWA)).isLessThan(saldoSebelum);

        String tersimpan = jdbc.queryForObject(
                "SELECT kartu_uid FROM transaksi WHERE id = ?", String.class, hasil.getTransaksiId());
        assertThat(tersimpan).as("UID transaksi disimpan dalam bentuk kanonik").isEqualTo(UID_KAPITAL);
    }
}
