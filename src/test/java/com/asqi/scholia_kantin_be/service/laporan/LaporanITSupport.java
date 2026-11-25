package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.ReferensiTipe;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.service.integrasi.CariSiswaPort;
import com.asqi.scholia_kantin_be.service.integrasi.InfoSiswa;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import com.asqi.scholia_kantin_be.service.kasir.PerintahMutasiSaldo;
import com.asqi.scholia_kantin_be.service.saldo.SetoranService;
import com.asqi.scholia_kantin_be.service.stok.LedgerStokService;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.FixtureInduk;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Fondasi bersama IT modul laporan (PRD §9.5) dengan PostgreSQL nyata
 * (Testcontainers).
 *
 * <p>Kelas ini menyiapkan induk skema v2, memangkas tabel transaksi/ledger
 * sebelum tiap uji, lalu menyediakan penyemai data lintas domain (transaksi,
 * item, top-up, void, titik kasir). Sub-kelas memilih service domain yang
 * diuji, sehingga tiap berkas uji fokus pada satu tanggung jawab — mengikuti
 * pemecahan {@code LaporanService} per domain (issue #358).
 *
 * <p>Mock {@link CariSiswaPort} dipasang di sini karena hampir semua domain
 * memakai lookup identitas siswa (saldo, riwayat, per siswa).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
abstract class LaporanITSupport {

    static final long SEKOLAH = 1L;
    static final long SEKOLAH_LAIN = 2L;
    static final long SISWA = 100L;
    static final long SISWA2 = 200L;
    static final long MENU_A = 10L;
    static final long MENU_B = 20L;
    static final long KATEGORI = 500L;
    static final long PETUGAS = 5L;
    static final long ADMIN_VOID = 777L;
    static final long PETUGAS_SETORAN = 555L;
    static final long SISWA_DIMENSI = 1L;

    @MockitoBean
    protected CariSiswaPort cariSiswaPort;

    @Autowired
    protected LaporanExportService exportService;

    @Autowired
    protected LedgerSaldoService ledgerSaldo;

    @Autowired
    protected LedgerStokService ledgerStok;

    @Autowired
    protected TransactionTemplate tx;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected JamKantin jam;

    @Autowired
    protected SetoranService setoranService;

    /**
     * Stub pencarian identitas siswa: NIS 2024001 → Budi Santoso (SISWA),
     * NIS 2024002 → siswa tanpa mutasi (SISWA2), selain itu kosong dan hanya
     * berlaku untuk sekolah uji utama (tenant-scope ikut diuji).
     */
    @BeforeEach
    void siapkanCariSiswa() {
        org.mockito.Mockito.when(cariSiswaPort.cari(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyBoolean(),
                        org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> {
                    long sekolahId = invocation.getArgument(0);
                    String q = invocation.getArgument(1);
                    if (SEKOLAH != sekolahId || q == null) {
                        return List.of();
                    }
                    if (q.equalsIgnoreCase("2024002")) {
                        return List.of(InfoSiswa.builder()
                                .siswaId(SISWA2).nis("2024002").nama("Siswa Tanpa Mutasi")
                                .kelas("7B").saldo(0).build());
                    }
                    if (!q.equalsIgnoreCase("2024001")) {
                        return List.of();
                    }
                    return List.of(InfoSiswa.builder()
                            .siswaId(SISWA).nis("2024001").nama("Budi Santoso")
                            .kelas("7A").saldo(0).build());
                });

        // Lookup bertarget (issue #398): kembalikan identitas untuk setiap ID yang
        // diminta, meniru integrasi yang sehat. Sekolah lain tetap kosong.
        org.mockito.Mockito.when(cariSiswaPort.cariBanyak(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    long sekolahId = invocation.getArgument(0);
                    java.util.Collection<Long> ids = invocation.getArgument(1);
                    if (SEKOLAH != sekolahId || ids == null || ids.isEmpty()) {
                        return java.util.Map.of();
                    }
                    java.util.Map<Long, InfoSiswa> peta = new java.util.HashMap<>();
                    for (Long id : ids) {
                        if (id == null) {
                            continue;
                        }
                        peta.put(id, InfoSiswa.builder().siswaId(id).nis("NIS-" + id)
                                .nama("Siswa " + id).kelas("7A").saldo(0).build());
                    }
                    return peta;
                });
    }

    /** Kosongkan tabel transaksi/ledger lalu siapkan induk skema v2 + katalog dasar. */
    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE transaksi_item, transaksi, sesi_kasir, titik_kasir, "
                + "mutasi_saldo, saldo, riwayat_stok, stok, kategori_menu, menu, "
                + "kartu, setoran, catatan_audit, pembatasan CASCADE");

        // Induk wajib skema v2 (FK): sekolah → siswa/pengguna.
        FixtureInduk.sekolah(jdbc, SEKOLAH, SEKOLAH_LAIN);
        FixtureInduk.penggunaUmum(jdbc, SEKOLAH);
        FixtureInduk.pengguna(jdbc, SEKOLAH, PETUGAS, ADMIN_VOID, PETUGAS_SETORAN, 6L);
        FixtureInduk.siswa(jdbc, SEKOLAH, SISWA, SISWA2, SISWA_DIMENSI);

        jdbc.update("INSERT INTO titik_kasir (id, sekolah_id, nama, is_active, created_at, updated_at) "
                + "VALUES (1, ?, 'Kasir 1', true, now(), now())", SEKOLAH);
        jdbc.update("INSERT INTO sesi_kasir (id, sekolah_id, titik_kasir_id, tanggal, status, "
                + "total_bruto, total_void, total_bersih, dibuka_at, auto_tutup, jurnal_buku_kas, "
                + "created_at, updated_at) "
                + "VALUES (1, ?, 1, CURRENT_DATE, 'TERBUKA', 0, 0, 0, now(), false, false, now(), now())",
                SEKOLAH);
        jdbc.update("INSERT INTO kategori_menu (id, sekolah_id, nama, urutan, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'Makanan', 0, true, now(), now())", KATEGORI, SEKOLAH);
        jdbc.update("INSERT INTO menu (id, sekolah_id, kategori_id, nama, harga_jual, satuan, "
                + "stok_minimum, is_active, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'Nasi Uduk', 8000, 'PCS', 2, true, now(), now())",
                MENU_A, SEKOLAH, KATEGORI);
        jdbc.update("INSERT INTO menu (id, sekolah_id, kategori_id, nama, harga_jual, satuan, "
                + "stok_minimum, is_active, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'Es Jeruk', 5000, 'PCS', 2, true, now(), now())",
                MENU_B, SEKOLAH, KATEGORI);
    }

    /** Catat satu mutasi kredit saldo lewat ledger nyata (bukan insert langsung). */
    void topup(long subjekId, SubjekTipe tipe, long nominal, JenisMutasiSaldo jenis) {
        tx.executeWithoutResult(s -> ledgerSaldo.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(tipe).siswaId(subjekId)
                .jenis(jenis).nominal(nominal)
                .idempotencyKey("topup-" + subjekId + "-" + System.nanoTime()).build()));
    }

    /** Semai satu transaksi SUKSES di titik kasir 1 oleh {@link #PETUGAS}. */
    void transaksi(long id, long subjekId, long total, long totalHpp) {
        jdbc.update("INSERT INTO transaksi (id, idempotency_key, sekolah_id, sesi_kasir_id, "
                + "titik_kasir_id, siswa_id, petugas_id, total, total_hpp, status, "
                + "waktu, created_at, updated_at) "
                + "VALUES (?, ?, ?, 1, 1, ?, ?, ?, ?, 'SUKSES', now(), now(), now())",
                id, "trx-" + id, SEKOLAH, subjekId, PETUGAS, total, totalHpp);
    }

    /** Semai satu baris item transaksi (snapshot nama/harga/HPP). */
    void item(long id, long transaksiId, long menuId, String nama, int qty,
              long harga, long hpp) {
        jdbc.update("INSERT INTO transaksi_item (id, transaksi_id, menu_id, nama_menu, kategori_id, "
                + "harga_jual, qty, hpp_snapshot, subtotal, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())",
                id, transaksiId, menuId, nama, KATEGORI, harga, qty, hpp, harga * qty);
    }

    /** Semai satu transaksi VOID (wajib beralasan, PRD §6.3). */
    void transaksiVoid(long id, long subjekId, long total, String alasan) {
        jdbc.update("INSERT INTO transaksi (id, idempotency_key, sekolah_id, sesi_kasir_id, "
                + "titik_kasir_id, siswa_id, petugas_id, total, total_hpp, status, "
                + "alasan_void, void_at, void_oleh, waktu, created_at, updated_at) "
                + "VALUES (?, ?, ?, 1, 1, ?, ?, ?, 0, 'VOID', ?, now(), ?, now(), now(), now())",
                id, "trx-" + id, SEKOLAH, subjekId, PETUGAS, total, alasan, ADMIN_VOID);
    }

    /** Tambah satu titik kasir pada sekolah uji utama. */
    void titikKasir(long id, String nama) {
        jdbc.update("INSERT INTO titik_kasir (id, sekolah_id, nama, is_active, created_at, updated_at) "
                + "VALUES (?, ?, ?, true, now(), now())", id, SEKOLAH, nama);
    }

    /** Semai transaksi SUKSES dengan titik kasir & petugas tertentu. */
    void transaksiDimensi(long id, long titikId, long petugasId, long total, long totalHpp) {
        jdbc.update("INSERT INTO transaksi (id, idempotency_key, sekolah_id, sesi_kasir_id, "
                + "titik_kasir_id, siswa_id, petugas_id, total, total_hpp, status, "
                + "waktu, created_at, updated_at) "
                + "VALUES (?, ?, ?, 1, ?, 1, ?, ?, ?, 'SUKSES', now(), now(), now())",
                id, "trx-" + id, SEKOLAH, titikId, petugasId, total, totalHpp);
    }

    /** Catat top-up tunai oleh petugas tertentu (aktor_id = petugasId). */
    void topupTunaiPetugas(long petugasId, long nominal) {
        long subjek = 50_000L + Math.abs(System.nanoTime() % 10_000);
        FixtureInduk.siswa(jdbc, SEKOLAH, subjek); // FK v2: mutasi_saldo.siswa_id → siswa
        tx.executeWithoutResult(s -> ledgerSaldo.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.SISWA).siswaId(subjek)
                .jenis(JenisMutasiSaldo.TOPUP_TUNAI).nominal(nominal)
                .referensiTipe(ReferensiTipe.TOPUP).aktorId(petugasId)
                .idempotencyKey("setoran-" + petugasId + "-" + System.nanoTime()).build()));
    }
}
