package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Batas nilai {@link PengaturanKantinRequest} (issue #415, OWASP A06:2025 /
 * CWE-1284).
 *
 * <p>Menguji dua sisi setiap angka: nilai di luar rentang ditolak, nilai tepat
 * di batas diterima. Field {@code null} tetap sah karena request ini bersifat
 * parsial (null = pertahankan nilai lama).
 */
@DisplayName("PengaturanKantinRequest — batas atas & bawah (issue #415)")
class PengaturanKantinRequestTest {

    private Validator validator;

    @BeforeEach
    void siapkan() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private Set<ConstraintViolation<PengaturanKantinRequest>> periksa(
            PengaturanKantinRequest request) {
        return validator.validate(request);
    }

    private static PengaturanKantinRequest durasi(Integer detik) {
        PengaturanKantinRequest r = new PengaturanKantinRequest();
        r.setDurasiFotoDetik(detik);
        return r;
    }

    private static PengaturanKantinRequest topup(Long min, Long maks) {
        PengaturanKantinRequest r = new PengaturanKantinRequest();
        r.setMinTopup(min);
        r.setMaksTopup(maks);
        return r;
    }

    private static PengaturanKantinRequest batasSaldo(Long nilai) {
        PengaturanKantinRequest r = new PengaturanKantinRequest();
        r.setBatasSaldoSiswa(nilai);
        return r;
    }

    private static PengaturanKantinRequest ambang(Integer hari) {
        PengaturanKantinRequest r = new PengaturanKantinRequest();
        r.setAmbangHari(hari);
        return r;
    }

    // ── durasiFotoDetik: 1..60 ──────────────────────────────────────────

    @Test
    @DisplayName("durasi foto 0 ditolak — 0 mematikan verifikasi visual di kasir")
    void durasiFotoNolDitolak() {
        assertThat(periksa(durasi(0)))
                .anySatisfy(v -> {
                    assertThat(v.getPropertyPath().toString()).isEqualTo("durasiFotoDetik");
                    assertThat(v.getMessage()).contains("minimal 1 detik");
                });
    }

    @Test
    @DisplayName("durasi foto 61 ditolak — timer foto tidak boleh membekukan POS")
    void durasiFotoTerlaluBesarDitolak() {
        assertThat(periksa(durasi(61)))
                .anySatisfy(v -> assertThat(v.getMessage()).contains("maksimal 60 detik"));
    }

    @Test
    @DisplayName("durasi foto 1 dan 60 diterima, null juga sah")
    void durasiFotoBatasDiterima() {
        assertThat(periksa(durasi(1))).isEmpty();
        assertThat(periksa(durasi(60))).isEmpty();
        assertThat(periksa(durasi(null))).isEmpty();
    }

    // ── minTopup / maksTopup: 1.000..100.000.000 ────────────────────────

    @Test
    @DisplayName("min/maks top-up di bawah Rp1.000 ditolak")
    void topupDiBawahMinimumDitolak() {
        assertThat(periksa(topup(999L, null)))
                .anySatisfy(v -> assertThat(v.getMessage()).contains("Minimum top-up minimal"));
        assertThat(periksa(topup(null, 999L)))
                .anySatisfy(v -> assertThat(v.getMessage()).contains("Maksimum top-up minimal"));
    }

    @Test
    @DisplayName("min/maks top-up di atas Rp100.000.000 ditolak")
    void topupDiAtasMaksimumDitolak() {
        assertThat(periksa(topup(100_000_001L, null)))
                .anySatisfy(v -> assertThat(v.getMessage()).contains("maksimal Rp100.000.000"));
        assertThat(periksa(topup(null, 100_000_001L)))
                .anySatisfy(v -> assertThat(v.getMessage()).contains("maksimal Rp100.000.000"));
    }

    @Test
    @DisplayName("min/maks top-up tepat di batas diterima")
    void topupBatasDiterima() {
        assertThat(periksa(topup(1_000L, 100_000_000L))).isEmpty();
        assertThat(periksa(topup(null, null))).isEmpty();
    }

    // ── batasSaldoSiswa: 10.000..500.000.000 ────────────────────────────

    @Test
    @DisplayName("batas saldo siswa 9.999 ditolak, 10.000 diterima")
    void batasSaldoBawah() {
        assertThat(periksa(batasSaldo(9_999L)))
                .anySatisfy(v -> assertThat(v.getMessage()).contains("minimal Rp10.000"));
        assertThat(periksa(batasSaldo(10_000L))).isEmpty();
    }

    @Test
    @DisplayName("batas saldo siswa 500.000.001 ditolak, 500.000.000 diterima")
    void batasSaldoAtas() {
        assertThat(periksa(batasSaldo(500_000_001L)))
                .anySatisfy(v -> assertThat(v.getMessage()).contains("maksimal Rp500.000.000"));
        assertThat(periksa(batasSaldo(500_000_000L))).isEmpty();
    }

    // ── ambangHari: 0..3.650 ────────────────────────────────────────────

    @Test
    @DisplayName("ambang hari -1 dan 3.651 ditolak, 0 dan 3.650 diterima")
    void ambangHariBatas() {
        assertThat(periksa(ambang(-1)))
                .anySatisfy(v -> assertThat(v.getMessage()).contains("tidak boleh negatif"));
        assertThat(periksa(ambang(3_651)))
                .anySatisfy(v -> assertThat(v.getMessage()).contains("maksimal 3650 hari"));
        assertThat(periksa(ambang(0))).isEmpty();
        assertThat(periksa(ambang(3_650))).isEmpty();
    }

    @Test
    @DisplayName("request kosong sah — semua field opsional (parsial)")
    void requestKosongSah() {
        assertThat(periksa(new PengaturanKantinRequest())).isEmpty();
    }
}
