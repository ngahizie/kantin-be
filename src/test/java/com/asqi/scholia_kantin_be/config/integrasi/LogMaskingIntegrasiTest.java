package com.asqi.scholia_kantin_be.config.integrasi;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.asqi.scholia_kantin_be.helper.MaskingKartu;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Anti-regresi kebocoran data pribadi di log adapter integrasi (issue #437,
 * OWASP A09:2025 / NIST CSF PR.DS-1).
 *
 * <p>UID kartu adalah <i>possession credential</i> sekaligus penanda identitas
 * siswa, jadi tidak boleh muncul utuh di log. Ada tiga jalur yang bocor dengan
 * cara berbeda, dan ketiganya diuji di sini:
 * <ol>
 *   <li>pesan {@code log.warn} di adapter (nilai identitas sebagai argumen),</li>
 *   <li>pesan retry {@code IntegrasiRestSupport.coba(...)} — label operasi ikut
 *       dicatat, jadi labelnya pun harus tersamar,</li>
 *   <li>argumen {@code e.getMessage()} — {@code ResourceAccessException} memuat
 *       URL lengkap dengan UID/ID di path atau query, sehingga masking label saja
 *       tidak cukup. Jalur ini dipicu dengan base-url {@code http://127.0.0.1:1}
 *       (port tertutup), bukan WireMock, karena kegagalan koneksinya harus nyata.</li>
 * </ol>
 */
@DisplayName("Log adapter integrasi — UID & ID siswa tersamar (#437)")
class LogMaskingIntegrasiTest {

    private static final String UID_MENTAH = "04A1B2C3D4";
    private static final String UID_TERSAMAR = MaskingKartu.TOPENG + "C3D4";
    /** ID panjang supaya pencarian "nilai mentah" tidak salah tangkap angka lain. */
    private static final long SISWA_ID = 987654321L;
    /** Port yang dijamin tidak ada pendengarnya → ResourceAccessException. */
    private static final String BASE_URL_MATI = "http://127.0.0.1:1";

    private WireMockServer wm;
    private Logger logger;
    private Level levelAwal;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void mulai() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();

        logger = (Logger) LoggerFactory.getLogger("com.asqi.scholia_kantin_be.config.integrasi");
        levelAwal = logger.getLevel();
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.TRACE);
    }

    @AfterEach
    void henti() {
        if (logger != null) {
            if (appender != null) {
                logger.detachAppender(appender);
                appender.stop();
            }
            // Kembalikan level semula supaya tidak mempengaruhi test lain.
            logger.setLevel(levelAwal);
        }
        if (wm != null) {
            wm.stop();
        }
    }

    private List<String> pesanLog() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private IntegrasiRestProperties props() {
        return new IntegrasiRestProperties();
    }

    private KartuLookupRestClient kartuLookup(int maxAttempts) {
        IntegrasiRestProperties p = props();
        var e = p.getKartuLookup();
        e.setEnabled(true);
        e.setBaseUrl(wm.baseUrl());
        e.setMaxAttempts(maxAttempts);
        e.setBackoffMs(5);
        return new KartuLookupRestClient(p);
    }

    private KartuLookupRestClient kartuLookupMati() {
        IntegrasiRestProperties p = props();
        var e = p.getKartuLookup();
        e.setEnabled(true);
        e.setBaseUrl(BASE_URL_MATI);
        e.setMaxAttempts(2);
        e.setBackoffMs(5);
        return new KartuLookupRestClient(p);
    }

    private UidSiswaRestClient uidSiswa(int maxAttempts) {
        IntegrasiRestProperties p = props();
        var e = p.getUidSiswa();
        e.setEnabled(true);
        e.setBaseUrl(wm.baseUrl());
        e.setMaxAttempts(maxAttempts);
        e.setBackoffMs(5);
        return new UidSiswaRestClient(p);
    }

    private CariSiswaRestClient cariSiswa(int maxAttempts) {
        IntegrasiRestProperties p = props();
        var e = p.getCariSiswa();
        e.setEnabled(true);
        e.setBaseUrl(wm.baseUrl());
        e.setMaxAttempts(maxAttempts);
        e.setBackoffMs(5);
        return new CariSiswaRestClient(p, mock(LedgerSaldoService.class));
    }

    private StatusSiswaRestClient statusSiswaMati() {
        IntegrasiRestProperties p = props();
        var e = p.getStatusSiswa();
        e.setEnabled(true);
        e.setBaseUrl(BASE_URL_MATI);
        e.setMaxAttempts(2);
        e.setBackoffMs(5);
        return new StatusSiswaRestClient(p);
    }

    // ── 5xx / 404 lewat WireMock ────────────────────────────────────────

    @Test
    @DisplayName("KartuLookup gagal 5xx: retry & WARN tidak memuat UID penuh")
    void kartuLookupGagalTidakMembocorkanUid() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/cek-kartu/" + UID_MENTAH))
                .willReturn(aResponse().withStatus(500)));

        kartuLookup(2).cariBerdasarkanUid(1L, UID_MENTAH);

        List<String> pesan = pesanLog();
        assertThat(pesan).isNotEmpty();
        assertThat(pesan).noneMatch(p -> p.contains(UID_MENTAH));
        assertThat(pesan).anyMatch(p -> p.contains(UID_TERSAMAR));
    }

    @Test
    @DisplayName("KartuLookup 404: log DEBUG memakai UID tersamar")
    void kartuLookupTidakDikenalTidakMembocorkanUid() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/cek-kartu/" + UID_MENTAH))
                .willReturn(aResponse().withStatus(404)));

        kartuLookup(1).cariBerdasarkanUid(1L, UID_MENTAH);

        List<String> pesan = pesanLog();
        assertThat(pesan).isNotEmpty();
        assertThat(pesan).noneMatch(p -> p.contains(UID_MENTAH));
        assertThat(pesan).anyMatch(p -> p.contains(UID_TERSAMAR));
    }

    @Test
    @DisplayName("UidSiswa gagal 5xx: WARN tidak memuat UID penuh")
    void uidSiswaGagalTidakMembocorkanUid() {
        wm.stubFor(get(urlPathEqualTo("/api/internal/siswa/cek-uid"))
                .willReturn(aResponse().withStatus(500)));

        uidSiswa(2).dipakaiSiswa(1L, UID_MENTAH);

        List<String> pesan = pesanLog();
        assertThat(pesan).isNotEmpty();
        assertThat(pesan).noneMatch(p -> p.contains(UID_MENTAH));
        assertThat(pesan).anyMatch(p -> p.contains(UID_TERSAMAR));
    }

    @Test
    @DisplayName("CariSiswa gagal 5xx: WARN tidak memuat ID siswa mentah")
    void cariSiswaGagalTidakMembocorkanIdSiswa() {
        wm.stubFor(get(urlPathMatching("/api/siswa/.*"))
                .willReturn(aResponse().withStatus(500)));

        cariSiswa(2).cariBerdasarkanId(1L, SISWA_ID);

        List<String> pesan = pesanLog();
        assertThat(pesan).isNotEmpty();
        assertThat(pesan).noneMatch(p -> p.contains(String.valueOf(SISWA_ID)));
        assertThat(pesan).anyMatch(p -> p.contains(MaskingKartu.idSiswa(SISWA_ID)));
    }

    @Test
    @DisplayName("CariSiswa ambil UID siswa gagal 5xx: ID siswa tetap tersamar")
    void cariUidSiswaGagalTidakMembocorkanIdSiswa() {
        wm.stubFor(get(urlPathMatching("/api/siswa/.*"))
                .willReturn(aResponse().withStatus(500)));

        cariSiswa(2).cariUidSiswa(1L, SISWA_ID);

        List<String> pesan = pesanLog();
        assertThat(pesan).isNotEmpty();
        assertThat(pesan).noneMatch(p -> p.contains(String.valueOf(SISWA_ID)));
        assertThat(pesan).anyMatch(p -> p.contains(MaskingKartu.idSiswa(SISWA_ID)));
    }

    // ── Koneksi gagal: pesan exception memuat URL ───────────────────────

    @Test
    @DisplayName("KartuLookup koneksi ditolak: UID di URL tidak bocor ke WARN")
    void kartuLookupKoneksiGagalTidakMembocorkanUid() {
        kartuLookupMati().cariBerdasarkanUid(1L, UID_MENTAH);

        List<String> pesan = pesanLog();
        assertThat(pesan).isNotEmpty();
        // Inilah jalur yang dulu bocor: ResourceAccessException memuat URL lengkap
        // termasuk UID di path, pada pesan retry maupun pesan adapter.
        assertThat(pesan).noneMatch(p -> p.contains(UID_MENTAH));
        assertThat(pesan).anyMatch(p -> p.contains("ResourceAccessException"));
        assertThat(pesan).anyMatch(p -> p.contains(UID_TERSAMAR));
    }

    @Test
    @DisplayName("StatusSiswa koneksi ditolak: ID siswa di URL tidak bocor ke WARN")
    void statusSiswaKoneksiGagalTidakMembocorkanIdSiswa() {
        statusSiswaMati().tidakAktif(1L, SISWA_ID);

        List<String> pesan = pesanLog();
        assertThat(pesan).isNotEmpty();
        assertThat(pesan).noneMatch(p -> p.contains(String.valueOf(SISWA_ID)));
        assertThat(pesan).anyMatch(p -> p.contains("ResourceAccessException"));
        assertThat(pesan).anyMatch(p -> p.contains(MaskingKartu.idSiswa(SISWA_ID)));
    }
}
