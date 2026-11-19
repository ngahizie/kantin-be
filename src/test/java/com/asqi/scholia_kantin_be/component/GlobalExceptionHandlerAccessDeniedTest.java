package com.asqi.scholia_kantin_be.component;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.asqi.scholia_kantin_be.config.ValidasiContractConfig;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji logging penolakan RBAC (SEC-06).
 *
 * <p>Memastikan 403 tetap dibalas dan tercatat level {@code WARN} dengan
 * identitas + path URI, tanpa query string.
 */
@DisplayName("GlobalExceptionHandler — log 403 (SEC-06)")
class GlobalExceptionHandlerAccessDeniedTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(new ValidasiContractConfig(true));

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        TenantContext.set(IdentitasKantin.builder()
                .userId("42")
                .sekolahId(7L)
                .build());
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        TenantContext.clear();
    }

    @Test
    @DisplayName("403 dicatat WARN dengan userId, sekolahId, dan path URI")
    void aksesDitolakDicatat() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/katalog/menu");
        request.setQueryString("token=rahasia");

        var response = handler.handleAccessDenied(
                new AccessDeniedException("peran tidak cukup"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(appender.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getFormattedMessage())
                            .contains("userId=42")
                            .contains("sekolahId=7")
                            .contains("/api/katalog/menu")
                            .doesNotContain("token=rahasia");
                });
    }
}
