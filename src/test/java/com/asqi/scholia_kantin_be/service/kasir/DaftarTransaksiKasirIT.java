package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.dto.HalamanResponse;
import com.asqi.scholia_kantin_be.dto.TransaksiKasirItem;
import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
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
 * Uji integrasi <b>{@code GET /api/kasir/transaksi}</b> — daftar transaksi untuk
 * koreksi bendahara (PRD §6.3, §9.2; issue <b>#212</b>) di atas PostgreSQL nyata.
 *
 * <p><b>Kenapa IT, bukan uji controller ber-mock.</b> Pelajaran #198/#199: bug
 * query (tipe parameter, dialect, batas hari) <b>tidak</b> muncul saat service
 * di-mock — SQL-nya tak pernah dieksekusi. Uji ini menutup <b>semua</b> filter
 * yang dijanjikan issue (#212 acceptance criteria) plus paginasi, isolasi tenant,
 * dan resolusi nama.
 *
 * <p><b>Batas hari</b> diuji eksplisit (23:45 vs 00:15) karena filter
 * {@code tanggal} harus mengikuti <b>zona waktu sekolah</b> ({@code JamKantin}),
 * bukan zona server.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class DaftarTransaksiKasirIT {

    private static final long SEKOLAH = 1L;
    private static final long SEKOLAH_LAIN = 2L;

    private static final long TITIK_UTAMA = 1L;
    private static final long TITIK_BELAKANG = 2L;
    private static final long TITIK_LAIN = 3L;

    // Subjek transaksi = SISWA (skema v2 tidak lagi punya kartu tamu).
    private static final long SISWA_A = 5001L;
    private static final long SISWA_B = 5002L;
    private static final long SISWA_C = 5003L;
    private static final long SISWA_D = 5004L;
    private static final long SISWA_LAIN = 5101L;

    private static final long PETUGAS_A = 42L;
    private static final long PETUGAS_B = 43L;
    private static final long PETUGAS_LAIN = 44L;

    private static final long KATEGORI = 100L;
    private static final long MENU_KOPI = 900L;
    private static final long MENU_PISANG = 901L;
    private static final long MENU_AIR = 902L;

    private static final long SESI_15 = 11L;   // sekolah 1, titik utama, 15 Okt
    private static final long SESI_16 = 12L;   // sekolah 1, titik utama, 16 Okt
    private static final long SESI_16B = 13L;  // sekolah 1, titik belakang, 16 Okt
    private static final long SESI_LAIN = 14L; // sekolah lain

    private static final LocalDate TGL_15 = LocalDate.parse("2026-10-15");
    private static final LocalDate TGL_16 = LocalDate.parse("2026-10-16");

    @Autowired
    private TransaksiKasirService service;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void siapkan() {
        TenantContext.clear();
        jdbc.execute("TRUNCATE TABLE transaksi_item, transaksi, sesi_kasir, titik_kasir, kartu, "
                + "menu, kategori_menu CASCADE");

        // Induk wajib skema v2 (FK): sekolah → siswa/pengguna.
        FixtureInduk.sekolah(jdbc, SEKOLAH, SEKOLAH_LAIN);
        FixtureInduk.pengguna(jdbc, SEKOLAH, PETUGAS_A, PETUGAS_B);
        FixtureInduk.pengguna(jdbc, SEKOLAH_LAIN, PETUGAS_LAIN);
        FixtureInduk.siswa(jdbc, SEKOLAH, SISWA_A, SISWA_B, SISWA_C, SISWA_D);
        FixtureInduk.siswa(jdbc, SEKOLAH_LAIN, SISWA_LAIN);

        titik(TITIK_UTAMA, SEKOLAH, "Kasir Utama");
        titik(TITIK_BELAKANG, SEKOLAH, "Kasir Belakang");
        titik(TITIK_LAIN, SEKOLAH_LAIN, "Kasir Sekolah Lain");

        sesi(SESI_15, SEKOLAH, TITIK_UTAMA, "2026-10-15");
        sesi(SESI_16, SEKOLAH, TITIK_UTAMA, "2026-10-16");
        sesi(SESI_16B, SEKOLAH, TITIK_BELAKANG, "2026-10-16");
        sesi(SESI_LAIN, SEKOLAH_LAIN, TITIK_LAIN, "2026-10-16");

        kartu("UID-A", SEKOLAH, SISWA_A, "KRT-001");
        kartu("UID-B", SEKOLAH, SISWA_B, "KRT-002");
        kartu("UID-D", SEKOLAH, SISWA_D, "KRT-004");
        kartu("UID-L", SEKOLAH_LAIN, SISWA_LAIN, "KRT-003");

        // id=1: 15 Okt 08:00 (SISWA + kartu) — sekolah 1
        trx(1L, SEKOLAH, SESI_15, TITIK_UTAMA, SISWA_A, "UID-A", PETUGAS_A, 15000L,
                "SUKSES", "2026-10-15T08:00:00+07:00");
        // id=2: 15 Okt 23:45 (SISWA, bayar tunai tanpa kartu) — MASIH tanggal 15 menurut zona sekolah
        trx(2L, SEKOLAH, SESI_15, TITIK_UTAMA, SISWA_B, null, PETUGAS_A, 12000L,
                "SUKSES", "2026-10-15T23:45:00+07:00");
        // id=3: 16 Okt 00:15 (SISWA, VOID) — sudah masuk tanggal 16
        trx(3L, SEKOLAH, SESI_16, TITIK_UTAMA, SISWA_C, null, PETUGAS_B, 20000L,
                "VOID", "2026-10-16T00:15:00+07:00");
        // id=4: 16 Okt 10:00 (SISWA, titik belakang)
        trx(4L, SEKOLAH, SESI_16B, TITIK_BELAKANG, SISWA_D, "UID-D", PETUGAS_B, 8000L,
                "SUKSES", "2026-10-16T10:00:00+07:00");
        // id=5: SEKOLAH LAIN — tidak boleh bocor ke tenant SEKOLAH
        trx(5L, SEKOLAH_LAIN, SESI_LAIN, TITIK_LAIN, SISWA_LAIN, "UID-L", PETUGAS_LAIN, 9000L,
                "SUKSES", "2026-10-16T11:00:00+07:00");

        // Katalog minimum — transaksi_item ber-FK ke menu & kategori_menu.
        kategori(KATEGORI, SEKOLAH, "Minuman");
        menu(MENU_KOPI, SEKOLAH, KATEGORI, "Kopi Susu", 8000L);
        menu(MENU_PISANG, SEKOLAH, KATEGORI, "Pisang Goreng", 15000L);
        menu(MENU_AIR, SEKOLAH, KATEGORI, "Air Mineral", 4000L);

        // Rincian item — dipakai uji "rincian item terbawa".
        // trx 1: 2x Kopi Susu (qty>1 → ringkasan menulis "2x ") + 1x Pisang Goreng.
        item(1L, 1L, MENU_KOPI, "Kopi Susu", KATEGORI, 8000L, 2, 16000L);
        item(2L, 1L, MENU_PISANG, "Pisang Goreng", KATEGORI, 15000L, 1, 15000L);
        // trx 4: satu item tanpa qty>1 → ringkasan tidak menulis "1x ".
        item(3L, 4L, MENU_AIR, "Air Mineral", KATEGORI, 4000L, 1, 4000L);
    }

    // ────────────────────────────────────────────────────────────────
    // Semua filter (acceptance criteria #212)
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("tanpa filter → semua transaksi tenant, urut waktu DESC")
    void tanpaFilter() {
        HalamanResponse<TransaksiKasirItem> hasil = service.daftar(SEKOLAH, null, null, null, 0, 20);

        assertThat(hasil.getItems()).extracting(TransaksiKasirItem::getId)
                .containsExactly(4L, 3L, 2L, 1L);
        assertThat(hasil.getTotal()).isEqualTo(4L);
    }

    @Test
    @DisplayName("filter sesiId → hanya transaksi sesi itu")
    void filterSesiId() {
        HalamanResponse<TransaksiKasirItem> hasil =
                service.daftar(SEKOLAH, SESI_15, null, null, 0, 20);

        assertThat(hasil.getItems()).extracting(TransaksiKasirItem::getId).containsExactly(2L, 1L);
        assertThat(hasil.getTotal()).isEqualTo(2L);
    }

    @Test
    @DisplayName("filter tanggal → batas hari mengikuti zona sekolah (23:45 vs 00:15)")
    void filterTanggal() {
        HalamanResponse<TransaksiKasirItem> tgl16 =
                service.daftar(SEKOLAH, null, TGL_16, null, 0, 20);
        HalamanResponse<TransaksiKasirItem> tgl15 =
                service.daftar(SEKOLAH, null, TGL_15, null, 0, 20);

        // 16 Okt: 00:15 (id=3) & 10:00 (id=4) — 23:45 milik 15 Okt, bukan di sini.
        assertThat(tgl16.getItems()).extracting(TransaksiKasirItem::getId).containsExactly(4L, 3L);
        // 15 Okt: 08:00 (id=1) & 23:45 (id=2).
        assertThat(tgl15.getItems()).extracting(TransaksiKasirItem::getId).containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("filter status → SUKSES / VOID")
    void filterStatus() {
        HalamanResponse<TransaksiKasirItem> void_ =
                service.daftar(SEKOLAH, null, null, StatusTransaksi.VOID, 0, 20);
        HalamanResponse<TransaksiKasirItem> sukses =
                service.daftar(SEKOLAH, null, null, StatusTransaksi.SUKSES, 0, 20);

        assertThat(void_.getItems()).extracting(TransaksiKasirItem::getId).containsExactly(3L);
        assertThat(sukses.getItems()).extracting(TransaksiKasirItem::getId)
                .containsExactly(4L, 2L, 1L);
    }

    @Test
    @DisplayName("filter kombinasi (sesiId + status) → terpenuhi keduanya")
    void filterKombinasi() {
        HalamanResponse<TransaksiKasirItem> hasil =
                service.daftar(SEKOLAH, SESI_15, null, StatusTransaksi.SUKSES, 0, 20);

        assertThat(hasil.getItems()).extracting(TransaksiKasirItem::getId).containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("filter tanpa hasil → halaman kosong (bukan 500)")
    void filterTanpaHasil() {
        HalamanResponse<TransaksiKasirItem> hasil =
                service.daftar(SEKOLAH, SESI_16B, TGL_15, null, 0, 20);

        assertThat(hasil.getItems()).isEmpty();
        assertThat(hasil.getTotal()).isZero();
    }

    @Test
    @DisplayName("tenant-scoped: transaksi sekolah lain tidak bocor")
    void tenantScoped() {
        HalamanResponse<TransaksiKasirItem> hasil =
                service.daftar(SEKOLAH_LAIN, null, null, null, 0, 20);

        assertThat(hasil.getItems()).extracting(TransaksiKasirItem::getId).containsExactly(5L);
        assertThat(hasil.getItems()).extracting(TransaksiKasirItem::getSiswaId)
                .containsExactly(SISWA_LAIN);
    }

    @Test
    @DisplayName("paginasi: halaman & ukuran dihormati")
    void paginasi() {
        HalamanResponse<TransaksiKasirItem> hal0 = service.daftar(SEKOLAH, null, null, null, 0, 2);
        HalamanResponse<TransaksiKasirItem> hal1 = service.daftar(SEKOLAH, null, null, null, 1, 2);

        assertThat(hal0.getItems()).extracting(TransaksiKasirItem::getId).containsExactly(4L, 3L);
        assertThat(hal1.getItems()).extracting(TransaksiKasirItem::getId).containsExactly(2L, 1L);
        assertThat(hal0.getTotal()).isEqualTo(4L);
        assertThat(hal0.getTotalHalaman()).isEqualTo(2);
        assertThat(hal0.getHalaman()).isZero();
        assertThat(hal0.getUkuran()).isEqualTo(2);
    }

    @Test
    @DisplayName("ukuran < 1 atau > 200 → 400 (bukan diam-diam diclamp), halaman negatif tetap 0")
    void ukuranTidakWajar() {
        assertThatThrownBy(() -> service.daftar(SEKOLAH, null, null, null, 0, 0))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("ukuran minimal 1");
        assertThatThrownBy(() -> service.daftar(SEKOLAH, null, null, null, 0, 99_999))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("ukuran maksimal 200");
        assertThat(service.daftar(SEKOLAH, null, null, null, -3, 20).getHalaman()).isZero();
    }

    // ────────────────────────────────────────────────────────────────
    // Isi respons (yang dibutuhkan UI koreksi bendahara)
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("nama titik kasir & subjek siswa terisi (batch, bukan N+1)")
    void namaTerisi() {
        HalamanResponse<TransaksiKasirItem> hasil = service.daftar(SEKOLAH, null, null, null, 0, 20);

        assertThat(hasil.getItems())
                .filteredOn(t -> t.getId().equals(4L))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.getTitikKasirNama()).isEqualTo("Kasir Belakang");
                    assertThat(t.getSubjekTipe()).isEqualTo(SubjekTipe.SISWA);
                    assertThat(t.getSiswaId()).isEqualTo(SISWA_D);
                    assertThat(t.getKartuUid()).isEqualTo("UID-D");
                });

        assertThat(hasil.getItems())
                .filteredOn(t -> t.getId().equals(2L))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.getTitikKasirNama()).isEqualTo("Kasir Utama");
                    assertThat(t.getSiswaId()).isEqualTo(SISWA_B);
                    // Bayar tunai tanpa kartu → jejak kartu memang kosong.
                    assertThat(t.getKartuUid()).isNull();
                });

        // Nama siswa tidak ada di kantin-be (data siswa milik admin-be, ADR-0002) → null.
        assertThat(hasil.getItems())
                .filteredOn(t -> t.getId().equals(1L))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.getSubjekTipe()).isEqualTo(SubjekTipe.SISWA);
                    assertThat(t.getSubjekNama()).isNull();
                });
    }

    @Test
    @DisplayName("VOID: dapatDikoreksi=false + alasan/waktu koreksi terbawa")
    void voidTidakDapatDikoreksi() {
        HalamanResponse<TransaksiKasirItem> hasil =
                service.daftar(SEKOLAH, null, null, StatusTransaksi.VOID, 0, 20);

        assertThat(hasil.getItems()).singleElement().satisfies(t -> {
            assertThat(t.getStatus()).isEqualTo(StatusTransaksi.VOID);
            assertThat(t.isDapatDikoreksi()).isFalse();
            assertThat(t.getAlasanVoid()).isEqualTo("salah input uji");
            assertThat(t.getVoidAt()).isNotNull();
        });

        assertThat(service.daftar(SEKOLAH, null, null, StatusTransaksi.SUKSES, 0, 20).getItems())
                .allSatisfy(t -> assertThat(t.isDapatDikoreksi()).isTrue());
    }

    @Test
    @DisplayName("rincian item terbawa (items[] + ringkasanItem) — bendahara perlu tahu apa yang dibeli")
    void rincianItemTerbawa() {
        HalamanResponse<TransaksiKasirItem> hasil =
                service.daftar(SEKOLAH, null, null, null, 0, 20);

        // trx 1: dua baris item, qty>1 pada baris pertama.
        assertThat(hasil.getItems())
                .filteredOn(t -> t.getId().equals(1L))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.getItems()).hasSize(2);
                    assertThat(t.getItems())
                            .extracting(TransaksiKasirItem.Item::nama)
                            .containsExactly("Kopi Susu", "Pisang Goreng");
                    assertThat(t.getItems().get(0).qty()).isEqualTo(2);
                    assertThat(t.getItems().get(0).harga()).isEqualTo(8000L);
                    assertThat(t.getItems().get(0).subtotal()).isEqualTo(16000L);
                    // qty>1 ditulis, qty=1 tidak (ringkasan tetap ringkas).
                    assertThat(t.getRingkasanItem()).isEqualTo("2x Kopi Susu, Pisang Goreng");
                });

        // trx 4: satu item, qty=1 → ringkasan tanpa "1x ".
        assertThat(hasil.getItems())
                .filteredOn(t -> t.getId().equals(4L))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.getItems()).hasSize(1);
                    assertThat(t.getRingkasanItem()).isEqualTo("Air Mineral");
                });

        // Transaksi tanpa item (trx 2): items[] kosong — bukan null, FE aman render.
        assertThat(hasil.getItems())
                .filteredOn(t -> t.getId().equals(2L))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.getItems()).isEmpty();
                    assertThat(t.getRingkasanItem()).isNull();
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

    private void sesi(long id, long sekolah, long titikId, String tanggal) {
        jdbc.update("""
                INSERT INTO sesi_kasir (id, sekolah_id, titik_kasir_id, tanggal, status)
                VALUES (?, ?, ?, ?::date, 'TERBUKA')
                """, id, sekolah, titikId, tanggal);
    }

    private void kartu(String uid, long sekolah, long siswaId, String nomor) {
        jdbc.update("""
                INSERT INTO kartu (uid, sekolah_id, siswa_id, nomor_kartu)
                VALUES (?, ?, ?, ?)
                """, uid, sekolah, siswaId, nomor);
    }

    private void kategori(long id, long sekolah, String nama) {
        jdbc.update("""
                INSERT INTO kategori_menu (id, sekolah_id, nama, urutan)
                VALUES (?, ?, ?, ?)
                """, id, sekolah, nama, (int) id);
    }

    private void menu(long id, long sekolah, long kategoriId, String nama, long harga) {
        jdbc.update("""
                INSERT INTO menu (id, sekolah_id, kategori_id, nama, harga_jual, satuan, stok_minimum)
                VALUES (?, ?, ?, ?, ?, 'PCS', 0)
                """, id, sekolah, kategoriId, nama, harga);
    }

    private void trx(long id, long sekolah, long sesiId, long titikId, long siswaId, String kartuUid,
                     long petugasId, long total, String status, String waktu) {
        boolean diVoid = "VOID".equals(status);
        jdbc.update("""
                INSERT INTO transaksi (id, idempotency_key, sekolah_id, sesi_kasir_id, titik_kasir_id,
                                       siswa_id, kartu_uid, petugas_id, total, total_hpp, status,
                                       alasan_void, void_at, void_oleh, waktu)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?,
                        ?, ?::timestamptz, ?, ?::timestamptz)
                """, id, "seed-212-" + id, sekolah, sesiId, titikId,
                siswaId, kartuUid, petugasId, total, status,
                diVoid ? "salah input uji" : null,
                diVoid ? waktu : null,
                diVoid ? petugasId : null,
                waktu);
    }

    /** Baris {@code transaksi_item} (snapshot nama &amp; harga saat transaksi). */
    private void item(long id, long transaksiId, long menuId, String namaMenu, long kategoriId,
                      long hargaJual, int qty, long subtotal) {
        jdbc.update("""
                INSERT INTO transaksi_item (id, transaksi_id, menu_id, nama_menu, kategori_id,
                                            harga_jual, qty, hpp_snapshot, subtotal)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?)
                """, id, transaksiId, menuId, namaMenu, kategoriId, hargaJual, qty, subtotal);
    }
}
