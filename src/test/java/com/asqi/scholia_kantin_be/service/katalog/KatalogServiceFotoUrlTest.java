package com.asqi.scholia_kantin_be.service.katalog;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji kepemilikan tenant pada path foto menu (M-03).
 *
 * <p>{@code @Pattern} di DTO tidak tahu tenant; helper ini menolak path
 * relatif yang menunjuk {@code sekolah-<id-lain>}.
 */
@DisplayName("KatalogService — fotoUrl wajib milik tenant (M-03)")
class KatalogServiceFotoUrlTest {

    private static final Long SEKOLAH = 7L;

    @Test
    @DisplayName("null/kosong, path tenant sendiri, dan URL absolut diterima")
    void nilaiSahDiterima() {
        String[] sah = {
                null,
                "",
                "   ",
                "sekolah-7/menu/a.png",
                "/api/storage/file?path=sekolah-7%2Fmenu%2Fa.png",
                "/api/storage/file?path=sekolah-7%2Fmenu%2Fa.png&x=1",
                "https://cdn.sekolah.id/kantin/sekolah-9/menu/a.png"
        };

        for (String url : sah) {
            assertThatCode(() -> KatalogService.validasiFotoUrlMilikTenant(SEKOLAH, url))
                    .as("fotoUrl %s", url)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("path relatif sekolah lain ditolak")
    void pathSekolahLainDitolak() {
        assertThatThrownBy(() -> KatalogService.validasiFotoUrlMilikTenant(
                SEKOLAH, "sekolah-9/menu/a.png"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("storage sekolah ini");
    }

    @Test
    @DisplayName("query path storage sekolah lain ditolak (termasuk yang ter-encode)")
    void queryPathSekolahLainDitolak() {
        assertThatThrownBy(() -> KatalogService.validasiFotoUrlMilikTenant(
                SEKOLAH, "/api/storage/file?path=sekolah-9%2Fmenu%2Fa.png"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("storage sekolah ini");
    }

    @Test
    @DisplayName("URL storage tanpa parameter path ditolak")
    void tanpaParameterPathDitolak() {
        assertThatThrownBy(() -> KatalogService.validasiFotoUrlMilikTenant(
                SEKOLAH, "/api/storage/file"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("storage sekolah ini");
    }

    @Test
    @DisplayName("traversal ter-encode ke sekolah lain ditolak")
    void traversalTerEncodeDitolak() {
        assertThatThrownBy(() -> KatalogService.validasiFotoUrlMilikTenant(
                SEKOLAH, "/api/storage/file?path=sekolah-7%2F..%2Fsekolah-9%2Fmenu%2Fa.png"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("storage sekolah ini");
    }

    @Test
    @DisplayName("segmen '..' pada path relatif ditolak")
    void traversalPathRelatifDitolak() {
        assertThatThrownBy(() -> KatalogService.validasiFotoUrlMilikTenant(
                SEKOLAH, "sekolah-7/menu/.."))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("storage sekolah ini");
    }

    @Test
    @DisplayName("parameter bernama lain (?xpath=...) tidak dianggap path")
    void parameterMiripPathTidakDiterima() {
        assertThatThrownBy(() -> KatalogService.validasiFotoUrlMilikTenant(
                SEKOLAH, "/api/storage/file?xpath=sekolah-7%2Fmenu%2Fa.png"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("storage sekolah ini");
    }

    @Test
    @DisplayName("path relatif huruf besar ke sekolah lain ditolak")
    void pathRelatifHurufBesarDitolak() {
        assertThatThrownBy(() -> KatalogService.validasiFotoUrlMilikTenant(
                SEKOLAH, "SEKOLAH-9/menu/a.png"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("storage sekolah ini");
    }

    @Test
    @DisplayName("URL storage huruf besar ke sekolah lain ditolak")
    void urlStorageHurufBesarDitolak() {
        assertThatThrownBy(() -> KatalogService.validasiFotoUrlMilikTenant(
                SEKOLAH, "/API/STORAGE/file?path=sekolah-9%2Fmenu%2Fa.png"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("storage sekolah ini");
    }

    @Test
    @DisplayName("URL absolut huruf besar ke host storage sendiri diperiksa tenant")
    void urlAbsolutHurufBesarDitolak() {
        assertThatThrownBy(() -> KatalogService.validasiFotoUrlMilikTenant(
                SEKOLAH, "https://cdn.sekolah.id/kantin/SEKOLAH-9/menu/a.png",
                "https://cdn.sekolah.id/kantin"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("storage sekolah ini");
    }

    @Test
    @DisplayName("URL absolut ke host storage sendiri wajib milik tenant")
    void urlAbsolutHostStorageDiperiksa() {
        String basis = "https://cdn.sekolah.id/kantin";

        assertThatCode(() -> KatalogService.validasiFotoUrlMilikTenant(
                SEKOLAH, "https://cdn.sekolah.id/kantin/sekolah-7/menu/a.png", basis))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> KatalogService.validasiFotoUrlMilikTenant(
                SEKOLAH, "https://cdn.sekolah.id/kantin/sekolah-9/menu/a.png", basis))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("storage sekolah ini");

        // Host lain tetap diizinkan (data demo/eksternal).
        assertThatCode(() -> KatalogService.validasiFotoUrlMilikTenant(
                SEKOLAH, "https://images.unsplash.com/foto.jpg", basis))
                .doesNotThrowAnyException();
    }
}
