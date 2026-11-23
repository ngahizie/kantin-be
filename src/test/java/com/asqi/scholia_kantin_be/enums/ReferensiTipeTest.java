package com.asqi.scholia_kantin_be.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Penjaga kontrak data kolom {@code referensi_tipe}.
 *
 * <p>Nilai yang tersimpan di {@code saldo_ledger}/{@code mutasi_stok} adalah
 * {@link ReferensiTipe#name()}. Test ini mengunci daftar nilainya supaya
 * penggantian nama konstanta tidak diam-diam mengubah data historis —
 * perubahan nama wajib disertai migrasi data dan update test ini.
 */
@DisplayName("ReferensiTipe — kontrak nilai kolom referensi_tipe")
class ReferensiTipeTest {

    @Test
    @DisplayName("Daftar nilai enum sama dengan nilai yang dipakai ledger")
    void daftarNilaiSesuaiKontrak() {
        List<String> nilai = Arrays.stream(ReferensiTipe.values())
                .map(Enum::name)
                .toList();

        assertThat(nilai).containsExactly(
                "TRANSAKSI",
                "TOPUP",
                "TOPUP_ONLINE",
                "TRANSFER",
                "REFUND",
                "KOREKSI",
                "BARANG_MASUK",
                "BARANG_MASUK_PEMBALIK",
                "OPNAME",
                "OPNAME_BATCH");
    }
}
