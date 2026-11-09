package com.asqi.scholia_kantin_be.dev;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.repository.KartuTamuRepository;
import com.asqi.scholia_kantin_be.service.integrasi.InfoKartu;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Jalur lookup kartu untuk mode <b>demo / dev</b> (issue #163).
 *
 * <p>Tujuan: membuka alur kasir <b>end-to-end</b> (tap → potong saldo/stok →
 * tutup sesi → cek laporan) di lokal <b>tanpa</b> menunggu data/keys tim SKOOLIA
 * (OPEN-QUESTIONS <b>Q7</b>). Mengenali Kartu Tamu dari tabel lokal dan kartu
 * siswa dari fixture {@code dev_siswa_rfid} (sekolah mana pun, selama barisnya
 * ada dan {@code aktif = true}); identitas master siswa tetap tidak disimpan di
 * kantin-be (ADR-0002).
 *
 * <p>Sejak seed demo tidak lagi memakai Kartu Tamu, fixture {@code dev_siswa_rfid}
 * menjadi satu-satunya jalur tap yang berfungsi di lokal — karena itu lookup-nya
 * tidak lagi dibatasi ke satu tenant/UID.
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

    private static final String SQL_FIXTURE_SISWA =
            "SELECT siswa_id FROM dev_siswa_rfid "
                    + "WHERE sekolah_id = ? AND lower(rfid_uid) = lower(?) AND aktif = true";

    private final JdbcTemplate jdbc;

    public KartuLookupDemo(KartuTamuRepository kartuTamuRepo, JdbcTemplate jdbc) {
        super(kartuTamuRepo);
        this.jdbc = jdbc;
    }

    @Override
    public InfoKartu cariBerdasarkanUid(Long sekolahId, String rfidUid) {
        InfoKartu kartu = super.cariBerdasarkanUid(sekolahId, rfidUid);
        if (kartu.isDikenal() || sekolahId == null || rfidUid == null || rfidUid.isBlank()) {
            return kartu;
        }

        // Fixture demo saja; identitas siswa tetap null sesuai ADR-0002.
        List<Long> siswaIds;
        try {
            siswaIds = jdbc.queryForList(SQL_FIXTURE_SISWA, Long.class, sekolahId, rfidUid.trim());
        } catch (DataAccessException e) {
            // Fail-closed bila seed lokal tidak dimuat.
            return kartu;
        }
        if (siswaIds.isEmpty()) {
            return kartu;
        }
        return InfoKartu.builder()
                .dikenal(true)
                .diblokir(false)
                .subjekTipe(SubjekTipe.SISWA)
                .subjekId(siswaIds.get(0))
                .build();
    }
}
