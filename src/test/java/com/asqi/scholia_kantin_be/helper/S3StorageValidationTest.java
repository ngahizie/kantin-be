package com.asqi.scholia_kantin_be.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji validasi nama objek S3/MinIO (B19 — cegah path-traversal / IDOR).
 * <p>
 * Tidak menyentuh jaringan/MinIO: hanya memanggil jalur validasi yang
 * fail-fast sebelum klien dibuat. Karena {@code viewFile}/{@code deleteFile}
 * memvalidasi <b>secara sinkron sebelum</b> menyentuh klien, pemanggilan dengan
 * nama objek terlarang tidak akan mencoba koneksi.
 */
class S3StorageValidationTest {

    private final S3Storage storage = new S3Storage(
            "http://localhost:9000",
            "",
            "",
            new LocalStorage());

    @Test
    @DisplayName("tolak traversal '../../etc/passwd'")
    void tolakTraversal() {
        assertThatThrownBy(() -> storage.viewFile("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.deleteFile("sekolah-1/../../rahasia.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("tolak nama objek absolut & backslash & kosong")
    void tolakBentukTerlarang() {
        assertThatThrownBy(() -> storage.viewFile("/etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.viewFile("sekolah-1\\\\rahasia.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.viewFile(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.viewFile("sekolah-1//ganda.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("tolak karakter di luar allowlist (spasi, ;, unicode)")
    void tolakKarakterAneh() {
        assertThatThrownBy(() -> storage.viewFile("sekolah-1/na ma.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.viewFile("sekolah-1/a;rm.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Content-Type ditentukan server-side dari ekstensi (M-02)")
    void contentTypeServerSide() {
        assertThat(S3Storage.contentTypeUntuk(".png")).isEqualTo("image/png");
        assertThat(S3Storage.contentTypeUntuk(".jpg")).isEqualTo("image/jpeg");
        assertThat(S3Storage.contentTypeUntuk(".webp")).isEqualTo("image/webp");
        assertThat(S3Storage.contentTypeUntuk(".exe")).isEqualTo("application/octet-stream");
    }
}