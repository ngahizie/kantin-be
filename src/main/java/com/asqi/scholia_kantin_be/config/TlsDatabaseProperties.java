package com.asqi.scholia_kantin_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Konfigurasi penjaga TLS koneksi PostgreSQL (issue <b>#432</b>, temuan SEC-01).
 *
 * <p>Latar belakang: driver pgjdbc memakai {@code sslmode=prefer} bila parameter
 * tidak diset — artinya koneksi <b>diam-diam jatuh ke plaintext</b> saat server
 * tidak mendukung TLS. Itu tidak bisa diterima untuk DB yang menyimpan saldo,
 * UID kartu, dan data siswa (OWASP A04:2025).
 *
 * <ul>
 *   <li>{@link #enforce} — bila {@code true} (default), koneksi yang tidak
 *       memakai {@code sslmode=verify-full} <b>menggagalkan start</b> pada
 *       profil non-dev.</li>
 *   <li>{@link #dikecualikanProfil} — profil yang boleh memakai plaintext
 *       <b>hanya bila</b> host DB-nya loopback (docker compose lokal, CI,
 *       demo, loadtest). Profil di luar daftar ini selalu divalidasi.</li>
 * </ul>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "kantin.db.tls")
public class TlsDatabaseProperties {

    /**
     * Bila {@code true} (default), TLS divalidasi saat start untuk profil
     * non-dev. Set {@code false} hanya untuk eksperimen lokal — produksi
     * wajib {@code true}.
     */
    private boolean enforce = true;

    /**
     * Profil yang dianggap dev: plaintext masih boleh <b>asalkan</b> host DB
     * loopback. Profil lain (staging/produksi) selalu wajib
     * {@code sslmode=verify-full}.
     */
    private List<String> dikecualikanProfil = List.of("local", "test", "demo", "loadtest");
}
