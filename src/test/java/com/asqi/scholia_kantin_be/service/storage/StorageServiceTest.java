package com.asqi.scholia_kantin_be.service.storage;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.dto.UploadResponse;
import com.asqi.scholia_kantin_be.helper.S3Storage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Uji {@link StorageService} — pemaksaan prefix tenant (B19, PRD §11.4),
 * validasi folder, dan penyusunan URL.
 *
 * <p>{@link S3Storage} di-mock: tak menyentuh MinIO/jaringan.
 */
@DisplayName("StorageService — isolasi tenant & validasi unggah")
class StorageServiceTest {

    private S3Storage storage;
    private StorageService service;

    @BeforeEach
    void setUp() {
        storage = mock(S3Storage.class);
        service = new StorageService(storage);
        ReflectionTestUtils.setField(service, "urlFinal", "");
    }

    private MockMultipartFile file(String nama) {
        return new MockMultipartFile("file", nama, "image/png", "isi".getBytes());
    }

    @Test
    @DisplayName("unggah memaksa prefix sekolah-<id>/<folder> (B19)")
    void unggahMemaksaPrefixTenant() throws Exception {
        when(storage.uploadFile(eq("sekolah-7/menu"), any()))
                .thenReturn("sekolah-7/menu/abc123.png");

        UploadResponse hasil = service.unggah(7L, "menu", file("foto.png"));

        assertThat(hasil.getPath()).isEqualTo("sekolah-7/menu/abc123.png");
        assertThat(hasil.getNamaAsli()).isEqualTo("foto.png");
        assertThat(hasil.getUkuran()).isEqualTo(3);
    }

    @Test
    @DisplayName("folder kosong → folder default 'lain'")
    void folderKosongPakaiDefault() throws Exception {
        when(storage.uploadFile(eq("sekolah-3/lain"), any()))
                .thenReturn("sekolah-3/lain/x.png");

        UploadResponse hasil = service.unggah(3L, null, file("x.png"));

        assertThat(hasil.getPath()).isEqualTo("sekolah-3/lain/x.png");
    }

    @Test
    @DisplayName("folder di luar allowlist ditolak")
    void folderTidakDikenalDitolak() {
        assertThatThrownBy(() -> service.unggah(7L, "../rahasia", file("x.png")))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Folder tidak dikenal");
    }

    @Test
    @DisplayName("berkas kosong ditolak")
    void berkasKosongDitolak() {
        MockMultipartFile kosong = new MockMultipartFile("file", "x.png", "image/png", new byte[0]);
        assertThatThrownBy(() -> service.unggah(7L, "menu", kosong))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("tidak boleh kosong");
    }

    @Test
    @DisplayName("error validasi S3Storage (mis. ekstensi) → 400, bukan 500")
    void errorValidasiStorageJadiBadRequest() throws Exception {
        when(storage.uploadFile(any(), any()))
                .thenThrow(new IllegalArgumentException("Tipe file tidak diizinkan: .exe"));

        // Folder non-menu: allowlist global tetap ditegakkan S3Storage.
        assertThatThrownBy(() -> service.unggah(7L, "lain", file("virus.exe")))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Tipe file tidak diizinkan");
    }

    @Test
    @DisplayName("foto menu > 5 MB ditolak sebelum menyentuh storage (M-01)")
    void fotoMenuLebihDariLimaMbDitolak() {
        byte[] besar = new byte[(int) (5 * 1024 * 1024) + 1];
        MockMultipartFile file = new MockMultipartFile("file", "besar.png", "image/png", besar);

        assertThatThrownBy(() -> service.unggah(7L, "menu", file))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("maksimal 5 MB");
    }

    @Test
    @DisplayName("folder nota boleh melebihi 5 MB selama <= 10 MB")
    void notaBolehSampaiSepuluhMb() throws Exception {
        byte[] besar = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "nota.pdf", "application/pdf", besar);
        when(storage.uploadFile(eq("sekolah-7/nota"), any()))
                .thenReturn("sekolah-7/nota/nota.pdf");

        UploadResponse hasil = service.unggah(7L, "nota", file);

        assertThat(hasil.getPath()).isEqualTo("sekolah-7/nota/nota.pdf");
    }

    @Test
    @DisplayName("ekstensi non-gambar ditolak untuk folder menu (M-02)")
    void ekstensiNonGambarMenuDitolak() {
        for (String nama : new String[]{"foto.svg", "arsip.zip", "video.mp4", "dokumen.doc"}) {
            assertThatThrownBy(() -> service.unggah(7L, "menu", file(nama)))
                    .as("ekstensi %s", nama)
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("tidak diizinkan untuk foto menu");
        }
    }

    @Test
    @DisplayName("gambar raster yang diizinkan tetap diterima untuk menu")
    void gambarRasterMenuDiterima() throws Exception {
        for (String nama : new String[]{"foto.jpg", "foto.jpeg", "foto.png", "foto.webp"}) {
            when(storage.uploadFile(eq("sekolah-7/menu"), any()))
                    .thenReturn("sekolah-7/menu/" + nama);
            UploadResponse hasil = service.unggah(7L, "menu", file(nama));
            assertThat(hasil.getPath()).isEqualTo("sekolah-7/menu/" + nama);
        }
    }

    @Test
    @DisplayName("url memakai minio.url-final bila dikonfigurasi")
    void urlMemakaiUrlFinal() throws Exception {
        ReflectionTestUtils.setField(service, "urlFinal", "https://cdn.test/kantin");
        when(storage.uploadFile(eq("sekolah-7/menu"), any()))
                .thenReturn("sekolah-7/menu/a.png");

        UploadResponse hasil = service.unggah(7L, "menu", file("a.png"));

        assertThat(hasil.getUrl()).isEqualTo("https://cdn.test/kantin/sekolah-7/menu/a.png");
    }

    @Test
    @DisplayName("buka menolak path di luar namespace tenant → 404")
    void bukaPathSekolahLainDitolak() {
        assertThatThrownBy(() -> service.buka(7L, "sekolah-9/menu/a.png"))
                .isInstanceOf(NotFoundEntity.class);
    }

    @Test
    @DisplayName("buka path tanpa prefix tenant → 404")
    void bukaPathTanpaPrefixDitolak() {
        assertThatThrownBy(() -> service.buka(7L, "menu/a.png"))
                .isInstanceOf(NotFoundEntity.class);
    }

    @Test
    @DisplayName("buka path milik tenant diteruskan ke storage")
    void bukaPathMilikTenantDiteruskan() throws Exception {
        InputStream is = new ByteArrayInputStream("x".getBytes());
        when(storage.viewFile("sekolah-7/menu/a.png")).thenReturn(is);

        assertThat(service.buka(7L, "sekolah-7/menu/a.png")).isSameAs(is);
    }
}
