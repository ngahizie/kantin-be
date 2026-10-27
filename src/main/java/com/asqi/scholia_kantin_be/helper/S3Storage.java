package com.asqi.scholia_kantin_be.helper;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.MinioException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;

/**
 * Object storage MinIO/S3 untuk foto menu & nota barang masuk, dengan
 * fallback ke {@link LocalStorage} saat kredensial MinIO belum diisi
 * (development/demo tanpa MinIO berjalan).
 *
 * <p>Parity dengan admin-be (helper/S3Storage). Lihat AGENTS.md §2.
 *
 * <p><b>Lazy init:</b> klien MinIO baru dibuat saat benar-benar dipakai.
 * Saat kredensial kosong, {@link LocalStorage} dipakai agar aplikasi tetap
 * bisa menyimpan/membaca berkas di direktori lokal tanpa MinIO.
 */
@Service
@Slf4j
public class S3Storage {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg",
        ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
        ".mp4", ".mp3", ".zip"
    );

    private final String url;
    private final String accessKey;
    private final String secretKey;

    /** Dapat dipakai bila kredensial MinIO kosong. */
    private final LocalStorage localStorage;

    /** Klien dibuat malas (double-checked locking) pada pemakaian pertama. */
    private volatile MinioClient client;

    @Value("${minio.bucket-name}")
    private String bucketName;

    public S3Storage(@Value("${minio.url}") String url,
                     @Value("${minio.access-key}") String accessKey,
                     @Value("${minio.secret-key}") String secretKey,
                     LocalStorage localStorage) {
        this.url = url;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.localStorage = localStorage;

        if (accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank()) {
            log.info("S3Storage: MinIO diaktifkan (endpoint={})", url);
        } else {
            log.info("S3Storage: kredensial MinIO kosong — pakai LocalStorage fallback");
        }
    }

    /** @return klien MinIO siap pakai; null bila fallback aktif. */
    private MinioClient client() {
        MinioClient local = this.client;
        if (local == null) {
            synchronized (this) {
                local = this.client;
                if (local == null) {
                    if (accessKey == null || accessKey.isBlank()
                            || secretKey == null || secretKey.isBlank()) {
                        return null; // fallback akan dihandle oleh pemanggil
                    }
                    local = MinioClient.builder()
                            .endpoint(url)
                            .credentials(accessKey, secretKey)
                            .build();
                    this.client = local;
                }
            }
        }
        return local;
    }

    private boolean useLocalStorage() {
        return client() == null;
    }

    private String validateAndExtractExtension(String originalFileName) {
        if (originalFileName == null || !originalFileName.contains(".")) {
            throw new IllegalArgumentException("File harus memiliki ekstensi");
        }
        String ext = originalFileName.substring(originalFileName.lastIndexOf(".")).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Tipe file tidak diizinkan: " + ext);
        }
        return ext;
    }

    /** Validasi nama objek agar tak bisa keluar dari namespace yang diizinkan (anti path-traversal/IDOR). */
    private void validasiObjectName(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException("Nama objek wajib diisi");
        }
        if (objectName.startsWith("/") || objectName.contains("\\") || objectName.contains("\0")) {
            throw new IllegalArgumentException("Nama objek tidak valid");
        }
        for (String segmen : objectName.split("/")) {
            if (segmen.isEmpty() || segmen.equals(".") || segmen.equals("..")) {
                throw new IllegalArgumentException("Nama objek tidak valid (segmen terlarang)");
            }
            if (!segmen.matches("[A-Za-z0-9._-]+")) {
                throw new IllegalArgumentException("Nama objek memuat karakter tidak diizinkan: " + segmen);
            }
        }
    }

    public String uploadFile(String folderName, MultipartFile file) throws Exception {
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null) {
            throw new IllegalArgumentException("Nama file tidak boleh null");
        }
        String extension = validateAndExtractExtension(originalFileName);
        String fileName = UUID.randomUUID().toString().replace("-", "") + extension;
        String objectName = folderName + "/" + fileName;
        validasiObjectName(objectName);

        if (useLocalStorage()) {
            return localStorage.uploadFile(folderName, file);
        }

        try (InputStream inputStream = file.getInputStream()) {
            client().putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            return objectName;
        } catch (MinioException e) {
            throw new Exception("Error while uploading file to MinIO", e);
        }
    }

    public void deleteFile(String objectName) throws Exception {
        validasiObjectName(objectName);

        if (useLocalStorage()) {
            localStorage.deleteFile(objectName);
            return;
        }

        client().removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
        );
    }

    public InputStream viewFile(String objectName) throws Exception {
        validasiObjectName(objectName);

        if (useLocalStorage()) {
            return localStorage.viewFile(objectName);
        }

        try {
            GetObjectResponse response = client().getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            return new BufferedInputStream(response);
        } catch (Exception e) {
            throw new Exception("Gagal mengambil file dari MinIO: " + e.getMessage());
        }
    }
}