package com.asqi.scholia_kantin_be.component.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.dao.CannotSerializeTransactionException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Metrik Prometheus untuk kondisi database sibuk (issue <b>#452</b>, lanjutan #435).
 *
 * <p>Sejak batas waktu DB dipasang (#435), query timeout, lock timeout, deadlock,
 * serialization failure, dan kolam koneksi penuh benar-benar bisa terjadi saat
 * kontensi. Semuanya dijawab 503, tetapi alert perlu membedakan <b>"lambat"</b>
 * dari <b>"gagal"</b> dan dari <b>"infrastruktur"</b> — satu angka 503 saja tidak
 * cukup untuk menentukan harus menaikkan batas waktu, memperbaiki query, atau
 * menambah koneksi.
 *
 * <p><b>Kardinalitas terbatas:</b> label {@code jenis} hanya boleh berisi
 * konstanta di {@link #JENIS_DIKENAL}; nilai lain jatuh ke {@link #JENIS_LAINNYA}.
 * Jangan pernah memasukkan SQL, nama tabel, atau ID ke label.
 */
@Component
public class MetrikDatabaseSibuk {

    /** Nama meter (Prometheus: {@code db_busy_total}). */
    public static final String NAMA = "db.busy";

    /** Statement melewati {@code statement_timeout} (SQLState 57014). */
    public static final String JENIS_QUERY_TIMEOUT = "query_timeout";

    /** Menunggu kunci melewati {@code lock_timeout} (SQLState 55P03). */
    public static final String JENIS_KUNCI = "lock";

    /** Deadlock terdeteksi PostgreSQL (SQLState 40P01). */
    public static final String JENIS_DEADLOCK = "deadlock";

    /** Serialization failure / perlu diulang (SQLState 40001). */
    public static final String JENIS_SERIALISASI = "serialisasi";

    /** Tidak mendapat koneksi dari kolam, atau DB tidak bisa dihubungi. */
    public static final String JENIS_KONEKSI = "koneksi";

    /** Kondisi transien lain yang tidak terklasifikasi. */
    public static final String JENIS_LAINNYA = "lainnya";

    private static final Set<String> JENIS_DIKENAL = Set.of(
            JENIS_QUERY_TIMEOUT, JENIS_KUNCI, JENIS_DEADLOCK,
            JENIS_SERIALISASI, JENIS_KONEKSI, JENIS_LAINNYA);

    private final MeterRegistry registry;

    public MetrikDatabaseSibuk(MeterRegistry registry) {
        this.registry = registry;
        // Pra-registrasi deret berbatas agar metrik muncul sejak start (nilai 0),
        // sehingga query rate/increase dan alert selalu punya deret awal.
        for (String jenis : JENIS_DIKENAL) {
            counter(jenis);
        }
    }

    /** Catat satu request yang dijawab 503 karena database sibuk. */
    public void catat(String jenis) {
        String aman = JENIS_DIKENAL.contains(jenis) ? jenis : JENIS_LAINNYA;
        counter(aman).increment();
    }

    /**
     * Petakan exception ke jenis yang stabil untuk label metrik.
     *
     * <p>Urutan pemeriksaan penting: tipe paling spesifik lebih dulu, karena
     * {@code CannotAcquireLockException} juga turunan {@link ConcurrencyFailureException}.
     */
    public static String jenisUntuk(Throwable ex) {
        if (ex instanceof QueryTimeoutException) {
            return JENIS_QUERY_TIMEOUT;
        }
        if (ex instanceof DeadlockLoserDataAccessException) {
            return JENIS_DEADLOCK;
        }
        if (ex instanceof CannotSerializeTransactionException) {
            return JENIS_SERIALISASI;
        }
        if (ex instanceof CannotAcquireLockException) {
            return JENIS_KUNCI;
        }
        if (ex instanceof CannotGetJdbcConnectionException) {
            return JENIS_KONEKSI;
        }
        if (ex instanceof ConcurrencyFailureException) {
            return JENIS_LAINNYA;
        }
        return JENIS_LAINNYA;
    }

    private Counter counter(String jenis) {
        return Counter.builder(NAMA)
                .description("Jumlah request yang dijawab 503 karena database sibuk — issue #452")
                .tag("jenis", jenis)
                .register(registry);
    }
}
