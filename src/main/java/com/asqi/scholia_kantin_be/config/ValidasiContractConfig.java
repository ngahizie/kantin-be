package com.asqi.scholia_kantin_be.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Pemegang properti {@code kantin.validation.http-400} — dibaca oleh
 * {@code GlobalExceptionHandler} untuk menentukan status respons validasi.
 *
 * <p>Nilai diikat <b>sekali</b> saat bean dibuat (immutable) dan diteruskan
 * sebagai parameter ke {@code CommonResponse.validationError(...)}, bukan
 * disimpan sebagai static mutable di {@code CommonResponse} seperti sebelumnya
 * (issue #361). Dipisah ke config agar controller tetap murni (tanpa urusan
 * global) dan nilai bisa di-override per lingkungan.
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
    private final boolean validasiHttp400;

    public ValidasiContractConfig(@Value("${kantin.validation.http-400:true}") boolean validasiHttp400) {
        this.validasiHttp400 = validasiHttp400;
    }

    /** true → respons validasi dijawab HTTP 400; false → HTTP 200 (kontrak lama). */
    public boolean validasiHttp400() {
        return validasiHttp400;
    }

    @PostConstruct
    void catatMode() {
        if (validasiHttp400) {
            log.info("Validasi dijawab HTTP 400 (mode REST modern).");
        } else {
            log.warn("Validasi dijawab HTTP 200 + code=100 (kontrak lama) — dinyalakan lewat kantin.validation.http-400=false.");
        }
    }
}
