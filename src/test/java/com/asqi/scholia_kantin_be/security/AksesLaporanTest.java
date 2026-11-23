package com.asqi.scholia_kantin_be.security;

import com.asqi.scholia_kantin_be.controller.LaporanController;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.enums.JenisLaporan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kunci anti-drift SEC-04 (issue #391): hak akses <b>per jenis laporan</b>
 * harus sama antara endpoint data JSON ({@link PerluPeran}) dan endpoint
 * ekspor berkas ({@link AksesLaporan}).
 *
 * <p><b>Bug yang dikunci:</b> {@code GET /api/laporan/kerugian-stok} menolak
 * Admin Sekolah (403), tetapi {@code GET /api/laporan/ekspor?jenis=KERUGIAN_STOK}
 * mengizinkannya karena endpoint ekspor hanya membatasi kepsek. Cukup ganti
 * endpoint, kontrol otorisasi terlewati.
 *
 * <p>Bila kelak seseorang menambah/mencabut peran pada salah satu sisi tanpa
 * menyesuaikan peta PRD §9.5, uji ini gagal — bukan diam-diam jadi celah.
 */
@DisplayName("SEC-04 (#391) — hak akses laporan: data JSON == ekspor berkas")
class AksesLaporanTest {

    /** Tiga parameter periode yang dipakai endpoint laporan berkala. */
    private static final Class<?>[] PERIODE = {LocalDate.class, OffsetDateTime.class, OffsetDateTime.class};

    /** Satu baris pemetaan: endpoint data → jenis laporan yang dilayaninya. */
    private record Endpoint(String method, JenisLaporan jenis, Class<?>... params) {
    }

    private static final List<Endpoint> ENDPOINT_DATA = List.of(
            new Endpoint("penjualan", JenisLaporan.PENJUALAN, PERIODE),
            new Endpoint("penjualanItem", JenisLaporan.PENJUALAN_ITEM, PERIODE),
            new Endpoint("penjualanKategori", JenisLaporan.PENJUALAN_KATEGORI, PERIODE),
            new Endpoint("penjualanPerTitik", JenisLaporan.PENJUALAN_TITIK, PERIODE),
            new Endpoint("penjualanPerPetugas", JenisLaporan.PENJUALAN_PETUGAS, PERIODE),
            new Endpoint("penjualanPerKasir", JenisLaporan.PENJUALAN_KASIR, PERIODE),
            new Endpoint("saldoMengendap", JenisLaporan.SALDO_MENGENDAP),
            new Endpoint("rekonsiliasi", JenisLaporan.REKONSILIASI, PERIODE),
            new Endpoint("stok", JenisLaporan.STOK, boolean.class, int.class, int.class),
            new Endpoint("kerugianStok", JenisLaporan.KERUGIAN_STOK,
                    LocalDate.class, OffsetDateTime.class, OffsetDateTime.class, int.class, int.class),
            new Endpoint("ringkasanKerugianStok", JenisLaporan.KERUGIAN_STOK, PERIODE),
            new Endpoint("pembatalan", JenisLaporan.PEMBATALAN, PERIODE),
            new Endpoint("perSiswa", JenisLaporan.PER_SISWA,
                    Long.class, LocalDate.class, OffsetDateTime.class, OffsetDateTime.class),
            new Endpoint("riwayatSiswa", JenisLaporan.PER_SISWA,
                    Long.class, OffsetDateTime.class, OffsetDateTime.class),
            new Endpoint("setoranTu", JenisLaporan.SETORAN_TU, PERIODE),
            new Endpoint("kartuStok", JenisLaporan.KARTU_STOK, Long.class, Integer.class));

    @Test
    @DisplayName("peta PRD §9.5 lengkap untuk semua jenis laporan (fail-closed bila tak terdaftar)")
    void petaLengkap() {
        for (JenisLaporan jenis : JenisLaporan.values()) {
            assertThat(AksesLaporan.peran(jenis))
                    .as("jenis %s wajib terdaftar di AksesLaporan (PRD §9.5)", jenis)
                    .isNotEmpty();
        }
        assertThat(AksesLaporan.boleh(null, AktorKantin.TU_SEKOLAH)).isFalse();
    }

    @Test
    @DisplayName("peran per jenis laporan sesuai kolom Akses PRD §9.5")
    void petaSesuaiPrd() {
        Set<AktorKantin> penjualan = EnumSet.of(AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH,
                AktorKantin.PENGELOLA_KANTIN, AktorKantin.KEPSEK);
        for (JenisLaporan jenis : List.of(JenisLaporan.PENJUALAN, JenisLaporan.PENJUALAN_ITEM,
                JenisLaporan.PENJUALAN_KATEGORI, JenisLaporan.PENJUALAN_TITIK,
                JenisLaporan.PENJUALAN_PETUGAS, JenisLaporan.PENJUALAN_KASIR)) {
            assertThat(AksesLaporan.peran(jenis)).as("%s: Bendahara, Admin, Pengelola, Kepsek", jenis)
                    .containsExactlyInAnyOrderElementsOf(penjualan);
        }

        assertThat(AksesLaporan.peran(JenisLaporan.SALDO_MENGENDAP))
                .containsExactlyInAnyOrder(AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH,
                        AktorKantin.KEPSEK);
        assertThat(AksesLaporan.peran(JenisLaporan.REKONSILIASI))
                .containsExactlyInAnyOrder(AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH,
                        AktorKantin.KEPSEK);

        // §9.5: stok, kartu stok, barang masuk — hanya Pengelola & Bendahara.
        for (JenisLaporan jenis : List.of(JenisLaporan.STOK, JenisLaporan.KARTU_STOK,
                JenisLaporan.BARANG_MASUK)) {
            assertThat(AksesLaporan.peran(jenis)).as("%s: Pengelola & Bendahara saja", jenis)
                    .containsExactlyInAnyOrder(AktorKantin.TU_SEKOLAH,
                            AktorKantin.PENGELOLA_KANTIN);
        }

        // §9.5: kerugian stok — Pengelola, Bendahara, Kepsek (TANPA Admin).
        assertThat(AksesLaporan.peran(JenisLaporan.KERUGIAN_STOK))
                .containsExactlyInAnyOrder(AktorKantin.TU_SEKOLAH, AktorKantin.PENGELOLA_KANTIN,
                        AktorKantin.KEPSEK);

        assertThat(AksesLaporan.peran(JenisLaporan.PEMBATALAN))
                .containsExactlyInAnyOrder(AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH);
        assertThat(AksesLaporan.peran(JenisLaporan.PER_SISWA))
                .containsExactlyInAnyOrder(AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH);
        assertThat(AksesLaporan.peran(JenisLaporan.SETORAN_TU))
                .containsExactly(AktorKantin.TU_SEKOLAH);
    }

    @Test
    @DisplayName("anotasi @PerluPeran tiap endpoint data == peta ekspor (satu sumber kebenaran)")
    void anotasiEndpointSamaDenganPeta() {
        for (Endpoint e : ENDPOINT_DATA) {
            assertThat(peranAnotasi(e))
                    .as("%s harus sama dengan peta ekspor untuk %s", e.method(), e.jenis())
                    .containsExactlyInAnyOrderElementsOf(AksesLaporan.peran(e.jenis()));
        }
    }

    @Test
    @DisplayName("SEC-04: Admin Sekolah ditolak KERUGIAN_STOK (data & ekspor), tetap boleh PENJUALAN")
    void adminSekolahKerugianStokDitolak() {
        assertThat(AksesLaporan.boleh(JenisLaporan.KERUGIAN_STOK, AktorKantin.ADMIN_SEKOLAH))
                .as("PRD §9.5 tidak menyebut Admin pada kerugian stok")
                .isFalse();
        assertThat(AksesLaporan.boleh(JenisLaporan.PENJUALAN, AktorKantin.ADMIN_SEKOLAH)).isTrue();
        // Regresi: dulu ekspor membolehkan admin karena hanya kepsek yang dibatasi.
        assertThat(peranAnotasi(new Endpoint("kerugianStok", JenisLaporan.KERUGIAN_STOK,
                LocalDate.class, OffsetDateTime.class, OffsetDateTime.class, int.class, int.class)))
                .doesNotContain(AktorKantin.ADMIN_SEKOLAH);
    }

    /** Peran yang tercantum pada {@code @PerluPeran} method controller. */
    private static List<AktorKantin> peranAnotasi(Endpoint e) {
        try {
            Method m = LaporanController.class.getDeclaredMethod(e.method(), e.params());
            PerluPeran p = m.getAnnotation(PerluPeran.class);
            assertThat(p).as("@PerluPeran wajib ada di %s", e.method()).isNotNull();
            return Arrays.asList(p.value());
        } catch (NoSuchMethodException ex) {
            throw new AssertionError("Method tidak ditemukan: " + e.method(), ex);
        }
    }
}
