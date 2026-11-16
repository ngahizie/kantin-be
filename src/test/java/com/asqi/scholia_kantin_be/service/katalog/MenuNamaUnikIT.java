package com.asqi.scholia_kantin_be.service.katalog;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.enums.SatuanMenu;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.FixtureInduk;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji integrasi keunikan nama menu (#339) terhadap PostgreSQL nyata.
 *
 * <p>Pemeriksaan di {@code KatalogService} saja tidak cukup: ia berjalan sebagai
 * "SELECT lalu INSERT" sehingga dua request yang tiba bersamaan bisa sama-sama
 * lolos. Kelas ini membuktikan bahwa <b>database</b> yang menjadi penentu
 * terakhir — bukan kode aplikasi.
 *
 * <p>Yang diuji:
 * <ul>
 *   <li>request paralel dengan nama sama → tepat satu tersimpan, sisanya ditolak;</li>
 *   <li>index menolak duplikat walau pemeriksaan aplikasi terlewati (INSERT langsung);</li>
 *   <li>nama sama tetap sah di sekolah berbeda (aturan berlaku per tenant);</li>
 *   <li>nama boleh dipakai ulang setelah menu lama dihapus (partial index).</li>
 * </ul>
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class MenuNamaUnikIT {

    private static final long SEKOLAH = 1L;
    private static final long SEKOLAH_LAIN = 2L;

    /** Jumlah request yang ditembakkan bersamaan; hanya satu boleh lolos. */
    private static final int JUMLAH_REQUEST_PARALEL = 6;

    @Autowired
    private KatalogService katalog;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    /** Mulai dari keadaan bersih: tabel katalog kosong, induk sekolah tersedia. */
    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE menu, kategori_menu CASCADE");
        FixtureInduk.sekolah(jdbc, SEKOLAH, SEKOLAH_LAIN);
    }

    /**
     * Menembak beberapa request pembuatan menu dengan nama identik secara
     * bersamaan (latch), lalu memastikan hanya satu transaksi yang commit.
     *
     * <p>Tanpa unique index, semua request lolos pemeriksaan aplikasi dan
     * tersimpan; dengan index, sisanya gagal. Kegagalan boleh datang sebagai
     * {@link ConflictException} (pemeriksaan aplikasi menangkap lebih dulu)
     * atau {@link DataIntegrityViolationException} (database yang menolak) —
     * keduanya berarti permintaan ditolak, dan itulah yang diuji.
     */
    @Test
    @DisplayName("request paralel nama sama: tepat satu tersimpan, sisanya ditolak")
    void requestParalelHanyaSatuTersimpan() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(JUMLAH_REQUEST_PARALEL);
        CountDownLatch mulai = new CountDownLatch(1);
        AtomicInteger sukses = new AtomicInteger();
        AtomicInteger ditolak = new AtomicInteger();

        Callable<Void> tugas = () -> {
            mulai.await();
            try {
                tx.executeWithoutResult(s -> katalog.buatMenu(SEKOLAH, null, "Jus Jeruk",
                        7_000L, SatuanMenu.BOTOL, null, 0, 1L));
                sukses.incrementAndGet();
            } catch (ConflictException | DataIntegrityViolationException e) {
                ditolak.incrementAndGet();
            }
            return null;
        };

        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < JUMLAH_REQUEST_PARALEL; i++) {
            futures.add(pool.submit(tugas));
        }
        mulai.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(sukses.get())
                .as("hanya satu request boleh berhasil")
                .isEqualTo(1);
        assertThat(ditolak.get())
                .as("sisanya harus ditolak, bukan tersimpan diam-diam")
                .isEqualTo(JUMLAH_REQUEST_PARALEL - 1);
        assertThat(hitungMenuAktifBernama(SEKOLAH, "JUS JERUK"))
                .as("tidak boleh ada baris ganda di database")
                .isEqualTo(1);
    }

    /**
     * Melewati service sepenuhnya: INSERT langsung ke tabel harus tetap
     * ditolak. Ini yang membedakan unique index dari pemeriksaan aplikasi —
     * jalur seed atau SQL manual pun tidak bisa menyelundupkan duplikat.
     */
    @Test
    @DisplayName("INSERT langsung (tanpa service) dengan beda kapital tetap ditolak database")
    void databaseMenolakDuplikatWalauServiceTerlewati() {
        jdbc.update("INSERT INTO menu (id, sekolah_id, nama, harga_jual) VALUES (?, ?, ?, ?)",
                9_900_001L, SEKOLAH, "Es Teh", 3_000L);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO menu (id, sekolah_id, nama, harga_jual) VALUES (?, ?, ?, ?)",
                9_900_002L, SEKOLAH, "ES TEH", 3_500L))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_menu_sekolah_nama");
    }

    /** Aturan unik berlaku per sekolah: nama yang sama di kantin lain adalah produk lain. */
    @Test
    @DisplayName("nama sama di sekolah berbeda tetap sah")
    void namaSamaBolehDiSekolahBerbeda() {
        katalog.buatMenu(SEKOLAH, null, "Ayam Geprek", 12_000L, SatuanMenu.PORSI, null, 0, 1L);

        assertThatCode(() -> katalog.buatMenu(SEKOLAH_LAIN, null, "Ayam Geprek",
                13_000L, SatuanMenu.PORSI, null, 0, 1L))
                .doesNotThrowAnyException();
        assertThat(hitungMenuAktifBernama(SEKOLAH_LAIN, "AYAM GEPREK")).isEqualTo(1);
    }

    /** Soft delete tidak boleh mengunci nama selamanya (index parsial). */
    @Test
    @DisplayName("nama boleh dipakai lagi setelah menu lama dihapus")
    void namaBolehDipakaiUlangSetelahDihapus() {
        var lama = katalog.buatMenu(SEKOLAH, null, "Bakso", 10_000L,
                SatuanMenu.PORSI, null, 0, 1L);
        katalog.hapusMenu(SEKOLAH, lama.getId(), 1L);

        var baru = katalog.buatMenu(SEKOLAH, null, "Bakso", 11_000L,
                SatuanMenu.PORSI, null, 0, 1L);

        assertThat(baru.getId()).isNotEqualTo(lama.getId());
        assertThat(hitungMenuAktifBernama(SEKOLAH, "BAKSO")).isEqualTo(1);
    }

    /**
     * Menghitung menu aktif dengan nama tertentu, memakai ekspresi yang sama
     * dengan index ({@code upper(nama)}) agar hasilnya mencerminkan apa yang
     * dijaga database.
     *
     * @param sekolahId tenant pemilik menu
     * @param namaHurufBesar nama yang sudah di-uppercase
     * @return jumlah baris menu hidup dengan nama tersebut
     */
    private int hitungMenuAktifBernama(long sekolahId, String namaHurufBesar) {
        Integer jumlah = jdbc.queryForObject("""
                SELECT count(*) FROM menu
                WHERE sekolah_id = ? AND upper(nama) = ? AND deleted_at IS NULL
                """, Integer.class, sekolahId, namaHurufBesar);
        return jumlah == null ? 0 : jumlah;
    }
}
