package com.asqi.scholia_kantin_be.dto;

import lombok.Builder;
import lombok.Data;

/**
 * UID kartu RFID <b>satu</b> siswa (issue #301).
 *
 * <p><b>Keputusan kontrak (Q7 lanjutan).</b> UID kartu adalah data sensitif
 * (dapat dipakai untuk memalsukan tap), sehingga <b>tidak</b> disertakan pada
 * daftar umum siswa {@code GET /api/siswa}. UID hanya diberikan lewat lookup
 * khusus per siswa {@code GET /api/siswa/{siswaId}/rfid} untuk peran yang
 * memang membutuhkannya (admin sekolah &amp; TU/Bendahara).
 *
 * <p><b>Fail-open (PRD §8.4).</b> Bila admin-be belum menyediakan UID, siswa
 * tidak ditemukan, atau integrasi gagal, {@code rfidUid} bernilai {@code null}
 * dan {@code ditemukan=false} — endpoint tetap 200, bukan error, dan UID
 * <b>tidak dikarang</b>.
 */
@Data
@Builder
public class UidSiswaResponse {

    /** ID siswa (lokal kantin-be = id siswa di admin-be). */
    private Long siswaId;

    /** UID kartu siswa; {@code null} bila tidak tersedia. */
    private String rfidUid;

    /** {@code true} bila UID berhasil diperoleh dari sumber data siswa. */
    private boolean ditemukan;
}
