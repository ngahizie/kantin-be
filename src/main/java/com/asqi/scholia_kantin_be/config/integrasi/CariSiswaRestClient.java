package com.asqi.scholia_kantin_be.config.integrasi;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.service.integrasi.CariSiswaPort;
import com.asqi.scholia_kantin_be.service.integrasi.InfoSiswa;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter REST nyata {@link CariSiswaPort} — pencarian siswa di <b>admin-be</b>
 * (PRD §9.2, issue <b>#214</b> · Q7 · penjaga #164).
 *
 * <p><b>Kenapa adapter ini penting:</b> {@code cari-siswa} sudah terdaftar di
 * {@link PemeriksaIntegrasiProduksi} sejak #164, tetapi implementasi nyatanya
 * belum ada — sehingga penjaga produksi bisa <i>lolos</i> saat
 * {@code kantin.integrasi.cari-siswa.enabled=true} padahal aplikasi tetap
 * memakai {@link com.asqi.scholia_kantin_be.service.integrasi.CariSiswaFallback}
 * (daftar kosong). Adapter ini menutup celah itu: bila saklar dinyalakan,
 * pencarian benar-benar memanggil admin-be.
 *
 * <p><b>Tenant:</b> {@code sekolahId} tidak dikirim ke admin-be karena
 * pembatasan tenant dilakukan dari token mesin-ke-mesin (pola sama seperti
 * {@link KartuLookupRestClient}). Parameter ini tetap dipakai untuk mengambil
 * saldo dari ledger lokal — saldo <b>tidak</b> berasal dari admin-be.
 *
 * <p><b>Fail-open (PRD §8.4):</b> kegagalan integrasi (timeout/5xx/payload
 * rusak) dikembalikan sebagai <b>daftar kosong</b> — sama dengan perilaku
 * {@code CariSiswaFallback} — agar layar TU tidak error karena admin-be down.
 * 404 juga berarti "tidak ada hasil", bukan kegagalan.
 *
 * <p><b>Kapan hidup?</b> Hanya bila
 * {@code kantin.integrasi.cari-siswa.enabled=true} (default <b>false</b>),
 * sehingga dev/demo/test tetap memakai fallback/demo.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "kantin.integrasi.cari-siswa", name = "enabled", havingValue = "true")
@Slf4j
public class CariSiswaRestClient extends IntegrasiRestSupport implements CariSiswaPort {

    /** Path default pencarian siswa (Q7). */
    public static final String PATH_DEFAULT = "/api/siswa/cari";

    /**
     * Path default lookup identitas satu siswa (issue #297). {@code {id}}
     * diganti ID siswa; bisa ditimpa lewat
     * {@code kantin.integrasi.cari-siswa.path-by-id}.
     */
    public static final String PATH_BY_ID_DEFAULT = "/api/siswa/{id}";

    /**
     * Path default lookup batch (issue #298). Daftar ID dikirim sebagai
     * parameter {@code ids=1,2,3}; bisa ditimpa lewat
     * {@code kantin.integrasi.cari-siswa.path-banyak}.
     */
    public static final String PATH_BANYAK_DEFAULT = "/api/siswa";

    /** Batas ID per permintaan batch agar URL tidak melebihi batas server. */
    private static final int BATAS_BATCH = 100;

    private final LedgerSaldoService ledgerSaldo;

    public CariSiswaRestClient(IntegrasiRestProperties properties, LedgerSaldoService ledgerSaldo) {
        super("CariSiswa", properties.getCariSiswa());
        this.ledgerSaldo = ledgerSaldo;
    }

    @Override
    public List<InfoSiswa> cari(Long sekolahId, String kataKunci, boolean hanyaTidakAktif, int batas) {
        int batasAman = Math.max(1, batas);
        String q = kataKunci == null ? "" : kataKunci.trim();

        JsonNode daftar;
        try {
            String json = coba("cari siswa q='" + q + "'", () -> {
                var spec = klien().get().uri(b -> b.path(path(PATH_DEFAULT))
                        .queryParam("q", q)
                        .queryParam("batas", batasAman)
                        .queryParam("hanyaTidakAktif", hanyaTidakAktif)
                        .build());
                return beriHeader(spec, null).retrieve().body(String.class);
            });
            daftar = daftarSiswa(json);
        } catch (RestClientResponseException e) {
            // 404 = tidak ada hasil (bukan error); status lain = admin-be menolak.
            log.warn("[CariSiswa] admin-be menjawab HTTP {} untuk q='{}' → daftar kosong.",
                    e.getStatusCode().value(), q);
            return List.of();
        } catch (RuntimeException e) {
            // Fail-open: sama seperti CariSiswaFallback (daftar kosong).
            log.warn("[CariSiswa] Gagal mencari siswa q='{}' (dianggap kosong): {}",
                    q, e.getMessage());
            return List.of();
        }

        List<InfoSiswa> hasil = new ArrayList<>();
        for (JsonNode item : daftar) {
            InfoSiswa siswa = keInfo(sekolahId, item);
            if (siswa == null) {
                continue;
            }
            // Filter di sisi klien sebagai jaring pengaman bila admin-be
            // mengabaikan parameter hanyaTidakAktif.
            if (hanyaTidakAktif && !Boolean.TRUE.equals(siswa.getTidakAktif())) {
                continue;
            }
            hasil.add(siswa);
            if (hasil.size() >= batasAman) {
                break;
            }
        }
        log.debug("[CariSiswa] q='{}' hanyaTidakAktif={} → {} hasil (admin-be).",
                q, hanyaTidakAktif, hasil.size());
        return List.copyOf(hasil);
    }

    /**
     * Lookup identitas satu siswa berdasarkan ID (issue #297).
     *
     * <p>Berbeda dari {@link #cari} yang menarik daftar hasil: di sini ID sudah
     * diketahui dari ledger, jadi satu permintaan tepat per ID. 404 / kegagalan
     * integrasi diperlakukan sebagai "tidak ada identitas" (fail-open), bukan
     * error, agar riwayat saldo tetap bisa ditampilkan.
     */
    @Override
    public Optional<InfoSiswa> cariBerdasarkanId(Long sekolahId, Long siswaId) {
        if (siswaId == null) {
            return Optional.empty();
        }
        JsonNode isi;
        try {
            String json = coba("identitas siswa id=" + siswaId, () -> {
                var spec = klien().get().uri(b -> b.path(pathById(siswaId)).build());
                return beriHeader(spec, null).retrieve().body(String.class);
            });
            isi = data(JSON.readTree(json));
        } catch (RestClientResponseException e) {
            log.warn("[CariSiswa] admin-be menjawab HTTP {} untuk siswa id={} → identitas kosong.",
                    e.getStatusCode().value(), siswaId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("[CariSiswa] Gagal mengambil identitas siswa id={} (dianggap kosong): {}",
                    siswaId, e.getMessage());
            return Optional.empty();
        }
        if (isi == null || isi.isNull() || !isi.isObject()) {
            return Optional.empty();
        }
        return Optional.ofNullable(keInfo(sekolahId, isi));
    }

    /**
     * Lookup identitas banyak siswa dalam satu/beberapa permintaan (issue #298).
     *
     * <p>ID dipecah per {@value #BATAS_BATCH} agar URL tetap wajar. Kegagalan
     * pada satu potongan tidak membatalkan potongan lain; siswa yang tidak
     * ditemukan tidak muncul sebagai kunci (pemanggil menganggap "tidak diketahui").
     */
    @Override
    public Map<Long, InfoSiswa> cariBanyak(Long sekolahId, Collection<Long> siswaIds) {
        if (siswaIds == null || siswaIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = siswaIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, InfoSiswa> hasil = new LinkedHashMap<>();
        for (int mulai = 0; mulai < ids.size(); mulai += BATAS_BATCH) {
            List<Long> potong = ids.subList(mulai, Math.min(mulai + BATAS_BATCH, ids.size()));
            String gabung = potong.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            try {
                String json = coba("identitas siswa batch n=" + potong.size(), () -> {
                    var spec = klien().get().uri(b -> b.path(pathBanyak())
                            .queryParam("ids", gabung)
                            .build());
                    return beriHeader(spec, null).retrieve().body(String.class);
                });
                for (JsonNode item : daftarSiswa(json)) {
                    InfoSiswa siswa = keInfo(sekolahId, item);
                    if (siswa != null && siswa.getSiswaId() != null) {
                        hasil.put(siswa.getSiswaId(), siswa);
                    }
                }
            } catch (RestClientResponseException e) {
                log.warn("[CariSiswa] admin-be menjawab HTTP {} untuk batch {} siswa → potongan dilewati.",
                        e.getStatusCode().value(), potong.size());
            } catch (RuntimeException e) {
                log.warn("[CariSiswa] Gagal mengambil batch {} siswa (dilewati): {}",
                        potong.size(), e.getMessage());
            }
        }
        log.debug("[CariSiswa] batch {} id → {} identitas.", ids.size(), hasil.size());
        return hasil;
    }

    /** Path batch: properti {@code path-banyak} bila diisi, selain itu default. */
    private String pathBanyak() {
        String p = konfigurasi().getPathBanyak();
        return (p == null || p.isBlank()) ? PATH_BANYAK_DEFAULT : p;
    }

    /** Path lookup per ID: properti {@code path-by-id} bila diisi, selain itu default. */
    private String pathById(Long siswaId) {
        String p = konfigurasi().getPathById();
        String pola = (p == null || p.isBlank()) ? PATH_BY_ID_DEFAULT : p;
        return pola.replace("{id}", String.valueOf(siswaId));
    }

    /**
     * Ambil array siswa dari respons admin-be. Menerima tiga bentuk:
     * amplop {@code {code,message,data:[...]}}, array datar, dan halaman
     * Spring {@code {content:[...]}} / {@code {items:[...]}}.
     */
    private JsonNode daftarSiswa(String json) {
        if (json == null || json.isBlank()) {
            return JSON.createArrayNode();
        }
        JsonNode root = JSON.readTree(json);
        JsonNode d = data(root);
        if (d == null || d.isNull()) {
            return JSON.createArrayNode();
        }
        if (d.isArray()) {
            return d;
        }
        for (String kunci : List.of("content", "items", "results")) {
            JsonNode isi = d.get(kunci);
            if (isi != null && isi.isArray()) {
                return isi;
            }
        }
        return JSON.createArrayNode();
    }

    /** Petakan satu baris JSON menjadi {@link InfoSiswa}; {@code null} bila id tak ada. */
    private InfoSiswa keInfo(Long sekolahId, JsonNode item) {
        if (item == null || !item.isObject() || item.get("id") == null || item.get("id").isNull()) {
            return null;
        }
        Long siswaId = item.get("id").asLong();
        String nama = teks(item, "namaLengkap");
        if (nama == null) {
            nama = teks(item, "nama");
        }
        String kelas = kelas(item);
        Boolean tidakAktif = tidakAktif(item);
        return InfoSiswa.builder()
                .siswaId(siswaId)
                .nis(teks(item, "nis"))
                .nama(nama)
                .kelas(kelas)
                .tidakAktif(tidakAktif)
                // Saldo selalu dari ledger kantin-be, bukan dari admin-be.
                .saldo(ledgerSaldo.saldo(sekolahId, SubjekTipe.SISWA, siswaId))
                .build();
    }

    /** Kelas bisa berupa string langsung atau objek {@code {namaKelas}}. */
    private static String kelas(JsonNode item) {
        JsonNode k = item.get("kelas");
        if (k == null || k.isNull()) {
            return null;
        }
        if (k.isObject()) {
            return teks(k, "namaKelas");
        }
        String teks = k.asText();
        return teks.isBlank() ? null : teks;
    }

    /** Bendera nonaktif: {@code tidakAktif} / {@code nonaktif}. */
    private static Boolean tidakAktif(JsonNode item) {
        for (String field : List.of("tidakAktif", "nonaktif")) {
            JsonNode v = item.get(field);
            if (v != null && !v.isNull()) {
                return v.asBoolean();
            }
        }
        return Boolean.FALSE;
    }

    private static String teks(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }
}
