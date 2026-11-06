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
import java.util.List;

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
