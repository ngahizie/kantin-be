package com.asqi.scholia_kantin_be.config;

import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji perilaku default fail-closed untuk endpoint operasional (SEC-05).
 *
 * <p>Konteks dibiarkan memakai nilai default dari {@code application.properties}
 * (tanpa override properti), lalu memastikan metrik Prometheus dan dokumen
 * OpenAPI menolak request tanpa token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class SecurityDefaultFailClosedIT {

    @LocalServerPort
    private int port;

    private int statusGet(String path) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    @DisplayName("default: /actuator/prometheus tanpa token ditolak 401")
    void prometheusTertutupSecaraDefault() throws Exception {
        assertThat(statusGet("/actuator/prometheus")).isEqualTo(401);
    }

    @Test
    @DisplayName("default: /v3/api-docs tanpa token ditolak 401")
    void apiDocsTertutupSecaraDefault() throws Exception {
        assertThat(statusGet("/v3/api-docs")).isEqualTo(401);
    }
}
