package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.enums.ArahMutasi;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.model.MutasiSaldo;
import com.asqi.scholia_kantin_be.repository.MutasiSaldoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Penjaga kontrak replay idempotensi saldo (issue #392).
 *
 * <p>Kunci idempotensi unik per tenant, bukan per subjek. Service dibuat tanpa
 * dependency lain karena semua kasus berhenti di lookup replay sebelum
 * menyentuh cache/ledger.
 */
class LedgerSaldoServiceReplayTest {

    private static final long SEKOLAH = 1L;
    private static final String KUNCI = "REFUND-BA-1";

    private final MutasiSaldoRepository repo = mock(MutasiSaldoRepository.class);
    private final LedgerSaldoService ledger = new LedgerSaldoService(repo, null, null, null, null);

    @Test
    @DisplayName("replay untuk siswa lain ditolak 409 — tidak mengembalikan data siswa lain")
    void subjekBerbedaDitolak() {
        beriMutasiLama(2L, JenisMutasiSaldo.REFUND, ArahMutasi.DEBIT);

        assertThatThrownBy(() -> ledger.debitSisaPenuh(perintah(3L, JenisMutasiSaldo.REFUND)
                .idempotencyKey(KUNCI).build()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("siswa atau jenis mutasi lain");
    }

    @Test
    @DisplayName("replay dengan jenis mutasi berbeda ditolak 409")
    void jenisBerbedaDitolak() {
        beriMutasiLama(2L, JenisMutasiSaldo.VOID_PENJUALAN, ArahMutasi.KREDIT);

        assertThatThrownBy(() -> ledger.kredit(perintah(2L, JenisMutasiSaldo.REFUND)
                .idempotencyKey(KUNCI).nominal(1_000L).build()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("siswa atau jenis mutasi lain");
    }

    @Test
    @DisplayName("replay dengan arah berbeda ditolak 409")
    void arahBerbedaDitolak() {
        beriMutasiLama(2L, JenisMutasiSaldo.KOREKSI, ArahMutasi.KREDIT);

        assertThatThrownBy(() -> ledger.debitSisaPenuh(perintah(2L, JenisMutasiSaldo.KOREKSI)
                .idempotencyKey(KUNCI).build()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("siswa atau jenis mutasi lain");
    }

    @Test
    @DisplayName("replay dengan subjek, jenis, dan arah sama tetap idempoten")
    void konteksSamaTetapReplay() {
        beriMutasiLama(2L, JenisMutasiSaldo.REFUND, ArahMutasi.DEBIT);

        HasilMutasiSaldo hasil = ledger.debitSisaPenuh(perintah(2L, JenisMutasiSaldo.REFUND)
                .idempotencyKey(KUNCI).build());

        assertThat(hasil.isIdempotentReplay()).isTrue();
        assertThat(hasil.getMutasi().getSiswaId()).isEqualTo(2L);
        assertThat(hasil.getSaldoSetelah()).isEqualTo(50_000L);
    }

    /** Mutasi lama yang dikembalikan repository sebagai hasil replay. */
    private void beriMutasiLama(long siswaId, JenisMutasiSaldo jenis, ArahMutasi arah) {
        when(repo.findBySekolahIdAndIdempotencyKey(SEKOLAH, KUNCI))
                .thenReturn(Optional.of(MutasiSaldo.builder()
                        .id(11L).sekolahId(SEKOLAH).siswaId(siswaId)
                        .arah(arah).jenis(jenis)
                        .nominal(50_000L).saldoSetelah(50_000L)
                        .idempotencyKey(KUNCI)
                        .build()));
    }

    /** Perintah dengan konteks dasar; nominal diisi per kasus. */
    private PerintahMutasiSaldo.PerintahMutasiSaldoBuilder perintah(long siswaId, JenisMutasiSaldo jenis) {
        return PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).siswaId(siswaId).jenis(jenis).nominal(0L);
    }
}
