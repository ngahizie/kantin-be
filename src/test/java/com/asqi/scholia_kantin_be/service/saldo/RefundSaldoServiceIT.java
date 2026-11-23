package com.asqi.scholia_kantin_be.service.saldo;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.dto.HasilRefundResponse;
import com.asqi.scholia_kantin_be.dto.KandidatRefundItem;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.repository.KartuRepository;
import com.asqi.scholia_kantin_be.repository.OrangTuaRepository;
import com.asqi.scholia_kantin_be.repository.SaldoRepository;
import com.asqi.scholia_kantin_be.repository.SiswaRepository;
import com.asqi.scholia_kantin_be.service.CariSiswaService;
import com.asqi.scholia_kantin_be.service.integrasi.CariSiswaLokal;
import com.asqi.scholia_kantin_be.service.integrasi.CariSiswaPort;
import com.asqi.scholia_kantin_be.service.integrasi.InfoSiswa;
import com.asqi.scholia_kantin_be.service.integrasi.NotifikasiService;
import com.asqi.scholia_kantin_be.service.integrasi.StatusSiswaPort;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji integrasi refund saldo siswa keluar &amp; pindah ke saudara
 * (PRD §9.3, issue #38) dengan PostgreSQL nyata (Testcontainers).
 *
 * <p>Memakai <b>fake</b> {@link StatusSiswaPort} (menyembunyikan ketergantungan
 * Q7), tetapi ledger, saldo, idempotency, append-only, &amp; tenant scoping
 * berjalan nyata.
 */
@SpringBootTest(properties = "kantin.siswa.sumber=integrasi")
@Import({TestcontainersConfig.class, RefundSaldoServiceIT.FakeStatusConfig.class})
@EnabledIfDockerAvailable
class RefundSaldoServiceIT {

    private static final long SEKOLAH = 1L;
    private static final long SEKOLAH_LAIN = 2L;
    private static final long SISWA_KELUAR = 100L;
    private static final long SISWA_SAUDARA = 200L;
    private static final long SISWA_LAIN = 300L;
    /** Siswa yang hanya terdaftar di SEKOLAH_LAIN (uji batas tenant REF-05). */
    private static final long SISWA_SEKOLAH_LAIN = 400L;
    private static final long AKTOR = 9L;

    @Autowired
    private RefundSaldoService service;

    @Autowired
    private LedgerSaldoService ledger;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SaldoRepository saldoRepo;

    @Autowired
    private SiswaRepository siswaRepo;

    @Autowired
    private OrangTuaRepository orangTuaRepo;

    @Autowired
    private KartuRepository kartuRepo;

    @Autowired
    private AuditLogger auditLogger;

    @Autowired
    private NotifikasiService notifikasiService;

    /** Fake status siswa: siswa yang "nonaktif" bisa diatur per uji. */
    static class FakeStatusSiswa implements StatusSiswaPort {
        final Set<Long> nonaktif = ConcurrentHashMap.newKeySet();
        final Set<Long> diblokir = ConcurrentHashMap.newKeySet();

        @Override
        public Boolean tidakAktif(Long sekolahId, Long siswaId) {
            return nonaktif.contains(siswaId);
        }

        @Override
        public void blokirKartu(Long sekolahId, Long siswaId, String alasan) {
            diblokir.add(siswaId);
        }
    }

    /** Fake identitas siswa (Q7): siswa yang punya identitas diatur per uji. */
    static class FakeCariSiswa implements CariSiswaPort {
        final Map<Long, InfoSiswa> data = new ConcurrentHashMap<>();
        final AtomicInteger panggilanBatch = new AtomicInteger();
        final AtomicInteger panggilanPerId = new AtomicInteger();

        @Override
        public List<InfoSiswa> cari(Long sekolahId, String kataKunci, boolean hanyaTidakAktif, int batas) {
            return List.of();
        }

        @Override
        public Optional<InfoSiswa> cariBerdasarkanId(Long sekolahId, Long siswaId) {
            panggilanPerId.incrementAndGet();
            return Optional.ofNullable(data.get(siswaId));
        }

        @Override
        public Map<Long, InfoSiswa> cariBanyak(Long sekolahId, Collection<Long> siswaIds) {
            panggilanBatch.incrementAndGet();
            Map<Long, InfoSiswa> hasil = new java.util.LinkedHashMap<>();
            if (siswaIds != null) {
                for (Long id : siswaIds) {
                    InfoSiswa s = data.get(id);
                    if (s != null) {
                        hasil.put(id, s);
                    }
                }
            }
            return hasil;
        }

        @Override
        public Optional<String> cariUidSiswa(Long sekolahId, Long siswaId) {
            return Optional.empty();
        }
    }

    @TestConfiguration
    static class FakeStatusConfig {
        @Bean
        @Primary
        FakeStatusSiswa fakeStatusSiswa() {
            return new FakeStatusSiswa();
        }

        @Bean
        @Primary
        FakeCariSiswa fakeCariSiswa() {
            return new FakeCariSiswa();
        }
    }

    @Autowired
    private FakeStatusSiswa status;

    @Autowired
    private FakeCariSiswa identitasSiswa;

    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE mutasi_saldo, saldo, orang_tua CASCADE");

        // Induk wajib skema v2 (FK): sekolah → siswa/pengguna.
        FixtureInduk.sekolah(jdbc, SEKOLAH, SEKOLAH_LAIN);
        FixtureInduk.pengguna(jdbc, SEKOLAH, AKTOR);
        FixtureInduk.siswa(jdbc, SEKOLAH, SISWA_KELUAR, SISWA_SAUDARA, SISWA_LAIN);
        FixtureInduk.siswa(jdbc, SEKOLAH_LAIN, SISWA_SEKOLAH_LAIN);
        jdbc.update("UPDATE siswa SET foto_url = NULL WHERE sekolah_id = ?", SEKOLAH);

        status.nonaktif.clear();
        status.diblokir.clear();
        status.nonaktif.add(SISWA_KELUAR);
        identitasSiswa.data.clear();
        identitasSiswa.panggilanBatch.set(0);
        identitasSiswa.panggilanPerId.set(0);
    }

    private void isiSaldo(long siswaId, long nominal) {
        tx.executeWithoutResult(s -> ledger.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.SISWA).siswaId(siswaId)
                .jenis(JenisMutasiSaldo.TOPUP_TUNAI).nominal(nominal)
                .idempotencyKey("seed-" + siswaId + "-" + System.nanoTime()).build()));
    }

    // ────────────────────────────────────────────────────────────────
    // REFUND
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("refund mengembalikan SELURUH sisa saldo → saldo 0 & kartu diblokir")
    void refundSeluruhSisa() {
        isiSaldo(SISWA_KELUAR, 35_000);

        HasilRefundResponse hasil = service.refund(
                SEKOLAH, SISWA_KELUAR, "REF-1", "siswa lulus", 9L);

        assertThat(hasil.getNominal()).isEqualTo(35_000L);
        assertThat(hasil.getSaldoSetelah()).isZero();
        assertThat(hasil.isKartuDiblokir()).isTrue();
        assertThat(ledger.saldo(SEKOLAH, SISWA_KELUAR)).isZero();
        assertThat(status.diblokir).contains(SISWA_KELUAR);

        String jenis = jdbc.queryForObject(
                "SELECT jenis FROM mutasi_saldo WHERE sekolah_id = ? AND siswa_id = ? AND arah = 'DEBIT'",
                String.class, SEKOLAH, SISWA_KELUAR);
        assertThat(jenis).isEqualTo("REFUND");
    }

    @Test
    @DisplayName("refund idempoten — bukti sama tidak mengembalikan dua kali")
    void refundIdempoten() {
        isiSaldo(SISWA_KELUAR, 20_000);

        HasilRefundResponse pertama = service.refund(SEKOLAH, SISWA_KELUAR, "REF-2", null, 9L);
        HasilRefundResponse kedua = service.refund(SEKOLAH, SISWA_KELUAR, "REF-2", null, 9L);

        assertThat(pertama.isIdempoten()).isFalse();
        assertThat(kedua.isIdempoten()).isTrue();
        assertThat(kedua.getNominal()).isEqualTo(20_000L);
        assertThat(ledger.saldo(SEKOLAH, SISWA_KELUAR)).isZero();

        Integer baris = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mutasi_saldo WHERE sekolah_id = ? AND siswa_id = ? AND jenis = 'REFUND'",
                Integer.class, SEKOLAH, SISWA_KELUAR);
        assertThat(baris).isEqualTo(1);
    }

    @Test
    @DisplayName("issue #392: bukti sama untuk siswa lain ditolak 409, saldo tidak berubah")
    void refundBuktiSamaSiswaLainDitolak() {
        isiSaldo(SISWA_KELUAR, 20_000);
        isiSaldo(SISWA_LAIN, 30_000);

        service.refund(SEKOLAH, SISWA_KELUAR, "REF-SUBJEK-1", null, 9L);

        assertThatThrownBy(() -> service.refund(SEKOLAH, SISWA_LAIN, "REF-SUBJEK-1", null, 9L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("siswa atau jenis mutasi lain");

        assertThat(ledger.saldo(SEKOLAH, SISWA_LAIN)).isEqualTo(30_000L);
        Integer baris = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mutasi_saldo WHERE sekolah_id = ? AND jenis = 'REFUND'",
                Integer.class, SEKOLAH);
        assertThat(baris).isEqualTo(1);
    }
    @Test
    @DisplayName("refund saldo kosong → ConflictException, tanpa mutasi")
    void refundSaldoKosongDitolak() {
        assertThatThrownBy(() -> service.refund(SEKOLAH, SISWA_LAIN, "REF-3", null, 9L))
                .isInstanceOf(ConflictException.class);
    }

    // ────────────────────────────────────────────────────────────────
    // PINDAH KE SAUDARA
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("pindah ke saudara — sumber 0, tujuan bertambah, dua kaki ledger")
    void pindahKeSaudara() {
        isiSaldo(SISWA_KELUAR, 12_000);
        isiSaldo(SISWA_SAUDARA, 5_000);

        HasilRefundResponse hasil = service.pindahKeSaudara(
                SEKOLAH, SISWA_KELUAR, SISWA_SAUDARA, "BA-1", "pindah ke adik", 9L);

        assertThat(hasil.getNominal()).isEqualTo(12_000L);
        assertThat(hasil.getSaldoSetelah()).isZero();
        assertThat(hasil.getTujuanSubjekId()).isEqualTo(SISWA_SAUDARA);
        assertThat(ledger.saldo(SEKOLAH, SISWA_KELUAR)).isZero();
        assertThat(ledger.saldo(SEKOLAH, SISWA_SAUDARA)).isEqualTo(17_000L);
        assertThat(status.diblokir).contains(SISWA_KELUAR);

        Integer transferRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mutasi_saldo WHERE jenis = 'TRANSFER'", Integer.class);
        assertThat(transferRows).isEqualTo(2); // satu DEBIT + satu KREDIT
    }

    @Test
    @DisplayName("pindah idempoten — berita acara sama tidak memindah dua kali")
    void pindahIdempoten() {
        isiSaldo(SISWA_KELUAR, 8_000);

        service.pindahKeSaudara(SEKOLAH, SISWA_KELUAR, SISWA_SAUDARA, "BA-2", null, 9L);
        service.pindahKeSaudara(SEKOLAH, SISWA_KELUAR, SISWA_SAUDARA, "BA-2", null, 9L);

        assertThat(ledger.saldo(SEKOLAH, SISWA_SAUDARA)).isEqualTo(8_000L);

        Integer transferRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mutasi_saldo WHERE jenis = 'TRANSFER'", Integer.class);
        assertThat(transferRows).isEqualTo(2); // tetap 2, bukan 4
    }

    @Test
    @DisplayName("pindah ke tujuan nonaktif ditolak")
    void pindahTujuanNonaktifDitolak() {
        isiSaldo(SISWA_KELUAR, 10_000);
        status.nonaktif.add(SISWA_SAUDARA);

        assertThatThrownBy(() -> service.pindahKeSaudara(
                SEKOLAH, SISWA_KELUAR, SISWA_SAUDARA, "BA-3", null, 9L))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("tidak aktif");
    }

    @Test
    @DisplayName("REF-05 (#386): tujuan tidak terdaftar di sekolah ini ditolak 404, tanpa mutasi")
    void pindahTujuanTidakTerdaftarDitolak() {
        isiSaldo(SISWA_KELUAR, 10_000);

        assertThatThrownBy(() -> service.pindahKeSaudara(
                SEKOLAH, SISWA_KELUAR, 999_999L, "BA-5", null, 9L))
                .isInstanceOf(NotFoundEntity.class)
                .hasMessageContaining("tidak ditemukan di sekolah ini");

        assertThat(ledger.saldo(SEKOLAH, SISWA_KELUAR)).isEqualTo(10_000L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM mutasi_saldo WHERE jenis = 'TRANSFER'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("REF-05 (#386): tujuan milik sekolah lain ditolak 404 (tenant scoping lokal)")
    void pindahTujuanSekolahLainDitolak() {
        isiSaldo(SISWA_KELUAR, 10_000);
        // Siswa ini nyata di tabel `siswa`, tetapi terdaftar pada SEKOLAH_LAIN.
        assertThat(siswaRepo.findByIdAndSekolahId(SISWA_SEKOLAH_LAIN, SEKOLAH_LAIN)).isPresent();

        assertThatThrownBy(() -> service.pindahKeSaudara(
                SEKOLAH, SISWA_KELUAR, SISWA_SEKOLAH_LAIN, "BA-6", null, 9L))
                .isInstanceOf(NotFoundEntity.class)
                .hasMessageContaining("tidak ditemukan di sekolah ini");

        assertThat(ledger.saldo(SEKOLAH, SISWA_KELUAR)).isEqualTo(10_000L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM mutasi_saldo WHERE jenis = 'TRANSFER'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("pindah ke diri sendiri ditolak")
    void pindahKeDiriSendiriDitolak() {
        isiSaldo(SISWA_KELUAR, 10_000);

        assertThatThrownBy(() -> service.pindahKeSaudara(
                SEKOLAH, SISWA_KELUAR, SISWA_KELUAR, "BA-4", null, 9L))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("tidak boleh sama");
    }

    // ────────────────────────────────────────────────────────────────
    // DAFTAR KANDIDAT & TENANT SCOPING
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("daftar kandidat hanya siswa bersisa saldo; filter nonaktif")
    void daftarKandidat() {
        isiSaldo(SISWA_KELUAR, 10_000);   // nonaktif, bersisa
        isiSaldo(SISWA_SAUDARA, 5_000);   // aktif, bersisa
        // SISWA_LAIN tidak diisi → tak ada baris saldo (tidak muncul).

        List<KandidatRefundItem> semua = service.daftarKandidat(SEKOLAH, false);
        assertThat(semua).extracting(KandidatRefundItem::getSiswaId)
                .containsExactlyInAnyOrder(SISWA_KELUAR, SISWA_SAUDARA);

        List<KandidatRefundItem> hanyaNonaktif = service.daftarKandidat(SEKOLAH, true);
        assertThat(hanyaNonaktif).extracting(KandidatRefundItem::getSiswaId)
                .containsExactly(SISWA_KELUAR);
        assertThat(hanyaNonaktif.get(0).getTidakAktif()).isTrue();
    }

    @Test
    @DisplayName("#298: kandidat memuat NIS/nama/kelas/wali/foto; identitas hilang → null (tidak dikarang)")
    void daftarKandidatIdentitas() {
        isiSaldo(SISWA_KELUAR, 10_000);
        isiSaldo(SISWA_SAUDARA, 5_000);
        identitasSiswa.data.put(SISWA_KELUAR, InfoSiswa.builder()
                .siswaId(SISWA_KELUAR).nis("2023001").nama("Maya Sari").kelas("9C")
                .fotoUrl("https://cdn.skoolia.id/siswa/maya.jpg")
                .namaOrangTua("Bapak Maya").teleponOrangTua("081234567890")
                .tidakAktif(true).saldo(10_000).build());
        // SISWA_SAUDARA sengaja tidak punya identitas di admin-be.

        List<KandidatRefundItem> hasil = service.daftarKandidat(SEKOLAH, false);

        KandidatRefundItem keluar = hasil.stream()
                .filter(k -> k.getSiswaId().equals(SISWA_KELUAR)).findFirst().orElseThrow();
        assertThat(keluar.getNis()).isEqualTo("2023001");
        assertThat(keluar.getNama()).isEqualTo("Maya Sari");
        assertThat(keluar.getKelas()).isEqualTo("9C");
        assertThat(keluar.getFotoUrl()).isEqualTo("https://cdn.skoolia.id/siswa/maya.jpg");
        assertThat(keluar.getNamaOrangTua()).isEqualTo("Bapak Maya");
        assertThat(keluar.getTeleponOrangTua()).isEqualTo("081234567890");
        assertThat(keluar.getTidakAktif()).isTrue();

        KandidatRefundItem saudara = hasil.stream()
                .filter(k -> k.getSiswaId().equals(SISWA_SAUDARA)).findFirst().orElseThrow();
        assertThat(saudara.getNis()).isNull();
        assertThat(saudara.getNama()).isNull();
        assertThat(saudara.getKelas()).isNull();
        assertThat(saudara.getFotoUrl()).isNull();
        assertThat(saudara.getNamaOrangTua()).isNull();
        assertThat(saudara.getTeleponOrangTua()).isNull();
        assertThat(saudara.getTidakAktif()).isFalse();   // dari port status, bukan tebakan
    }

    @Test
    @DisplayName("PRD §9.3: kandidat memuat namaOrangTua, teleponOrangTua dari orang_tua & fotoUrl dari siswa; tanpa wali aktif → null")
    void daftarKandidatWaliDanFotoDariTabelLokal() {
        isiSaldo(SISWA_KELUAR, 25_000);
        isiSaldo(SISWA_SAUDARA, 15_000);
        isiSaldo(SISWA_LAIN, 10_000);

        // SISWA_KELUAR: punya foto_url + 2 wali aktif (IBU & AYAH → AYAH harus menang secara deterministik)
        jdbc.update("UPDATE siswa SET foto_url = ? WHERE sekolah_id = ? AND id = ?",
                "https://cdn.skoolia.id/foto/siswa-100.jpg", SEKOLAH, SISWA_KELUAR);
        jdbc.update("""
                INSERT INTO orang_tua (sekolah_id, siswa_id, nama, telepon, hubungan, aktif)
                VALUES (?, ?, 'Ibu Keluar', '081300000100', 'IBU', true),
                       (?, ?, 'Bapak Keluar', '081200000100', 'AYAH', true)
                """, SEKOLAH, SISWA_KELUAR, SEKOLAH, SISWA_KELUAR);

        // SISWA_SAUDARA: hanya punya wali nonaktif (aktif=false) & tanpa foto_url → harus null
        jdbc.update("""
                INSERT INTO orang_tua (sekolah_id, siswa_id, nama, telepon, hubungan, aktif)
                VALUES (?, ?, 'Wali Nonaktif', '081111111111', 'AYAH', false)
                """, SEKOLAH, SISWA_SAUDARA);

        // SISWA_LAIN: tanpa baris orang_tua & tanpa foto_url → harus null

        CariSiswaLokal cariSiswaLokal = new CariSiswaLokal(siswaRepo, orangTuaRepo, kartuRepo, ledger);
        RefundSaldoService serviceLokal = new RefundSaldoService(
                ledger, saldoRepo, siswaRepo, status, auditLogger, notifikasiService,
                new CariSiswaService(cariSiswaLokal));

        List<KandidatRefundItem> hasil = serviceLokal.daftarKandidat(SEKOLAH, false);

        KandidatRefundItem keluar = hasil.stream()
                .filter(k -> k.getSiswaId().equals(SISWA_KELUAR)).findFirst().orElseThrow();
        assertThat(keluar.getNamaOrangTua()).isEqualTo("Bapak Keluar");
        assertThat(keluar.getTeleponOrangTua()).isEqualTo("081200000100");
        assertThat(keluar.getFotoUrl()).isEqualTo("https://cdn.skoolia.id/foto/siswa-100.jpg");

        KandidatRefundItem saudara = hasil.stream()
                .filter(k -> k.getSiswaId().equals(SISWA_SAUDARA)).findFirst().orElseThrow();
        assertThat(saudara.getNamaOrangTua()).isNull();
        assertThat(saudara.getTeleponOrangTua()).isNull();
        assertThat(saudara.getFotoUrl()).isNull();

        KandidatRefundItem lain = hasil.stream()
                .filter(k -> k.getSiswaId().equals(SISWA_LAIN)).findFirst().orElseThrow();
        assertThat(lain.getNamaOrangTua()).isNull();
        assertThat(lain.getTeleponOrangTua()).isNull();
        assertThat(lain.getFotoUrl()).isNull();
    }

    @Test
    @DisplayName("#298: daftar kandidat memakai lookup batch — bukan N+1 per siswa")
    void daftarKandidatTanpaNPlus1() {
        isiSaldo(SISWA_KELUAR, 10_000);
        isiSaldo(SISWA_SAUDARA, 5_000);
        isiSaldo(SISWA_LAIN, 7_000);

        service.daftarKandidat(SEKOLAH, false);

        assertThat(identitasSiswa.panggilanBatch.get())
                .as("identitas diambil sekali untuk semua kandidat").isEqualTo(1);
        assertThat(identitasSiswa.panggilanPerId.get())
                .as("tidak ada lookup identitas per siswa").isZero();
    }

    @Test
    @DisplayName("tenant scoping — sekolah lain tidak melihat saldo sekolah ini")
    void tenantScoping() {
        isiSaldo(SISWA_KELUAR, 10_000);

        List<KandidatRefundItem> kandidatLain = service.daftarKandidat(SEKOLAH_LAIN, false);
        assertThat(kandidatLain).isEmpty();
    }
}
