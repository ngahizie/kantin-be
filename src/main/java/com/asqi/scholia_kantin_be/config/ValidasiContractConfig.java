package com.asqi.scholia_kantin_be.config;

import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Menyambungkan properti {@code kantin.validation.http-400} ke
 * {@link CommonResponse}.
 *
 * <p>Lihat dokumentasi di {@code CommonResponse} &amp; OPEN-QUESTIONS tentang
 * pilihan 200 vs 400 untuk respons validasi. Dipisah ke config agar controller
 * tetap murni (tanpa urusan global) dan nilai bisa di-override per lingkungan.
 */
@Configuration
@Slf4j
public class ValidasiContractConfig {

    /**
     * true (default) = balas HTTP 400; false = HTTP 200 + code=100 (kontrak lama).
     *
     * <p>Saklar mundur untuk environment yang belum siap: set
     * {@code kantin.validation.http-400=false}. Default 400 sejak issue #251.
     */
    @Value("${kantin.validation.http-400:true}")
    private boolean validasiHttp400;

    @PostConstruct
    void terapkan() {
        CommonResponse.setValidasiHttp400(validasiHttp400);
        if (validasiHttp400) {
            log.info("Validasi dijawab HTTP 400 (mode REST modern).");
        } else {
            log.warn("Validasi dijawab HTTP 200 + code=100 (kontrak lama) — dinyalakan lewat kantin.validation.http-400=false.");
        }
    }
}
