package com.asqi.scholia_kantin_be.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Penjaga enkripsi koneksi PostgreSQL (issue <b>#432</b>, temuan SEC-01 audit
 * OWASP Top 10:2025 — <b>A04 Cryptographic Failures</b>).
 *
 * <p>Masalah yang dicegah: {@code DB_URL} tanpa parameter {@code sslmode} membuat
 * driver pgjdbc memakai default {@code prefer}, sehingga koneksi <b>diam-diam
 * memakai plaintext</b> bila server PostgreSQL tidak menyediakan TLS. Kredensial
 * DB serta data pribadi siswa (nama, UID kartu, saldo) dapat disadap di jaringan.
 *
 * <p>Aturan yang ditegakkan saat {@link ApplicationReadyEvent}:
 * <ol>
 *   <li>Bila {@code kantin.db.tls.enforce=false} ⇒ hanya INFO (dev eksperimen).</li>
 *   <li>Bila profil termasuk {@code kantin.db.tls.dikecualikan-profil} <b>dan</b>
 *       host DB loopback ⇒ WARN, start tetap jalan (docker compose lokal, CI,
 *       demo, loadtest). Profil {@code local} dengan host DB <b>non-loopback</b>
 *       tetap divalidasi — mencegah "produksi yang lupa ganti profil".</li>
 *   <li>Selain itu, {@code sslmode} <b>wajib</b> {@code verify-full} (memverifikasi
 *       CA <i>dan</i> hostname). {@code require}/{@code verify-ca}/{@code prefer}/
 *       {@code disable}/kosong ⇒ {@link IllegalStateException} ⇒ <b>start gagal</b>
 *       (fail-closed, pola sama dengan {@code PemeriksaTopologiDatabase}).</li>
 * </ol>
 *
 * <p>{@code sslrootcert} kosong tidak menggagalkan start (verifikasi memakai
 * truststore default JVM), tetapi dicatat sebagai peringatan karena CA internal
 * sebaiknya dipasang eksplisit.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PemeriksaTlsDatabase {

    /** Satu-satunya {@code sslmode} yang diterima: verifikasi CA <i>dan</i> hostname. */
    static final String SSLMODE_DIWAJIBKAN = "verify-full";

    /** Host yang dianggap "mesin sendiri" sehingga plaintext masih wajar di dev. */
    private static final Set<String> HOST_LOKAL = Set.of(
            "localhost", "127.0.0.1", "::1", "[::1]", "0:0:0:0:0:0:0:1");

    private final TlsDatabaseProperties properties;
    private final Environment environment;

    @EventListener(ApplicationReadyEvent.class)
    public void periksa() {
        String url = environment.getProperty("spring.datasource.url");
        String sslmode = ambilParameter(url, "sslmode");
        String host = PemeriksaTopologiDatabase.host(url);
        String profil = Arrays.toString(environment.getActiveProfiles());

        if (!properties.isEnforce()) {
            log.info("Penjaga TLS database NONAKTIF (kantin.db.tls.enforce=false): sslmode='{}', host='{}', profil={}. "
                    + "Aktifkan di staging/produksi (issue #432).", sslmode, host, profil);
            return;
        }

        if (profilDev() && hostLokal(host)) {
            log.warn("TLS database TIDAK diwajibkan: profil dev {} + host DB lokal '{}' (sslmode='{}'). "
                    + "Staging/produksi WAJIB memakai sslmode=verify-full (issue #432).",
                    profil, host, sslmode);
            return;
        }

        if (!SSLMODE_DIWAJIBKAN.equalsIgnoreCase(sslmode)) {
            throw new IllegalStateException(
                    "Koneksi PostgreSQL tidak terenkripsi dengan benar (OWASP A04:2025, issue #432): "
                            + "sslmode=" + (sslmode == null
                            ? "(tidak diset → driver memakai default 'prefer' yang bisa jatuh ke plaintext)"
                            : "'" + sslmode + "'")
                            + ", host='" + host + "', profil=" + profil
                            + ". Wajib sslmode=verify-full (bukan require/verify-ca/prefer/disable) pada DB_URL. "
                            + "Contoh: DB_URL=jdbc:postgresql://<host>:5432/kantin_db"
                            + "?sslmode=verify-full&sslrootcert=/etc/kantin/pg-ca.pem");
        }

        if (kosong(ambilParameter(url, "sslrootcert"))) {
            log.warn("TLS database aktif (sslmode=verify-full) tetapi sslrootcert kosong — verifikasi memakai "
                    + "truststore default JVM. Disarankan mengisi sslrootcert dengan CA PostgreSQL internal.");
        }

        log.info("TLS database terverifikasi: sslmode=verify-full, host='{}', profil={} (issue #432).",
                host, profil);
    }

    /**
     * {@code true} bila seluruh profil aktif termasuk daftar dev, atau tidak ada
     * profil aktif sama sekali (unit/integration test tanpa profil).
     */
    private boolean profilDev() {
        String[] aktif = environment.getActiveProfiles();
        if (aktif.length == 0) {
            return true;
        }
        List<String> dikecualikan = properties.getDikecualikanProfil();
        if (dikecualikan == null || dikecualikan.isEmpty()) {
            return false;
        }
        return Arrays.stream(aktif)
                .allMatch(p -> dikecualikan.stream().anyMatch(d -> d != null && d.equalsIgnoreCase(p)));
    }

    /** {@code true} bila host menunjuk mesin sendiri. */
    static boolean hostLokal(String host) {
        return host != null && HOST_LOKAL.contains(host.toLowerCase(Locale.ROOT));
    }

    /**
     * Ambil nilai parameter query pada URL JDBC (nama case-insensitive).
     *
     * @return nilai parameter, string kosong bila parameter ada tanpa nilai,
     *         atau {@code null} bila parameter tidak ada / URL tidak punya query
     */
    static String ambilParameter(String url, String nama) {
        if (url == null || nama == null) {
            return null;
        }
        int q = url.indexOf('?');
        if (q < 0) {
            return null;
        }
        for (String bagian : url.substring(q + 1).split("&")) {
            int eq = bagian.indexOf('=');
            String kunci = (eq < 0 ? bagian : bagian.substring(0, eq)).trim();
            if (kunci.equalsIgnoreCase(nama)) {
                return eq < 0 ? "" : bagian.substring(eq + 1).trim();
            }
        }
        return null;
    }

    private static boolean kosong(String nilai) {
        return nilai == null || nilai.isBlank();
    }
}
