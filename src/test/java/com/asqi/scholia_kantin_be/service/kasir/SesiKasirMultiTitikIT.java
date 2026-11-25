package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.dto.kasir.SesiKasirAktifData;
import com.asqi.scholia_kantin_be.model.SesiKasir;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji integrasi operasional multi-titik kasir (issue #345).
 *
 * <p>Dua loket di <b>sekolah yang sama</b> harus bisa dibuka bersamaan oleh dua
 * petugas berbeda, masing-masing sesi mencatat pembukanya sendiri
 * ({@code sesi_kasir.dibuka_oleh}), dan respons baca sesi aktif membawa nama
 * titik kasir. Sebelum #345 tidak ada akun kasir kedua di seeder dan pembuka
 * sesi tidak tercatat, sehingga alur ini tidak bisa diuji maupun diaudit.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class SesiKasirMultiTitikIT {

    private static final long SEKOLAH = 1L;
    private static final long TITIK_A = 901L;
    private static final long TITIK_B = 902L;
    private static final long KASIR_A = 41L;
    private static final long KASIR_B = 42L;

    @Autowired
    private SesiKasirService sesi;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void siapkan() {
        jdbc.execute("TRUNCATE TABLE sesi_kasir, transaksi, transaksi_item CASCADE");
        FixtureInduk.titikKasir(jdbc, SEKOLAH, TITIK_A, TITIK_B);
        FixtureInduk.pengguna(jdbc, SEKOLAH, KASIR_A, KASIR_B);
    }

    @Test
    @DisplayName("dua titik kasir di sekolah sama dibuka bersamaan oleh dua petugas berbeda")
    void duaTitikDibukaBersamaan() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch mulai = new CountDownLatch(1);

        List<Future<SesiKasir>> futures = new ArrayList<>();
        futures.add(pool.submit(() -> {
            mulai.await();
            return sesi.bukaSesi(SEKOLAH, TITIK_A, KASIR_A);
        }));
        futures.add(pool.submit(() -> {
            mulai.await();
            return sesi.bukaSesi(SEKOLAH, TITIK_B, KASIR_B);
        }));
        mulai.countDown();

        List<SesiKasir> hasil = new ArrayList<>();
        for (Future<SesiKasir> f : futures) {
            hasil.add(f.get());
        }
        pool.shutdown();

        // Dua sesi terpisah, bukan satu sesi yang dipakai bersama.
        assertThat(hasil).hasSize(2);
        assertThat(hasil.get(0).getId()).as("sesi tiap titik harus berbeda")
                .isNotEqualTo(hasil.get(1).getId());
        assertThat(hasil).allSatisfy(s -> assertThat(s.getSekolahId()).isEqualTo(SEKOLAH));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM sesi_kasir WHERE sekolah_id = ? AND status = 'TERBUKA'",
                Integer.class, SEKOLAH)).as("dua sesi terbuka di sekolah yang sama").isEqualTo(2);

        // Jejak audit: tiap sesi mencatat petugas yang membukanya, bukan petugas terakhir.
        assertThat(dibukaOleh(TITIK_A)).as("pembuka sesi titik A").isEqualTo(KASIR_A);
        assertThat(dibukaOleh(TITIK_B)).as("pembuka sesi titik B").isEqualTo(KASIR_B);

        // Respons baca sesi aktif membawa nama titik + pembuka (kontrak FE POS).
        SesiKasirAktifData aktifB = sesi.ambilSesiAktif(SEKOLAH, TITIK_B);
        assertThat(aktifB).as("sesi aktif titik B").isNotNull();
        assertThat(aktifB.titikKasirNama()).isEqualTo("Kasir Uji " + TITIK_B);
        assertThat(aktifB.dibukaOleh()).isEqualTo(KASIR_B);
        assertThat(aktifB.totalTransaksi()).isZero();
    }

    @Test
    @DisplayName("buka ulang titik yang sama: idempoten, pembuka asli tidak ditimpa")
    void bukaUlangTidakMenimpaPembuka() {
        SesiKasir pertama = sesi.bukaSesi(SEKOLAH, TITIK_A, KASIR_A);
        SesiKasir kedua = sesi.bukaSesi(SEKOLAH, TITIK_A, KASIR_B);

        assertThat(kedua.getId()).as("sesi hari yang sama dipakai ulang").isEqualTo(pertama.getId());
        assertThat(dibukaOleh(TITIK_A)).as("pembuka asli tetap tercatat").isEqualTo(KASIR_A);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM sesi_kasir WHERE sekolah_id = ?", Integer.class, SEKOLAH))
                .as("tidak boleh ada sesi ganda").isEqualTo(1);
    }

    private Long dibukaOleh(long titikKasirId) {
        return jdbc.queryForObject(
                "SELECT dibuka_oleh FROM sesi_kasir WHERE sekolah_id = ? AND titik_kasir_id = ?",
                Long.class, SEKOLAH, titikKasirId);
    }
}
