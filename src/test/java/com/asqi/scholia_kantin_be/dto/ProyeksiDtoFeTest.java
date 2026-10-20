package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.Transaksi;
import com.asqi.scholia_kantin_be.model.TransaksiItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proyeksi DTO yang dipakai endpoint pembungkus FE (issue #223/#225/#229).
 *
 * <p>Uji tanpa database: memastikan nama field &amp; nilai hasil mapping sesuai
 * tipe FE — termasuk format {@code nomorReferensi}, status kartu
 * {@code ACTIVE}/{@code BLOCKED}, dan fallback nama pembeli saat data induk
 * siswa tidak tersedia (ADR-0002).
 */
@DisplayName("Proyeksi DTO endpoint pembungkus FE (#223/#225/#229)")
class ProyeksiDtoFeTest {

    @Test
    @DisplayName("#225 kartu tamu aktif → ACTIVE, nonaktif → BLOCKED")
    void kartuTamuStatus() {
        BarisKartuTamu aktif = BarisKartuTamu.builder()
                .kartuId(7L).nomorKartu("KT-007").rfidUid("UID7")
                .labelPemegang("Budi").aktif(true).saldo(85_000L)
                .dibuatPada(OffsetDateTime.parse("2026-10-01T08:00:00+07:00").toInstant())
                .build();

        KartuTamuSaldoDetail d = KartuTamuSaldoDetail.dari(aktif);

        assertThat(d.id()).isEqualTo(7L);
        assertThat(d.uid()).isEqualTo("UID7");
        assertThat(d.saldo()).isEqualTo(85_000L);
        assertThat(d.status()).isEqualTo("ACTIVE");
        assertThat(d.createdAt()).isEqualTo(aktif.getDibuatPada());

        aktif.setAktif(false);
        assertThat(KartuTamuSaldoDetail.dari(aktif).status()).isEqualTo("BLOCKED");
    }

    @Test
    @DisplayName("#229 nomor referensi TRX-<tanggal>-<id>, item & void ikut terbawa")
    void transaksiSesiItem() {
        Transaksi t = Transaksi.builder()
                .id(900105L).sekolahId(1L).sesiKasirId(2L).titikKasirId(1L)
                .subjekTipe(SubjekTipe.KARTU_TAMU).subjekId(5L).kartuUid("DE000005")
                .waktu(OffsetDateTime.parse("2026-10-19T11:40:00+07:00"))
                .total(5_000L).status(StatusTransaksi.VOID)
                .alasanVoid("Salah input menu").voidOleh(2L)
                .build();

        TransaksiItem item = TransaksiItem.builder()
                .menuId(1L).namaMenu("Nasi Kuning").qty(1)
                .hargaJual(5_000L).subtotal(5_000L)
                .build();

        TransaksiSesiItem d = TransaksiSesiItem.dari(t, "Kasir Utama", "Tamu Umum", List.of(item));

        assertThat(d.nomorReferensi()).isEqualTo("TRX-20261019-900105");
        assertThat(d.pembeliTipe()).isEqualTo("KARTU_TAMU");
        assertThat(d.pembeliNama()).isEqualTo("Tamu Umum");
        assertThat(d.status()).isEqualTo(StatusTransaksi.VOID);
        assertThat(d.voidAlasan()).isEqualTo("Salah input menu");
        assertThat(d.voidOleh()).isEqualTo("2");
        assertThat(d.items()).hasSize(1);
        assertThat(d.items().get(0).namaMenu()).isEqualTo("Nasi Kuning");
        assertThat(d.items().get(0).subtotal()).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("#229 subjek SISWA tanpa nama induk → 'Siswa #<id>', bukan null")
    void transaksiSesiItemSiswaTanpaNama() {
        Transaksi t = Transaksi.builder()
                .id(1002L).subjekTipe(SubjekTipe.SISWA).subjekId(1001L)
                .waktu(OffsetDateTime.parse("2026-10-20T09:00:00+07:00"))
                .total(7_000L).status(StatusTransaksi.SUKSES)
                .build();

        TransaksiSesiItem d = TransaksiSesiItem.dari(t, "Kasir", null, List.of());

        assertThat(d.pembeliNama()).isEqualTo("Siswa #1001");
        assertThat(d.pembeliKelas()).isNull();
        assertThat(d.items()).isEmpty();
        assertThat(d.total()).isEqualTo(7_000L);
    }
}
