package com.asqi.scholia_kantin_be.helper;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

/**
 * Implementasi object storage berbasis lokal (fallback untuk demo).
 * File disimpan di ${minio.upload-dir} (default: ./tmp/kantin-uploads).
 * Digunakan saat MinIO/S3 tidak dikonfigurasi atau untuk development/demo.
 */
@Service
@Slf4j
public class LocalStorage {

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg",
                    ".pdf", ".doc", ".docx", ".xls", ".xlsx",
                    ".mp4", ".mp3", ".zip");

    @Value("${minio.bucket-name:kantin}")
    private String bucketName;

    @Value("${minio.upload-dir:./tmp/kantin-uploads}")
    private String uploadDir;

    private Path rootDir;

    @PostConstruct
    public void init() {
        this.rootDir = Path.of(uploadDir);
        try {
            Files.createDirectories(rootDir);
            log.info("LocalStorage root directory: {}", rootDir.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Gagal membuat direktori storage lokal: " + uploadDir, e);
        }
    }

    /** Validasi ekstensi file */
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

    /** Validasi nama object (anti path-traversal / IDOR) */
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

    /** Simpan file ke disk lokal */
    public String uploadFile(String folderName, MultipartFile file) throws Exception {
        try {
            String originalFileName = file.getOriginalFilename();
            if (isNull(originalFileName)) {
                throw new IllegalArgumentException("Nama file tidak boleh null");
            }
            String extension = validateAndExtractExtension(originalFileName);
            String fileName = UUID.randomUUID().toString().replace("-", "") + extension;
            String objectName = folderName + "/" + fileName;

            validasiObjectName(objectName);

            Path target = rootDir.resolve(objectName);
            Files.createDirectories(target.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("File unggah disimpan lokal: {} (tipe: {})", target, file.getContentType());
            return objectName;
        } catch (IOException e) {
            throw new Exception("Gagal menyimpan file ke storage lokal", e);
        }
    }

    /** Hapus file dari disk lokal */
    public void deleteFile(String objectName) throws Exception {
        validasiObjectName(objectName);
        Path filePath = rootDir.resolve(objectName);
        if (Files.exists(filePath)) {
            Files.delete(filePath);
            log.info("File dihapus dari storage lokal: {}", filePath);
        } else {
            log.warn("File tidak ditemukan untuk dihapus: {}", filePath);
        }
    }

    /** Baca input stream file dari disk lokal */
    public InputStream viewFile(String objectName) throws Exception {
        validasiObjectName(objectName);
        Path filePath = rootDir.resolve(objectName);
        if (!Files.exists(filePath)) {
            throw new Exception("File tidak ditemukan di storage lokal: " + filePath);
        }
        return new BufferedInputStream(Files.newInputStream(filePath));
    }

    /** Daftar file di dalam folder tertentu */
    public List<String> listFiles(String folderName) throws Exception {
        Path dir = rootDir.resolve(folderName);
        if (!Files.exists(dir)) {
            return Collections.emptyList();
        }
        try {
            return Files.list(dir)
                    .filter(Files::isRegularFile)
                    .map(p -> {
                        String relative = rootDir.relativize(p).toString();
                        // Normalisasi separator
                        return relative.replace("\\", "/");
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new Exception("Gagal membaca daftar file di storage lokal", e);
        }
    }
}