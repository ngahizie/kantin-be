package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.dto.HalamanResponse;
import com.asqi.scholia_kantin_be.dto.TransaksiKasirItem;
import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
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
 * Uji integrasi <b>{@code GET /api/kasir/transaksi/sesi-tutup}</b> — daftar
 * transaksi <b>kandidat koreksi bendahara</b> (PRD §6.3, §9.2; issue
 * <b>#230</b>) di atas PostgreSQL nyata.
 *
 * <p><b>Kenapa IT, bukan uji controller ber-mock.</b> Pelajaran #198/#199:
 * bug query (tipe parameter, dialect, subquery) <b>tidak</b> muncul saat
 * service di-mock — SQL-nya tak pernah dieksekusi. Uji ini menutup kontrak
 * issue #230: hanya transaksi sesi {@code DITUTUP} milik tenant, filter
 * opsional, paginasi, dan flag {@code dapatDikoreksi}.
 *
 * <p>Beda kunci dengan {@link DaftarTransaksiKasirIT}: seed di sini sengaja
 * mencampur sesi {@code TERBUKA} dan {@code DITUTUP} — transaksi sesi terbuka
 * <b>tidak boleh</b> muncul di daftar kandidat koreksi.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class DaftarTransaksiSesiTutupIT {

    private static final long SEKOLAH = 1L;
    private static final long SEKOLAH_LAIN = 2L;

    private static final long TITIK_UTAMA = 1L;
    private static final long TITIK_SAMPING = 3L;
    private static final long TITIK_LAIN = 2L;

    private static final long SESI_TUTUP = 21L;    // sekolah 1, 15 Okt, DITUTUP
    private static final long SESI_TUTUP_B = 22L;  // sekolah 1, 16 Okt, DITUTUP
    private static final long SESI_TERBUKA = 23L;  // sekolah 1, titik samping, 16 Okt, TERBUKA
    private static final long SESI_LAIN = 24L;     // sekolah lain, DITUTUP

    private static final LocalDate TGL_15 = LocalDate.parse("2026-10-15");
    private static final LocalDate TGL_16 = LocalDate.parse("2026-10-16");

    @Autowired
    private TransaksiKasirService service;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void siapkan() {
        TenantContext.clear();
        jdbc.execute("TRUNCATE TABLE transaksi, sesi_kasir, titik_kasir, kartu_tamu CASCADE");

        titik(TITIK_UTAMA, SEKOLAH, "Kasir Utama");
        titik(TITIK_SAMPING, SEKOLAH, "Kasir Samping");
        titik(TITIK_LAIN, SEKOLAH_LAIN, "Kasir Sekolah Lain");

        sesi(SESI_TUTUP, SEKOLAH, TITIK_UTAMA, "2026-10-15", "DITUTUP");
        sesi(SESI_TUTUP_B, SEKOLAH, TITIK_UTAMA, "2026-10-16", "DITUTUP");
        sesi(SESI_TERBUKA, SEKOLAH, TITIK_SAMPING, "2026-10-16", "TERBUKA");
        sesi(SESI_LAIN, SEKOLAH_LAIN, TITIK_LAIN, "2026-10-16", "DITUTUP");

        kartu(100L, SEKOLAH, "KT-001", "Bu Sri");
        kartu(101L, SEKOLAH_LAIN, "KT-003", "Bapak Lain");

        // id=1: sesi DITUTUP (15 Okt) — SISWA → kandidat koreksi
        trx(1L, SEKOLAH, SESI_TUTUP, TITIK_UTAMA, "SISWA", 5001L, 42L, 15000L,
                "SUKSES", "2026-10-15T08:00:00+07:00");
        // id=2: sesi DITUTUP (15 Okt) — sudah VOID (masih tampil, tak dapat dikoreksi lagi)
        trx(2L, SEKOLAH, SESI_TUTUP, TITIK_UTAMA, "SISWA", 5002L, 42L, 12000L,
                "VOID", "2026-10-15T23:45:00+07:00");
        // id=3: sesi TERBUKA — TIDAK boleh muncul di daftar kandidat koreksi
        trx(3L, SEKOLAH, SESI_TERBUKA, TITIK_SAMPING, "SISWA", 5003L, 42L, 9000L,
                "SUKSES", "2026-10-16T10:00:00+07:00");
        // id=4: sesi DITUTUP (16 Okt) — KARTU_TAMU → nama terisi
        trx(4L, SEKOLAH, SESI_TUTUP_B, TITIK_UTAMA, "KARTU_TAMU", 100L, 43L, 8000L,
                "SUKSES", "2026-10-16T10:00:00+07:00");
        // id=5: SEKOLAH LAIN (sesi DITUTUP) — tidak boleh bocor ke tenant SEKOLAH
        trx(5L, SEKOLAH_LAIN, SESI_LAIN, TITIK_LAIN, "KARTU_TAMU", 101L, 44L, 7000L,
                "SUKSES", "2026-10-16T11:00:00+07:00");
    }

    // ────────────────────────────────────────────────────────────────
    // Kontrak inti issue #230
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("hanya transaksi sesi DITUTUP milik tenant (sesi TERBUKA dikecualikan)")
    void hanyaSesiTutup() {
        HalamanResponse<TransaksiKasirItem> hasil =
                service.daftarSesiTutup(SEKOLAH, null, null, null, 0, 20);

        // id=3 (sesi TERBUKA) tidak muncul; id=5 (tenant lain) tidak bocor.
        assertThat(hasil.getItems()).extracting(TransaksiKasirItem::getId)
                .containsExactly(4L, 2L, 1L);
        assertThat(hasil.getTotal()).isEqualTo(3L);
    }

    @Test
    @DisplayName("tenant-scoped: transaksi sekolah lain (sesi DITUTUP) tidak bocor")
    void tenantScoped() {
        HalamanResponse<TransaksiKasirItem> hasil =
                service.daftarSesiTutup(SEKOLAH_LAIN, null, null, null, 0, 20);

        assertThat(hasil.getItems()).extracting(TransaksiKasirItem::getId).containsExactly(5L);
        assertThat(hasil.getItems()).extracting(TransaksiKasirItem::getSubjekNama)
                .containsExactly("Bapak Lain");
    }

    @Test
    @DisplayName("filter sesiId → hanya transaksi sesi tertutup itu")
    void filterSesiId() {
        HalamanResponse<TransaksiKasirItem> hasil =
                service.daftarSesiTutup(SEKOLAH, SESI_TUTUP, null, null, 0, 20);

        assertThat(hasil.getItems()).extracting(TransaksiKasirItem::getId).containsExactly(2L, 1L);
        assertThat(hasil.getTotal()).isEqualTo(2L);
    }

    @Test
    @DisplayName("sesiId milik sesi TERBUKA → ditolak (bukan daftar kosong diam-diam)")
    void sesiTerbukaDitolak() {
        assertThatThrownBy(() -> service.daftarSesiTutup(
                SEKOLAH, SESI_TERBUKA, null, null, 0, 20))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("masih terbuka");
    }

    @Test
    @DisplayName("sesiId milik tenant lain → 404")
    void sesiTenantLainDitolak() {
        assertThatThrownBy(() -> service.daftarSesiTutup(
                SEKOLAH, SESI_LAIN, null, null, 0, 20))
                .isInstanceOf(NotFoundEntity.class);
    }

    @Test
    @DisplayName("sesiId tidak ada → 404")
    void sesiTidakAdaDitolak() {
        assertThatThrownBy(() -> service.daftarSesiTutup(
                SEKOLAH, 999L, null, null, 0, 20))
                .isInstanceOf(NotFoundEntity.class);
    }

    @Test
    @DisplayName("filter tanggal + status kombinasi tetap bekerja di atas filter sesi-tutup")
    void filterKombinasi() {
        HalamanResponse<TransaksiKasirItem> tgl15Sukses =
                service.daftarSesiTutup(SEKOLAH, null, TGL_15, StatusTransaksi.SUKSES, 0, 20);
        HalamanResponse<TransaksiKasirItem> tgl15Void =
                service.daftarSesiTutup(SEKOLAH, null, TGL_15, StatusTransaksi.VOID, 0, 20);
        HalamanResponse<TransaksiKasirItem> tgl16 =
                service.daftarSesiTutup(SEKOLAH, null, TGL_16, null, 0, 20);

        assertThat(tgl15Sukses.getItems()).extracting(TransaksiKasirItem::getId).containsExactly(1L);
        assertThat(tgl15Void.getItems()).extracting(TransaksiKasirItem::getId).containsExactly(2L);
        // 16 Okt: hanya id=4 — id=3 ada di 16 Okt tapi sesinya TERBUKA.
        assertThat(tgl16.getItems()).extracting(TransaksiKasirItem::getId).containsExactly(4L);
    }

    @Test
    @DisplayName("paginasi: halaman & ukuran dihormati")
    void paginasi() {
        HalamanResponse<TransaksiKasirItem> hal0 =
                service.daftarSesiTutup(SEKOLAH, null, null, null, 0, 2);
        HalamanResponse<TransaksiKasirItem> hal1 =
                service.daftarSesiTutup(SEKOLAH, null, null, null, 1, 2);

        assertThat(hal0.getItems()).extracting(TransaksiKasirItem::getId).containsExactly(4L, 2L);
        assertThat(hal1.getItems()).extracting(TransaksiKasirItem::getId).containsExactly(1L);
        assertThat(hal0.getTotal()).isEqualTo(3L);
        assertThat(hal0.getTotalHalaman()).isEqualTo(2);
        assertThat(hal0.getHalaman()).isZero();
        assertThat(hal0.getUkuran()).isEqualTo(2);
    }

    @Test
    @DisplayName("ukuran < 1 atau > 200 → 400 (bukan diam-diam diclamp)")
    void ukuranTidakWajar() {
        assertThatThrownBy(() -> service.daftarSesiTutup(SEKOLAH, null, null, null, 0, 0))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("ukuran minimal 1");
        assertThatThrownBy(() -> service.daftarSesiTutup(SEKOLAH, null, null, null, 0, 99_999))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("ukuran maksimal 200");
    }

    // ────────────────────────────────────────────────────────────────
    // Isi respons (yang dibutuhkan UI koreksi bendahara)
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("dapatDikoreksi: SUKSES=true, VOID=false + alasan/waktu void terbawa")
    void flagDapatDikoreksi() {
        HalamanResponse<TransaksiKasirItem> hasil =
                service.daftarSesiTutup(SEKOLAH, SESI_TUTUP, null, null, 0, 20);

        assertThat(hasil.getItems())
                .filteredOn(t -> t.getId().equals(1L))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.getStatus()).isEqualTo(StatusTransaksi.SUKSES);
                    assertThat(t.isDapatDikoreksi()).isTrue();
                });

        assertThat(hasil.getItems())
                .filteredOn(t -> t.getId().equals(2L))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.getStatus()).isEqualTo(StatusTransaksi.VOID);
                    assertThat(t.isDapatDikoreksi()).isFalse();
                    assertThat(t.getAlasanVoid()).isEqualTo("salah input uji");
                    assertThat(t.getVoidAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("nama titik kasir & subjek kartu tamu terisi (batch, bukan N+1)")
    void namaTerisi() {
        HalamanResponse<TransaksiKasirItem> hasil =
                service.daftarSesiTutup(SEKOLAH, null, null, null, 0, 20);

        assertThat(hasil.getItems())
                .filteredOn(t -> t.getId().equals(4L))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.getTitikKasirNama()).isEqualTo("Kasir Utama");
                    assertThat(t.getSubjekTipe()).isEqualTo(SubjekTipe.KARTU_TAMU);
                    assertThat(t.getSubjekNama()).isEqualTo("Bu Sri");
                });

        // SISWA: nama tidak ada di kantin-be (data siswa milik admin-be, ADR-0002) → null.
        assertThat(hasil.getItems())
                .filteredOn(t -> t.getId().equals(1L))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.getSubjekTipe()).isEqualTo(SubjekTipe.SISWA);
                    assertThat(t.getSubjekNama()).isNull();
                });
    }

    // ────────────────────────────────────────────────────────────────
    // Helper seed
    // ────────────────────────────────────────────────────────────────

    private void titik(long id, long sekolah, String nama) {
        jdbc.update("""
                INSERT INTO titik_kasir (id, sekolah_id, nama)
                VALUES (?, ?, ?)
                """, id, sekolah, nama);
    }

    private void sesi(long id, long sekolah, long titikId, String tanggal, String status) {
        jdbc.update("""
                INSERT INTO sesi_kasir (id, sekolah_id, titik_kasir_id, tanggal, status)
                VALUES (?, ?, ?, ?::date, ?)
                """, id, sekolah, titikId, tanggal, status);
    }

    private void kartu(long id, long sekolah, String nomor, String labelPemegang) {
        jdbc.update("""
                INSERT INTO kartu_tamu (id, sekolah_id, nomor_kartu, label_pemegang, dibuat_oleh)
                VALUES (?, ?, ?, ?, 42)
                """, id, sekolah, nomor, labelPemegang);
    }

    private void trx(long id, long sekolah, long sesiId, long titikId, String subjekTipe,
                     long subjekId, long petugasId, long total, String status, String waktu) {
        boolean diVoid = "VOID".equals(status);
        jdbc.update("""
                INSERT INTO transaksi (id, idempotency_key, sekolah_id, sesi_kasir_id, titik_kasir_id,
                                       subjek_tipe, subjek_id, petugas_id, total, total_hpp, status,
                                       alasan_void, void_at, void_oleh, waktu)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?,
                        ?, ?::timestamptz, ?, ?::timestamptz)
                """, id, "seed-230-" + id, sekolah, sesiId, titikId,
                subjekTipe, subjekId, petugasId, total, status,
                diVoid ? "salah input uji" : null,
                diVoid ? waktu : null,
                diVoid ? petugasId : null,
                waktu);
    }
}
