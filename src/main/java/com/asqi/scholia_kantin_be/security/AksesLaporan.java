package com.asqi.scholia_kantin_be.security;

import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.enums.JenisLaporan;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Satu sumber kebenaran hak akses <b>per jenis laporan</b> (PRD §9.5) — SEC-04,
 * issue #391.
 *
 * <p><b>Masalah yang ditutup:</b> endpoint data JSON memakai
 * {@link PerluPeran} per method, sedangkan endpoint ekspor
 * ({@code GET /api/laporan/ekspor}) melayani semua jenis lewat satu anotasi
 * longgar. Akibatnya {@link AktorKantin#ADMIN_SEKOLAH} ditolak 403 pada
 * {@code GET /api/laporan/kerugian-stok} tetapi <b>boleh</b> mengunduh berkas
 * yang sama lewat {@code ekspor?jenis=KERUGIAN_STOK} — kontrol otorisasi bisa
 * dilewati hanya dengan mengganti endpoint.
 *
 * <p><b>Aturan:</b> peta di bawah adalah cermin kolom <i>Akses</i> PRD §9.5 dan
 * <b>wajib sama</b> dengan anotasi {@link PerluPeran} pada endpoint data
 * (dijaga {@code AksesLaporanKonsistenTest}). Menambah peran di salah satu sisi
 * tanpa sisi lain akan membuat tes merah — itulah tujuan kelas ini.
 *
 * <p>Bila jenis laporan tidak ada di peta → <b>fail-closed</b> (tidak ada peran
 * yang berhak), bukan terbuka.
 */
public final class AksesLaporan {

    /** Laporan penjualan &amp; dimensinya: Bendahara, Kepsek, Admin, Pengelola (PRD §9.5). */
    private static final Set<AktorKantin> PENJUALAN = EnumSet.of(
            AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH,
            AktorKantin.PENGELOLA_KANTIN, AktorKantin.KEPSEK);

    /** Saldo mengendap &amp; rekonsiliasi: Bendahara, Admin, Kepsek. */
    private static final Set<AktorKantin> SALDO = EnumSet.of(
            AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.KEPSEK);

    /** Stok, kartu stok, barang masuk: Pengelola &amp; Bendahara (tanpa kepsek/admin). */
    private static final Set<AktorKantin> STOK = EnumSet.of(
            AktorKantin.TU_SEKOLAH, AktorKantin.PENGELOLA_KANTIN);

    /** Kerugian stok: Pengelola, Bendahara, Kepsek — <b>tanpa Admin</b> (PRD §9.5). */
    private static final Set<AktorKantin> KERUGIAN = EnumSet.of(
            AktorKantin.TU_SEKOLAH, AktorKantin.PENGELOLA_KANTIN, AktorKantin.KEPSEK);

    /** Pembatalan kasir &amp; per siswa: Bendahara, Admin. */
    private static final Set<AktorKantin> BENDAHARA_ADMIN = EnumSet.of(
            AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH);

    /** Setoran kas TU: hanya Bendahara (PRD §9.5). */
    private static final Set<AktorKantin> SETORAN = EnumSet.of(AktorKantin.TU_SEKOLAH);

    private static final Map<JenisLaporan, Set<AktorKantin>> PETA;

    static {
        Map<JenisLaporan, Set<AktorKantin>> peta = new EnumMap<>(JenisLaporan.class);
        peta.put(JenisLaporan.PENJUALAN, PENJUALAN);
        peta.put(JenisLaporan.PENJUALAN_ITEM, PENJUALAN);
        peta.put(JenisLaporan.PENJUALAN_KATEGORI, PENJUALAN);
        peta.put(JenisLaporan.PENJUALAN_TITIK, PENJUALAN);
        peta.put(JenisLaporan.PENJUALAN_PETUGAS, PENJUALAN);
        peta.put(JenisLaporan.PENJUALAN_KASIR, PENJUALAN);
        peta.put(JenisLaporan.SALDO_MENGENDAP, SALDO);
        peta.put(JenisLaporan.REKONSILIASI, SALDO);
        peta.put(JenisLaporan.STOK, STOK);
        peta.put(JenisLaporan.KARTU_STOK, STOK);
        peta.put(JenisLaporan.BARANG_MASUK, STOK);
        peta.put(JenisLaporan.KERUGIAN_STOK, KERUGIAN);
        peta.put(JenisLaporan.PEMBATALAN, BENDAHARA_ADMIN);
        peta.put(JenisLaporan.PER_SISWA, BENDAHARA_ADMIN);
        peta.put(JenisLaporan.SETORAN_TU, SETORAN);
        PETA = Collections.unmodifiableMap(peta);
    }

    private AksesLaporan() {
    }

    /**
     * Peran yang berhak membaca/mengekspor jenis laporan ini (PRD §9.5).
     *
     * @return himpunan peran tak bermutasi; kosong (fail-closed) bila jenis tak terdaftar
     */
    public static Set<AktorKantin> peran(JenisLaporan jenis) {
        Set<AktorKantin> peran = PETA.get(jenis);
        return peran == null ? Set.of() : Collections.unmodifiableSet(peran);
    }

    /**
     * Apakah {@code peran} berhak membaca <b>dan</b> mengekspor jenis laporan ini.
     *
     * @param peran peran pemanggil; {@code null} (konteks tak tersedia) → {@code false}
     */
    public static boolean boleh(JenisLaporan jenis, AktorKantin peran) {
        return peran != null && peran(jenis).contains(peran);
    }
}
