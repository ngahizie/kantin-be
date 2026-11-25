package com.asqi.scholia_kantin_be.dto.laporan;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.helper.MaskingKartu;
import com.asqi.scholia_kantin_be.model.Siswa;
import com.asqi.scholia_kantin_be.model.Transaksi;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Satu baris laporan <b>Pembatalan kasir</b> (PRD §9.5, issue #114):
 * transaksi yang dibatalkan lewat tombol Batalkan ("Kartu dipakai bukan
 * pemiliknya") per subjek.
 *
 * <p><b>Enrichment (SEC-BATAL-04, issue #395):</b> identitas siswa dan nama
 * titik kasir diisi di server. Sebelumnya klien harus menyambungkan sendiri
 * lewat {@code /api/siswa?batas=50}, yang memotong senyap siswa di luar batas
 * itu sehingga muncul sebagai "Siswa #ID".
 *
 * <p><b>Masking (SEC-BATAL-05):</b> {@code kartuUid} dikirim tersamar
 * ({@code ****C3D4}) — UID RFID adalah kredensial kepemilikan, bukan data
 * tampilan.
 */
@Data
@Builder
public class BarisPembatalanKasir {

    private Long transaksiId;
    private SubjekTipe subjekTipe;
    private Long siswaId;
    /** Nama siswa pemilik kartu (null bila transaksi tunai tanpa kartu). */
    private String siswaNama;
    /** NIS siswa (null bila tidak ada di master atau transaksi tanpa siswa). */
    private String siswaNis;
    /** Kelas siswa (null bila tidak ada di master atau transaksi tanpa siswa). */
    private String siswaKelas;
    /** UID kartu yang dipakai — <b>tersamar</b>, hanya 4 karakter terakhir. */
    private String kartuUid;
    private long total;
    private String alasanVoid;
    private OffsetDateTime voidAt;
    private Long voidOleh;
    private Long petugasId;
    private Long titikKasirId;
    /** Nama titik kasir (null bila titik sudah tidak ada di master). */
    private String titikKasirNama;
    private OffsetDateTime waktu;

    /**
     * Proyeksi entitas → DTO dengan identitas yang sudah di-resolve pemanggil.
     *
     * @param t          transaksi berstatus VOID
     * @param siswaById  peta id siswa → entitas (tenant-scoped), boleh kosong
     * @param namaTitik  peta id titik kasir → nama, boleh kosong
     */
    public static BarisPembatalanKasir dari(Transaksi t, Map<Long, Siswa> siswaById,
                                            Map<Long, String> namaTitik) {
        Siswa siswa = t.getSiswaId() == null ? null : siswaById.get(t.getSiswaId());
        return BarisPembatalanKasir.builder()
                .transaksiId(t.getId())
                .subjekTipe(t.getSiswaId() == null ? null : SubjekTipe.SISWA)
                .siswaId(t.getSiswaId())
                .siswaNama(siswa == null ? null : siswa.getNama())
                .siswaNis(siswa == null ? null : siswa.getNis())
                .siswaKelas(siswa == null ? null : siswa.getKelas())
                .kartuUid(MaskingKartu.uid(t.getKartuUid()))
                .total(t.getTotal())
                .alasanVoid(t.getAlasanVoid())
                .voidAt(t.getVoidAt())
                .voidOleh(t.getVoidOleh())
                .petugasId(t.getPetugasId())
                .titikKasirId(t.getTitikKasirId())
                .titikKasirNama(t.getTitikKasirId() == null ? null
                        : namaTitik.get(t.getTitikKasirId()))
                .waktu(t.getWaktu())
                .build();
    }
}
