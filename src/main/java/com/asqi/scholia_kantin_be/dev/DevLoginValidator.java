package com.asqi.scholia_kantin_be.dev;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Validator kredensial untuk shim login dev.
 *
 * <p>Sumber akun: tabel {@code dev_pengguna} yang dibuat oleh seed dev
 * ({@code db/seed/R__seed_dev.sql}) — jadi tabel & datanya hanya ada di lokal,
 * tidak pernah masuk skema produksi (kantin-be tidak menyimpan master pengguna,
 * ADR-0002; identitas asli datang dari admin-be lewat JWT).
 *
 * <p><b>Gagal-tertutup.</b> Akun tidak dikenal, password salah, akun nonaktif,
 * atau seed belum dimuat → tidak ada token yang diterbitkan.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DevLoginValidator {

    private static final String SQL_CARI = """
            SELECT id, sekolah_id, username, nama, role, password_hash
            FROM dev_pengguna
            WHERE lower(username) = lower(?) AND aktif
            """;

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** Identitas yang dipakai menerbitkan token; seluruhnya berasal dari akun. */
    public record AkunDev(long id, long sekolahId, String username, String nama, String role) {
    }

    /** Akun yang cocok, atau {@link Optional#empty()} bila kredensial tidak sah. */
    public Optional<AkunDev> autentikasi(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            return Optional.empty();
        }

        List<Map<String, Object>> baris;
        try {
            baris = jdbc.queryForList(SQL_CARI, username.trim());
        } catch (DataAccessException e) {
            // Tabel belum ada = seed dev belum dimuat. Jangan bocorkan ke klien.
            log.warn("[DEV-LOGIN] tabel dev_pengguna tidak terbaca ({}). Muat seed dev dengan "
                            + "menambahkan classpath:db/seed ke spring.flyway.locations.",
                    e.getClass().getSimpleName());
            return Optional.empty();
        }

        if (baris.size() != 1) {
            return Optional.empty();
        }

        Map<String, Object> r = baris.get(0);
        String hash = (String) r.get("password_hash");
        if (hash == null || !encoder.matches(password, hash)) {
            return Optional.empty();
        }

        return Optional.of(new AkunDev(
                ((Number) r.get("id")).longValue(),
                ((Number) r.get("sekolah_id")).longValue(),
                (String) r.get("username"),
                (String) r.get("nama"),
                (String) r.get("role")));
    }
}
