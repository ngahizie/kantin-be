package com.asqi.scholia_kantin_be.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji keputusan "kredensial MinIO terisi" pada {@link S3Storage}.
 *
 * <p>Latar belakang: fallback ke {@link LocalStorage} hanya aktif bila kredensial
 * dianggap kosong. Sebelum perbaikan ini, menyalin {@code .env.example} apa adanya
 * (nilainya {@code <GANTI_MINIO_ACCESS_KEY>}) tetap dianggap "terisi" sehingga
 * aplikasi mencoba menghubungi MinIO yang tidak berjalan dan unggah gagal.
 *
 * <p>Tidak menyentuh jaringan/MinIO.
 */
class S3StorageKredensialTest {

    /** {@link LocalStorage} palsu agar uji tidak menulis ke disk. */
    static class LocalStoragePalsu extends LocalStorage {
        @Override
        public String uploadFile(String folderName, org.springframework.web.multipart.MultipartFile file) {
            return folderName + "/palsu.jpg";
        }
    }

    @ParameterizedTest(name = "\"{0}\" → belum dikonfigurasi")
    @ValueSource(strings = {
            "",
            "   ",
            "<GANTI_MINIO_ACCESS_KEY>",
            "<ganti_minio_secret_key>",
            "GANTI_AKU",
            "changeme",
            "  ChangeMe  ",
            "TODO",
            "xxx",
            "placeholder",
            "your-access-key"
    })
    @DisplayName("nilai kosong & placeholder dianggap belum dikonfigurasi")
    void placeholderBelumDikonfigurasi(String nilai) {
        assertThat(S3Storage.kredensialTerisi(nilai)).isFalse();
    }

    @Test
    @DisplayName("null dianggap belum dikonfigurasi")
    void nullBelumDikonfigurasi() {
        assertThat(S3Storage.kredensialTerisi(null)).isFalse();
    }

    @ParameterizedTest(name = "\"{0}\" → terkunci sebagai kredensial asli")
    @ValueSource(strings = {
            "minioadmin",
            "AKIAIOSFODNN7EXAMPLEKEY",
            "kunci-rahasia-sekolah-2026"
    })
    @DisplayName("kredensial asli tetap dianggap terisi (tidak jatuh ke fallback)")
    void kredensialAsliTetapTerisi(String nilai) {
        assertThat(S3Storage.kredensialTerisi(nilai)).isTrue();
    }

    @Test
    @DisplayName("kredensial placeholder → unggah lewat LocalStorage, bukan MinIO")
    void unggahDenganPlaceholderMemakaiLocalStorage() throws Exception {
        S3Storage storage = new S3Storage(
                "http://localhost:9000",
                "<GANTI_MINIO_ACCESS_KEY>",
                "<GANTI_MINIO_SECRET_KEY>",
                new LocalStoragePalsu());
        MockMultipartFile berkas = new MockMultipartFile(
                "file", "foto-menu.jpg", "image/jpeg", new byte[]{1, 2, 3});

        String path = storage.uploadFile("sekolah-1/menu", berkas);

        assertThat(path).isEqualTo("sekolah-1/menu/palsu.jpg");
    }
}
