package com.asqi.scholia_kantin_be.dev;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.repository.KartuTamuRepository;
import com.asqi.scholia_kantin_be.service.integrasi.InfoKartu;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Jalur lookup kartu untuk mode <b>demo / dev</b> (issue #163).
 *
 * <p>Tujuan: membuka alur kasir <b>end-to-end</b> (tap → potong saldo/stok →
 * tutup sesi → cek laporan) di lokal <b>tanpa</b> menunggu data/keys tim SKOOLIA
 * (OPEN-QUESTIONS <b>Q7</b>). Mengenali Kartu Tamu dari tabel lokal dan satu
 * fixture UID siswa {@code DA100001} → siswa demo 1001 di sekolah 10; identitas
 * master siswa tetap tidak disimpan di kantin-be (ADR-0002).
 *
 * <p>Lookup Kartu Tamu memakai {@link KartuLookupKartuTamu}; fixture siswa khusus
 * demo dibatasi ke tenant 10 dan tidak ikut aktif pada profil {@code loadtest}.
 *
 * <p><b>Gerbang keamanan:</b> hanya hidup bila {@code kantin.demo.enabled=true}
 * (default <b>false</b>). {@code @Primary} agar menang atas
 * {@link com.asqi.scholia_kantin_be.service.integrasi.KartuLookupFallback}.
 *
 * <p><b>JANGAN aktifkan di staging/production.</b> Jangan aktifkan bersamaan
 * dengan {@code kantin.loadtest.enabled} (dua bean {@code @Primary} → ambigu).
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "kantin.demo", name = "enabled", havingValue = "true")
public class KartuLookupDemo extends KartuLookupKartuTamu {

    private static final String UID_SISWA_DEMO = "DA100001";
    private static final Long SEKOLAH_DEMO = 10L;
    private static final Long SISWA_DEMO = 1001L;

    private final JdbcTemplate jdbc;

    public KartuLookupDemo(KartuTamuRepository kartuTamuRepo, JdbcTemplate jdbc) {
        super(kartuTamuRepo);
        this.jdbc = jdbc;
    }

    @Override
    public InfoKartu cariBerdasarkanUid(Long sekolahId, String rfidUid) {
        var kartu = super.cariBerdasarkanUid(sekolahId, rfidUid);
        if (kartu.isDikenal() || !SEKOLAH_DEMO.equals(sekolahId)
                || !UID_SISWA_DEMO.equalsIgnoreCase(rfidUid)) {
            return kartu;
        }

        // Fixture demo saja; identitas siswa tetap null sesuai ADR-0002.
        try {
            boolean tersedia = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM dev_siswa_rfid "
                            + "WHERE sekolah_id = ? AND siswa_id = ? AND rfid_uid = ? AND aktif = true)",
                    Boolean.class, SEKOLAH_DEMO, SISWA_DEMO, rfidUid));
            if (!tersedia) {
                return kartu;
            }
        } catch (DataAccessException e) {
            // Fail-closed bila seed lokal tidak dimuat.
            return kartu;
        }
        return InfoKartu.builder()
                .dikenal(true)
                .diblokir(false)
                .subjekTipe(SubjekTipe.SISWA)
                .subjekId(SISWA_DEMO)
                .build();
    }
}
