package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.TitikKasirResponse;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.konfigurasi.AktivasiModulService;
import com.asqi.scholia_kantin_be.service.konfigurasi.PengaturanKantinService;
import com.asqi.scholia_kantin_be.service.konfigurasi.TitikKasirService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uji wiring {@code PATCH /api/pengaturan-kantin/titik-kasir/{id}/toggle} (issue #228):
 * body opsional, tenant &amp; aktor dari konteks, pesan mengikuti status hasil.
 */
@DisplayName("PengaturanKantinController — toggle titik kasir (#228)")
class PengaturanKantinControllerTest {

    private TitikKasirService service;
    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    private static class PrincipalResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(MethodParameter p, ModelAndViewContainer m,
                                      NativeWebRequest r, WebDataBinderFactory b) {
            return TenantContext.get();
        }
    }

    @BeforeEach
    void setUp() {
        service = mock(TitikKasirService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PengaturanKantinController(
                        mock(PengaturanKantinService.class), service, mock(AktivasiModulService.class)))
                .setCustomArgumentResolvers(new PrincipalResolver())
                .build();
        TenantContext.set(IdentitasKantin.builder().userId("42").sekolahId(7L).build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void bodyAktifDiteruskanKeService() throws Exception {
        when(service.toggle(eq(7L), eq(5L), eq(false), eq(42L)))
                .thenReturn(TitikKasirResponse.builder().id(5L).nama("Kasir 1").aktif(false).build());

        mockMvc.perform(patch("/api/pengaturan-kantin/titik-kasir/5/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aktif\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aktif").value(false))
                .andExpect(jsonPath("$.message").value("Titik kasir dinonaktifkan"));

        verify(service).toggle(eq(7L), eq(5L), eq(false), eq(42L));
    }

    @Test
    void bodyKosongMembalikStatus() throws Exception {
        when(service.toggle(eq(7L), eq(5L), isNull(), eq(42L)))
                .thenReturn(TitikKasirResponse.builder().id(5L).nama("Kasir 1").aktif(true).build());

        mockMvc.perform(patch("/api/pengaturan-kantin/titik-kasir/5/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aktif").value(true))
                .andExpect(jsonPath("$.message").value("Titik kasir diaktifkan"));

        verify(service).toggle(eq(7L), eq(5L), isNull(), eq(42L));
    }

    @Test
    void tanpaBodyJugaDiterima() throws Exception {
        when(service.toggle(eq(7L), eq(5L), isNull(), eq(42L)))
                .thenReturn(TitikKasirResponse.builder().id(5L).nama("Kasir 1").aktif(true).build());

        mockMvc.perform(patch("/api/pengaturan-kantin/titik-kasir/5/toggle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aktif").value(true));

        verify(service).toggle(eq(7L), eq(5L), isNull(), eq(42L));
    }
}
