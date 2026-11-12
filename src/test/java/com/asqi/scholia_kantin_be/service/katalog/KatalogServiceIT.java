package com.asqi.scholia_kantin_be.service.katalog;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.enums.SatuanMenu;
import com.asqi.scholia_kantin_be.repository.StokRepository;
import com.asqi.scholia_kantin_be.service.integrasi.InfoMenu;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.asqi.scholia_kantin_be.support.FixtureInduk;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji integrasi katalog (PRD §7.1) dengan PostgreSQL nyata.
 *
 * <p>Menegakkan: soft delete, tenant-scoped 404, kategori terpakai tak bisa
 * dinonaktifkan, ubah harga tercatat di audit, dan adapter lookup menu
 * (dipakai kasir) mengembalikan harga &amp; status aktif yang benar.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class KatalogServiceIT {

    private static final long SEKOLAH = 1L;
    private static final long SEKOLAH_LAIN = 2L;

    @Autowired
    private KatalogService katalog;

    @Autowired
    private MenuLookupKatalogAdapter lookup;

    @Autowired
    private StokRepository cacheRepo;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE menu, kategori_menu CASCADE");

        FixtureInduk.sekolah(jdbc, SEKOLAH, SEKOLAH_LAIN);
    }

    @Test
    @DisplayName("buat kategori & menu, lalu lookup menu mengembalikan harga benar")
    void buatDanLookup() {
        var kategori = katalog.buatKategori(SEKOLAH, "Makanan Berat", 1, 1L);
        var menu = katalog.buatMenu(SEKOLAH, kategori.getId(), "Nasi Goreng",
                15_000L, SatuanMenu.PORSI, null, 5, 1L);

        assertThat(menu.getIsActive()).isTrue();
        assertThat(menu.getHargaJual()).isEqualTo(15_000L);

        InfoMenu info = lookup.cari(SEKOLAH, menu.getId());
        assertThat(info).isNotNull();
        assertThat(info.getNama()).isEqualTo("Nasi Goreng");
        assertThat(info.getHargaJual()).isEqualTo(15_000L);
        assertThat(info.getKategoriId()).isEqualTo(kategori.getId());
        assertThat(info.isAktif()).isTrue();
    }

    @Test
    @DisplayName("lookup menu sekolah lain = null (tenant-scoped, PRD §11.4)")
    void lookupIsolasiTenant() {
        var kategori = katalog.buatKategori(SEKOLAH, "Snack", 1, 1L);
        var menu = katalog.buatMenu(SEKOLAH, kategori.getId(), "Keripik",
                5_000L, SatuanMenu.PCS, null, 0, 1L);

        assertThat(lookup.cari(SEKOLAH_LAIN, menu.getId())).isNull();
    }

    @Test
    @DisplayName("ubah harga jual tercatat sebagai audit UBAH_HARGA_JUAL")
    void ubahHargaTercatatAudit() {
        var menu = katalog.buatMenu(SEKOLAH, null, "Es Teh",
                3_000L, SatuanMenu.BOTOL, null, 0, 1L);

        var diubah = katalog.ubahMenu(SEKOLAH, menu.getId(), null, null,
                4_000L, null, null, null, null, 7L);

        assertThat(diubah.getHargaJual()).isEqualTo(4_000L);
        Integer jumlah = jdbc.queryForObject(
                "SELECT count(*) FROM catatan_audit WHERE aksi = 'UBAH_HARGA_JUAL' AND entitas_id = ?",
                Integer.class, String.valueOf(menu.getId()));
        assertThat(jumlah).isEqualTo(1);
    }

    @Test
    @DisplayName("kategori yang masih punya menu (aktif maupun nonaktif) tidak boleh dihapus")
    void kategoriTerpakaiTidakBisaDihapus() {
        var kategori = katalog.buatKategori(SEKOLAH, "Minuman", 1, 1L);
        katalog.buatMenu(SEKOLAH, kategori.getId(), "Teh Manis",
                3_000L, SatuanMenu.PCS, null, 0, 1L);

        assertThatThrownBy(() -> katalog.hapusKategori(SEKOLAH, kategori.getId(), 1L))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("masih dipakai");
    }

    @Test
    @DisplayName("menu nonaktif tetap menghalangi hapus kategori (tidak boleh menggantung)")
    void kategoriDenganMenuNonaktifTidakBisaDihapus() {
        var kategori = katalog.buatKategori(SEKOLAH, "Snack", 1, 1L);
        var menu = katalog.buatMenu(SEKOLAH, kategori.getId(), "Keripik",
                5_000L, SatuanMenu.PCS, null, 0, 1L);
        katalog.nonaktifkanMenu(SEKOLAH, menu.getId(), 1L);

        assertThatThrownBy(() -> katalog.hapusKategori(SEKOLAH, kategori.getId(), 1L))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("masih dipakai");
    }

    @Test
    @DisplayName("hapus kategori kosong: hilang dari daftar & nama boleh dipakai ulang")
    void hapusKategoriKosongHilangDanNamaBolehDipakaiUlang() {
        var kategori = katalog.buatKategori(SEKOLAH, "Snack", 1, 1L);

        katalog.hapusKategori(SEKOLAH, kategori.getId(), 1L);

        assertThat(katalog.daftarKategori(SEKOLAH, false)).isEmpty();
        assertThatThrownBy(() -> katalog.ubahKategori(SEKOLAH, kategori.getId(), "Snack2", 2, null, 1L))
                .isInstanceOf(NotFoundEntity.class);

        // Nama & urutan yang sama boleh dipakai kategori baru (partial unique).
        var baru = katalog.buatKategori(SEKOLAH, "Snack", 1, 1L);
        assertThat(baru.getId()).isNotEqualTo(kategori.getId());
        assertThat(katalog.daftarKategori(SEKOLAH, false)).hasSize(1);
    }

    @Test
    @DisplayName("daftar kategori menyertakan jumlahItem (menu belum dihapus)")
    void daftarKategoriMenyertakanJumlahItem() {
        var makanan = katalog.buatKategori(SEKOLAH, "Makanan", 1, 1L);
        var minuman = katalog.buatKategori(SEKOLAH, "Minuman", 2, 1L);
        katalog.buatMenu(SEKOLAH, makanan.getId(), "Nasi Goreng", 15_000L, SatuanMenu.PORSI, null, 0, 1L);
        var teh = katalog.buatMenu(SEKOLAH, makanan.getId(), "Teh", 3_000L, SatuanMenu.PCS, null, 0, 1L);
        katalog.hapusMenu(SEKOLAH, teh.getId(), 1L);

        var hasil = katalog.daftarKategori(SEKOLAH, false);

        assertThat(hasil).hasSize(2);
        assertThat(hasil).filteredOn(k -> k.getNama().equals("Makanan"))
                .singleElement()
                .satisfies(k -> assertThat(k.getJumlahItem()).isEqualTo(1L));
        assertThat(hasil).filteredOn(k -> k.getNama().equals("Minuman"))
                .singleElement()
                .satisfies(k -> assertThat(k.getJumlahItem()).isZero());
    }

    @Test
    @DisplayName("soft delete: menu nonaktif tetap ada & lookup menandai nonaktif")
    void softDeleteMenu() {
        var menu = katalog.buatMenu(SEKOLAH, null, "Kue",
                2_000L, SatuanMenu.PCS, null, 0, 1L);
        katalog.nonaktifkanMenu(SEKOLAH, menu.getId(), 1L);

        InfoMenu info = lookup.cari(SEKOLAH, menu.getId());
        assertThat(info).isNotNull();
        assertThat(info.isAktif()).isFalse();
        // Nama/harga tetap tersimpan (riwayat).
        assertThat(info.getNama()).isEqualTo("Kue");
    }

    @Test
    @DisplayName("kategori/menu sekolah lain = 404 saat diubah")
    void tenantScoped404() {
        var menu = katalog.buatMenu(SEKOLAH, null, "Roti",
                2_500L, SatuanMenu.PCS, null, 0, 1L);

        assertThatThrownBy(() -> katalog.ubahMenu(SEKOLAH_LAIN, menu.getId(), null, null,
                3_000L, null, null, null, null, 1L))
                .isInstanceOf(NotFoundEntity.class);
    }

    @Test
    @DisplayName("nama kategori duplikat (beda besar-kecil) ditolak")
    void kategoriDuplikatDitolak() {
        katalog.buatKategori(SEKOLAH, "Snack", 1, 1L);

        assertThatThrownBy(() -> katalog.buatKategori(SEKOLAH, "snack", 2, 1L))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("sudah ada");
    }

    @Test
    @DisplayName("stokBerjalan batch: hanya menu tenant yang terisi (baris stok dibuat otomatis)")
    void stokBerjalanBatch() {
        var menuA = katalog.buatMenu(SEKOLAH, null, "Nasi Goreng",
                15_000L, SatuanMenu.PORSI, null, 5, 1L);
        var menuB = katalog.buatMenu(SEKOLAH, null, "Es Teh",
                3_000L, SatuanMenu.BOTOL, null, 0, 1L);
        var menuC = katalog.buatMenu(SEKOLAH, null, "Kue",
                2_000L, SatuanMenu.PCS, null, 0, 1L);
        // Menu sekolah lain — tak boleh ikut terhitung.
        var menuSekolahLain = katalog.buatMenu(SEKOLAH_LAIN, null, "Roti",
                2_500L, SatuanMenu.PCS, null, 0, 1L);

        // Baris stok sudah dibuat otomatis saat buatMenu (#113) → cukup UPDATE.
        jdbc.update("UPDATE stok SET stok = ? WHERE menu_id = ?", 12, menuA.getId());
        jdbc.update("UPDATE stok SET stok = ? WHERE menu_id = ?", 4, menuB.getId());
        jdbc.update("UPDATE stok SET stok = ? WHERE menu_id = ?", 99, menuSekolahLain.getId());

        var stok = katalog.stokBerjalan(SEKOLAH,
                java.util.List.of(menuA.getId(), menuB.getId(), menuC.getId()));

        assertThat(stok).containsEntry(menuA.getId(), 12);
        assertThat(stok).containsEntry(menuB.getId(), 4);
        // Menu C punya baris stok (dibuat otomatis) dengan stok 0.
        assertThat(stok).containsEntry(menuC.getId(), 0);
        assertThat(stok).doesNotContainKey(menuSekolahLain.getId());
    }

    @Test
    @DisplayName("stokBerjalan satu menu: 0 saat baru dibuat, nilai benar setelah diisi")
    void stokBerjalanSatuMenu() {
        var menu = katalog.buatMenu(SEKOLAH, null, "Bakso",
                10_000L, SatuanMenu.PORSI, null, 0, 1L);

        assertThat(katalog.stokBerjalan(SEKOLAH, menu.getId())).isZero();

        jdbc.update("UPDATE stok SET stok = ? WHERE menu_id = ?", 8, menu.getId());

        assertThat(katalog.stokBerjalan(SEKOLAH, menu.getId())).isEqualTo(8);
        // Sekolah lain tak melihat stok sekolah pemilik baris.
        assertThat(katalog.stokBerjalan(SEKOLAH_LAIN, menu.getId())).isZero();
    }

    // ────────────────────────────────────────────────────────────────
    // #113 — sinkron stok_minimum menu → stok
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("#113: buat menu → baris stok dibuat dengan stok_minimum = menu.stok_minimum")
    void buatMenuSinkronStokMinimum() {
        var menu = katalog.buatMenu(SEKOLAH, null, "Nasi Uduk",
                8_000L, SatuanMenu.PORSI, null, 7, 1L);

        Integer min = jdbc.queryForObject(
                "SELECT stok_minimum FROM stok WHERE menu_id = ?", Integer.class, menu.getId());
        assertThat(min).isEqualTo(7);
    }

    @Test
    @DisplayName("#113: ubah stok_minimum menu → stok ikut tersinkron")
    void ubahMenuSinkronStokMinimum() {
        var menu = katalog.buatMenu(SEKOLAH, null, "Es Jeruk",
                5_000L, SatuanMenu.BOTOL, null, 3, 1L);

        katalog.ubahMenu(SEKOLAH, menu.getId(), null, null, null, null, null, 10, null, 1L);

        Integer min = jdbc.queryForObject(
                "SELECT stok_minimum FROM stok WHERE menu_id = ?", Integer.class, menu.getId());
        assertThat(min).isEqualTo(10);
    }

    @Test
    @DisplayName("hapus menu: hilang dari daftar, stok menipis, & nonaktifkan kategori, namun lookup riwayat tetap aman")
    void hapusMenuSoftDeleteViaDeletedAt() {
        var kategori = katalog.buatKategori(SEKOLAH, "Snack", 1, 1L);
        var menu = katalog.buatMenu(SEKOLAH, kategori.getId(), "Risoles",
                3_000L, SatuanMenu.PCS, null, 5, 1L);

        katalog.hapusMenu(SEKOLAH, menu.getId(), 1L);

        assertThat(katalog.daftarMenu(SEKOLAH, null, false)).isEmpty();
        assertThat(cacheRepo.stokMenipis(SEKOLAH)).isEmpty();
        assertThatThrownBy(() -> katalog.lihatMenu(SEKOLAH, menu.getId()))
                .isInstanceOf(NotFoundEntity.class);
        assertThatThrownBy(() -> katalog.ubahMenu(SEKOLAH, menu.getId(), null, "Baru",
                null, null, null, null, true, 1L))
                .isInstanceOf(NotFoundEntity.class);

        // Kategori bisa dihapus karena satu-satunya itemnya sudah dihapus.
        var katTerhapus = katalog.hapusKategori(SEKOLAH, kategori.getId(), 1L);
        assertThat(katTerhapus.getJumlahItem()).isZero();
        assertThat(katalog.daftarKategori(SEKOLAH, false)).isEmpty();

        // Lookup riwayat transaksi lama tetap mempertahankan nama sambil menandai nonaktif.
        InfoMenu info = lookup.cari(SEKOLAH, menu.getId());
        assertThat(info).isNotNull();
        assertThat(info.isAktif()).isFalse();
        assertThat(info.getNama()).isEqualTo("Risoles");
    }

    @Test
    @DisplayName("kodeBarcode: unik per sekolah pada item aktif, boleh NULL, dan dapat dipakai ulang setelah hapus")
    void kodeBarcodeUnikPerSekolahDanReuseSetelahHapus() {
        var m1 = katalog.buatMenu(SEKOLAH, null, "Teh Botol",
                4_500L, SatuanMenu.BOTOL, null, 5, " 8996001300112 ", 1L);
        assertThat(m1.getKodeBarcode()).isEqualTo("8996001300112");

        // Banyak menu tanpa barcode diperbolehkan.
        var mKosong1 = katalog.buatMenu(SEKOLAH, null, "Gorengan",
                1_500L, SatuanMenu.PCS, null, 0, "", 1L);
        var mKosong2 = katalog.buatMenu(SEKOLAH, null, "Lontong",
                2_000L, SatuanMenu.PCS, null, 0, null, 1L);
        assertThat(mKosong1.getKodeBarcode()).isNull();
        assertThat(mKosong2.getKodeBarcode()).isNull();

        // Duplikat di sekolah sama ditolak.
        assertThatThrownBy(() -> katalog.buatMenu(SEKOLAH, null, "Teh Botol Duplikat",
                5_000L, SatuanMenu.BOTOL, null, 0, "8996001300112", 1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("8996001300112");

        // Sekolah lain boleh memakai kode barcode yang sama.
        var mLain = katalog.buatMenu(SEKOLAH_LAIN, null, "Teh Botol",
                4_500L, SatuanMenu.BOTOL, null, 0, "8996001300112", 1L);
        assertThat(mLain.getKodeBarcode()).isEqualTo("8996001300112");

        // Setelah dihapus (soft delete), barcode dapat dipakai ulang pada menu baru.
        katalog.hapusMenu(SEKOLAH, m1.getId(), 1L);
        var mBaru = katalog.buatMenu(SEKOLAH, null, "Teh Botol Baru",
                5_000L, SatuanMenu.BOTOL, null, 5, "8996001300112", 1L);
        assertThat(mBaru.getKodeBarcode()).isEqualTo("8996001300112");

        // Mengirim string kosong saat ubahMenu membersihkan kodeBarcode.
        var dikosongkan = katalog.ubahMenu(SEKOLAH, mBaru.getId(), null, null, null, null,
                null, null, null, "", 1L);
        assertThat(dikosongkan.getKodeBarcode()).isNull();
    }
}
