package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.helper.NomorStruk;
import com.asqi.scholia_kantin_be.model.Transaksi;

import java.time.OffsetDateTime;

/**
 * Hasil <b>refund/void/koreksi</b> satu transaksi kasir (PRD §6.3, §9.2) —
 * bentuk respons yang dikirim ke klien setelah saldo &amp; stok dikembalikan.
 *
 * <p><b>Kenapa DTO, bukan entity.</b> Sebelumnya endpoint ini mengembalikan
 * entity {@link Transaksi} apa adanya. Entity itu punya relasi dua arah
 * {@code Transaksi.items} (OneToMany) &harr; {@code TransaksiItem.transaksi}
 * (ManyToOne) tanpa {@code @JsonIgnore}, sehingga Jackson menelusuri siklus
 * tanpa henti dan berhenti di
 * {@code HttpMessageNotWritableException: Document nesting depth (501) exceeds
 * the maximum allowed (500)}. Akibatnya refund <b>berhasil di server</b>
 * (transaksi jadi VOID, saldo &amp; stok kembali) tetapi klien menerima respons
 * rusak dan menampilkan "gagal"; kasir yang mengulang lalu dijawab
 * "Transaksi sudah di-refund". DTO ini memutus siklus tersebut sekaligus
 * menstabilkan kontrak JSON (entity bukan kontrak).
 *
 * <p>Nama field disamakan dengan {@link TransaksiSesiItem} ({@code nomorStruk},
 * {@code voidAlasan}, {@code voidAt}, {@code voidOleh}) supaya FE memakai satu
 * bentuk data untuk riwayat sesi dan hasil refund.
 *
 * @param id          id transaksi
 * @param nomorStruk  kode struk ({@code TRX-yyyyMMdd-<id>}) — sama dengan yang
 *                    dipakai verifikasi refund, jadi bisa langsung dicetak/ditunjukkan
 * @param waktu       waktu transaksi
 * @param subjekTipe  {@code SISWA} atau {@code KARTU_TAMU}
 * @param subjekId    id siswa / kartu tamu
 * @param kartuUid    UID kartu yang dipakai saat transaksi
 * @param total       nominal yang dikembalikan ke saldo
 * @param status      status setelah operasi ({@code VOID})
 * @param voidAlasan  alasan refund/koreksi
 * @param voidAt      waktu refund/koreksi
 * @param voidOleh    id akun pelaku
 */
public record TransaksiRefundResponse(
        Long id,
        String nomorStruk,
        OffsetDateTime waktu,
        String subjekTipe,
        Long subjekId,
        String kartuUid,
        Long total,
        StatusTransaksi status,
        String voidAlasan,
        OffsetDateTime voidAt,
        Long voidOleh) {

    /**
     * Proyeksi entity → DTO.
     *
     * <p>{@code null} diteruskan sebagai {@code null} (bukan NPE) supaya
     * pemanggil yang tidak menyiapkan data — termasuk uji controller ber-mock —
     * tetap mendapat respons {@code data: null}.
     */
    public static TransaksiRefundResponse dari(Transaksi t) {
        if (t == null) {
            return null;
        }
        return new TransaksiRefundResponse(
                t.getId(),
                NomorStruk.dari(t.getId(), t.getWaktu()),
                t.getWaktu(),
                t.getSubjekTipe() == null ? null : t.getSubjekTipe().name(),
                t.getSubjekId(),
                t.getKartuUid(),
                t.getTotal(),
                t.getStatus(),
                t.getAlasanVoid(),
                t.getVoidAt(),
                t.getVoidOleh());
    }
}
