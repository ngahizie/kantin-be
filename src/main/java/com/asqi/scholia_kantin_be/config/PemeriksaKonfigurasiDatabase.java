package com.asqi.scholia_kantin_be.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Penjaga konfigurasi database yang <b>diisi kosong</b> (issue #452).
 *
 * <p>Placeholder Spring ({@code ${VAR:default}}) hanya memakai nilai default bila
 * variabelnya <b>tidak ada</b>. Kalau variabelnya ada tetapi kosong, nilai kosong
 * itulah yang dipakai, dan akibatnya membingungkan:
 *
 * <ul>
 *   <li>{@code KANTIN_DB_STATEMENT_TIMEOUT=} → {@code set_config('statement_timeout','',false)}
 *       → Hikari gagal membuat koneksi (baru ketahuan saat request pertama).</li>
 *   <li>{@code KANTIN_FLYWAY_URL=} → properti ada tetapi kosong, {@code @ConditionalOnProperty}
 *       tetap match, datasource Flyway tanpa URL → start gagal.</li>
 *   <li>{@code KANTIN_DB_CONNECTION_TIMEOUT_MS=} → binding ke angka gagal.</li>
 * </ul>
 *
 * <p>Penjaga ini mengubah kegagalan samar itu menjadi satu pesan yang menyebut
 * variabel mana yang salah dan apa yang harus dilakukan (hapus barisnya, jangan
 * dikosongkan). Berjalan sekali saat aplikasi siap; melempar exception = start
 * dibatalkan, jadi tidak ada instance yang melayani tap dengan konfigurasi rusak.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PemeriksaKonfigurasiDatabase {

    /** Variabel yang diisi operator dan tidak boleh kosong bila ada. */
    static final List<String> VARIABEL_DIPERIKSA = List.of(
            "KANTIN_DB_STATEMENT_TIMEOUT",
            "KANTIN_DB_LOCK_TIMEOUT",
            "KANTIN_DB_IDLE_TX_TIMEOUT",
            "KANTIN_DB_CONNECTION_TIMEOUT_MS",
            "KANTIN_DB_STATEMENT_TIMEOUT_LAPORAN",
            "KANTIN_LAPORAN_EKSPOR_KONKUREN_MAKS",
            "KANTIN_FLYWAY_URL",
            "KANTIN_FLYWAY_USERNAME",
            "KANTIN_FLYWAY_PASSWORD");

    /** Batas minimum {@code connectionTimeout} Hikari. */
    static final int CONNECTION_TIMEOUT_MINIMUM_MS = 250;

    private final Environment environment;

    @EventListener(ApplicationReadyEvent.class)
    public void periksa() {
        List<String> kosong = new ArrayList<>();
        for (String nama : VARIABEL_DIPERIKSA) {
            String nilai = environment.getProperty(nama);
            if (nilai != null && nilai.isBlank()) {
                kosong.add(nama);
            }
        }
        if (!kosong.isEmpty()) {
            throw new IllegalStateException(
                    "Konfigurasi database diisi KOSONG: " + String.join(", ", kosong) + ". "
                            + "Placeholder Spring hanya memakai nilai default bila variabel TIDAK ADA, "
                            + "bukan bila kosong — variabel kosong membuat statement_timeout='' atau "
                            + "datasource Flyway tanpa URL sehingga aplikasi gagal start. "
                            + "HAPUS barisnya dari .env (jangan dikosongkan) atau isi nilainya. "
                            + "Lihat docs/checklist-pra-rilis.md (issue #452).");
        }

        String initSql = environment.getProperty(
                "spring.datasource.hikari.connection-init-sql", "");
        if (initSql.contains("''")) {
            throw new IllegalStateException(
                    "connection-init-sql memuat nilai GUC kosong (''): " + initSql
                            + ". Periksa KANTIN_DB_STATEMENT_TIMEOUT / KANTIN_DB_LOCK_TIMEOUT / "
                            + "KANTIN_DB_IDLE_TX_TIMEOUT — hapus barisnya bila tidak dipakai (issue #452).");
        }

        Integer connectionTimeout = environment.getProperty(
                "spring.datasource.hikari.connection-timeout", Integer.class);
        if (connectionTimeout != null && connectionTimeout < CONNECTION_TIMEOUT_MINIMUM_MS) {
            throw new IllegalStateException(
                    "connection-timeout=" + connectionTimeout + " ms di bawah batas minimum Hikari ("
                            + CONNECTION_TIMEOUT_MINIMUM_MS + " ms). Periksa "
                            + "KANTIN_DB_CONNECTION_TIMEOUT_MS (issue #452).");
        }

        Integer konkurenEkspor = environment.getProperty(
                "kantin.laporan.ekspor-konkuren-maks", Integer.class);
        if (konkurenEkspor != null && konkurenEkspor < 1) {
            throw new IllegalStateException(
                    "kantin.laporan.ekspor-konkuren-maks=" + konkurenEkspor
                            + " tidak valid (minimal 1). Periksa KANTIN_LAPORAN_EKSPOR_KONKUREN_MAKS (issue #452).");
        }

        log.info("Konfigurasi database terverifikasi: tidak ada variabel kosong, "
                + "connection-timeout={} ms, ekspor-konkuren-maks={} (issue #452).",
                connectionTimeout, konkurenEkspor);
    }
}
