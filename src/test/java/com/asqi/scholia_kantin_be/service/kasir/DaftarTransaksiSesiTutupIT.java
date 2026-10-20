package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.dto.TransaksiSesiTutupItem;
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
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji integrasi <b>{@code GET /api/transaksi/sesi-tutup}</b> — daftar transaksi
 * pada sesi yang sudah DITUTUP sebagai kandidat koreksi bendahara
 * (PRD §6.3, §9.2; issue <b>#230</b>) di atas PostgreSQL nyata.
 *
 * <p><b>Kenapa IT, bukan uji controller ber-mock.</b> Pelajaran #198/#199: bug
 * query (join lintas entitas, tipe parameter, batas hari) tidak muncul saat
 * service di-mock — SQL-nya tak pernah dieksekusi. Uji ini menjalankan query
 * {@code Transaksi ⨝ SesiKasir} sungguhan.
 *
 * <p><b>Kontrak FE dijaga eksplisit.</b> Halaman {@code KoreksiBendaharaPage}
 * membaca field {@code snake_case} ({@code subjek_nama}, {@code items[].menu_id},
 * …) dan memanggil {@code .toLowerCase()} pada {@code subjek_nama}/{@code subjek_info}
 * — karena itu uji terakhir memastikan JSON-nya benar-benar snake_case dan
 * kedua field itu <b>tidak pernah null</b> (null = halaman crash).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class DaftarTransaksiSesiTutupIT {

    private static final long SEKOLAH = 1L;
    private static final long SEKOLAH_LAIN = 2L;

    private static final long TITIK_UTAMA = 1L;
    private static final long TITIK_LAIN = 3L;
    private static final long TITIK_BUKA = 4L; // titik lain utk sesi yang masih TERBUKA

    private static final long SESI_TUTUP = 21L;   // sekolah 1, DITUTUP, hari ini
    private static final long SESI_TUTUP_LAIN = 22L; // sekolah lain, DITUTUP
    private static final long SESI_BUKA = 23L;    // sekolah 1, titik lain, masih TERBUKA
    private static final long SESI_LAMA = 24L;    // sekolah 1, DITUTUP, 30 hari lalu

    @Autowired
    private TransaksiKasirService service;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Mapper yang sama dengan lapisan HTTP Spring Boot 4 (Jackson 3,
     * {@code tools.jackson}) — supaya uji ini benar-benar memverifikasi JSON
     * yang diterima FE, bukan mapper lain.
     */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @BeforeEach
    void siapkan() {
        TenantContext.clear();
        jdbc.execute("TRUNCATE TABLE transaksi, transaksi_item, sesi_kasir, titik_kasir, "
                + "kartu_tamu, saldo_ledger CASCADE");

        titik(TITIK_UTAMA, SEKOLAH, "Kasir Utama");
        titik(TITIK_BUKA, SEKOLAH, "Kasir Cadangan");
        titik(TITIK_LAIN, SEKOLAH_LAIN, "Kasir Sekolah Lain");

        LocalDate hariIni = LocalDate.now();
        String tglHariIni = hariIni.toString();
        String tglLama = hariIni.minusDays(30).toString();

        sesi(SESI_TUTUP, SEKOLAH, TITIK_UTAMA, tglHariIni, "DITUTUP");
        sesi(SESI_BUKA, SEKOLAH, TITIK_BUKA, tglHariIni, "TERBUKA");
        sesi(SESI_LAMA, SEKOLAH, TITIK_UTAMA, tglLama, "DITUTUP");
        sesi(SESI_TUTUP_LAIN, SEKOLAH_LAIN, TITIK_LAIN, tglHariIni, "DITUTUP");

        kartu(100L, SEKOLAH, "KT-001", "Bu Sri");
        kartu(101L, SEKOLAH, "KT-002", null); // label kosong → fallback nomor kartu
        kartu(102L, SEKOLAH_LAIN, "KT-003", "Bapak Lain");

        // Sesi tertutup: satu SUKSES (dengan 2 item) + satu VOID (sudah dikoreksi).
        trx(1L, SEKOLAH, SESI_TUTUP, TITIK_UTAMA, "KARTU_TAMU", 100L, 42L, 19000L,
                "SUKSES", tglHariIni + "T08:00:00+07:00");
        trx(2L, SEKOLAH, SESI_TUTUP, TITIK_UTAMA, "KARTU_TAMU", 101L, 42L, 8000L,
                "VOID", tglHariIni + "T09:00:00+07:00");
        // Sesi masih terbuka — TIDAK boleh muncul (belum boleh dikoreksi bendahara).
        trx(3L, SEKOLAH, SESI_BUKA, TITIK_BUKA, "KARTU_TAMU", 100L, 42L, 5000L,
                "SUKSES", tglHariIni + "T10:00:00+07:00");
        // Sesi tertutup 30 hari lalu — di luar jendela default 7 hari.
        trx(4L, SEKOLAH, SESI_LAMA, TITIK_UTAMA, "KARTU_TAMU", 100L, 42L, 4000L,
                "SUKSES", tglLama + "T08:00:00+07:00");
        // Tenant lain — tidak boleh bocor.
        trx(5L, SEKOLAH_LAIN, SESI_TUTUP_LAIN, TITIK_LAIN, "KARTU_TAMU", 102L, 44L, 9000L,
                "SUKSES", tglHariIni + "T11:00:00+07:00");

        item(11L, 1L, 2L, "Nasi Goreng Ayam", 15000L, 1, 15000L);
        item(12L, 1L, 9L, "Es Teh Manis", 4000L, 1, 4000L);
        item(13L, 2L, 2L, "Nasi Goreng Ayam", 15000L, 1, 8000L);

        // Mutasi koreksi atas transaksi #2 (VOID oleh bendahara) → referensi tampil.
        ledgerKoreksi(900L, SEKOLAH, 2L, "BA-KOR-2026-0001");
    }

    // ────────────────────────────────────────────────────────────────
    // Filter & isi
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("hanya transaksi sesi DITUTUP (sesi terbuka tidak ikut)")
    void hanyaSesiDitutup() {
        List<TransaksiSesiTutupItem> hasil = service.sesiTutup(SEKOLAH, null, null, null, 0);

        assertThat(hasil).extracting(TransaksiSesiTutupItem::id)
                .containsExactly("2", "1"); // terbaru dulu, #3 (sesi terbuka) tidak ada
    }

    @Test
    @DisplayName("jendela default 7 hari: sesi tertutup 30 hari lalu tidak ikut")
    void jendelaDefault() {
        List<TransaksiSesiTutupItem> hasil = service.sesiTutup(SEKOLAH, null, null, null, 0);

        assertThat(hasil).extracting(TransaksiSesiTutupItem::id).doesNotContain("4");
    }

    @Test
    @DisplayName("filter tanggal eksplisit menjangkau transaksi lama")
    void filterTanggalLama() {
        LocalDate lama = LocalDate.now().minusDays(30);
        List<TransaksiSesiTutupItem> hasil = service.sesiTutup(SEKOLAH, lama, null, null, 0);

        assertThat(hasil).extracting(TransaksiSesiTutupItem::id).containsExactly("4");
    }

    @Test
    @DisplayName("tenant-scoped: transaksi sekolah lain tidak bocor")
    void tenantScoped() {
        List<TransaksiSesiTutupItem> hasil = service.sesiTutup(SEKOLAH_LAIN, null, null, null, 0);

        assertThat(hasil).extracting(TransaksiSesiTutupItem::id).containsExactly("5");
        assertThat(hasil).singleElement()
                .satisfies(t -> assertThat(t.subjekNama()).isEqualTo("Bapak Lain"));
    }

    @Test
    @DisplayName("status: SUKSES → SELESAI, VOID → DIKOREKSI + referensi koreksi")
    void statusDanReferensi() {
        List<TransaksiSesiTutupItem> hasil = service.sesiTutup(SEKOLAH, null, null, null, 0);

        TransaksiSesiTutupItem dikoreksi = hasil.stream()
                .filter(t -> t.id().equals("2")).findFirst().orElseThrow();
        assertThat(dikoreksi.status()).isEqualTo("DIKOREKSI");
        assertThat(dikoreksi.koreksiReferensiId()).isEqualTo("BA-KOR-2026-0001");

        TransaksiSesiTutupItem selesai = hasil.stream()
                .filter(t -> t.id().equals("1")).findFirst().orElseThrow();
        assertThat(selesai.status()).isEqualTo("SELESAI");
        assertThat(selesai.koreksiReferensiId()).isNull();
    }

    @Test
    @DisplayName("rincian item transaksi ikut (tanpa N+1, urutan tidak dijamin)")
    void rincianItem() {
        List<TransaksiSesiTutupItem> hasil = service.sesiTutup(SEKOLAH, null, null, null, 0);

        TransaksiSesiTutupItem trx1 = hasil.stream()
                .filter(t -> t.id().equals("1")).findFirst().orElseThrow();
        assertThat(trx1.items()).hasSize(2)
                .extracting(TransaksiSesiTutupItem.Item::nama)
                .containsExactlyInAnyOrder("Nasi Goreng Ayam", "Es Teh Manis");
        assertThat(trx1.items()).anySatisfy(i -> {
            assertThat(i.menuId()).isEqualTo(2L);
            assertThat(i.harga()).isEqualTo(15000L);
            assertThat(i.subtotal()).isEqualTo(15000L);
        });
        assertThat(trx1.total()).isEqualTo(19000L);
    }

    @Test
    @DisplayName("nama titik kasir & label kartu terisi; subjek tanpa label pakai nomor kartu")
    void namaTerisi() {
        List<TransaksiSesiTutupItem> hasil = service.sesiTutup(SEKOLAH, null, null, null, 0);

        assertThat(hasil).allSatisfy(t -> {
            assertThat(t.titikKasir()).isEqualTo("Kasir Utama");
            assertThat(t.petugasKasir()).isEmpty(); // data admin-be — tidak difabrikasi
        });
        assertThat(hasil).filteredOn(t -> t.id().equals("1")).singleElement()
                .satisfies(t -> {
                    assertThat(t.subjekTipe()).isEqualTo(SubjekTipe.KARTU_TAMU);
                    assertThat(t.subjekNama()).isEqualTo("Bu Sri");
                    assertThat(t.subjekInfo()).isEqualTo("KT-001");
                });
        assertThat(hasil).filteredOn(t -> t.id().equals("2")).singleElement()
                .satisfies(t -> assertThat(t.subjekNama()).isEqualTo("KT-002"));
    }

    @Test
    @DisplayName("subjek SISWA tanpa data port → nama/info string kosong (bukan null)")
    void siswaTanpaDataPortTetapString() {
        // Subjek SISWA yang tidak ada di port (CariSiswaDemo kosong di profil uji).
        trx(6L, SEKOLAH, SESI_TUTUP, TITIK_UTAMA, "SISWA", 777001L, 42L, 11000L,
                "SUKSES", LocalDate.now() + "T12:00:00+07:00");

        List<TransaksiSesiTutupItem> hasil = service.sesiTutup(SEKOLAH, null, null, null, 0);

        assertThat(hasil).filteredOn(t -> t.id().equals("6")).singleElement()
                .satisfies(t -> {
                    assertThat(t.subjekTipe()).isEqualTo(SubjekTipe.SISWA);
                    assertThat(t.subjekNama()).isNotNull();
                    assertThat(t.subjekInfo()).isNotNull();
                });
    }

    @Test
    @DisplayName("batas baris dihormati & tenant tanpa data → list kosong (bukan 500)")
    void batasDanKosong() {
        assertThat(service.sesiTutup(SEKOLAH, null, null, null, 1)).hasSize(1);
        assertThat(service.sesiTutup(999L, null, null, null, 0)).isEmpty();
    }

    // ────────────────────────────────────────────────────────────────
    // Kontrak JSON untuk FE (issue #230)
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("JSON snake_case sesuai kontrak FE: subjek_nama, items[].menu_id, dst.")
    @SuppressWarnings("unchecked")
    void jsonSnakeCase() throws Exception {
        List<TransaksiSesiTutupItem> hasil = service.sesiTutup(SEKOLAH, null, null, null, 0);

        String json = MAPPER.writeValueAsString(hasil.get(0));
        Map<String, Object> map = MAPPER.readValue(json, new tools.jackson.core.type.TypeReference<Map<String, Object>>() { });

        assertThat(map).containsKeys("id", "waktu", "sesi_kasir_id", "titik_kasir",
                "petugas_kasir", "subjek_tipe", "subjek_id", "subjek_nama", "subjek_info",
                "total", "items", "status");
        assertThat(map.get("subjek_nama")).isInstanceOf(String.class);

        List<Map<String, Object>> items = (List<Map<String, Object>>) map.get("items");
        assertThat(items).isNotEmpty();
        assertThat(items.get(0)).containsKeys("menu_id", "nama", "qty", "harga", "subtotal");
    }

    // ────────────────────────────────────────────────────────────────
    // Helper seed
    // ────────────────────────────────────────────────────────────────

    private void titik(long id, long sekolah, String nama) {
        jdbc.update("INSERT INTO titik_kasir (id, sekolah_id, nama) VALUES (?, ?, ?)",
                id, sekolah, nama);
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

    private void item(long id, long transaksiId, long menuId, String nama, long harga,
                      int qty, long subtotal) {
        jdbc.update("""
                INSERT INTO transaksi_item (id, transaksi_id, menu_id, nama_menu, harga_jual,
                                            qty, hpp_snapshot, subtotal)
                VALUES (?, ?, ?, ?, ?, ?, 0, ?)
                """, id, transaksiId, menuId, nama, harga, qty, subtotal);
    }

    private void ledgerKoreksi(long id, long sekolah, long transaksiId, String referensiId) {
        jdbc.update("""
                INSERT INTO saldo_ledger (id, sekolah_id, subjek_tipe, subjek_id, arah, jenis,
                                          nominal, saldo_setelah, transaksi_id, referensi_id,
                                          keterangan, aktor_id, waktu)
                VALUES (?, ?, 'KARTU_TAMU', 101, 'KREDIT', 'KOREKSI',
                        ?, ?, ?, ?, 'Koreksi: salah input uji', 77, now())
                """, id, sekolah, 8000L, 8000L, transaksiId, referensiId);
    }
}
