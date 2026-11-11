package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.PerluPeran;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.CariSiswaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uji wiring {@link SiswaController} untuk lookup UID kartu per siswa
 * (issue #301): tenant diambil dari konteks, UID tidak ditemukan tetap 200,
 * dan peran yang boleh mengakses hanya admin sekolah + TU.
 */
@DisplayName("SiswaController — UID kartu per siswa (#301)")
class SiswaControllerTest {

    private CariSiswaService cariSiswa;
    private MockMvc mockMvc;

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
        cariSiswa = mock(CariSiswaService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SiswaController(cariSiswa))
                .setCustomArgumentResolvers(new PrincipalResolver())
                .build();
        TenantContext.set(IdentitasKantin.builder().userId("42").sekolahId(7L).build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("#301: UID tersedia → dikembalikan untuk siswa tersebut")
    void uidTersedia() throws Exception {
        when(cariSiswa.uidKartu(eq(7L), eq(101L))).thenReturn(Optional.of("DA100001"));

        mockMvc.perform(get("/api/siswa/101/rfid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.siswaId").value(101))
                .andExpect(jsonPath("$.data.rfidUid").value("DA100001"))
                .andExpect(jsonPath("$.data.ditemukan").value(true));

        verify(cariSiswa).uidKartu(eq(7L), eq(101L));
    }

    @Test
    @DisplayName("#301: UID tidak ditemukan → tetap 200, rfidUid null (tidak dikarang)")
    void uidTidakDitemukan() throws Exception {
        when(cariSiswa.uidKartu(eq(7L), eq(999L))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/siswa/999/rfid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.siswaId").value(999))
                .andExpect(jsonPath("$.data.rfidUid").doesNotExist())
                .andExpect(jsonPath("$.data.ditemukan").value(false));
    }

    @Test
    @DisplayName("#301: hanya admin sekolah & TU yang boleh membaca UID")
    void peranTerbatas() throws Exception {
        PerluPeran perlu = SiswaController.class
                .getMethod("uidSiswa", Long.class)
                .getAnnotation(PerluPeran.class);

        assertThat(perlu).as("endpoint UID wajib punya @PerluPeran").isNotNull();
        assertThat(perlu.value())
                .containsExactlyInAnyOrder(AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH);
    }
}
