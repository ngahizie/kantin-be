package com.asqi.scholia_kantin_be.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Penyamaran UID kartu & pseudonim ID siswa (issue #395, #437).
 *
 * <p>Nilai mentah tidak boleh terbaca dari hasil penyamaran, dan pseudonim harus
 * stabil supaya baris log untuk siswa yang sama tetap bisa dikorelasikan.
 */
@DisplayName("MaskingKartu — UID & pseudonim ID siswa")
class MaskingKartuTest {

    // ── uid() ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("UID panjang → hanya 4 karakter terakhir yang terbaca")
    void uidPanjang() {
        assertThat(MaskingKartu.uid("04A1B2C3D4")).isEqualTo("****C3D4");
    }

    @Test
    @DisplayName("UID ≤ 4 karakter disamarkan seluruhnya")
    void uidPendek() {
        assertThat(MaskingKartu.uid("A1B2")).isEqualTo("****");
        assertThat(MaskingKartu.uid("A")).isEqualTo("****");
    }

    @Test
    @DisplayName("UID kosong/null → null, bukan string kosong")
    void uidKosong() {
        assertThat(MaskingKartu.uid(null)).isNull();
        assertThat(MaskingKartu.uid("   ")).isNull();
    }

    // ── idSiswa() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("ID siswa → pseudonim stabil, tidak memuat angka ID-nya")
    void idSiswaPseudonimStabil() {
        String pseudonim = MaskingKartu.idSiswa(1001L);

        assertThat(pseudonim).startsWith("S#").hasSize(10);   // S# + 8 hex (32 bit)
        assertThat(pseudonim).isEqualTo(MaskingKartu.idSiswa(1001L));
        assertThat(pseudonim).doesNotContain("1001");
    }

    @Test
    @DisplayName("ID berbeda → pseudonim berbeda")
    void idSiswaBerbeda() {
        assertThat(MaskingKartu.idSiswa(1001L)).isNotEqualTo(MaskingKartu.idSiswa(1002L));
    }

    @Test
    @DisplayName("ID null → null")
    void idSiswaNull() {
        assertThat(MaskingKartu.idSiswa(null)).isNull();
    }
}
