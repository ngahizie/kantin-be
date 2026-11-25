package com.asqi.scholia_kantin_be.dto.saldo;

import com.asqi.scholia_kantin_be.service.kasir.HasilMutasiSaldo;
import lombok.Builder;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * Respons `POST /api/saldo/topup` dan `POST /api/saldo/koreksi`.
 *
 * <p>Menggantikan pemakaian langsung value object {@link HasilMutasiSaldo}
 * yang memuat entity {@code MutasiSaldo}. Field {@code mutasi} kini berupa
 * {@link MutasiKoreksiItem}, jadi kolom ledger internal tidak ikut terekspos.
 * Field {@code saldoSebelum}, {@code waktu}, dan {@code idempotentReplay}
 * tetap dipertahankan agar klien tidak perlu menghitung ulang.
 */
@Data
@Builder
public class HasilMutasiSaldoResponse {
    private MutasiKoreksiItem mutasi;
    private long saldoSebelum;
    private long saldoSetelah;
    private OffsetDateTime waktu;
    /** true bila permintaan adalah replay idempotency key yang sama. */
    private boolean idempotentReplay;

    /**
     * Konversi hasil mutasi layer service ke DTO respons API.
     *
     * <p>{@code mutasi} boleh null (dipakai tes controller dengan service
     * mock); field turunan lalu diisi dari saldo akhir tanpa proyeksi ledger.
     *
     * @param hasil hasil mutasi dari service; null menghasilkan DTO kosong
     * @return DTO respons yang tidak membawa entity
     */
    public static HasilMutasiSaldoResponse dari(HasilMutasiSaldo hasil) {
        if (hasil == null) {
            return HasilMutasiSaldoResponse.builder().build();
        }
        MutasiKoreksiItem mutasi = hasil.getMutasi() == null
                ? null : MutasiKoreksiItem.dari(hasil.getMutasi());
        return HasilMutasiSaldoResponse.builder()
                .mutasi(mutasi)
                .saldoSebelum(mutasi == null ? hasil.getSaldoSetelah() : mutasi.getSaldoSebelum())
                .saldoSetelah(hasil.getSaldoSetelah())
                .waktu(mutasi == null ? null : mutasi.getWaktu())
                .idempotentReplay(hasil.isIdempotentReplay())
                .build();
    }
}
