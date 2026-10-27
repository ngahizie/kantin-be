package com.asqi.scholia_kantin_be.helper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji LocalStorage: unggah, baca, hapus, daftar, validasi ekstensi, validasi path.
 */
class LocalStorageTest {

    private static final Path TEST_DIR = Paths.get("./tmp/kantin-uploads-test");

    private LocalStorage storage;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(TEST_DIR);
        storage = new LocalStorage();
        // Gunakan reflection untuk set field uploadDir (karena @Value di @PostConstruct)
        java.lang.reflect.Field uploadDirField = LocalStorage.class.getDeclaredField("uploadDir");
        uploadDirField.setAccessible(true);
        uploadDirField.set(storage, TEST_DIR.toString());

        java.lang.reflect.Field rootDirField = LocalStorage.class.getDeclaredField("rootDir");
        rootDirField.setAccessible(true);
        rootDirField.set(storage, TEST_DIR);

        // Init ulang (createDirectories)
        java.lang.reflect.Method init = LocalStorage.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(storage);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Hapus seluruh isi direktori test
        try (var stream = Files.walk(TEST_DIR)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); }
                        catch (IOException ignored) {}
                    });
        }
    }

    @Test
    void unggahDanBacaBerhasil() throws Exception {
        byte[] content = "file-test-content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg",
                new ByteArrayInputStream(content));

        String objectName = storage.uploadFile("sekolah-1/menu", file);

        assertThat(objectName).startsWith("sekolah-1/menu/")
                .contains(".jpg");

        InputStream in = storage.viewFile(objectName);
        byte[] read = in.readAllBytes();
        assertThat(read).isEqualTo(content);
    }

    @Test
    void hapusBerhasil() throws Exception {
        byte[] content = "content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.png", "image/png",
                new ByteArrayInputStream(content));

        String objectName = storage.uploadFile("sekolah-1/nota", file);
        Path filePath = TEST_DIR.resolve(objectName);
        assertThat(Files.exists(filePath)).isTrue();

        storage.deleteFile(objectName);
        assertThat(Files.exists(filePath)).isFalse();
    }

    @Test
    void tolakEkstensiTerlarang() throws IOException {
        byte[] content = "content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.exe", "application/octet-stream",
                new ByteArrayInputStream(content));

        assertThatThrownBy(() -> storage.uploadFile("sekolah-1/menu", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tipe file tidak diizinkan");
    }

    @Test
    void tolakPathTraversal() {
        assertThatThrownBy(() -> storage.viewFile("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.viewFile("/etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.viewFile("sekolah-1/../rahasia.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tolakKarakterAneh() {
        assertThatThrownBy(() -> storage.viewFile("sekolah-1/na ma.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.viewFile("sekolah-1/a;rm.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listFileBerhasil() throws Exception {
        byte[] content = "content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg",
                new ByteArrayInputStream(content));
        storage.uploadFile("sekolah-1/menu", file);

        var list = storage.listFiles("sekolah-1/menu");
        assertThat(list).isNotEmpty();
    }
}