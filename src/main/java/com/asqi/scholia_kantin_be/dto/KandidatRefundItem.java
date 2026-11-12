package com.asqi.scholia_kantin_be.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Satu baris daftar kandidat refund/pindah saldo (PRD §9.3, issue #38).
 *
 * <p>Berasal dari saldo berjalan ({@code saldo_cache}) subjek SISWA yang masih
 * bersisa, lalu diperkaya identitas &amp; status keaktifan dari admin-be
 * (issue #298) — identitas lewat {@code CariSiswaPort#cariBanyak} dan status
 * lewat {@code StatusSiswaPort#tidakAktifBanyak}. Keduanya <b>batch</b>, bukan
 * satu panggilan per kandidat.
 *
 * <p><b>Kontrak data identitas.</b> {@code nis}, {@code nama}, dan {@code kelas}
 * bernilai {@code null} bila integrasi admin-be belum aktif, siswa tidak
 * ditemukan, atau integrasi gagal — FE menampilkan fallback (mis. "Siswa #id")
 * dan <b>tidak</b> mengarang identitas.
 *
 * <p><b>Status tidak aktif.</b> Field {@code tidakAktif} hanya boolean:
 * {@code TRUE} nonaktif, {@code FALSE} aktif, {@code null} tidak diketahui.
 * Alasan (lulus / pindah / keluar) <b>tidak</b> dibedakan karena kontrak
 * admin-be belum menyediakannya — jangan diisi dengan tebakan.
 */
@Data
@Builder
public class KandidatRefundItem {

    private Long siswaId;

    /** Saldo berjalan subjek (rupiah). */
    private long saldo;

    /** NIS siswa dari admin-be; {@code null} bila tidak tersedia. */
    private String nis;

    /** Nama siswa dari admin-be; {@code null} bila tidak tersedia. */
    private String nama;

    /** Kelas siswa dari admin-be; {@code null} bila tidak tersedia. */
    private String kelas;

    /**
     * Status keaktifan siswa: {@code TRUE} nonaktif (lulus/pindah/keluar),
     * {@code FALSE} aktif, {@code null} tidak diketahui (integrasi Q7 belum
     * siap atau siswa tidak ditemukan).
     */
    private Boolean tidakAktif;
}
