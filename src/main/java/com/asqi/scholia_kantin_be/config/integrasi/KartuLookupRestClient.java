package com.asqi.scholia_kantin_be.config.integrasi;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.service.integrasi.InfoKartu;
import com.asqi.scholia_kantin_be.service.integrasi.KartuLookupPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.util.Optional;

/**
 * Adapter REST nyata {@link KartuLookupPort} — kartu siswa dari <b>admin-be</b>
 * ({@code GET /api/siswa/cek-kartu/{rfidUid}}, INTEGRATIONS.md §4, issue <b>#160</b>).
 *
 * <p>Kartu tamu sudah dihapus dari skema v2 (PRD §9.4 lama): seluruh pemegang
 * kartu adalah siswa, jadi tidak ada lagi lookup lokal sebelum admin-be.
 *
 * <p><b>Status blokir TIDAK di-cache (PRD §11.11).</b> Adapter hanya mengisi
 * {@code diblokir=false}; blokir sesungguhnya ditempelkan oleh
 * {@code KontrolKartuService.terapkan()} yang membaca DB kantin-be tiap tap.
 * Adapter tidak menyimpan state apa pun.
 *
 * <p><b>Kapan hidup?</b> Hanya bila
 * {@code kantin.integrasi.kartu-lookup.enabled=true} (default <b>false</b>).
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "kantin.integrasi.kartu-lookup", name = "enabled", havingValue = "true")
@Slf4j
public class KartuLookupRestClient extends IntegrasiRestSupport implements KartuLookupPort {

    /** Path default cek pemilik kartu siswa (Q7). */
    public static final String PATH_DEFAULT = "/api/siswa/cek-kartu/{rfidUid}";

    public KartuLookupRestClient(IntegrasiRestProperties properties) {
        super("KartuLookup", properties.getKartuLookup());
    }

    @Override
    public InfoKartu cariBerdasarkanUid(Long sekolahId, String rfidUid) {
        if (rfidUid == null || rfidUid.isBlank()) {
            return InfoKartu.tidakDikenal();
        }
        return cariSiswa(rfidUid);
    }

    private InfoKartu cariSiswa(String rfidUid) {
        try {
            String json = coba("lookup " + rfidUid, () -> {
                var spec = klien().get().uri(path(PATH_DEFAULT), rfidUid);
                return beriHeader(spec, null).retrieve().body(String.class);
            });
            return parseSiswa(json);
        } catch (RestClientResponseException e) {
            // 404 = kartu tidak terdaftar ke siswa mana pun (bukan error).
            if (e.getStatusCode().value() == 404) {
                log.debug("[KartuLookup] UID {} tidak dikenal siswa (404).", rfidUid);
                return InfoKartu.tidakDikenal();
            }
            log.warn("[KartuLookup] admin-be menolak lookup UID {} (HTTP {}).", rfidUid,
                    e.getStatusCode().value());
            return InfoKartu.tidakDikenal();
        } catch (RuntimeException e) {
            // Fail-closed untuk lookup kartu: kartu tak dikenal → tap ditolak dengan
            // pesan benar (PRD §6.1), bukan crash atau meloloskan transaksi.
            log.warn("[KartuLookup] Gagal lookup UID {} (dianggap tidak dikenal): {}",
                    rfidUid, e.getMessage());
            return InfoKartu.tidakDikenal();
        }
    }

    private InfoKartu parseSiswa(String json) {
        if (json == null || json.isBlank()) {
            return InfoKartu.tidakDikenal();
        }
        JsonNode root = JSON.readTree(json);
        JsonNode d = data(root);
        if (d == null || d.isNull() || !d.isObject() || d.get("id") == null) {
            return InfoKartu.tidakDikenal();
        }
        Long id = d.get("id").asLong();
        String nama = teks(d, "namaLengkap");
        String kelas = d.has("kelas") && d.get("kelas") != null
                ? teks(d.get("kelas"), "namaKelas") : null;
        String foto = teks(d, "fotoAnakUrl");
        return InfoKartu.builder()
                .dikenal(true)
                .diblokir(false)
                .subjekTipe(SubjekTipe.SISWA)
                .siswaId(id)
                .nama(nama)
                .kelas(kelas)
                .fotoUrl(foto)
                .build();
    }

    private static String teks(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

}
