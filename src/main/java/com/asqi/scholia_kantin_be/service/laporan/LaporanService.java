package com.asqi.scholia_kantin_be.service.laporan;

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
import com.asqi.scholia_kantin_be.dto.RingkasanPenjualan;
import com.asqi.scholia_kantin_be.dto.RingkasanRekonsiliasi;
import com.asqi.scholia_kantin_be.dto.RingkasanKerugianStok;
import com.asqi.scholia_kantin_be.dto.RingkasanSaldoMengendap;
import com.asqi.scholia_kantin_be.dto.SiswaSaldoDetail;
import com.asqi.scholia_kantin_be.service.integrasi.InfoSiswa;
import com.asqi.scholia_kantin_be.enums.ArahMutasi;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;
import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.enums.JenisPembatasan;
import com.asqi.scholia_kantin_be.model.Pembatasan;
import com.asqi.scholia_kantin_be.model.KategoriMenu;
import com.asqi.scholia_kantin_be.model.Menu;
import com.asqi.scholia_kantin_be.model.RiwayatStok;
import com.asqi.scholia_kantin_be.model.Saldo;
import com.asqi.scholia_kantin_be.model.MutasiSaldo;
import com.asqi.scholia_kantin_be.model.Stok;
import com.asqi.scholia_kantin_be.model.Transaksi;
import com.asqi.scholia_kantin_be.model.TransaksiItem;
import com.asqi.scholia_kantin_be.repository.KategoriMenuRepository;
import com.asqi.scholia_kantin_be.repository.PembatasanRepository;
import com.asqi.scholia_kantin_be.repository.MenuRepository;
import com.asqi.scholia_kantin_be.repository.RiwayatStokRepository;
import com.asqi.scholia_kantin_be.repository.SaldoRepository;
import com.asqi.scholia_kantin_be.repository.MutasiSaldoRepository;
import com.asqi.scholia_kantin_be.repository.StokRepository;
import com.asqi.scholia_kantin_be.repository.TransaksiItemRepository;
import com.asqi.scholia_kantin_be.repository.TransaksiRepository;
import com.asqi.scholia_kantin_be.service.stok.HppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Laporan &amp; agregasi (PRD §9.5). Semua query <b>tenant-scoped</b>
 * ({@code sekolahId} eksplisit) dan <b>baca-saja</b> — tidak mengubah ledger.
 *
 * <p>Bila {@code dari}/{@code sampai} {@code null}, periode dianggap
 * <b>hari ini</b> menurut zona sekolah ({@link JamKantin}). Laporan berbasis
 * periode memakai rentang <b>inklusif-dari, eksklusif-sampai</b>.
 *
 * <p><b>Invariant rekonsiliasi (PRD §5):</b> {@link #rekonsiliasi} memeriksa
 * {@code Σ KREDIT − Σ DEBIT == saldo mengendap}. Bila tidak sama → peringatan.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LaporanService {

    /** Batas baris laporan item terlaris (top-N). */
    private static final int MAKS_ITEM_TERLARIS = 100;

    /** Rentang default riwayat siswa bila FE tidak mengirim dari/sampai. */
    private static final int HARI_RIWAYAT_DEFAULT = 30;

    private static final Set<JenisMutasiSaldo> JENIS_KOREKSI =
            EnumSet.of(JenisMutasiSaldo.KOREKSI, JenisMutasiSaldo.PENYESUAIAN);

    private final MutasiSaldoRepository saldoLedgerRepo;
    private final SaldoRepository saldoCacheRepo;
    private final TransaksiRepository transaksiRepo;
    private final TransaksiItemRepository transaksiItemRepo;
    private final RiwayatStokRepository mutasiStokRepo;
    private final StokRepository stokCacheRepo;
    private final MenuRepository menuRepo;
    private final KategoriMenuRepository kategoriRepo;
    private final com.asqi.scholia_kantin_be.repository.TitikKasirRepository titikKasirRepo;
    private final HppService hppService;
    private final JamKantin jam;
    private final PembatasanRepository pembatasanRepo;
    private final com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService ledgerSaldo;
    private final com.asqi.scholia_kantin_be.service.saldo.SetoranService setoranTuService;
    private final com.asqi.scholia_kantin_be.service.CariSiswaService cariSiswaService;

    // ────────────────────────────────────────────────────────────────
    // Periode
    // ────────────────────────────────────────────────────────────────

    /** Rentang waktu efektif: default hari ini (zona sekolah) bila null. */
    private OffsetDateTime[] rentang(LocalDate tanggal, OffsetDateTime dari, OffsetDateTime sampai) {
        LocalDate tgl = (tanggal != null) ? tanggal : jam.hariIni();
        OffsetDateTime awal = (dari != null) ? dari : tgl.atStartOfDay(jam.zona()).toOffsetDateTime();
        OffsetDateTime akhir = (sampai != null) ? sampai : awal.plusDays(1);
        return new OffsetDateTime[]{awal, akhir};
    }

    // ────────────────────────────────────────────────────────────────
    // Penjualan & laba kotor (PRD §9.5)
    // ────────────────────────────────────────────────────────────────

    /** Ringkasan penjualan &amp; laba kotor satu periode. */
    @Transactional(readOnly = true)
    public RingkasanPenjualan ringkasanPenjualan(Long sekolahId, LocalDate tanggal,
                                                 OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = rentang(tanggal, dari, sampai);
        OffsetDateTime awal = r[0];
        OffsetDateTime akhir = r[1];

        long jumlahSukses = 0, bruto = 0, jumlahVoid = 0, nilaiVoid = 0;
        for (Object[] baris : transaksiRepo.rekapPerStatusRentang(sekolahId, awal, akhir)) {
            StatusTransaksi status = (StatusTransaksi) baris[0];
            long jumlah = ((Number) baris[1]).longValue();
            long total = ((Number) baris[2]).longValue();
            if (status == StatusTransaksi.SUKSES) {
                jumlahSukses = jumlah;
                bruto = total;
            } else if (status == StatusTransaksi.VOID) {
                jumlahVoid = jumlah;
                nilaiVoid = total;
            }
        }
        long modal = nolJikaNull(transaksiRepo.totalModalRentang(
                sekolahId, StatusTransaksi.SUKSES, awal, akhir));
        long laba = bruto - modal;

        return new RingkasanPenjualan(awal, akhir, jumlahSukses, bruto,
                jumlahVoid, nilaiVoid, bruto, modal, laba, persen(laba, bruto));
    }

    /** Persentase (2 desimal); 0 bila pembagi nol. */
    private static double persen(long bagian, long total) {
        if (total == 0) {
            return 0.0;
        }
        return Math.round(bagian * 10_000.0 / total) / 100.0;
    }

    /** Penjualan per item (PRD §9.5). */
    @Transactional(readOnly = true)
    public List<BarisPenjualan> penjualanPerItem(Long sekolahId, LocalDate tanggal,
                                                 OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = rentang(tanggal, dari, sampai);
        List<BarisPenjualan> hasil = new ArrayList<>();
        for (Object[] b : transaksiItemRepo.itemTerlaris(
                sekolahId, r[0], r[1], PageRequest.of(0, MAKS_ITEM_TERLARIS))) {
            long qty = ((Number) b[2]).longValue();
            long total = ((Number) b[3]).longValue();
            long modal = b.length > 4 && b[4] != null ? ((Number) b[4]).longValue() : 0L;
            hasil.add(new BarisPenjualan(
                    (Long) b[0], (String) b[1], qty, total,
                    qty == 0 ? null : total / qty, modal, persen(total - modal, total)));
        }
        return hasil;
    }

    /** Penjualan per kategori (PRD §9.5) — nama kategori diisi dari katalog. */
    @Transactional(readOnly = true)
    public List<BarisPenjualan> penjualanPerKategori(Long sekolahId, LocalDate tanggal,
                                                     OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = rentang(tanggal, dari, sampai);
        Map<Long, String> namaKategori = new HashMap<>();
        kategoriRepo.daftar(sekolahId, false)
                .forEach(k -> namaKategori.put(k.getId(), k.getNama()));

        List<BarisPenjualan> hasil = new ArrayList<>();
        for (Object[] b : transaksiItemRepo.penjualanPerKategori(sekolahId, r[0], r[1])) {
            Long kategoriId = (Long) b[0];
            long qty = ((Number) b[1]).longValue();
            long total = ((Number) b[2]).longValue();
            long modal = b.length > 3 && b[3] != null ? ((Number) b[3]).longValue() : 0L;
            hasil.add(new BarisPenjualan(
                    kategoriId, namaKategori.get(kategoriId), qty, total,
                    null, modal, persen(total - modal, total)));
        }
        return hasil;
    }

    // ────────────────────────────────────────────────────────────────
    // Saldo mengendap (PRD §9.5)
    // ────────────────────────────────────────────────────────────────

    /** Total dana titipan (kewajiban sekolah) — seluruhnya saldo siswa. */
    @Transactional(readOnly = true)
    public RingkasanSaldoMengendap saldoMengendap(Long sekolahId) {
        long saldo = 0, jumlah = 0;
        for (Object[] b : saldoCacheRepo.totalMengendap(sekolahId)) {
            saldo = ((Number) b[0]).longValue();
            jumlah = ((Number) b[1]).longValue();
        }
        return new RingkasanSaldoMengendap(saldo, saldo, jumlah);
    }

    /**
     * Rincian saldo mengendap per siswa (issue #224).
     * Identitas dicari melalui CariSiswaPort; saldo/kontrol dibaca tenant-lokal.
     */
    @Transactional(readOnly = true)
    public List<SiswaSaldoDetail> saldoMengendapSiswa(Long sekolahId, String search, int batas) {
        List<com.asqi.scholia_kantin_be.service.integrasi.InfoSiswa> siswaList =
                cariSiswaService.cari(sekolahId, search, false, batas);
        List<Long> siswaIds = siswaList.stream()
                .map(com.asqi.scholia_kantin_be.service.integrasi.InfoSiswa::getSiswaId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, OffsetDateTime> waktuTerakhir = new HashMap<>();
        if (!siswaIds.isEmpty()) {
            saldoLedgerRepo.waktuTerakhirPerSubjek(sekolahId, siswaIds)
                    .forEach(baris -> waktuTerakhir.put(
                            ((Number) baris[0]).longValue(), (OffsetDateTime) baris[1]));
        }
        return siswaList.stream()
                .map(siswa -> {
                    Long siswaId = siswa.getSiswaId();
                    Saldo cache = saldoCacheRepo
                            .findBySekolahIdAndSiswaId(sekolahId, siswaId)
                            .orElse(null);
                    List<Pembatasan> pembatasan = pembatasanRepo
                            .findBySekolahIdAndSiswaId(sekolahId, siswaId);
                    boolean diblokir = pembatasan.stream()
                            .anyMatch(x -> x.getJenis() == JenisPembatasan.BLOKIR_SISWA && x.isAktif());
                    Long limit = pembatasan.stream()
                            .filter(x -> x.getJenis() == JenisPembatasan.LIMIT_HARIAN && x.isAktif())
                            .map(Pembatasan::getNominal)
                            .findFirst()
                            .orElse(null);
                    return new SiswaSaldoDetail(
                            siswaId, siswa.getNis(), null, siswa.getNama(), siswa.getKelas(),
                            cache == null || cache.getSaldo() == null ? 0L : cache.getSaldo(),
                            ledgerSaldo.belanjaHariIni(sekolahId, siswaId),
                            limit, diblokir, null, null,
                            waktuTerakhir.get(siswaId));
                })
                .toList();
    }

    // ────────────────────────────────────────────────────────────────
    // Rekonsiliasi harian (PRD §9.5, invariant §5)
    // ────────────────────────────────────────────────────────────────

    /** Arus saldo periode + pemeriksaan invariant menyeluruh. */
    @Transactional(readOnly = true)
    public RingkasanRekonsiliasi rekonsiliasi(Long sekolahId, LocalDate tanggal,
                                              OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = rentang(tanggal, dari, sampai);
        OffsetDateTime awal = r[0];
        OffsetDateTime akhir = r[1];

        long topupOnline = 0, topupTunai = 0, penjualan = 0, voidPenjualan = 0;
        long koreksiMasuk = 0, koreksiKeluar = 0, refund = 0, transfer = 0;

        for (Object[] b : saldoLedgerRepo.rekapArusRentang(sekolahId, awal, akhir)) {
            ArahMutasi arah = (ArahMutasi) b[0];
            JenisMutasiSaldo jenis = (JenisMutasiSaldo) b[1];
            long nominal = ((Number) b[2]).longValue();
            switch (jenis) {
                case TOPUP_ONLINE -> topupOnline += nominal;
                case TOPUP_TUNAI -> topupTunai += nominal;
                case PENJUALAN -> penjualan += nominal;
                case VOID_PENJUALAN -> voidPenjualan += nominal;
                case REFUND -> refund += nominal;
                case TRANSFER -> transfer += nominal;
                case KOREKSI, PENYESUAIAN -> {
                    if (arah == ArahMutasi.KREDIT) {
                        koreksiMasuk += nominal;
                    } else {
                        koreksiKeluar += nominal;
                    }
                }
                default -> { /* tidak ada */ }
            }
        }

        long totalKredit = 0, totalDebit = 0;
        for (Object[] b : saldoLedgerRepo.totalPerArah(sekolahId)) {
            ArahMutasi arah = (ArahMutasi) b[0];
            long nominal = ((Number) b[1]).longValue();
            if (arah == ArahMutasi.KREDIT) {
                totalKredit = nominal;
            } else {
                totalDebit = nominal;
            }
        }

        long mengendap = saldoMengendap(sekolahId).total();
        long selisih = (totalKredit - totalDebit) - mengendap;

        return new RingkasanRekonsiliasi(awal, akhir, topupOnline, topupTunai,
                penjualan, voidPenjualan, koreksiMasuk, koreksiKeluar, refund,
                transfer, mengendap, totalKredit, totalDebit, selisih, selisih == 0);
    }

    // ────────────────────────────────────────────────────────────────
    // Stok (PRD §9.5)
    // ────────────────────────────────────────────────────────────────

    /** Stok sekarang + nilai persediaan (stok × HPP) untuk semua menu. */
    @Transactional(readOnly = true)
    public List<BarisStok> laporanStok(Long sekolahId, boolean hanyaMenipis) {
        // Sumber kebenaran nama, kategori & stok minimum = katalog menu (PRD §7.1).
        // (stok_cache.stok_minimum tidak disinkron dari menu, jadi jangan dipakai.)
        Map<Long, com.asqi.scholia_kantin_be.model.Menu> menuById = new HashMap<>();
        menuRepo.findAll(MenuRepository.daftarSpec(sekolahId, null, false)).forEach(m -> menuById.put(m.getId(), m));

        List<BarisStok> hasil = new ArrayList<>();
        for (Stok s : stokCacheRepo.findBySekolahIdAndMenuIdIn(
                sekolahId, menuById.keySet().isEmpty() ? Set.of(-1L) : menuById.keySet())) {
            var menu = menuById.get(s.getMenuId());
            if (menu == null) {
                continue; // baris cache tanpa menu (yatim) — lewati
            }
            int stokMinimum = menu.getStokMinimum() == null ? 0 : menu.getStokMinimum();
            boolean menipis = s.getStok() <= stokMinimum;
            if (hanyaMenipis && !menipis) {
                continue;
            }
            hasil.add(new BarisStok(s.getMenuId(), menu.getNama(), menu.getKategoriId(),
                    s.getStok(), stokMinimum, s.getHpp(),
                    hppService.nilaiPersediaan(s.getStok(), s.getHpp()), menipis,
                    menu.getNama(), s.getStok()));
        }
        return hasil;
    }

    /** Kerugian stok: opname keluar &amp; barang rusak pada periode (PRD §9.5). */
    @Transactional(readOnly = true)
    public List<BarisKerugianStok> kerugianStok(Long sekolahId, LocalDate tanggal,
                                                OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = rentang(tanggal, dari, sampai);
        List<RiwayatStok> mutasi = mutasiStokRepo.kerugianRentang(
                sekolahId, EnumSet.of(JenisMutasiStok.OPNAME_KELUAR, JenisMutasiStok.BARANG_RUSAK),
                r[0], r[1]);

        if (mutasi.isEmpty()) {
            return List.of();
        }

        Set<Long> menuIds = mutasi.stream().map(RiwayatStok::getMenuId).collect(Collectors.toSet());
        List<Menu> menus = menuRepo.findAllById(menuIds);
        Map<Long, Menu> menuMap = menus.stream()
                .filter(m -> m.getSekolahId().equals(sekolahId))
                .collect(Collectors.toMap(Menu::getId, m -> m, (a, b) -> a));

        Set<Long> kategoriIds = menus.stream()
                .map(Menu::getKategoriId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> kategoriMap = kategoriRepo.findAllById(kategoriIds).stream()
                .filter(k -> k.getSekolahId().equals(sekolahId))
                .collect(Collectors.toMap(KategoriMenu::getId, KategoriMenu::getNama, (a, b) -> a));

        List<BarisKerugianStok> hasil = new ArrayList<>();
        for (RiwayatStok m : mutasi) {
            Menu menu = menuMap.get(m.getMenuId());
            String namaMenu = menu != null ? menu.getNama() : "Menu #" + m.getMenuId();
            String kategoriNama = (menu != null && menu.getKategoriId() != null)
                    ? kategoriMap.getOrDefault(menu.getKategoriId(), "Umum")
                    : "Umum";
            long hpp = m.getHppSnapshot() != null ? m.getHppSnapshot() : 0L;
            long totalNilai = m.getQty() * hpp;
            String petugas = m.getAktorNama() != null ? m.getAktorNama() : "Petugas";

            hasil.add(new BarisKerugianStok(
                    m.getId(),
                    m.getWaktu(),
                    m.getMenuId(),
                    namaMenu,
                    kategoriNama,
                    m.getJenis().name(),
                    m.getQty(),
                    hpp,
                    totalNilai,
                    m.getAlasan() != null ? m.getAlasan() : "-",
                    m.getReferensiId() != null ? m.getReferensiId() : "-",
                    petugas));
        }
        return hasil;
    }

    /** Ringkasan kerugian stok dari agregasi yang sama dengan endpoint daftar (#226). */
    @Transactional(readOnly = true)
    public RingkasanKerugianStok ringkasanKerugianStok(Long sekolahId, LocalDate tanggal,
                                                       OffsetDateTime dari, OffsetDateTime sampai) {
        List<BarisKerugianStok> baris = kerugianStok(sekolahId, tanggal, dari, sampai);
        long kerugianOpname = baris.stream().filter(b -> b.jenis().equals(JenisMutasiStok.OPNAME_KELUAR.name()))
                .mapToLong(BarisKerugianStok::totalNilai).sum();
        long qtyOpname = baris.stream().filter(b -> b.jenis().equals(JenisMutasiStok.OPNAME_KELUAR.name()))
                .mapToLong(BarisKerugianStok::totalQty).sum();
        long kerugianRusak = baris.stream().filter(b -> b.jenis().equals(JenisMutasiStok.BARANG_RUSAK.name()))
                .mapToLong(BarisKerugianStok::totalNilai).sum();
        long qtyRusak = baris.stream().filter(b -> b.jenis().equals(JenisMutasiStok.BARANG_RUSAK.name()))
                .mapToLong(BarisKerugianStok::totalQty).sum();
        long jumlahKejadian = baris.stream().mapToLong(BarisKerugianStok::jumlahBaris).sum();
        return new RingkasanKerugianStok(kerugianOpname + kerugianRusak, qtyOpname + qtyRusak,
                jumlahKejadian, kerugianOpname, qtyOpname, kerugianRusak, qtyRusak);
    }

    /** Barang masuk pada periode (PRD §9.5). */
    @Transactional(readOnly = true)
    public List<RiwayatStok> barangMasuk(Long sekolahId, LocalDate tanggal,
                                        OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = rentang(tanggal, dari, sampai);
        return mutasiStokRepo.barangMasukRentang(sekolahId, r[0], r[1]);
    }

    /** Kartu stok per item: riwayat mutasi satu menu (PRD §9.5). */
    @Transactional(readOnly = true)
    public List<RiwayatStok> kartuStok(Long sekolahId, Long menuId, int batas) {
        int limit = (batas <= 0 || batas > 500) ? 200 : batas;
        return mutasiStokRepo.kartuStok(sekolahId, menuId, PageRequest.of(0, limit));
    }

    /**
     * Laporan <b>Kartu stok per item</b> (PRD §9.5, issue #118): riwayat mutasi
     * satu menu + saldo stok berjalan. Menu divalidasi tenant-scoped — bila menu
     * bukan milik sekolah, kembalikan {@code null} (handler → 404).
     */
    @Transactional(readOnly = true)
    public KartuStokItem kartuStokItem(Long sekolahId, Long menuId, int batas) {
        var menu = menuRepo.findByIdAndSekolahId(menuId, sekolahId).orElse(null);
        if (menu == null) {
            return null;
        }
        int limit = (batas <= 0 || batas > 500) ? 200 : batas;
        List<RiwayatStok> mutasi = mutasiStokRepo.kartuStok(sekolahId, menuId, PageRequest.of(0, limit));
        int stokSekarang = stokCacheRepo.findByMenuId(menuId)
                .filter(s -> s.getSekolahId().equals(sekolahId))
                .map(s -> s.getStok() == null ? 0 : s.getStok())
                .orElse(0);
        return new KartuStokItem(menuId, menu.getNama(),
                menu.getSatuan() == null ? null : menu.getSatuan().name(),
                stokSekarang,
                mutasi.stream().map(KartuStokItem.BarisMutasi::dari).toList());
    }

    // ────────────────────────────────────────────────────────────────
    // Pembatalan kasir (PRD §9.5, issue #114)
    // ────────────────────────────────────────────────────────────────

    /**
     * Laporan <b>Pembatalan kasir</b> (PRD §9.5): daftar transaksi yang
     * dibatalkan (status {@code VOID}) pada periode, terbaru dulu. Mencakup
     * alasan void, waktu, petugas, &amp; subjek — untuk audit sengketa
     * "Kartu dipakai bukan pemiliknya".
     */
    @Transactional(readOnly = true)
    public List<BarisPembatalanKasir> pembatalanKasir(Long sekolahId, LocalDate tanggal,
                                                      OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = rentang(tanggal, dari, sampai);
        return transaksiRepo.pembatalanRentang(sekolahId, r[0], r[1])
                .stream()
                .map(BarisPembatalanKasir::dari)
                .toList();
    }

    /** Riwayat saldo satu subjek pada periode (laporan per siswa, PRD §9.5). */
    @Transactional(readOnly = true)
    public List<MutasiSaldo> mutasiSaldoRentang(Long sekolahId, LocalDate tanggal,
                                                OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = rentang(tanggal, dari, sampai);
        return saldoLedgerRepo.padaRentang(sekolahId, r[0], r[1]);
    }

    /**
     * Riwayat satu siswa untuk komplain orang tua (issue #223): profil + seluruh
     * mutasi saldo pada rentang, terbaru lebih dulu, dengan rincian belanja.
     *
     * <p><b>Tenant guard.</b> Siswa dianggap ada hanya bila punya baris
     * {@code saldo_cache} di sekolah pemanggil — id siswa milik sekolah lain
     * menghasilkan <b>404</b>. Default rentang 30 hari terakhir bila FE tidak
     * mengirim {@code dari}/{@code sampai}.
     *
     * <p>Identitas siswa (NIS/nama/kelas/orang tua) tidak disimpan kantin-be
     * (ADR-0002) sehingga bernilai {@code null}; lihat {@link LaporanRiwayatSiswa}.
     */
    @Transactional(readOnly = true)
    public LaporanRiwayatSiswa riwayatSiswa(Long sekolahId, Long siswaId,
                                            OffsetDateTime dari, OffsetDateTime sampai) {
        Saldo cache = saldoCacheRepo.findBySekolahIdAndSiswaId(sekolahId, siswaId)
                .orElseThrow(() -> new NotFoundEntity("Siswa " + siswaId + " tidak ditemukan"));

        OffsetDateTime akhir = sampai != null ? sampai : OffsetDateTime.now(jam.zona());
        OffsetDateTime awal = dari != null ? dari : akhir.minusDays(HARI_RIWAYAT_DEFAULT);

        List<MutasiSaldo> mutasi = new ArrayList<>(
                saldoLedgerRepo.padaRentangSubjek(sekolahId, siswaId, awal, akhir));
        mutasi.sort(Comparator.comparing(MutasiSaldo::getWaktu).reversed()
                .thenComparing(MutasiSaldo::getId, Comparator.reverseOrder()));

        Set<Long> transaksiIds = mutasi.stream()
                .map(MutasiSaldo::getTransaksiId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Transaksi> transaksiById = transaksiIds.isEmpty() ? Map.of()
                : transaksiRepo.findAllById(transaksiIds).stream()
                .filter(t -> sekolahId.equals(t.getSekolahId()))
                .collect(Collectors.toMap(Transaksi::getId, Function.identity()));

        Map<Long, String> namaTitik = new HashMap<>();
        titikKasirRepo.findBySekolahId(sekolahId).forEach(t -> namaTitik.put(t.getId(), t.getNama()));

        Map<Long, List<TransaksiItem>> itemByTrx = transaksiIds.isEmpty() ? Map.of()
                : transaksiItemRepo.findByTransaksiIdIn(transaksiIds).stream()
                .collect(Collectors.groupingBy(i -> i.getTransaksi().getId()));

        List<LaporanRiwayatSiswa.Baris> riwayat = mutasi.stream().map(l -> {
            Transaksi t = l.getTransaksiId() == null ? null : transaksiById.get(l.getTransaksiId());
            List<LaporanRiwayatSiswa.Item> items = t == null ? List.of()
                    : itemByTrx.getOrDefault(t.getId(), List.of()).stream()
                    .map(i -> new LaporanRiwayatSiswa.Item(i.getMenuId(), i.getNamaMenu(),
                            i.getQty(), i.getHargaJual(), i.getSubtotal()))
                    .toList();

            return new LaporanRiwayatSiswa.Baris(
                    String.valueOf(l.getId()),
                    l.getWaktu(),
                    jenisRiwayat(l.getJenis()),
                    l.getArah() == null ? null : l.getArah().name(),
                    l.getNominal() == null ? 0L : l.getNominal(),
                    l.getSaldoSetelah() == null ? 0L : l.getSaldoSetelah(),
                    t == null ? "-" : namaTitik.getOrDefault(t.getTitikKasirId(), "-"),
                    "-",   // nama petugas tidak ada di kantin-be (ADR-0002)
                    t == null ? String.valueOf(l.getId()) : String.valueOf(t.getId()),
                    l.getKeterangan() == null ? "" : l.getKeterangan(),
                    items);
        }).toList();

        List<Pembatasan> pembatasan = pembatasanRepo
                .findBySekolahIdAndSiswaId(sekolahId, siswaId);
        long limit = pembatasan.stream()
                .filter(x -> x.getJenis() == JenisPembatasan.LIMIT_HARIAN && x.isAktif())
                .map(Pembatasan::getNominal)
                .findFirst()
                .orElse(0L);
        Pembatasan blokir = pembatasan.stream()
                .filter(x -> x.getJenis() == JenisPembatasan.BLOKIR_SISWA && x.isAktif())
                .findFirst().orElse(null);
        boolean diblokir = blokir != null;

        // Identitas siswa (NIS/nama/kelas) berasal dari admin-be lewat lookup per ID
        // (issue #297). Fail-open: bila integrasi belum aktif / siswa tidak ditemukan,
        // profil tetap dikembalikan dengan identitas null dan riwayat tetap tampil.
        InfoSiswa identitas = null;
        try {
            identitas = cariSiswaService.identitas(sekolahId, siswaId).orElse(null);
        } catch (RuntimeException e) {
            log.warn("Identitas siswa {} tidak dapat diambil (riwayat tetap dikirim): {}",
                    siswaId, e.getMessage());
        }

        LaporanRiwayatSiswa.Profil profil = new LaporanRiwayatSiswa.Profil(
                siswaId,
                identitas == null ? null : identitas.getNis(),
                identitas == null ? null : identitas.getNama(),
                identitas == null ? null : identitas.getKelas(),
                identitas == null ? null : identitas.getFotoUrl(),
                cache.getSaldo() == null ? 0L : cache.getSaldo(),
                ledgerSaldo.belanjaHariIni(sekolahId, siswaId),
                limit,
                diblokir,
                identitas == null ? null : identitas.getNamaOrangTua(),
                identitas == null ? null : identitas.getTeleponOrangTua(),
                diblokir ? blokir.getAlasan() : null);

        return new LaporanRiwayatSiswa(profil, riwayat);
    }

    /**
     * Riwayat transaksi/mutasi saldo keseluruhan siswa untuk halaman Laporan Riwayat Transaksi.
     */
    @Transactional(readOnly = true)
    public List<LaporanRiwayatKeseluruhanItem> riwayatKeseluruhan(
            Long sekolahId, LocalDate tanggal, OffsetDateTime dari, OffsetDateTime sampai, int batas) {

        int batasAman = Math.min(Math.max(batas, 1), 500);
        Pageable pageable = PageRequest.of(0, batasAman);

        List<MutasiSaldo> mutasi;
        if (tanggal != null) {
            OffsetDateTime awal = tanggal.atStartOfDay(jam.zona()).toOffsetDateTime();
            OffsetDateTime akhir = awal.plusDays(1);
            mutasi = saldoLedgerRepo.riwayatKeseluruhanRentang(sekolahId, awal, akhir, pageable);
        } else if (dari != null || sampai != null) {
            OffsetDateTime akhir = sampai != null ? sampai : OffsetDateTime.now(jam.zona());
            OffsetDateTime awal = dari != null ? dari : akhir.minusDays(HARI_RIWAYAT_DEFAULT);
            mutasi = saldoLedgerRepo.riwayatKeseluruhanRentang(sekolahId, awal, akhir, pageable);
        } else {
            mutasi = saldoLedgerRepo.riwayatKeseluruhanTerbaru(sekolahId, pageable);
        }

        Set<Long> transaksiIds = mutasi.stream()
                .map(MutasiSaldo::getTransaksiId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Transaksi> transaksiById = transaksiIds.isEmpty() ? Map.of()
                : transaksiRepo.findAllById(transaksiIds).stream()
                .filter(t -> sekolahId.equals(t.getSekolahId()))
                .collect(Collectors.toMap(Transaksi::getId, Function.identity()));

        Map<Long, String> namaTitik = new HashMap<>();
        titikKasirRepo.findBySekolahId(sekolahId).forEach(t -> namaTitik.put(t.getId(), t.getNama()));

        Map<Long, List<TransaksiItem>> itemByTrx = transaksiIds.isEmpty() ? Map.of()
                : transaksiItemRepo.findByTransaksiIdIn(transaksiIds).stream()
                .collect(Collectors.groupingBy(i -> i.getTransaksi().getId()));

        Map<Long, com.asqi.scholia_kantin_be.service.integrasi.InfoSiswa> siswaById = new HashMap<>();
        try {
            for (com.asqi.scholia_kantin_be.service.integrasi.InfoSiswa s : cariSiswaService.cari(sekolahId, "", false, 50)) {
                siswaById.put(s.getSiswaId(), s);
            }
        } catch (Exception e) {
            // port fallback
        }

        return mutasi.stream().map(l -> {
            Transaksi t = l.getTransaksiId() == null ? null : transaksiById.get(l.getTransaksiId());
            List<LaporanRiwayatSiswa.Item> items = t == null ? List.of()
                    : itemByTrx.getOrDefault(t.getId(), List.of()).stream()
                    .map(i -> new LaporanRiwayatSiswa.Item(i.getMenuId(), i.getNamaMenu(),
                            i.getQty(), i.getHargaJual(), i.getSubtotal()))
                    .toList();

            Long sId = l.getSiswaId();
            com.asqi.scholia_kantin_be.service.integrasi.InfoSiswa info =
                    sId != null ? siswaById.get(sId) : null;
            String siswaNama = info != null ? info.getNama() : null;
            String siswaNis = info != null ? info.getNis() : null;
            String siswaKelas = info != null ? info.getKelas() : null;

            return new LaporanRiwayatKeseluruhanItem(
                    String.valueOf(l.getId()),
                    l.getWaktu(),
                    jenisRiwayat(l.getJenis()),
                    l.getArah() == null ? null : l.getArah().name(),
                    l.getNominal() == null ? 0L : l.getNominal(),
                    l.getSaldoSetelah() == null ? 0L : l.getSaldoSetelah(),
                    t == null ? "-" : namaTitik.getOrDefault(t.getTitikKasirId(), "-"),
                    "-",
                    t == null ? String.valueOf(l.getId()) : String.valueOf(t.getId()),
                    l.getKeterangan() == null ? "" : l.getKeterangan(),
                    items,
                    sId,
                    siswaNama,
                    siswaNis,
                    siswaKelas);
        }).toList();
    }


    /** Jenis mutasi BE → istilah riwayat yang dipakai FE. */
    private static String jenisRiwayat(JenisMutasiSaldo jenis) {
        if (jenis == null) {
            return null;
        }
        return switch (jenis) {
            case PENJUALAN -> "BELANJA";
            case VOID_PENJUALAN -> "VOID";
            default -> jenis.name();
        };
    }

    /**
     * Penjualan per <b>titik kasir</b> pada periode (PRD §9.5, issue #116).
     * Nama titik diresolusi dari katalog titik kasir.
     */
    @Transactional(readOnly = true)
    public List<BarisPenjualanDimensi> penjualanPerTitik(Long sekolahId, LocalDate tanggal,
                                                         OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = rentang(tanggal, dari, sampai);
        Map<Long, String> namaTitik = new HashMap<>();
        titikKasirRepo.findBySekolahId(sekolahId)
                .forEach(t -> namaTitik.put(t.getId(), t.getNama()));

        List<BarisPenjualanDimensi> hasil = new ArrayList<>();
        for (Object[] b : transaksiRepo.penjualanPerTitikRentang(sekolahId, r[0], r[1])) {
            Long id = (Long) b[0];
            long jumlah = ((Number) b[1]).longValue();
            long nilai = ((Number) b[2]).longValue();
            long hpp = ((Number) b[3]).longValue();
            hasil.add(new BarisPenjualanDimensi(id, namaTitik.get(id), jumlah, nilai, hpp, nilai - hpp));
        }
        return hasil;
    }

    /**
     * Penjualan per <b>petugas</b> pada periode (PRD §9.5, issue #116).
     * Nama petugas tidak tersedia di kantin-be (ada di admin-be) → {@code null}.
     */
    @Transactional(readOnly = true)
    public List<BarisPenjualanDimensi> penjualanPerPetugas(Long sekolahId, LocalDate tanggal,
                                                           OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = rentang(tanggal, dari, sampai);
        List<BarisPenjualanDimensi> hasil = new ArrayList<>();
        for (Object[] b : transaksiRepo.penjualanPerPetugasRentang(sekolahId, r[0], r[1])) {
            Long id = (Long) b[0];
            long jumlah = ((Number) b[1]).longValue();
            long nilai = ((Number) b[2]).longValue();
            long hpp = ((Number) b[3]).longValue();
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
     */
    @Transactional(readOnly = true)
    public List<BarisPenjualanKasir> penjualanPerKasir(Long sekolahId, LocalDate tanggal,
                                                       OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = rentang(tanggal, dari, sampai);
        Map<Long, String> namaTitik = new HashMap<>();
        titikKasirRepo.findBySekolahId(sekolahId)
                .forEach(t -> namaTitik.put(t.getId(), t.getNama()));

        List<BarisPenjualanKasir> hasil = new ArrayList<>();
        for (Object[] b : transaksiRepo.penjualanPerKasirRentang(sekolahId, r[0], r[1])) {
            Long titikId = (Long) b[0];
            Long petugasId = (Long) b[1];
            long jumlahSukses = ((Number) b[2]).longValue();
            long nilaiSukses = ((Number) b[3]).longValue();
            long nilaiVoid = ((Number) b[4]).longValue();
            hasil.add(new BarisPenjualanKasir(
                    titikId, namaTitik.get(titikId), petugasId,
                    String.valueOf(petugasId), jumlahSukses,
                    nilaiSukses + nilaiVoid, nilaiVoid, nilaiSukses));
        }
        return hasil;
    }

    /**
     * Laporan <b>Per siswa</b> (PRD §9.5, issue #117): riwayat lengkap satu
     * subjek (SISWA/KARTU_TAMU) pada periode — ringkasan + daftar transaksi +
     * mutasi saldo. Untuk menjawab komplain orang tua. Tenant-scoped.
     */
    @Transactional(readOnly = true)
    public LaporanPerSiswa laporanPerSiswa(Long sekolahId, Long siswaId,
                                           LocalDate tanggal, OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = rentang(tanggal, dari, sampai);
        List<Transaksi> trx = transaksiRepo.transaksiSubjekRentang(sekolahId, siswaId, r[0], r[1]);
        List<MutasiSaldo> mutasi = saldoLedgerRepo.padaRentangSubjek(sekolahId, siswaId, r[0], r[1]);

        long jmlSukses = 0, nilaiSukses = 0, jmlVoid = 0, nilaiVoid = 0, totalHpp = 0, totalTopup = 0;
        for (Transaksi t : trx) {
            if (t.getStatus() == StatusTransaksi.SUKSES) {
                jmlSukses++;
                nilaiSukses += nolJikaNull(t.getTotal());
                totalHpp += nolJikaNull(t.getTotalHpp());
            } else if (t.getStatus() == StatusTransaksi.VOID) {
                jmlVoid++;
                nilaiVoid += nolJikaNull(t.getTotal());
            }
        }
        for (MutasiSaldo l : mutasi) {
            if (l.getJenis() == JenisMutasiSaldo.TOPUP_TUNAI) {
                totalTopup += nolJikaNull(l.getNominal());
            }
        }

        long saldo = saldoCacheRepo.findBySekolahIdAndSiswaId(sekolahId, siswaId)
                .map(c -> c.getSaldo() == null ? 0L : c.getSaldo())
                .orElse(0L);

        return new LaporanPerSiswa(SubjekTipe.SISWA, siswaId, saldo, label(r),
                new LaporanPerSiswa.Ringkasan(jmlSukses, nilaiSukses, jmlVoid, nilaiVoid, totalTopup, totalHpp),
                trx.stream().map(LaporanPerSiswa.BarisTransaksi::dari).toList(),
                mutasi.stream().map(LaporanPerSiswa.BarisMutasi::dari).toList());
    }

    private String label(OffsetDateTime[] r) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return f.format(r[0]) + " s/d " + f.format(r[1]);
    }

    // ────────────────────────────────────────────────────────────────
    // Setoran kas TU (PRD §9.2 & §9.5, issue #143)
    // ────────────────────────────────────────────────────────────────

    /**
     * Laporan <b>Setoran kas TU</b> (PRD §9.5): rekap top-up tunai
     * <b>per petugas per hari</b> + uang yang sudah dikonfirmasi disetor +
     * <b>selisih</b>. Sumber kebenaran rekap = {@link
     * com.asqi.scholia_kantin_be.service.saldo.SetoranService#rekap} (ledger
     * {@code TOPUP_TUNAI} + {@code setoran_tu} append-only), sehingga laporan
     * modul laporan &amp; endpoint operasional selalu konsisten.
     *
     * <p>Tenant-scoped; baca-saja. {@code tanggal} {@code null} = hari ini
     * (zona kantin). Akses PRD §9.5: <b>Bendahara</b> (dipetakan ke
     * {@code TU_SEKOLAH}) — kepsek <b>tidak</b> berhak.
     */
    @Transactional(readOnly = true)
    public List<RekapSetoranItem> laporanSetoran(Long sekolahId, LocalDate tanggal) {
        return setoranTuService.rekap(sekolahId, tanggal);
    }

    private static long nolJikaNull(Long nilai) {
        return nilai == null ? 0L : nilai;
    }
}
