package com.asqi.scholia_kantin_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Konfigurasi penjaga <b>peran database least-privilege</b> (issue <b>#433</b>,
 * temuan SEC-02 audit OWASP Top 10:2025 — A01 Broken Access Control &amp;
 * A07 Authentication Failures).
 *
 * <p>Latar belakang: {@code DB_USERNAME} dulu default {@code postgres}
 * (superuser) dan tidak ada pemisahan role migrasi (DDL) dari role runtime
 * (DML). Satu jalur injection/IDOR yang lolos berarti hak penuh atas seluruh
 * database. Penjaga ini menegakkan aturan tersebut saat start.
 *
 * <ul>
 *   <li>{@link #enforce} — bila {@code true} (default), pelanggaran
 *       <b>menggagalkan start</b> pada profil non-dev.</li>
 *   <li>{@link #dikecualikanProfil} — profil dev yang boleh memakai kredensial
 *       longgar <b>hanya bila</b> host DB loopback (docker compose lokal, CI,
 *       demo, loadtest).</li>
 *   <li>{@link #userTerlarang} — nama user DB yang ditolak sebagai role
 *       runtime (superuser bawaan PostgreSQL/managed service).</li>
 *   <li>{@link #wajibPisahMigrasi} — bila {@code true} (default), role Flyway
 *       ({@code spring.flyway.user}) wajib beda dari role runtime.</li>
 *   <li>{@link #cekAtribut} — bila {@code true} (default), atribut role nyata
 *       dibaca dari {@code pg_roles} (bukan hanya namanya).</li>
 * </ul>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "kantin.db.peran")
public class PeranDatabaseProperties {

    /**
     * Bila {@code true} (default), pelanggaran peran DB menggagalkan start
     * untuk profil non-dev. Set {@code false} hanya untuk eksperimen lokal —
     * staging/produksi wajib {@code true}.
     */
    private boolean enforce = true;

    /**
     * Profil yang dianggap dev: kredensial longgar masih boleh <b>asalkan</b>
     * host DB loopback. Profil lain selalu divalidasi.
     */
    private List<String> dikecualikanProfil = List.of("local", "test", "demo", "loadtest");

    /**
     * Nama user DB yang DITOLAK sebagai role runtime (dicocokkan
     * case-insensitive, harus sama persis). Semua ini superuser bawaan
     * PostgreSQL atau managed service.
     */
    private List<String> userTerlarang = List.of(
            "postgres", "superuser", "rdsadmin", "azure_superuser", "cloudsqlsuperuser");

    /**
     * Bila {@code true} (default), role migrasi ({@code spring.flyway.user})
     * wajib berbeda dari role runtime, dan password-nya wajib terisi.
     */
    private boolean wajibPisahMigrasi = true;

    /**
     * Bila {@code true} (default), penjaga membaca atribut role sesungguhnya
     * dari {@code pg_roles} + hak pada skema {@code public} — nama user yang
     * "terlihat aman" tetap ditolak bila ternyata superuser / punya CREATE.
     */
    private boolean cekAtribut = true;
}
