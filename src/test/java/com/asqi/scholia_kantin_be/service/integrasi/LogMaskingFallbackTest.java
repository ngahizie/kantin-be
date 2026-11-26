package com.asqi.scholia_kantin_be.service.integrasi;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.asqi.scholia_kantin_be.helper.MaskingKartu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Anti-regresi kebocoran identitas pada log implementasi {@code *Fallback}
 * (issue #437, review #446 butir 2).
 *
 * <p>Adapter REST integrasi default <b>OFF</b> ({@code kantin.integrasi.*.enabled=false}),
 * jadi justru kelas fallback inilah yang aktif di produksi bila flag belum
 * dinyalakan — dan sebelumnya mencetak UID penuh di level WARN pada setiap tap.
 */
@DisplayName("Log implementasi fallback — UID & ID siswa tersamar (#437)")
class LogMaskingFallbackTest {

    private static final String UID_MENTAH = "04A1B2C3D4";
    private static final long SISWA_ID = 987654321L;

    private Logger logger;
    private Level levelAwal;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void mulai() {
        logger = (Logger) LoggerFactory.getLogger("com.asqi.scholia_kantin_be.service.integrasi");
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
            logger.setLevel(levelAwal);
        }
    }

    private List<String> pesanLog() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    @DisplayName("KartuLookupFallback tidak mencetak UID penuh di WARN")
    void kartuLookupFallbackTersamar() {
        new KartuLookupFallback().cariBerdasarkanUid(1L, UID_MENTAH);

        List<String> pesan = pesanLog();
        assertThat(pesan).isNotEmpty();
        assertThat(pesan).noneMatch(p -> p.contains(UID_MENTAH));
        assertThat(pesan).anyMatch(p -> p.contains(MaskingKartu.uid(UID_MENTAH)));
    }

    @Test
    @DisplayName("StatusSiswaFallback tidak mencetak ID siswa penuh di WARN")
    void statusSiswaFallbackTersamar() {
        StatusSiswaFallback fallback = new StatusSiswaFallback();
        fallback.tidakAktif(1L, SISWA_ID);
        fallback.blokirKartu(1L, SISWA_ID, "saldo mengendap");

        List<String> pesan = pesanLog();
        assertThat(pesan).hasSize(2);
        assertThat(pesan).noneMatch(p -> p.contains(String.valueOf(SISWA_ID)));
        assertThat(pesan).allMatch(p -> p.contains(MaskingKartu.idSiswa(SISWA_ID)));
    }

    @Test
    @DisplayName("UidSiswaFallback (DEBUG) tetap memakai UID tersamar")
    void uidSiswaFallbackTersamar() {
        new UidSiswaFallback().dipakaiSiswa(1L, UID_MENTAH);

        List<String> pesan = pesanLog();
        assertThat(pesan).isNotEmpty();
        assertThat(pesan).noneMatch(p -> p.contains(UID_MENTAH));
        assertThat(pesan).anyMatch(p -> p.contains(MaskingKartu.uid(UID_MENTAH)));
    }
}
