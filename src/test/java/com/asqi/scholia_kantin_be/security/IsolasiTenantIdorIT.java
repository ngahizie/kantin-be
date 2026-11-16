package com.asqi.scholia_kantin_be.security;

import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.service.katalog.KatalogService;
import com.asqi.scholia_kantin_be.service.konfigurasi.TitikKasirService;
import com.asqi.scholia_kantin_be.service.kartu.KontrolKartuService;
import com.asqi.scholia_kantin_be.service.stok.StokOperasiService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Opsi A dari audit OWASP A01 — bukti bahwa isolasi tenant berlaku pada
 * <b>perilaku</b>, bukan hanya pada niat baik penulis kode.
 *
 * <p>Setiap panggilan di bawah memakai {@code sekolahId} sekolah A (1) tetapi
 * menunjuk baris milik sekolah B (2). Semuanya harus dijawab
 * {@link NotFoundEntity} — 404 — bukan mengembalikan data, bukan mengubah data.
 * Kalau suatu endpoint/servis lupa menyaring {@code sekolah_id}, kelas ini merah.
 *
 * <p>Kenapa di lapisan service, bukan HTTP: controller hanya meneruskan
 * {@code TenantContext.sekolahIdWajib()} ke service. Titik penegakan yang
 * sebenarnya ada di service, dan di sanalah kelas ini menyerang.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
@DisplayName("Isolasi tenant — sekolah A tidak boleh menyentuh data sekolah B (A01)")
class IsolasiTenantIdorIT {

    private static final long SEKOLAH_A = 1L;
    private static final long SEKOLAH_B = 2L;

    private static final long KATEGORI_B = 800L;
    private static final long MENU_B = 900L;
    private static final long SISWA_B = 700L;
    private static final long TITIK_B = 600L;

    @Autowired private KatalogService katalogService;
    @Autowired private KontrolKartuService kontrolKartuService;
    @Autowired private StokOperasiService stokOperasiService;
    @Autowired private TitikKasirService titikKasirService;

    @Autowired private TransactionTemplate tx;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void siapkan() {
        jdbc.execute("TRUNCATE TABLE mutasi_saldo, saldo, riwayat_stok, stok, transaksi_item, "
                + "transaksi, transaksi_tertunda, pembatasan, kartu CASCADE");

        FixtureInduk.sekolah(jdbc, SEKOLAH_A, SEKOLAH_B);
        FixtureInduk.pengguna(jdbc, SEKOLAH_A, 1L);
        FixtureInduk.kategori(jdbc, SEKOLAH_A, 801L, "Kategori A");
        FixtureInduk.menu(jdbc, SEKOLAH_A, 901L, "Menu A");
        FixtureInduk.titikKasir(jdbc, SEKOLAH_A, 901L);

        // Data milik sekolah B — inilah yang tidak boleh tersentuh oleh sekolah A.
        FixtureInduk.pengguna(jdbc, SEKOLAH_B, 2L);
        FixtureInduk.kategori(jdbc, SEKOLAH_B, KATEGORI_B, "Kategori B");
        FixtureInduk.menu(jdbc, SEKOLAH_B, MENU_B, "Menu B");
        FixtureInduk.siswa(jdbc, SEKOLAH_B, SISWA_B);
        FixtureInduk.titikKasir(jdbc, SEKOLAH_B, TITIK_B);
    }

    @Test
    void lihatMenuSekolahLainDijawab404() {
        assertThatThrownBy(() -> katalogService.lihatMenu(SEKOLAH_A, MENU_B))
                .isInstanceOf(NotFoundEntity.class);
    }

    @Test
    void ubahMenuSekolahLainDijawab404() {
        assertThatThrownBy(() -> katalogService.ubahMenu(SEKOLAH_A, MENU_B, KATEGORI_B,
                "Nama Diubah", 1_000L, null, null, 0, true, 1L))
                .isInstanceOf(NotFoundEntity.class);
    }

    @Test
    void hapusKategoriSekolahLainDijawab404() {
        assertThatThrownBy(() -> katalogService.hapusKategori(SEKOLAH_A, KATEGORI_B, 1L))
                .isInstanceOf(NotFoundEntity.class);
    }

    @Test
    void kontrolSiswaSekolahLainDijawab404() {
        assertThatThrownBy(() -> kontrolKartuService.kontrol(SEKOLAH_A, SISWA_B))
                .isInstanceOf(NotFoundEntity.class);
    }

    @Test
    void ubahBlokirSiswaSekolahLainDijawab404() {
        assertThatThrownBy(() -> kontrolKartuService.ubahBlokir(SEKOLAH_A, SISWA_B,
                true, null, 1L))
                .isInstanceOf(NotFoundEntity.class);
    }

    @Test
    void lihatStokMenuSekolahLainDijawab404() {
        assertThatThrownBy(() -> stokOperasiService.lihat(SEKOLAH_A, MENU_B))
                .isInstanceOf(NotFoundEntity.class);
    }

    @Test
    void detailTitikKasirSekolahLainDijawab404() {
        assertThatThrownBy(() -> titikKasirService.detail(SEKOLAH_A, TITIK_B))
                .isInstanceOf(NotFoundEntity.class);
    }

    @Test
    void dataSekolahAUntukBarisMilikSendiriTetapBisaDiakses() {
        // Kontrol: memastikan kelas ini hijau bukan karena semua panggilan gagal.
        var menu = katalogService.lihatMenu(SEKOLAH_A, 901L);
        assertThat(menu.getNama()).isEqualTo("Menu A");

        var titik = titikKasirService.detail(SEKOLAH_A, 901L);
        assertThat(titik).isNotNull();
    }

    @Test
    void menulisDataSekolahBTidakMengubahBArisMilikSekolahB() {
        long sebelum = tx.execute(s -> jdbc.queryForObject(
                "SELECT harga_jual FROM menu WHERE id = ?", Long.class, MENU_B));

        assertThatThrownBy(() -> katalogService.ubahMenu(SEKOLAH_A, MENU_B, KATEGORI_B,
                "Nama Diubah", 1_000L, null, null, 0, true, 1L))
                .isInstanceOf(NotFoundEntity.class);

        long sesudah = tx.execute(s -> jdbc.queryForObject(
                "SELECT harga_jual FROM menu WHERE id = ?", Long.class, MENU_B));
        assertThat(sesudah).isEqualTo(sebelum);
    }
}