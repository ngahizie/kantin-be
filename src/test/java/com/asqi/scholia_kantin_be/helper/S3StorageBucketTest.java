package com.asqi.scholia_kantin_be.helper;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Uji pembuatan bucket otomatis pada {@link S3Storage}.
 *
 * <p>Sebelumnya bucket {@code kantin} harus dibuat manual lewat console MinIO
 * ({@code README-DEV.md} §5). Kalau terlewat, unggah gagal dengan pesan yang
 * tidak menunjukkan penyebab sebenarnya.
 *
 * <p>Tidak menyentuh MinIO sungguhan — klien di-mock.
 */
class S3StorageBucketTest {

    private MinioClient klien;

    @BeforeEach
    void siapkan() {
        klien = mock(MinioClient.class);
    }

    private S3Storage storageDenganBucket(String namaBucket) {
        S3Storage storage = new S3Storage(
                "http://localhost:9000", "minioadmin", "minioadmin", new LocalStorage());
        ReflectionTestUtils.setField(storage, "bucketName", namaBucket);
        return storage;
    }

    @Test
    @DisplayName("bucket belum ada → dibuat otomatis sekali")
    void membuatBucketBilaBelumAda() throws Exception {
        when(klien.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        storageDenganBucket("kantin").pastikanBucket(klien);

        verify(klien, times(1)).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    @DisplayName("bucket sudah ada → tidak dibuat ulang")
    void tidakMembuatBucketBilaSudahAda() throws Exception {
        when(klien.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        storageDenganBucket("kantin").pastikanBucket(klien);

        verify(klien, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    @DisplayName("pemeriksaan hanya sekali walau dipanggil berkali-kali")
    void memeriksaSekaliPerInstance() throws Exception {
        when(klien.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        S3Storage storage = storageDenganBucket("kantin");

        storage.pastikanBucket(klien);
        storage.pastikanBucket(klien);
        storage.pastikanBucket(klien);

        verify(klien, times(1)).bucketExists(any(BucketExistsArgs.class));
        verify(klien, times(1)).makeBucket(any(MakeBucketArgs.class));
    }
}
