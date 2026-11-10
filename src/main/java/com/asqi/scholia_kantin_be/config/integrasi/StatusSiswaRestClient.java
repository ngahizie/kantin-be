package com.asqi.scholia_kantin_be.config.integrasi;

import com.asqi.scholia_kantin_be.service.integrasi.StatusSiswaPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter REST nyata {@link StatusSiswaPort} → admin-be (INTEGRATIONS.md §4.4,
 * issue <b>#160</b>).
 *
 * <p><b>Dua operasi:</b>
 * <ul>
 *   <li>{@code tidakAktif(sekolahId, siswaId)} — baca status keaktifan siswa.
 *       Membaca {@code GET /api/siswa/get-by-id/{id}} lalu memeriksa
 *       {@code isActive}/{@code isLulus}/{@code statusSiswa}. Gagal → {@code null}
 *       (&ldquo;tidak diketahui&rdquo;, fail-safe) agar daftar kandidat refund
 *       tidak salah terfilter.</li>
 *   <li>{@code blokirKartu(...)} — {@code POST} ke admin-be, <b>best-effort</b>:
 *       kegagalan hanya dicatat (tidak melempar) karena perpindahan uang di ledger
 *       sudah sah &amp; idempoten (PRD §9.3).</li>
 * </ul>
 *
 * <p><b>Status blokir TIDAK di-cache (PRD §11.11)</b> — adapter tidak menyimpan
 * state; tiap panggilan memeriksa server.
 *
 * <p><b>Kapan hidup?</b> Hanya bila
 * {@code kantin.integrasi.status-siswa.enabled=true} (default <b>false</b>).
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "kantin.integrasi.status-siswa", name = "enabled", havingValue = "true")
@Slf4j
public class StatusSiswaRestClient extends IntegrasiRestSupport implements StatusSiswaPort {

    /** Path default baca detail siswa (Q7). */
    public static final String PATH_DEFAULT = "/api/siswa/get-by-id/{id}";

    /** Path default status batch (issue #298): {@code /api/siswa/status?ids=1,2,3}. */
    public static final String PATH_BANYAK_DEFAULT = "/api/siswa/status";

    /** Batas ID per permintaan batch. */
    private static final int BATAS_BATCH = 100;

    private final IntegrasiRestProperties.StatusSiswa statusSiswaCfg;

    public StatusSiswaRestClient(IntegrasiRestProperties properties) {
        super("StatusSiswa", properties.getStatusSiswa());
        this.statusSiswaCfg = properties.getStatusSiswa();
    }

    @Override
    public Boolean tidakAktif(Long sekolahId, Long siswaId) {
        if (siswaId == null) {
            return null;
        }
        try {
            String json = coba("status siswa=" + siswaId, () -> {
                var spec = klien().get().uri(path(PATH_DEFAULT), siswaId);
                return beriHeader(spec, null).retrieve().body(String.class);
            });
            return parseTidakAktif(json);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                // Siswa tak ditemukan = tidak diketahui (fail-safe), bukan "nonaktif".
                log.debug("[StatusSiswa] siswa={} tidak ditemukan (404).", siswaId);
                return null;
            }
            log.warn("[StatusSiswa] admin-be menolak status siswa={} (HTTP {}).", siswaId,
                    e.getStatusCode().value());
            return null;
        } catch (RuntimeException e) {
            log.warn("[StatusSiswa] Gagal status siswa={} (tidak diketahui): {}", siswaId,
                    e.getMessage());
            return null;
        }
    }

    private Boolean parseTidakAktif(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return tidakAktifDari(data(JSON.readTree(json)));
    }

    /** Terjemahkan satu node siswa admin-be menjadi status nonaktif. */
    private Boolean tidakAktifDari(JsonNode d) {
        if (d == null || d.isNull() || !d.isObject()) {
            return null;
        }
        // Prioritas: isActive=false ATAU isLulus=true ATAU statusSiswa menandakan keluar.
        JsonNode isActive = d.get("isActive");
        if (isActive != null && !isActive.isNull() && !isActive.asBoolean()) {
            return Boolean.TRUE;
        }
        JsonNode isLulus = d.get("isLulus");
        if (isLulus != null && !isLulus.isNull() && isLulus.asBoolean()) {
            return Boolean.TRUE;
        }
        JsonNode status = d.get("statusSiswa");
        if (status != null && !status.isNull()) {
            String s = status.asText("").toLowerCase();
            if (s.contains("lulus") || s.contains("pindah") || s.contains("keluar")
                    || s.contains("nonaktif") || s.contains("tidak aktif")) {
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        }
        // isActive=true eksplisit → aktif.
        if (isActive != null && !isActive.isNull()) {
            return Boolean.FALSE;
        }
        return null;
    }

    /**
     * Status keaktifan banyak siswa dalam satu/beberapa permintaan (issue #298).
     *
     * <p>ID dipecah per {@value #BATAS_BATCH}; kegagalan satu potongan tidak
     * membatalkan potongan lain. Siswa yang tidak muncul di respons tidak diberi
     * kunci (pemanggil menganggap "tidak diketahui"), bukan dianggap aktif.
     */
    @Override
    public Map<Long, Boolean> tidakAktifBanyak(Long sekolahId, Collection<Long> siswaIds) {
        if (siswaIds == null || siswaIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = siswaIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, Boolean> hasil = new LinkedHashMap<>();
        for (int mulai = 0; mulai < ids.size(); mulai += BATAS_BATCH) {
            List<Long> potong = ids.subList(mulai, Math.min(mulai + BATAS_BATCH, ids.size()));
            String gabung = potong.stream().map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(","));
            try {
                String json = coba("status siswa batch n=" + potong.size(), () -> {
                    var spec = klien().get().uri(b -> b.path(pathBanyakStatus())
                            .queryParam("ids", gabung)
                            .build());
                    return beriHeader(spec, null).retrieve().body(String.class);
                });
                JsonNode isi = data(JSON.readTree(json));
                if (isi != null && isi.isArray()) {
                    for (JsonNode item : isi) {
                        JsonNode id = item.get("id");
                        if (id != null && !id.isNull()) {
                            hasil.put(id.asLong(), tidakAktifDari(item));
                        }
                    }
                }
            } catch (RestClientResponseException e) {
                log.warn("[StatusSiswa] admin-be menjawab HTTP {} untuk batch {} siswa → potongan dilewati.",
                        e.getStatusCode().value(), potong.size());
            } catch (RuntimeException e) {
                log.warn("[StatusSiswa] Gagal batch status {} siswa (dilewati): {}",
                        potong.size(), e.getMessage());
            }
        }
        log.debug("[StatusSiswa] batch {} id → {} status.", ids.size(), hasil.size());
        return hasil;
    }

    /** Path batch status: properti {@code path-banyak} bila diisi, selain itu default. */
    private String pathBanyakStatus() {
        String p = statusSiswaCfg.getPathBanyak();
        return (p == null || p.isBlank()) ? PATH_BANYAK_DEFAULT : p;
    }

    @Override
    public void blokirKartu(Long sekolahId, Long siswaId, String alasan) {
        if (siswaId == null) {
            return;
        }
        try {
            Map<String, Object> body = badan();
            isi(body, "sekolahId", sekolahId);
            isi(body, "siswaId", siswaId);
            isi(body, "alasan", alasan);
            byte[] raw = bytesJson(body);
            coba("blokir kartu siswa=" + siswaId, () -> {
                var spec = klien().post()
                        .uri(statusSiswaCfg.getBlokirPath())
                        .contentType(MediaType.APPLICATION_JSON);
                return beriHeader(spec, raw).body(body).retrieve().body(String.class);
            });
            log.info("[StatusSiswa] Kartu siswa={} diblokir (alasan={}).", siswaId, alasan);
        } catch (RestClientResponseException e) {
            log.warn("[StatusSiswa] Blokir kartu siswa={} ditolak (HTTP {}).", siswaId,
                    e.getStatusCode().value());
        } catch (RuntimeException e) {
            // Best-effort: perpindahan uang di ledger sudah sah & idempoten (PRD §9.3).
            log.warn("[StatusSiswa] Blokir kartu siswa={} gagal (best-effort, tidak membatalkan): {}",
                    siswaId, e.getMessage());
        }
    }
}
