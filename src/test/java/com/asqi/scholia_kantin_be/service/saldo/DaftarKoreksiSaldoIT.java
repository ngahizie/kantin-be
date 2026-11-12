package com.asqi.scholia_kantin_be.service.saldo;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.dto.HalamanResponse;
import com.asqi.scholia_kantin_be.dto.MutasiKoreksiItem;
import com.asqi.scholia_kantin_be.enums.ArahMutasi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.security.TenantContext;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji integrasi <b>{@code GET /api/saldo/koreksi}</b> — daftar mutasi koreksi
 * saldo (PRD §9.2; issue <b>#213</b>) di atas PostgreSQL nyata.
 *
 * <p><b>Kenapa IT.</b> Pelajaran #198/#199: bug query (tipe parameter, dialect,
 * batas hari) tidak muncul saat service di-mock. Uji ini menutup semua filter
 * yang dijanjikan issue (#213 acceptance criteria) plus paginasi, isolasi tenant,
 * penyaringan jenis {@code KOREKSI}, dan perhitungan {@code saldoSebelum}.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class DaftarKoreksiSaldoIT {

    private static final long SEKOLAH = 1L;
    private static final long SEKOLAH_LAIN = 2L;

    private static final LocalDate TGL_15 = LocalDate.parse("2026-10-15");
    private static final LocalDate TGL_16 = LocalDate.parse("2026-10-16");
    private static final LocalDate TGL_17 = LocalDate.parse("2026-10-17"); // tidak ada mutasi

    // Subjek mutasi = SISWA (skema v2 tidak lagi punya kartu tamu).
    private static final long SISWA_A = 5001L;
    private static final long SISWA_B = 5002L;
    private static final long SISWA_C = 5003L;
    private static final long SISWA_LAIN = 5101L;
    private static final long AKTOR = 77L;

    @Autowired
    private SaldoOperasiService operasi;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void siapkan() {
        TenantContext.clear();
        jdbc.execute("TRUNCATE TABLE mutasi_saldo, saldo CASCADE");

        // Induk wajib skema v2 (FK): sekolah → siswa/pengguna.
        FixtureInduk.sekolah(jdbc, SEKOLAH, SEKOLAH_LAIN);
        FixtureInduk.pengguna(jdbc, SEKOLAH, AKTOR);
        FixtureInduk.siswa(jdbc, SEKOLAH, SISWA_A, SISWA_B, SISWA_C);
        FixtureInduk.siswa(jdbc, SEKOLAH_LAIN, SISWA_LAIN);

        // Bukan koreksi — TIDAK boleh muncul di endpoint ini.
        ledger(1L, SEKOLAH, SISWA_A, "KREDIT", "TOPUP_TUNAI", 50_000L, 50_000L,
                "2026-10-15T08:00:00+07:00", null);
        // Koreksi DEBIT 15 Okt 23:45 — masih tanggal 15 menurut zona sekolah
        ledger(2L, SEKOLAH, SISWA_A, "DEBIT", "KOREKSI", 10_000L, 40_000L,
                "2026-10-15T23:45:00+07:00", "salah input topup");
        // Koreksi KREDIT 16 Okt 00:15 — sudah masuk tanggal 16
        ledger(3L, SEKOLAH, SISWA_B, "KREDIT", "KOREKSI", 25_000L, 25_000L,
                "2026-10-16T00:15:00+07:00", "pengembalian kelebihan");
        // Koreksi DEBIT 16 Okt 10:00 (siswa lain)
        ledger(4L, SEKOLAH, SISWA_C, "DEBIT", "KOREKSI", 5_000L, 20_000L,
                "2026-10-16T10:00:00+07:00", "koreksi audit");
        // Sekolah lain — tidak boleh bocor
        ledger(5L, SEKOLAH_LAIN, SISWA_LAIN, "KREDIT", "KOREKSI", 15_000L, 15_000L,
                "2026-10-16T11:00:00+07:00", "koreksi sekolah lain");
    }

    // ────────────────────────────────────────────────────────────────
    // Semua filter (acceptance criteria #213)
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("tanpa filter → hanya jenis KOREKSI milik tenant, urut waktu DESC")
    void tanpaFilter() {
        HalamanResponse<MutasiKoreksiItem> hasil =
                operasi.daftarKoreksi(SEKOLAH, null, null, 0, 20);

        // id=1 (TOPUP_TUNAI) & id=6 tidak ada → hanya 3 koreksi tenant ini.
        assertThat(hasil.getItems()).extracting(MutasiKoreksiItem::getId)
                .containsExactly(4L, 3L, 2L);
        assertThat(hasil.getTotal()).isEqualTo(3L);
    }

    @Test
    @DisplayName("filter tanggal → batas hari mengikuti zona sekolah (23:45 vs 00:15)")
    void filterTanggal() {
        HalamanResponse<MutasiKoreksiItem> tgl16 =
                operasi.daftarKoreksi(SEKOLAH, TGL_16, null, 0, 20);
        HalamanResponse<MutasiKoreksiItem> tgl15 =
                operasi.daftarKoreksi(SEKOLAH, TGL_15, null, 0, 20);

        assertThat(tgl16.getItems()).extracting(MutasiKoreksiItem::getId).containsExactly(4L, 3L);
        assertThat(tgl15.getItems()).extracting(MutasiKoreksiItem::getId).containsExactly(2L);
    }

    @Test
    @DisplayName("semua baris hasil ber-subjek SISWA (skema v2: kartu tamu tidak ada lagi)")
    void subjekSelaluSiswa() {
        HalamanResponse<MutasiKoreksiItem> hasil =
                operasi.daftarKoreksi(SEKOLAH, null, null, 0, 20);

        assertThat(hasil.getItems()).extracting(MutasiKoreksiItem::getSubjekTipe)
                .containsOnly(SubjekTipe.SISWA);
        assertThat(hasil.getItems()).extracting(MutasiKoreksiItem::getSiswaId)
                .containsExactlyInAnyOrder(SISWA_C, SISWA_B, SISWA_A);
    }

    @Test
    @DisplayName("filter siswaId → satu subjek tertentu")
    void filterSubjekId() {
        HalamanResponse<MutasiKoreksiItem> hasil =
                operasi.daftarKoreksi(SEKOLAH, null, SISWA_B, 0, 20);

        assertThat(hasil.getItems()).extracting(MutasiKoreksiItem::getId).containsExactly(3L);
    }

    @Test
    @DisplayName("filter kombinasi (tanggal + siswaId) → terpenuhi keduanya")
    void filterKombinasi() {
        HalamanResponse<MutasiKoreksiItem> hasil =
                operasi.daftarKoreksi(SEKOLAH, TGL_16, SISWA_C, 0, 20);

        assertThat(hasil.getItems()).extracting(MutasiKoreksiItem::getId).containsExactly(4L);
    }

    @Test
    @DisplayName("filter tanpa hasil → halaman kosong (bukan 500)")
    void filterTanpaHasil() {
        HalamanResponse<MutasiKoreksiItem> hasil =
                operasi.daftarKoreksi(SEKOLAH, TGL_17, null, 0, 20);

        assertThat(hasil.getItems()).isEmpty();
        assertThat(hasil.getTotal()).isZero();
    }

    @Test
    @DisplayName("tenant-scoped: koreksi sekolah lain tidak bocor")
    void tenantScoped() {
        HalamanResponse<MutasiKoreksiItem> hasil =
                operasi.daftarKoreksi(SEKOLAH_LAIN, null, null, 0, 20);

        assertThat(hasil.getItems()).extracting(MutasiKoreksiItem::getId).containsExactly(5L);
        assertThat(hasil.getItems()).extracting(MutasiKoreksiItem::getSiswaId)
                .containsExactly(SISWA_LAIN);
    }

    @Test
    @DisplayName("paginasi: halaman & ukuran dihormati")
    void paginasi() {
        HalamanResponse<MutasiKoreksiItem> hal0 = operasi.daftarKoreksi(SEKOLAH, null, null, 0, 2);
        HalamanResponse<MutasiKoreksiItem> hal1 = operasi.daftarKoreksi(SEKOLAH, null, null, 1, 2);

        assertThat(hal0.getItems()).extracting(MutasiKoreksiItem::getId).containsExactly(4L, 3L);
        assertThat(hal1.getItems()).extracting(MutasiKoreksiItem::getId).containsExactly(2L);
        assertThat(hal0.getTotal()).isEqualTo(3L);
        assertThat(hal0.getTotalHalaman()).isEqualTo(2);
    }

    @Test
    @DisplayName("ukuran < 1 atau > 200 → 400 (bukan diam-diam diclamp), halaman negatif tetap 0")
    void ukuranTidakWajar() {
        assertThatThrownBy(() -> operasi.daftarKoreksi(SEKOLAH, null, null, 0, 0))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("ukuran minimal 1");
        assertThatThrownBy(() -> operasi.daftarKoreksi(SEKOLAH, null, null, 0, 99_999))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("ukuran maksimal 200");
        assertThat(operasi.daftarKoreksi(SEKOLAH, null, null, -3, 20).getHalaman()).isZero();
    }

    // ────────────────────────────────────────────────────────────────
    // Isi respons (yang dibutuhkan UI koreksi bendahara)
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("saldoSebelum dihitung dari arah: KREDIT turun, DEBIT naik")
    void saldoSebelumDihitung() {
        HalamanResponse<MutasiKoreksiItem> hasil =
                operasi.daftarKoreksi(SEKOLAH, null, null, 0, 20);

        // id=2: DEBIT 10.000, saldoSetelah 40.000 → sebelum 50.000
        assertThat(hasil.getItems()).filteredOn(m -> m.getId().equals(2L)).singleElement()
                .satisfies(m -> {
                    assertThat(m.getArah()).isEqualTo(ArahMutasi.DEBIT);
                    assertThat(m.getNominal()).isEqualTo(10_000L);
                    assertThat(m.getSaldoSebelum()).isEqualTo(50_000L);
                    assertThat(m.getSaldoSetelah()).isEqualTo(40_000L);
                });

        // id=3: KREDIT 25.000, saldoSetelah 25.000 → sebelum 0
        assertThat(hasil.getItems()).filteredOn(m -> m.getId().equals(3L)).singleElement()
                .satisfies(m -> {
                    assertThat(m.getArah()).isEqualTo(ArahMutasi.KREDIT);
                    assertThat(m.getSaldoSebelum()).isZero();
                    assertThat(m.getSaldoSetelah()).isEqualTo(25_000L);
                });
    }

    @Test
    @DisplayName("subjek siswa: siswaId terisi, subjekNama null (master siswa milik admin-be)")
    void namaSubjek() {
        HalamanResponse<MutasiKoreksiItem> hasil =
                operasi.daftarKoreksi(SEKOLAH, null, null, 0, 20);

        assertThat(hasil.getItems()).filteredOn(m -> m.getId().equals(3L)).singleElement()
                .satisfies(m -> {
                    assertThat(m.getSubjekTipe()).isEqualTo(SubjekTipe.SISWA);
                    assertThat(m.getSiswaId()).isEqualTo(SISWA_B);
                    // Nama siswa tidak ada di kantin-be (ADR-0002) → null.
                    assertThat(m.getSubjekNama()).isNull();
                });

        assertThat(hasil.getItems()).filteredOn(m -> m.getId().equals(2L)).singleElement()
                .satisfies(m -> {
                    assertThat(m.getSubjekTipe()).isEqualTo(SubjekTipe.SISWA);
                    assertThat(m.getSiswaId()).isEqualTo(SISWA_A);
                    assertThat(m.getSubjekNama()).isNull();
                });
    }

    @Test
    @DisplayName("jejak audit terbawa: keterangan, referensi, aktor, transaksi")
    void jejakAudit() {
        HalamanResponse<MutasiKoreksiItem> hasil =
                operasi.daftarKoreksi(SEKOLAH, null, null, 0, 20);

        assertThat(hasil.getItems()).filteredOn(m -> m.getId().equals(2L)).singleElement()
                .satisfies(m -> {
                    assertThat(m.getKeterangan()).isEqualTo("salah input topup");
                    assertThat(m.getReferensiId()).isEqualTo("BA-2026-10-15-002");
                    assertThat(m.getAktorId()).isEqualTo(77L);
                    assertThat(m.getWaktu()).isNotNull();
                });
    }

    // ────────────────────────────────────────────────────────────────
    // Helper seed
    // ────────────────────────────────────────────────────────────────

    private void ledger(long id, long sekolah, long siswaId, String arah,
                        String jenis, long nominal, long saldoSetelah, String waktu,
                        String keterangan) {
        jdbc.update("""
                INSERT INTO mutasi_saldo (id, sekolah_id, siswa_id, arah, jenis,
                                          nominal, saldo_setelah, referensi_id, keterangan,
                                          aktor_id, waktu)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::timestamptz)
                """, id, sekolah, siswaId, arah, jenis,
                nominal, saldoSetelah, "BA-2026-10-15-00" + id, keterangan, AKTOR, waktu);
    }
}
