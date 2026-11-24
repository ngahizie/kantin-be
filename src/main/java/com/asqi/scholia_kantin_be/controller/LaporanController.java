package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.component.exception.ForbiddenException;
import com.asqi.scholia_kantin_be.dto.BarisKerugianStok;
import com.asqi.scholia_kantin_be.dto.BarisPembatalanKasir;
import com.asqi.scholia_kantin_be.dto.BarisPenjualanDimensi;
import com.asqi.scholia_kantin_be.dto.BarisPenjualanKasir;
import com.asqi.scholia_kantin_be.dto.BarisPenjualan;
import com.asqi.scholia_kantin_be.dto.BarisStok;
import com.asqi.scholia_kantin_be.dto.KartuStokItem;
import com.asqi.scholia_kantin_be.dto.LaporanPerSiswa;
import com.asqi.scholia_kantin_be.dto.LaporanRiwayatKeseluruhanItem;
import com.asqi.scholia_kantin_be.dto.LaporanRiwayatSiswa;
import com.asqi.scholia_kantin_be.dto.RekapSetoranItem;
import com.asqi.scholia_kantin_be.dto.SiswaSaldoDetail;
import com.asqi.scholia_kantin_be.dto.RingkasanPenjualan;
import com.asqi.scholia_kantin_be.dto.RingkasanRekonsiliasi;
import com.asqi.scholia_kantin_be.dto.RingkasanSaldoMengendap;
import com.asqi.scholia_kantin_be.dto.RingkasanKerugianStok;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.enums.JenisLaporan;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.security.AksesLaporan;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.PerluPeran;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.laporan.LaporanExportService;
import com.asqi.scholia_kantin_be.service.laporan.LaporanMasking;
import com.asqi.scholia_kantin_be.service.laporan.LaporanPembatalanService;
import com.asqi.scholia_kantin_be.service.laporan.LaporanPenjualanService;
import com.asqi.scholia_kantin_be.service.laporan.LaporanSaldoService;
import com.asqi.scholia_kantin_be.service.laporan.LaporanSetoranService;
import com.asqi.scholia_kantin_be.service.laporan.LaporanSiswaService;
import com.asqi.scholia_kantin_be.service.laporan.LaporanStokService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Endpoint laporan &amp; ekspor Excel (PRD §9.5).
 *
 * <p>Periode: kirim {@code tanggal} (YYYY-MM-DD, default hari ini zona sekolah)
 * <b>atau</b> rentang {@code dari}/{@code sampai} (ISO date-time). Bila keduanya
 * kosong → hari ini. Semua laporan tenant-scoped dari token.
 *
 * <p>Akses mengikuti PRD §9.5 per kolom <i>Akses</i>. Peran {@link AktorKantin#KEPSEK}
 * (kepala sekolah) bersifat <b>read-only</b> dan hanya pada laporan yang
 * menyebutnya — penjualan (termasuk dimensi titik kasir, petugas, dan kasir)
 * &amp; laba kotor, saldo mengendap, rekonsiliasi, kerugian stok. Laporan
 * <b>stok</b> &amp; <b>barang masuk</b> tidak dibuka untuk kepsek
 * (PRD §9.5: hanya Pengelola &amp; Bendahara). Lihat ADR-0012 &amp; issue #123.
 */
@RestController
@RequestMapping("api/laporan")
@RequiredArgsConstructor
public class LaporanController {

    private final LaporanPenjualanService penjualan;
    private final LaporanSaldoService saldo;
    private final LaporanStokService stok;
    private final LaporanPembatalanService pembatalan;
    private final LaporanSiswaService siswa;
    private final LaporanSetoranService setoran;
    private final LaporanExportService exportService;

    /** Ringkasan penjualan &amp; laba kotor (PRD §9.5). */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH,
            AktorKantin.PENGELOLA_KANTIN, AktorKantin.KEPSEK})
    @GetMapping("penjualan")
    public ResponseEntity<Response<RingkasanPenjualan>> penjualan(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        RingkasanPenjualan ringkasan =
                penjualan.ringkasanPenjualan(TenantContext.sekolahIdWajib(), tanggal, dari, sampai);
        // SEC-02 (#388): Admin Sekolah tidak berhak melihat modal/laba/margin (PRD §9.5).
        return CommonResponse.data(bolehLihatLaba() ? ringkasan : LaporanMasking.tanpaLaba(ringkasan));
    }

    /** Penjualan per item (PRD §9.5). */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH,
            AktorKantin.PENGELOLA_KANTIN, AktorKantin.KEPSEK})
    @GetMapping("penjualan/item")
    public ResponseEntity<Response<List<BarisPenjualan>>> penjualanItem(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        List<BarisPenjualan> baris =
                penjualan.penjualanPerItem(TenantContext.sekolahIdWajib(), tanggal, dari, sampai);
        return CommonResponse.data(bolehLihatLaba() ? baris : LaporanMasking.tanpaLaba(baris));
    }

    /** Penjualan per kategori (PRD §9.5). */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH,
            AktorKantin.PENGELOLA_KANTIN, AktorKantin.KEPSEK})
    @GetMapping("penjualan/kategori")
    public ResponseEntity<Response<List<BarisPenjualan>>> penjualanKategori(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        List<BarisPenjualan> baris =
                penjualan.penjualanPerKategori(TenantContext.sekolahIdWajib(), tanggal, dari, sampai);
        return CommonResponse.data(bolehLihatLaba() ? baris : LaporanMasking.tanpaLaba(baris));
    }

    /** Saldo mengendap — dana titipan (PRD §9.5). */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.KEPSEK})
    @GetMapping("saldo-mengendap")
    public ResponseEntity<Response<RingkasanSaldoMengendap>> saldoMengendap() {
        return CommonResponse.data(saldo.saldoMengendap(TenantContext.sekolahIdWajib()));
    }

    /** Saldo mengendap per siswa (issue #224), dibatasi untuk menghindari pemuatan seluruh siswa. */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @GetMapping("saldo-mengendap/siswa")
    public ResponseEntity<Response<List<SiswaSaldoDetail>>> saldoMengendapSiswa(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "20") int batas) {
        int batasAman = Math.min(Math.max(batas, 1), 50);
        return CommonResponse.data(saldo.saldoMengendapSiswa(
                TenantContext.sekolahIdWajib(), search, batasAman));
    }

    /**
     * Rekonsiliasi harian + pemeriksaan invariant (PRD §9.5, §5).
     *
     * <p>Periode hanya membatasi arus kas yang ditampilkan. Field
     * {@code selisih}/{@code seimbang} tetap merupakan invarian <b>all-time</b>;
     * periode tanpa transaksi tidak membuat laporan otomatis seimbang.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.KEPSEK})
    @GetMapping("rekonsiliasi")
    public ResponseEntity<Response<RingkasanRekonsiliasi>> rekonsiliasi(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        return CommonResponse.data(
                saldo.rekonsiliasi(TenantContext.sekolahIdWajib(), tanggal, dari, sampai));
    }

    /**
     * Laporan stok + nilai persediaan (PRD §9.5). <b>Tanpa kepsek</b> — kolom
     * akses §9.5 hanya Pengelola &amp; Bendahara.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @GetMapping("stok")
    public ResponseEntity<Response<List<BarisStok>>> stok(
            @RequestParam(defaultValue = "false") boolean hanyaMenipis,
            @RequestParam(defaultValue = "0") int halaman,
            @RequestParam(defaultValue = "200") int ukuran) {
        Page<BarisStok> hasil = stok.laporanStok(
                TenantContext.sekolahIdWajib(), hanyaMenipis, halaman, ukuran);
        return CommonResponse.dataHalaman(hasil, hasil.getContent());
    }

    /**
     * Kerugian stok (opname keluar &amp; barang rusak) (PRD §9.5) — berhalaman
     * (SEC-03, issue #391): {@code data} tetap array (klien lama aman) dengan
     * metadata {@code total}/{@code totalPages}/{@code currentPage}.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.PENGELOLA_KANTIN, AktorKantin.KEPSEK})
    @GetMapping("kerugian-stok")
    public ResponseEntity<Response<List<BarisKerugianStok>>> kerugianStok(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai,
            @RequestParam(defaultValue = "0") int halaman,
            @RequestParam(defaultValue = "200") int ukuran) {
        Page<BarisKerugianStok> hasil = stok.kerugianStok(
                TenantContext.sekolahIdWajib(), tanggal, dari, sampai, halaman, ukuran);
        return CommonResponse.dataHalaman(hasil, hasil.getContent());
    }

    /** Ringkasan agregat kerugian stok, konsisten dengan daftar pada periode sama (#226). */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.PENGELOLA_KANTIN, AktorKantin.KEPSEK})
    @GetMapping("kerugian-stok/ringkasan")
    public ResponseEntity<Response<RingkasanKerugianStok>> ringkasanKerugianStok(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        return CommonResponse.data(stok.ringkasanKerugianStok(
                TenantContext.sekolahIdWajib(), tanggal, dari, sampai));
    }

    /**
     * Laporan <b>Pembatalan kasir</b> (PRD §9.5, issue #114): daftar transaksi
     * yang di-void (tombol Batalkan) per subjek pada periode. RBAC Bendahara/Admin.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @GetMapping("pembatalan")
    public ResponseEntity<Response<List<BarisPembatalanKasir>>> pembatalan(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        return CommonResponse.data(
                pembatalan.pembatalanKasir(TenantContext.sekolahIdWajib(), tanggal, dari, sampai));
    }

    /**
     * Laporan <b>Penjualan per titik kasir</b> (PRD §9.5, issue #116): jumlah
     * transaksi, nilai, HPP, laba kotor per titik. RBAC Bendahara/Admin/Kepsek.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH,
            AktorKantin.PENGELOLA_KANTIN, AktorKantin.KEPSEK})
    @GetMapping("penjualan/titik")
    public ResponseEntity<Response<List<BarisPenjualanDimensi>>> penjualanPerTitik(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        List<BarisPenjualanDimensi> baris =
                penjualan.penjualanPerTitik(TenantContext.sekolahIdWajib(), tanggal, dari, sampai);
        return CommonResponse.data(bolehLihatLaba() ? baris : LaporanMasking.tanpaLabaDimensi(baris));
    }

    /**
     * Laporan <b>Penjualan per petugas</b> (PRD §9.5, issue #116): jumlah
     * transaksi, nilai, HPP, laba kotor per petugas. RBAC Bendahara/Admin/Kepsek.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH,
            AktorKantin.PENGELOLA_KANTIN, AktorKantin.KEPSEK})
    @GetMapping("penjualan/petugas")
    public ResponseEntity<Response<List<BarisPenjualanDimensi>>> penjualanPerPetugas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        List<BarisPenjualanDimensi> baris =
                penjualan.penjualanPerPetugas(TenantContext.sekolahIdWajib(), tanggal, dari, sampai);
        return CommonResponse.data(bolehLihatLaba() ? baris : LaporanMasking.tanpaLabaDimensi(baris));
    }

    /**
     * Penjualan <b>per kasir</b> — grouping per titik kasir + petugas
     * (PRD §9.5, issue #227). Tagihan FE {@code LaporanPenjualanPage}:
     * {@code laporanApi.getPenjualanPerKasir()}. Semantik bruto/void/bersih
     * konsisten dengan rekap sesi kasir.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH,
            AktorKantin.PENGELOLA_KANTIN, AktorKantin.KEPSEK})
    @GetMapping("penjualan/kasir")
    public ResponseEntity<Response<List<BarisPenjualanKasir>>> penjualanPerKasir(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        return CommonResponse.data(
                penjualan.penjualanPerKasir(TenantContext.sekolahIdWajib(), tanggal, dari, sampai));
    }

    /**
     * Laporan <b>Per siswa</b> (PRD §9.5, issue #117): riwayat lengkap satu
     * subjek ({@code SISWA}) pada periode — ringkasan + daftar transaksi +
     * mutasi saldo. Untuk menjawab komplain orang tua. RBAC Bendahara/Admin.
     *
     * <p>Admin Sekolah tidak berhak melihat HPP (PRD §9.5) — {@code totalHpp}
     * pada ringkasan dan tiap transaksi dikembalikan {@code null}.
     *
     * @param siswaId id siswa
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @GetMapping("per-siswa")
    public ResponseEntity<Response<LaporanPerSiswa>> perSiswa(
            @RequestParam Long siswaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        LaporanPerSiswa hasil = siswa.laporanPerSiswa(TenantContext.sekolahIdWajib(), siswaId,
                tanggal, dari, sampai);
        return CommonResponse.data(bolehLihatLaba() ? hasil : LaporanMasking.tanpaHpp(hasil));
    }

    /**
     * Riwayat satu <b>siswa</b> untuk komplain orang tua (issue #223) — bentuk
     * yang dipakai FE {@code LaporanRiwayatSiswaPage}: {@code {profil, riwayat}}.
     *
     * <p>Data diambil dari ledger/cache saldo milik kantin-be (bukan query baru).
     * Default rentang 30 hari terakhir bila {@code dari}/{@code sampai} kosong.
     * Identitas siswa (NIS/nama/kelas/orang tua) tidak tersedia di BE — ADR-0002.
     *
     * @param siswaId id siswa (harus punya saldo di sekolah pemanggil, jika tidak 404)
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @GetMapping("siswa/{siswaId}/riwayat")
    public ResponseEntity<Response<LaporanRiwayatSiswa>> riwayatSiswa(
            @PathVariable Long siswaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        return CommonResponse.data(
                siswa.riwayatSiswa(TenantContext.sekolahIdWajib(), siswaId, dari, sampai));
    }

    /**
     * Riwayat transaksi/mutasi saldo keseluruhan siswa (seluruh subjek) untuk
     * halaman Laporan Riwayat Transaksi (FE LaporanRiwayatKeseluruhanPage).
     *
     * <p><b>Kepsek ikut diizinkan (SEC-RIW-03, issue #398):</b> PRD §9.5
     * <b>belum punya baris eksplisit</b> untuk laporan ini; akses baca kepsek
     * ditambahkan atas permintaan DoD #398. Perlu konfirmasi PRD/PM karena baris
     * terdekat di §9.5 ("Per siswa") justru dibatasi Bendahara &amp; Admin.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH,
            AktorKantin.PENGELOLA_KANTIN, AktorKantin.KEPSEK})
    @GetMapping("transaksi-keseluruhan")
    public ResponseEntity<Response<List<LaporanRiwayatKeseluruhanItem>>> riwayatKeseluruhan(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai,
            @RequestParam(defaultValue = "100") int batas) {
        return CommonResponse.data(
                siswa.riwayatKeseluruhan(TenantContext.sekolahIdWajib(), tanggal, dari, sampai, batas));
    }


    /**
     * Laporan <b>Setoran kas TU</b> (PRD §9.5, issue #143): rekap top-up tunai
     * per petugas per hari + uang disetor + <b>selisih</b>. RBAC <b>Bendahara</b>
     * (PRD §9.5: hanya Bendahara) — kepsek <b>tidak</b> berhak. Tanpa kepsek.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH})
    @GetMapping("setoran-tu")
    public ResponseEntity<Response<List<RekapSetoranItem>>> setoranTu(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        return CommonResponse.data(
                setoran.laporanSetoran(TenantContext.sekolahIdWajib(), tanggal, dari, sampai));
    }

    /**
     * Laporan <b>Kartu stok per item</b> (PRD §9.5, issue #118): riwayat mutasi
     * satu menu (masuk, terjual, void, penyesuaian) + saldo stok berjalan.
     * RBAC Pengelola/Bendahara.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @GetMapping("kartu-stok/{menuId}")
    public ResponseEntity<Response<KartuStokItem>> kartuStok(
            @org.springframework.web.bind.annotation.PathVariable Long menuId,
            @RequestParam(required = false) Integer batas) {
        KartuStokItem kartu = stok.kartuStokItem(TenantContext.sekolahIdWajib(), menuId,
                batas == null ? 0 : batas);
        if (kartu == null) {
            throw new com.asqi.scholia_kantin_be.component.exception.NotFoundEntity(
                    "Menu tidak ditemukan");
        }
        return CommonResponse.data(kartu);
    }

    /**
     * Ekspor laporan ke Excel (.xlsx) — PRD §9.5 "semua laporan dapat diekspor".
     * Mengembalikan berkas unduhan langsung (bukan JSON).
     *
     * <p><b>SEC-04 (issue #391):</b> hak akses per jenis laporan ditegakkan lewat
     * {@link AksesLaporan} — sumber yang sama dengan endpoint data JSON. Dulu
     * hanya kepsek yang dibatasi, sehingga Admin Sekolah bisa mengunduh
     * {@code KERUGIAN_STOK} meski endpoint datanya menolaknya 403.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH,
            AktorKantin.PENGELOLA_KANTIN, AktorKantin.KEPSEK})
    @GetMapping("ekspor")
    public ResponseEntity<ByteArrayResource> ekspor(
            @RequestParam JenisLaporan jenis,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai,
            @RequestParam(required = false) Long menuId,
            @RequestParam(required = false) Long siswaId) {

        AktorKantin peran = peranSekarang();
        if (!AksesLaporan.boleh(jenis, peran)) {
            throw new ForbiddenException("Peran " + peran + " tidak berhak mengekspor laporan "
                    + jenis + " (PRD §9.5)");
        }

        LaporanExportService.HasilEkspor hasil = exportService.ekspor(
                TenantContext.sekolahIdWajib(), jenis, tanggal, dari, sampai, menuId,
                siswaId);

        MediaType xlsx = MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + hasil.namaBerkas() + "\"")
                .contentType(xlsx)
                .body(new ByteArrayResource(hasil.isi()));
    }

    /** Peran pemanggil saat ini; {@code null} bila konteks tidak tersedia (mis. tes unit). */
    private static AktorKantin peranSekarang() {
        IdentitasKantin identitas = TenantContext.get();
        return identitas == null ? null : identitas.getPeran();
    }

    /** PRD §9.5: modal/laba/margin hanya untuk Bendahara, Pengelola, dan Kepsek. */
    private static boolean bolehLihatLaba() {
        return LaporanMasking.bolehLihatLaba(peranSekarang());
    }
}
