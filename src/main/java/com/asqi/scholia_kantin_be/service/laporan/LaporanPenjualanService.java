package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.dto.BarisPenjualan;
import com.asqi.scholia_kantin_be.dto.BarisPenjualanDimensi;
import com.asqi.scholia_kantin_be.dto.BarisPenjualanKasir;
import com.asqi.scholia_kantin_be.dto.RingkasanPenjualan;
import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.repository.KategoriMenuRepository;
import com.asqi.scholia_kantin_be.repository.TitikKasirRepository;
import com.asqi.scholia_kantin_be.repository.TransaksiItemRepository;
import com.asqi.scholia_kantin_be.repository.TransaksiRepository;
import com.asqi.scholia_kantin_be.repository.projection.ItemTerlaris;
import com.asqi.scholia_kantin_be.repository.projection.PenjualanDimensi;
import com.asqi.scholia_kantin_be.repository.projection.PenjualanKasir;
import com.asqi.scholia_kantin_be.repository.projection.PenjualanKategori;
import com.asqi.scholia_kantin_be.repository.projection.RekapStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Laporan penjualan &amp; laba kotor (PRD §9.5): ringkasan, per item, per
 * kategori, per titik kasir, per petugas, per kasir.
 *
 * <p>Semua query <b>tenant-scoped</b> ({@code sekolahId} eksplisit) dan
 * <b>baca-saja</b> — tidak mengubah ledger. Periode memakai rentang
 * inklusif-dari, eksklusif-sampai (lihat {@link LaporanHelper#rentang}).
 */
@Service
@RequiredArgsConstructor
public class LaporanPenjualanService {

    /** Batas baris laporan item terlaris (top-N). */
    private static final int MAKS_ITEM_TERLARIS = 100;

    private final TransaksiRepository transaksiRepo;
    private final TransaksiItemRepository transaksiItemRepo;
    private final KategoriMenuRepository kategoriRepo;
    private final TitikKasirRepository titikKasirRepo;
    private final JamKantin jam;

    /**
     * Ringkasan penjualan &amp; laba kotor satu periode.
     *
     * @param sekolahId tenant pemanggil
     * @param tanggal   tanggal tunggal (opsional)
     * @param dari      awal periode eksplisit (opsional)
     * @param sampai    akhir periode eksplisit, eksklusif (opsional)
     * @return ringkasan jumlah SUKSES/VOID, bruto, modal (HPP), dan laba kotor
     */
    @Transactional(readOnly = true)
    public RingkasanPenjualan ringkasanPenjualan(Long sekolahId, LocalDate tanggal,
                                                 OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = LaporanHelper.rentang(jam, tanggal, dari, sampai);
        OffsetDateTime awal = r[0];
        OffsetDateTime akhir = r[1];

        long jumlahSukses = 0, bruto = 0, jumlahVoid = 0, nilaiVoid = 0;
        for (RekapStatus baris : transaksiRepo.rekapPerStatusRentang(sekolahId, awal, akhir)) {
            StatusTransaksi status = baris.getStatus();
            long jumlah = baris.getJumlah();
            long total = baris.getTotal();
            if (status == StatusTransaksi.SUKSES) {
                jumlahSukses = jumlah;
                bruto = total;
            } else if (status == StatusTransaksi.VOID) {
                jumlahVoid = jumlah;
                nilaiVoid = total;
            }
        }
        long modal = LaporanHelper.nolJikaNull(transaksiRepo.totalModalRentang(
                sekolahId, StatusTransaksi.SUKSES, awal, akhir));
        long laba = bruto - modal;

        return new RingkasanPenjualan(awal, akhir, jumlahSukses, bruto,
                jumlahVoid, nilaiVoid, bruto, modal, laba, LaporanHelper.persen(laba, bruto));
    }

    /**
     * Penjualan per item (PRD §9.5).
     *
     * @return baris terlaris (maks. {@value #MAKS_ITEM_TERLARIS}) beserta qty,
     *     nilai, modal, dan laba per item
     */
    @Transactional(readOnly = true)
    public List<BarisPenjualan> penjualanPerItem(Long sekolahId, LocalDate tanggal,
                                                 OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = LaporanHelper.rentang(jam, tanggal, dari, sampai);
        List<BarisPenjualan> hasil = new ArrayList<>();
        for (ItemTerlaris b : transaksiItemRepo.itemTerlaris(
                sekolahId, r[0], r[1], PageRequest.of(0, MAKS_ITEM_TERLARIS))) {
            long qty = LaporanHelper.nolJikaNull(b.getQty());
            long total = LaporanHelper.nolJikaNull(b.getSubtotal());
            long modal = LaporanHelper.nolJikaNull(b.getModal());
            hasil.add(new BarisPenjualan(
                    b.getMenuId(), b.getNamaMenu(), qty, total,
                    qty == 0 ? null : total / qty, modal, LaporanHelper.persen(total - modal, total)));
        }
        return hasil;
    }

    /**
     * Penjualan per kategori (PRD §9.5) — nama kategori diisi dari katalog.
     *
     * @return baris per kategori; kategori yang sudah tidak ada bernama kosong
     */
    @Transactional(readOnly = true)
    public List<BarisPenjualan> penjualanPerKategori(Long sekolahId, LocalDate tanggal,
                                                     OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = LaporanHelper.rentang(jam, tanggal, dari, sampai);
        Map<Long, String> namaKategori = new HashMap<>();
        kategoriRepo.daftar(sekolahId, false)
                .forEach(k -> namaKategori.put(k.getId(), k.getNama()));

        List<BarisPenjualan> hasil = new ArrayList<>();
        for (PenjualanKategori b : transaksiItemRepo.penjualanPerKategori(sekolahId, r[0], r[1])) {
            Long kategoriId = b.getKategoriId();
            long qty = LaporanHelper.nolJikaNull(b.getQty());
            long total = LaporanHelper.nolJikaNull(b.getSubtotal());
            long modal = LaporanHelper.nolJikaNull(b.getModal());
            hasil.add(new BarisPenjualan(
                    kategoriId, namaKategori.get(kategoriId), qty, total,
                    null, modal, LaporanHelper.persen(total - modal, total)));
        }
        return hasil;
    }

    /**
     * Penjualan per <b>titik kasir</b> pada periode (PRD §9.5, issue #116).
     * Nama titik diresolusi dari katalog titik kasir.
     *
     * @return baris per titik kasir, urut nilai menurun
     */
    @Transactional(readOnly = true)
    public List<BarisPenjualanDimensi> penjualanPerTitik(Long sekolahId, LocalDate tanggal,
                                                         OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = LaporanHelper.rentang(jam, tanggal, dari, sampai);
        Map<Long, String> namaTitik = new HashMap<>();
        titikKasirRepo.findBySekolahId(sekolahId)
                .forEach(t -> namaTitik.put(t.getId(), t.getNama()));

        List<BarisPenjualanDimensi> hasil = new ArrayList<>();
        for (PenjualanDimensi b : transaksiRepo.penjualanPerTitikRentang(sekolahId, r[0], r[1])) {
            Long id = b.getDimensiId();
            long jumlah = LaporanHelper.nolJikaNull(b.getJumlah());
            long nilai = LaporanHelper.nolJikaNull(b.getNilai());
            long hpp = LaporanHelper.nolJikaNull(b.getHpp());
            hasil.add(new BarisPenjualanDimensi(id, namaTitik.get(id), jumlah, nilai, hpp, nilai - hpp));
        }
        return hasil;
    }

    /**
     * Penjualan per <b>petugas</b> pada periode (PRD §9.5, issue #116).
     * Nama petugas tidak tersedia di kantin-be (ada di admin-be) → {@code null}.
     *
     * @return baris per petugas, urut nilai menurun
     */
    @Transactional(readOnly = true)
    public List<BarisPenjualanDimensi> penjualanPerPetugas(Long sekolahId, LocalDate tanggal,
                                                           OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = LaporanHelper.rentang(jam, tanggal, dari, sampai);
        List<BarisPenjualanDimensi> hasil = new ArrayList<>();
        for (PenjualanDimensi b : transaksiRepo.penjualanPerPetugasRentang(sekolahId, r[0], r[1])) {
            Long id = b.getDimensiId();
            long jumlah = LaporanHelper.nolJikaNull(b.getJumlah());
            long nilai = LaporanHelper.nolJikaNull(b.getNilai());
            long hpp = LaporanHelper.nolJikaNull(b.getHpp());
            hasil.add(new BarisPenjualanDimensi(id, null, jumlah, nilai, hpp, nilai - hpp));
        }
        return hasil;
    }

    /**
     * Penjualan <b>per kasir</b> — grouping per titik kasir + petugas
     * (PRD §9.5, issue #227). Kontrak FE {@code BarisPenjualanKasir}: daftar
     * kasir di {@code LaporanPenjualanPage.tsx}.
     *
     * <p>Semantik mengikuti rekap sesi kasir ({@code
     * SesiKasirService.hitungRekap}) agar Σ {@code penjualanBersih} konsisten
     * dengan {@code totalBersih} sesi pada periode yang sama: {@code
     * penjualanBruto} mencakup transaksi VOID (uang yang menyentuh kasir),
     * {@code nilaiVoid} memisahkannya, dan {@code penjualanBersih} =
     * bruto − void = Σ SUKSES. Nama petugas tidak tersedia di kantin-be →
     * field {@code petugas} diisi id petugas (String) sebagai placeholder.
     *
     * @return baris per (titik kasir, petugas), urut titik lalu petugas
     */
    @Transactional(readOnly = true)
    public List<BarisPenjualanKasir> penjualanPerKasir(Long sekolahId, LocalDate tanggal,
                                                       OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = LaporanHelper.rentang(jam, tanggal, dari, sampai);
        Map<Long, String> namaTitik = new HashMap<>();
        titikKasirRepo.findBySekolahId(sekolahId)
                .forEach(t -> namaTitik.put(t.getId(), t.getNama()));

        List<BarisPenjualanKasir> hasil = new ArrayList<>();
        for (PenjualanKasir b : transaksiRepo.penjualanPerKasirRentang(sekolahId, r[0], r[1])) {
            Long titikId = b.getTitikKasirId();
            Long petugasId = b.getPetugasId();
            long jumlahSukses = LaporanHelper.nolJikaNull(b.getJumlahSukses());
            long nilaiSukses = LaporanHelper.nolJikaNull(b.getNilaiSukses());
            long nilaiVoid = LaporanHelper.nolJikaNull(b.getNilaiVoid());
            hasil.add(new BarisPenjualanKasir(
                    titikId, namaTitik.get(titikId), petugasId,
                    String.valueOf(petugasId), jumlahSukses,
                    nilaiSukses + nilaiVoid, nilaiVoid, nilaiSukses));
        }
        return hasil;
    }
}