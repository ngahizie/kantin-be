package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.enums.ArahMutasi;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.MutasiSaldo;
import com.asqi.scholia_kantin_be.model.Saldo;
import com.asqi.scholia_kantin_be.repository.MutasiSaldoRepository;
import com.asqi.scholia_kantin_be.repository.SaldoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Penjaga kontrak replay idempotensi saldo (issue #392, review #393).
 *
 * <p>Semua dependency di-mock, bukan diisi {@code null}: selain pra-cek replay,
 * ada jalur balapan ({@code DataIntegrityViolationException}) yang menyentuh
 * cache, generator id, jam, dan entity manager sebelum menyimpan baris.
 */
class LedgerSaldoServiceReplayTest {

    private static final long SEKOLAH = 1L;
    private static final String KUNCI = "REFUND-BA-1";

    private final MutasiSaldoRepository repo = mock(MutasiSaldoRepository.class);
    private final SaldoRepository cacheRepo = mock(SaldoRepository.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
    private final JamKantin jam = mock(JamKantin.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final LedgerSaldoService ledger =
            new LedgerSaldoService(repo, cacheRepo, idGenerator, jam, entityManager);

    @Test
    @DisplayName("replay untuk siswa lain ditolak 409 — tidak mengembalikan data siswa lain")
    void subjekBerbedaDitolak() {
        beriMutasiLama(2L, JenisMutasiSaldo.REFUND, ArahMutasi.DEBIT, 50_000L);

        assertThatThrownBy(() -> ledger.debitSisaPenuh(perintah(3L, JenisMutasiSaldo.REFUND)
                .idempotencyKey(KUNCI).build()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("siswa lain");
    }

    @Test
    @DisplayName("replay dengan jenis mutasi berbeda ditolak 409")
    void jenisBerbedaDitolak() {
        beriMutasiLama(2L, JenisMutasiSaldo.VOID_PENJUALAN, ArahMutasi.KREDIT, 50_000L);

        assertThatThrownBy(() -> ledger.kredit(perintah(2L, JenisMutasiSaldo.REFUND)
                .idempotencyKey(KUNCI).nominal(50_000L).build()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("jenis mutasi lain");
    }

    @Test
    @DisplayName("replay dengan arah berbeda ditolak 409")
    void arahBerbedaDitolak() {
        beriMutasiLama(2L, JenisMutasiSaldo.KOREKSI, ArahMutasi.KREDIT, 50_000L);

        assertThatThrownBy(() -> ledger.debitSisaPenuh(perintah(2L, JenisMutasiSaldo.KOREKSI)
                .idempotencyKey(KUNCI).build()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("arah mutasi lain");
    }

    @Test
    @DisplayName("replay dengan nominal beda ditolak 409 (mode nominal tetap)")
    void nominalBerbedaDitolak() {
        beriMutasiLama(2L, JenisMutasiSaldo.TOPUP_TUNAI, ArahMutasi.KREDIT, 50_000L);

        assertThatThrownBy(() -> ledger.kredit(perintah(2L, JenisMutasiSaldo.TOPUP_TUNAI)
                .idempotencyKey(KUNCI).nominal(60_000L).build()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("nominal lain");
    }

    @Test
    @DisplayName("replay dengan subjek, jenis, arah, dan nominal sama tetap idempoten")
    void konteksSamaTetapReplay() {
        beriMutasiLama(2L, JenisMutasiSaldo.REFUND, ArahMutasi.DEBIT, 50_000L);

        HasilMutasiSaldo hasil = ledger.debitSisaPenuh(perintah(2L, JenisMutasiSaldo.REFUND)
                .idempotencyKey(KUNCI).build());

        assertThat(hasil.isIdempotentReplay()).isTrue();
        assertThat(hasil.getMutasi().getSiswaId()).isEqualTo(2L);
        assertThat(hasil.getSaldoSetelah()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("balapan INSERT: pemenang milik siswa lain tetap ditolak 409")
    void balapanPemenangSiswaLainDitolak() {
        siapkanBalapanPemenangBedabedakan();

        assertThatThrownBy(() -> ledger.kredit(perintah(3L, JenisMutasiSaldo.TOPUP_TUNAI)
                .idempotencyKey(KUNCI).nominal(10_000L).build()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("siswa lain");
    }

    @Test
    @DisplayName("balapan INSERT: pemenang konteks sama tetap replay")
    void balapanPemenangSamaReplay() {
        siapkanBalapan(mutasi(2L, JenisMutasiSaldo.TOPUP_TUNAI, ArahMutasi.KREDIT, 10_000L));

        HasilMutasiSaldo hasil = ledger.kredit(perintah(2L, JenisMutasiSaldo.TOPUP_TUNAI)
                .idempotencyKey(KUNCI).nominal(10_000L).build());

        assertThat(hasil.isIdempotentReplay()).isTrue();
        assertThat(hasil.getMutasi().getSiswaId()).isEqualTo(2L);
    }

    /** Pemenang balapan dari siswa lain (nominal juga beda) untuk uji penolakan. */
    private void siapkanBalapanPemenangBedabedakan() {
        siapkanBalapan(mutasi(9L, JenisMutasiSaldo.TOPUP_TUNAI, ArahMutasi.KREDIT, 99_000L));
    }

    /** Mutasi lama yang dikembalikan repository sebagai hasil replay. */
    private void beriMutasiLama(long siswaId, JenisMutasiSaldo jenis, ArahMutasi arah, long nominal) {
        when(repo.findBySekolahIdAndIdempotencyKey(SEKOLAH, KUNCI))
                .thenReturn(Optional.of(mutasi(siswaId, jenis, arah, nominal)));
    }

    /** Baris ledger pemenang. */
    private MutasiSaldo mutasi(long siswaId, JenisMutasiSaldo jenis, ArahMutasi arah, long nominal) {
        return MutasiSaldo.builder()
                .id(11L).sekolahId(SEKOLAH).siswaId(siswaId)
                .arah(arah).jenis(jenis)
                .nominal(nominal).saldoSetelah(50_000L)
                .idempotencyKey(KUNCI)
                .build();
    }

    /** Simulasikan INSERT kalah balapan UNIQUE (sekolah_id, idempotency_key). */
    private void siapkanBalapan(MutasiSaldo pemenang) {
        when(cacheRepo.kunciUntukUpdate(SEKOLAH, 2L)).thenReturn(Optional.of(saldo(2L)));
        when(cacheRepo.kunciUntukUpdate(SEKOLAH, 3L)).thenReturn(Optional.of(saldo(3L)));
        when(cacheRepo.kunciUntukUpdate(SEKOLAH, 9L)).thenReturn(Optional.of(saldo(9L)));
        when(idGenerator.berikutnyaLong()).thenReturn(123L);
        when(jam.sekarang()).thenReturn(OffsetDateTime.now());
        when(repo.save(any(MutasiSaldo.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));
        // Pra-cek belum melihat baris; pemenang baru muncul saat INSERT kalah UNIQUE.
        when(repo.findBySekolahIdAndIdempotencyKey(SEKOLAH, KUNCI))
                .thenReturn(Optional.empty(), Optional.of(pemenang));
    }

    /** Baris cache untuk siswa yang diminta. */
    private Saldo saldo(long siswaId) {
        return Saldo.builder()
                .sekolahId(SEKOLAH).siswaId(siswaId).saldo(10_000L)
                .build();
    }

    /** Perintah dengan konteks dasar; nominal diisi per kasus. */
    private PerintahMutasiSaldo.PerintahMutasiSaldoBuilder perintah(long siswaId, JenisMutasiSaldo jenis) {
        return PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).siswaId(siswaId).jenis(jenis).nominal(0L);
    }
}
