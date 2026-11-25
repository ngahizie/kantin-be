package com.asqi.scholia_kantin_be.dto.saldo;

import com.asqi.scholia_kantin_be.enums.ArahMutasi;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji batas panjang {@code referensiId} pada DTO saldo (issue #382).
 *
 * <p>Setiap DTO menambahkan awalan tetap saat menyusun
 * {@code mutasi_saldo.idempotency_key} (64 karakter), sehingga batas per DTO
 * berbeda: koreksi 56, top-up 52, refund 57, pindah saldo 49. Sebelum
 * perbaikan, keempatnya memakai 60 sehingga referensi 57–60 karakter (atau
 * lebih pendek untuk awalan panjang) menghasilkan kunci 65–75 karakter dan
 * INSERT gagal sebagai HTTP 500.
 */
@DisplayName("DTO saldo — batas referensiId vs idempotency_key 64 karakter (#382)")
class BatasReferensiIdempotencyTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static String isi(int panjang) {
        return "R".repeat(panjang);
    }

    private <T> Set<ConstraintViolation<T>> periksa(T dto) {
        return validator.validate(dto);
    }

    // ── Koreksi saldo: 64 − 8 ("KOREKSI-") = 56 ────────────────────────────

    private KoreksiSaldoRequest koreksi(String referensiId) {
        KoreksiSaldoRequest r = new KoreksiSaldoRequest();
        r.setSiswaId(7L);
        r.setArah(ArahMutasi.KREDIT);
        r.setNominal(5_000L);
        r.setAlasan("Koreksi uji");
        r.setReferensiId(referensiId);
        return r;
    }

    @Test
    @DisplayName("koreksi: 56 karakter lolos, 57 ditolak")
    void koreksiBatas56() {
        assertThat(periksa(koreksi(isi(56)))).isEmpty();
        assertThat(periksa(koreksi(isi(57))))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("referensiId");
    }

    // ── Top-up tunai: 64 − 12 ("TOPUP-TUNAI-") = 52 ────────────────────────

    private TopUpRequest topUp(String referensiId) {
        TopUpRequest r = new TopUpRequest();
        r.setSiswaId(7L);
        r.setNominal(10_000L);
        r.setReferensiId(referensiId);
        return r;
    }

    @Test
    @DisplayName("top-up: 52 karakter lolos, 53 ditolak")
    void topUpBatas52() {
        assertThat(periksa(topUp(isi(52)))).isEmpty();
        assertThat(periksa(topUp(isi(53))))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("referensiId");
    }

    // ── Refund: 64 − 7 ("REFUND-") = 57 ────────────────────────────────────

    private RefundSaldoRequest refund(String referensiId) {
        RefundSaldoRequest r = new RefundSaldoRequest();
        r.setSiswaId(7L);
        r.setReferensiId(referensiId);
        return r;
    }

    @Test
    @DisplayName("refund: 57 karakter lolos, 58 ditolak")
    void refundBatas57() {
        assertThat(periksa(refund(isi(57)))).isEmpty();
        assertThat(periksa(refund(isi(58))))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("referensiId");
    }

    // ── Pindah saldo: 64 − 9 ("TRANSFER-") − 6 ("-MASUK") = 49 ─────────────

    private PindahSaldoRequest pindah(String referensiId) {
        PindahSaldoRequest r = new PindahSaldoRequest();
        r.setSiswaSumberId(7L);
        r.setSiswaTujuanId(8L);
        r.setReferensiId(referensiId);
        return r;
    }

    @Test
    @DisplayName("pindah saldo: 49 karakter lolos, 50 ditolak")
    void pindahBatas49() {
        assertThat(periksa(pindah(isi(49)))).isEmpty();
        assertThat(periksa(pindah(isi(50))))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("referensiId");
    }

    @ParameterizedTest(name = "referensiId kosong ditolak (NotBlank)")
    @ValueSource(strings = {"", "   "})
    void referensiKosongDitolak(String kosong) {
        assertThat(periksa(koreksi(kosong))).isNotEmpty();
        assertThat(periksa(refund(kosong))).isNotEmpty();
        assertThat(periksa(pindah(kosong))).isNotEmpty();
        assertThat(periksa(topUp(kosong))).isNotEmpty();
    }
}
