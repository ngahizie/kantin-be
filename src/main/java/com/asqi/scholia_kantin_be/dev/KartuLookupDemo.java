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

    record SiswaKartu(Long siswaId, boolean aktif, String nama, String kelas) {
        /** Dipakai pengujian lama yang belum peduli nama. */
        SiswaKartu(Long siswaId, boolean aktif) {
            this(siswaId, aktif, null, null);
        }
    }

    private final JdbcTemplate jdbc;

    public KartuLookupDemo(KartuTamuRepository kartuTamuRepo, JdbcTemplate jdbc) {
        super(kartuTamuRepo);
        this.jdbc = jdbc;
    }

    @Override
    public InfoKartu cariBerdasarkanUid(Long sekolahId, String rfidUid) {
        var kartu = super.cariBerdasarkanUid(sekolahId, rfidUid);
        if (kartu.isDikenal()) {
            return kartu;
        }

        if (sekolahId == null || rfidUid == null || rfidUid.isBlank()) {
            return kartu;
        }

        // Fixture demo saja; identitas siswa tetap null sesuai ADR-0002.
        try {
            var list = jdbc.query(
                    "SELECT siswa_id, aktif, nama, kelas FROM dev_siswa_rfid "
                            + "WHERE sekolah_id = ? AND UPPER(rfid_uid) = UPPER(?)",
                    (rs, rowNum) -> new SiswaKartu(
                            rs.getLong("siswa_id"),
                            rs.getBoolean("aktif"),
                            rs.getString("nama"),
                            rs.getString("kelas")),
                    sekolahId, rfidUid.trim());

            if (!list.isEmpty()) {
                SiswaKartu sk = list.get(0);
                return InfoKartu.builder()
                        .dikenal(true)
                        .diblokir(!sk.aktif())
                        .subjekTipe(SubjekTipe.SISWA)
                        .subjekId(sk.siswaId())
                        // Nama/kelas hanya untuk tampilan struk demo; boleh null.
                        .nama(sk.nama())
                        .kelas(sk.kelas())
                        .build();
            }
        } catch (DataAccessException e) {
            // Fail-closed bila seed lokal tidak dimuat.
            return kartu;
        }
        return kartu;
    }
}
