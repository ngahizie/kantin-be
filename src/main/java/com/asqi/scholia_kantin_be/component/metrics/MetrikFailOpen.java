package com.asqi.scholia_kantin_be.component.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Metrik Prometheus untuk observability jalur <b>fail-open</b> yang disetujui
 * (issue <b>#439</b>, OWASP A10:2025, NIST CSF DE.CM / RS.AN).
 *
 * <p>Jalur fail-open (mis. rate limit saat Redis mati, blacklist token saat Redis
 * mati, serta adapter integrasi ke SKOOLIA) sengaja dibiarkan lewat agar kasir
 * tidak lumpuh ({@code docs/checklist-pra-rilis.md §D}). Komponen ini memastikan
 * kondisi bypass tersebut <b>terlihat</b> lewat {@code /actuator/prometheus} dan
 * bisa memicu alert tanpa mengubah perilaku fail-open.
 *
 * <p><b>Kardinalitas terbatas:</b> seluruh nilai label {@code reason} dan
 * {@code adapter} dinormalisasi ke daftar konstanta yang diketahui (fallback
 * {@link #LABEL_LAINNYA}) agar tidak pernah memicu ledakan deret metrik maupun
 * membocorkan PII/ID siswa ke label Prometheus.
 */
@Component
public class MetrikFailOpen {

    /** Nama meter rate-limit bypass (Prometheus: {@code ratelimit_bypass_total}). */
    public static final String NAMA_RATELIMIT_BYPASS = "ratelimit.bypass";

    /** Rate limit dilewati karena Redis error / timeout saat fail-open aktif. */
    public static final String REASON_REDIS_ERROR = "redis_error";

    /** Rate limit dilewati karena saklar {@code kantin.rate-limit.enabled=false}. */
    public static final String REASON_DISABLED = "disabled";

    /** Nama meter adapter integrasi/cache fail-open (Prometheus: {@code integrasi_fail_open_total}). */
    public static final String NAMA_INTEGRASI_FAIL_OPEN = "integrasi.fail.open";

    public static final String ADAPTER_UID_SISWA = "uid_siswa";
    public static final String ADAPTER_AKTIVASI_MODUL = "aktivasi_modul";
    public static final String ADAPTER_AKTIVASI_CACHE = "aktivasi_cache";
    public static final String ADAPTER_TOKEN_BLACKLIST = "token_blacklist";

    /** Fallback aman untuk menjaga kardinalitas label tetap terbatas. */
    public static final String LABEL_LAINNYA = "lainnya";

    private static final Set<String> REASON_DIKENAL = Set.of(
            REASON_REDIS_ERROR,
            REASON_DISABLED
    );

    private static final Set<String> ADAPTER_DIKENAL = Set.of(
            ADAPTER_UID_SISWA,
            ADAPTER_AKTIVASI_MODUL,
            ADAPTER_AKTIVASI_CACHE,
            ADAPTER_TOKEN_BLACKLIST
    );

    private final MeterRegistry registry;

    public MetrikFailOpen(MeterRegistry registry) {
        this.registry = registry;
        // Pra-registrasi deret berbatas agar metrik muncul sejak start di /actuator/prometheus
        // (dengan nilai 0) sehingga query rate/increase & alert Prometheus selalu punya deret awal.
        for (String reason : REASON_DIKENAL) {
            counterRatelimitBypass(reason);
        }
        for (String adapter : ADAPTER_DIKENAL) {
            counterIntegrasiFailOpen(adapter);
        }
    }

    /** Catat satu permintaan yang melewati rate limit (fail-open atau dinonaktifkan). */
    public void catatRatelimitBypass(String reason) {
        String safeReason = REASON_DIKENAL.contains(reason) ? reason : LABEL_LAINNYA;
        counterRatelimitBypass(safeReason).increment();
    }

    /** Catat satu kejadian fail-open pada adapter integrasi / cache eksternal. */
    public void catatIntegrasiFailOpen(String adapter) {
        String safeAdapter = ADAPTER_DIKENAL.contains(adapter) ? adapter : LABEL_LAINNYA;
        counterIntegrasiFailOpen(safeAdapter).increment();
    }

    private Counter counterRatelimitBypass(String reason) {
        return Counter.builder(NAMA_RATELIMIT_BYPASS)
                .description("Jumlah request yang melewati rate limit (fail-open / dinonaktifkan) — issue #439")
                .tag("reason", reason)
                .register(registry);
    }

    private Counter counterIntegrasiFailOpen(String adapter) {
        return Counter.builder(NAMA_INTEGRASI_FAIL_OPEN)
                .description("Jumlah kejadian fail-open pada adapter integrasi / cache eksternal — issue #439")
                .tag("adapter", adapter)
                .register(registry);
    }
}
