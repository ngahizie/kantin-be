package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.component.exception.InvariantViolationException;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Penjagaan kontrak {@link LedgerSaldoService} (issue #360).
 *
 * <p>Perintah mutasi yang cacat adalah bug pemanggil, bukan kesalahan pengguna,
 * sehingga dijawab {@link InvariantViolationException} (→ HTTP 500), bukan
 * {@code IllegalArgumentException} generik. Service dibuat tanpa dependency
 * karena semua kasus di sini berhenti di {@code validasi()} sebelum menyentuh
 * repository.
 */
class LedgerSaldoServiceValidasiTest {

    private final LedgerSaldoService ledger = new LedgerSaldoService(null, null, null, null, null);

    @Test
    @DisplayName("perintah tanpa sekolahId ditolak")
    void sekolahIdWajib() {
        assertThatThrownBy(() -> ledger.kredit(perintah().sekolahId(null).build()))
                .isInstanceOf(InvariantViolationException.class)
                .hasMessageContaining("sekolahId");
    }

    @Test
    @DisplayName("perintah tanpa subjek ditolak")
    void subjekWajib() {
        assertThatThrownBy(() -> ledger.kredit(perintah().siswaId(null).build()))
                .isInstanceOf(InvariantViolationException.class)
                .hasMessageContaining("subjek");
    }

    @Test
    @DisplayName("perintah tanpa jenis mutasi ditolak")
    void jenisWajib() {
        assertThatThrownBy(() -> ledger.kredit(perintah().jenis(null).build()))
                .isInstanceOf(InvariantViolationException.class)
                .hasMessageContaining("jenis mutasi");
    }

    @Test
    @DisplayName("nominal ≤ 0 ditolak pada mutasi biasa")
    void nominalHarusPositif() {
        assertThatThrownBy(() -> ledger.debit(perintah().nominal(0L).build()))
                .isInstanceOf(InvariantViolationException.class)
                .hasMessageContaining("nominal");
    }

    private PerintahMutasiSaldo.PerintahMutasiSaldoBuilder perintah() {
        return PerintahMutasiSaldo.builder()
                .sekolahId(1L)
                .siswaId(2L)
                .jenis(JenisMutasiSaldo.TOPUP_TUNAI)
                .nominal(1_000L);
    }
}
