package com.asqi.scholia_kantin_be.service.saldo;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.dto.RekapSetoranItem;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.dto.SetoranResponse;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import com.asqi.scholia_kantin_be.service.kasir.PerintahMutasiSaldo;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.FixtureInduk;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji integrasi setoran kas TU harian (PRD §9.2, issue #39) dengan PostgreSQL
 * nyata (Testcontainers).
 *
 * <p>Menegakkan: rekap top-up tunai per petugas per hari, konfirmasi setoran +
 * selisih kas <b>dicatat</b> (append-only), idempotency berita acara, dan
 * tenant scoping.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class SetoranServiceIT {

    private static final long SEKOLAH = 1L;
    private static final long PETUGAS_A = 555L;
    private static final long PETUGAS_B = 777L;
    private static final long BENDAHARA = 999L;

    @Autowired
    private SetoranService setoranService;

    @Autowired
    private LedgerSaldoService ledger;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JamKantin jam;

    private long subjekBerikut = 10_000L;

    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE setoran, mutasi_saldo, saldo, catatan_audit CASCADE");
        subjekBerikut = 10_000L;
        // Skema v2: setoran/mutasi_saldo ber-FK ke sekolah, pengguna, dan siswa.
        FixtureInduk.sekolah(jdbc, SEKOLAH);
        FixtureInduk.pengguna(jdbc, SEKOLAH, PETUGAS_A, PETUGAS_B, BENDAHARA);
    }

    /** Catat satu top-up tunai oleh petugas tertentu (aktor_id = petugasId). */
    private void topUpTunai(long petugasId, long nominal) {
        long subjek = subjekBerikut++;
        FixtureInduk.siswa(jdbc, SEKOLAH, subjek);
        tx.executeWithoutResult(s -> ledger.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH)
                .subjekTipe(SubjekTipe.SISWA)
                .siswaId(subjek)
                .jenis(JenisMutasiSaldo.TOPUP_TUNAI)
                .nominal(nominal)
                .idempotencyKey("topup-" + petugasId + "-" + nominal + "-" + System.nanoTime())
                .referensiTipe("TOPUP")
                .aktorId(petugasId)
                .build()));
    }

    @Test
    @DisplayName("rekap: total top-up tunai per petugas per hari (belum disetor → selisih = total)")
    void rekapPerPetugas() {
        topUpTunai(PETUGAS_A, 50_000);
        topUpTunai(PETUGAS_A, 30_000);
        topUpTunai(PETUGAS_B, 20_000);

        List<RekapSetoranItem> rekap = setoranService.rekap(SEKOLAH, jam.hariIni());

        assertThat(rekap).hasSize(2);
        RekapSetoranItem a = rekap.stream()
                .filter(r -> r.getPetugasId().equals(PETUGAS_A)).findFirst().orElseThrow();
        assertThat(a.getTotalTopup()).isEqualTo(80_000);
        assertThat(a.getJumlahDisetor()).isZero();
        assertThat(a.getSelisih()).isEqualTo(80_000);

        RekapSetoranItem b = rekap.stream()
                .filter(r -> r.getPetugasId().equals(PETUGAS_B)).findFirst().orElseThrow();
        assertThat(b.getTotalTopup()).isEqualTo(20_000);
    }

    @Test
    @DisplayName("konfirmasi setoran sesuai (selisih 0) → tersimpan, total diambil dari ledger")
    void konfirmasiSesuai() {
        topUpTunai(PETUGAS_A, 80_000);

        SetoranResponse s = setoranService.konfirmasi(SEKOLAH, jam.hariIni(), PETUGAS_A,
                80_000, "BA-2026-001", null, BENDAHARA);

        assertThat(s.getTotalTopup()).isEqualTo(80_000);
        assertThat(s.getJumlahDisetor()).isEqualTo(80_000);
        assertThat(s.getSelisih()).isZero(); // dihitung kolom GENERATED DB
        assertThat(s.getReferensiId()).isEqualTo("BA-2026-001");

        // Rekap mencerminkan setoran.
        RekapSetoranItem a = setoranService.rekap(SEKOLAH, jam.hariIni()).stream()
                .filter(r -> r.getPetugasId().equals(PETUGAS_A)).findFirst().orElseThrow();
        assertThat(a.getJumlahDisetor()).isEqualTo(80_000);
        assertThat(a.getSelisih()).isZero();
        assertThat(a.getReferensiId()).isEqualTo("BA-2026-001");

        // Audit tercatat.
        Integer audit = jdbc.queryForObject(
                "SELECT COUNT(*) FROM catatan_audit WHERE aksi = 'KONFIRMASI_SETORAN_TU'", Integer.class);
        assertThat(audit).isEqualTo(1);
    }

    @Test
    @DisplayName("selisih kas DICATAT (kurang setor) — tidak dihapus, wajib catatan")
    void selisihKurangSetorDicatat() {
        topUpTunai(PETUGAS_A, 80_000);

        SetoranResponse s = setoranService.konfirmasi(SEKOLAH, jam.hariIni(), PETUGAS_A,
                75_000, "BA-2026-002", "Kurang Rp5.000, uang belum kembali dari siswa", BENDAHARA);

        assertThat(s.getSelisih()).isEqualTo(5_000); // kurang setor = positif
        assertThat(jdbc.queryForObject(
                "SELECT selisih FROM setoran WHERE id = ?", Long.class, s.getId()))
                .isEqualTo(5_000L);
    }

    @Test
    @DisplayName("selisih tanpa catatan → ditolak (PRD §9.2: selisih wajib beralasan)")
    void selisihTanpaCatatanDitolak() {
        topUpTunai(PETUGAS_A, 80_000);

        assertThatThrownBy(() -> setoranService.konfirmasi(SEKOLAH, jam.hariIni(), PETUGAS_A,
                75_000, "BA-2026-003", "  ", BENDAHARA))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("catatan");
    }

    @Test
    @DisplayName("lebih setor (selisih negatif) → dicatat")
    void selisihLebihSetor() {
        topUpTunai(PETUGAS_A, 80_000);

        SetoranResponse s = setoranService.konfirmasi(SEKOLAH, jam.hariIni(), PETUGAS_A,
                85_000, "BA-2026-004", "Lebih Rp5.000, kemungkinan salah hitung", BENDAHARA);

        assertThat(s.getSelisih()).isEqualTo(-5_000);
    }

    @Test
    @DisplayName("idempoten: berita acara sama → tidak mencatat setoran dua kali")
    void konfirmasiIdempoten() {
        topUpTunai(PETUGAS_A, 80_000);

        SetoranResponse s1 = setoranService.konfirmasi(SEKOLAH, jam.hariIni(), PETUGAS_A,
                80_000, "BA-2026-005", null, BENDAHARA);
        SetoranResponse s2 = setoranService.konfirmasi(SEKOLAH, jam.hariIni(), PETUGAS_A,
                80_000, "BA-2026-005", null, BENDAHARA);

        assertThat(s2.getId()).isEqualTo(s1.getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM setoran", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("setoran hanya INSERT — UPDATE/DELETE ditolak trigger (append-only)")
    void appendOnly() {
        topUpTunai(PETUGAS_A, 80_000);
        SetoranResponse s = setoranService.konfirmasi(SEKOLAH, jam.hariIni(), PETUGAS_A,
                80_000, "BA-2026-006", null, BENDAHARA);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE setoran SET jumlah_disetor = 0 WHERE id = ?", s.getId()))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM setoran WHERE id = ?", s.getId()))
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("tenant scoping: rekap sekolah lain tidak bocor")
    void tenantScoped() {
        topUpTunai(PETUGAS_A, 80_000); // sekolah 1

        assertThat(setoranService.rekap(2L, jam.hariIni())).isEmpty();
    }

    @Test
    @DisplayName("rekap rentang: mengelompokkan top-up & status setoran per tanggal dan petugas")
    void rekapRentangMultiHari() {
        LocalDate hariIni = jam.hariIni();
        LocalDate kemarin = hariIni.minusDays(1);
        OffsetDateTime waktuKemarin = kemarin.atTime(10, 0).atZone(jam.zona()).toOffsetDateTime();

        // Top-up hari ini lewat service, dan sisipkan 1 top-up kemarin langsung di ledger
        topUpTunai(PETUGAS_A, 60_000);
        long subjekKemarin = subjekBerikut++;
        FixtureInduk.siswa(jdbc, SEKOLAH, subjekKemarin);
        jdbc.update("""
                INSERT INTO mutasi_saldo (id, sekolah_id, siswa_id, arah, jenis,
                                          nominal, saldo_setelah, referensi_id, keterangan,
                                          aktor_id, waktu)
                VALUES (?, ?, ?, 'KREDIT', 'TOPUP_TUNAI', ?, ?, ?, ?, ?, ?)
                """, 990_001L, SEKOLAH, subjekKemarin, 40_000L, 40_000L,
                "TOPUP-KEMARIN-1", "Top-up tunai kemarin", PETUGAS_B, waktuKemarin);

        setoranService.konfirmasi(SEKOLAH, kemarin, PETUGAS_B, 40_000, "BA-RENTANG-1", null, BENDAHARA);

        OffsetDateTime dari = kemarin.atStartOfDay(jam.zona()).toOffsetDateTime();
        OffsetDateTime sampai = hariIni.plusDays(1).atStartOfDay(jam.zona()).toOffsetDateTime();

        List<RekapSetoranItem> hasil = setoranService.rekap(SEKOLAH, null, dari, sampai);

        assertThat(hasil).hasSize(2);
        // Terbaru lebih dulu (hari ini, lalu kemarin)
        assertThat(hasil.get(0).getTanggal()).isEqualTo(hariIni);
        assertThat(hasil.get(0).getPetugasId()).isEqualTo(PETUGAS_A);
        assertThat(hasil.get(0).getTotalTopup()).isEqualTo(60_000);
        assertThat(hasil.get(0).getReferensiId()).isNull();

        assertThat(hasil.get(1).getTanggal()).isEqualTo(kemarin);
        assertThat(hasil.get(1).getPetugasId()).isEqualTo(PETUGAS_B);
        assertThat(hasil.get(1).getTotalTopup()).isEqualTo(40_000);
        assertThat(hasil.get(1).getJumlahDisetor()).isEqualTo(40_000);
        assertThat(hasil.get(1).getSelisih()).isZero();
        assertThat(hasil.get(1).getReferensiId()).isEqualTo("BA-RENTANG-1");

        // Rentang terbalik ditolak
        assertThatThrownBy(() -> setoranService.rekap(SEKOLAH, null, sampai, dari))
                .isInstanceOf(InvalidOperationException.class);
    }
}
