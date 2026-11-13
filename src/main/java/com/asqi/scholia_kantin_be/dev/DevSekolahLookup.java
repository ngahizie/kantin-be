package com.asqi.scholia_kantin_be.dev;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Pembaca identitas sekolah untuk shim login dev.
 *
 * <p>Sebelumnya {@code DevLoginController} memakai nama sekolah yang
 * <b>di-hardcode</b> di dalam {@code switch} — akibatnya nama yang tampil di FE
 * tidak sama dengan isi tabel {@code sekolah} (mis. "SMA Negeri 1 SKOOLIA" vs
 * "SMP Negeri 1 Skoolia") dan {@code sekolah.logo_url} (V32) tidak pernah
 * terkirim ke FE sama sekali.
 *
 * <p>Kelas ini membaca {@code nama} + {@code logo_url} langsung dari tabel
 * {@code sekolah} hasil seed dev, sehingga header FE bisa memakai logo sekolah
 * yang sebenarnya (fallback ke {@code /logo.png} bila {@code logo_url} NULL).
 *
 * <p><b>Gagal-terbuka untuk tampilan saja.</b> Bila tabel/seed belum ada,
 * login tetap berhasil dengan nama pengganti dan logo {@code null}; identitas
 * yang dipakai menerbitkan token tetap sepenuhnya berasal dari akun
 * ({@link DevLoginValidator}), bukan dari kelas ini.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DevSekolahLookup {

    private static final String SQL_CARI_SEKOLAH = """
            SELECT id, nama, logo_url
            FROM sekolah
            WHERE id = ?
            """;

    private final JdbcTemplate jdbc;

    /** Identitas sekolah yang dipakai FE (nama + tautan logo). */
    public record InfoSekolah(long id, String nama, String logoUrl) {
    }

    /**
     * Cari identitas sekolah berdasarkan id.
     *
     * @return data sekolah; bila tidak ditemukan/tabel belum ada, nama pengganti
     *         {@code "Sekolah <id>"} dengan {@code logoUrl} {@code null}.
     */
    public InfoSekolah cari(long sekolahId) {
        List<Map<String, Object>> baris;
        try {
            baris = jdbc.queryForList(SQL_CARI_SEKOLAH, sekolahId);
        } catch (DataAccessException e) {
            log.warn("[DEV-LOGIN] tabel sekolah tidak terbaca ({}). Memakai nama pengganti.",
                    e.getClass().getSimpleName());
            return pengganti(sekolahId);
        }

        if (baris.isEmpty()) {
            log.warn("[DEV-LOGIN] sekolah id={} tidak ada di tabel sekolah; memakai nama pengganti.", sekolahId);
            return pengganti(sekolahId);
        }

        Map<String, Object> r = baris.get(0);
        Object nama = r.get("nama");
        Object logoUrl = r.get("logo_url");
        return new InfoSekolah(
                sekolahId,
                nama == null ? pengganti(sekolahId).nama() : nama.toString(),
                logoUrl == null ? null : logoUrl.toString());
    }

    private InfoSekolah pengganti(long sekolahId) {
        return new InfoSekolah(sekolahId, "Sekolah " + sekolahId, null);
    }
}
