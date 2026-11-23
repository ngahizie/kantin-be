package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.dto.BarisBarangMasuk;
import com.asqi.scholia_kantin_be.dto.BarisKerugianStok;
import com.asqi.scholia_kantin_be.dto.BarisStok;
import com.asqi.scholia_kantin_be.dto.KartuStokItem;
import com.asqi.scholia_kantin_be.dto.RingkasanKerugianStok;
import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.helper.UkuranHalaman;
import com.asqi.scholia_kantin_be.model.KategoriMenu;
import com.asqi.scholia_kantin_be.model.Menu;
import com.asqi.scholia_kantin_be.model.RiwayatStok;
import com.asqi.scholia_kantin_be.repository.KategoriMenuRepository;
import com.asqi.scholia_kantin_be.repository.MenuRepository;
import com.asqi.scholia_kantin_be.repository.RiwayatStokRepository;
import com.asqi.scholia_kantin_be.repository.StokRepository;
import com.asqi.scholia_kantin_be.repository.projection.AgregatKerugianStok;
import com.asqi.scholia_kantin_be.repository.projection.BarisStokLaporan;
import com.asqi.scholia_kantin_be.service.stok.HppService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Laporan stok (PRD §9.5): posisi stok + nilai persediaan, kerugian stok,
 * ringkasan kerugian, barang masuk, dan kartu stok per item.
 *
 * <p>Semua query tenant-scoped dan baca-saja. Nama, kategori, dan stok minimum
 * dibaca dari katalog menu (PRD §7.1) — <code>stok.stok_minimum</code> tidak
 * disinkron dari menu sehingga tidak dipakai sebagai sumber kebenaran.
 */
@Service
@RequiredArgsConstructor
public class LaporanStokService {

    /** Batas maksimum baris kartu stok yang diterima dari klien. */
    private static final int MAKS_KARTU_STOK = 500;

    /** Jumlah baris kartu stok bila klien tidak mengirim/mengirim batas tak wajar. */
    private static final int BATAS_KARTU_STOK_DEFAULT = 200;

    /**
     * Jenis mutasi yang dihitung sebagai kerugian stok (PRD §9.5, #391):
     * opname keluar &amp; barang rusak.
     */
    private static final Set<JenisMutasiStok> JENIS_KERUGIAN =
            EnumSet.of(JenisMutasiStok.OPNAME_KELUAR, JenisMutasiStok.BARANG_RUSAK);

    private final RiwayatStokRepository mutasiStokRepo;
    private final StokRepository stokCacheRepo;
    private final MenuRepository menuRepo;
    private final KategoriMenuRepository kategoriRepo;
    private final HppService hppService;
    private final JamKantin jam;

    /**
     * Posisi stok sekarang + nilai persediaan (stok × HPP) untuk semua menu.
     *
     * @param sekolahId    tenant pemanggil
     * @param hanyaMenipis bila {@code true}, hanya baris dengan stok ≤ minimum
     * @return satu halaman baris stok (≤ {@code ukuran}) untuk menu yang punya
     *         cache stok di sekolah ini, beserta total baris untuk metadata
     */
    @Transactional(readOnly = true)
    public Page<BarisStok> laporanStok(Long sekolahId, boolean hanyaMenipis, int halaman, int ukuran) {
        // Issue #375: dulu seluruh menu + seluruh baris stok_cache tenant dimuat ke
        // memori lalu difilter di Java (uncapped result set → risiko heap exhaustion).
        // Sekarang paginasi dilakukan di database dan ukuran halaman divalidasi:
        // di luar rentang → 400, bukan di-clamp diam-diam (PRD §6.2, issue #257).
        int ukuranAman = UkuranHalaman.validasi(ukuran, UkuranHalaman.UKURAN_MAKS);
        int halamanAman = Math.max(0, halaman);
        return stokCacheRepo
                .halamanLaporanStok(sekolahId, hanyaMenipis, PageRequest.of(halamanAman, ukuranAman))
                .map(this::keBarisStok);
    }

    /** Proyeksi query → DTO kontrak FE (alias {@code namaMenu}/{@code stokBerjalan}, issue #98). */
    private BarisStok keBarisStok(BarisStokLaporan r) {
        int stok = r.getStok() == null ? 0 : r.getStok();
        int stokMinimum = r.getStokMinimum() == null ? 0 : r.getStokMinimum();
        Long hpp = r.getHpp();
        return new BarisStok(r.getMenuId(), r.getNama(), r.getKategoriId(), stok, stokMinimum, hpp,
                hppService.nilaiPersediaan(stok, hpp), stok <= stokMinimum,
                r.getNama(), stok, r.getKodeBarcode());
    }

    /**
     * Kerugian stok: opname keluar &amp; barang rusak pada periode (PRD §9.5).
     *
     * <p><b>SEC-03 (issue #391):</b> daftar dibatasi per halaman di database —
     * dulu satu periode penuh dimuat ke memori JVM tanpa batas atas. Ukuran di
     * luar rentang {@code 1..200} → 400 (PRD §6.2, pola sama dengan laporan
     * stok #375), bukan di-clamp diam-diam.
     *
     * @param halaman indeks halaman (0-based); negatif dinormalkan ke 0
     * @param ukuran  jumlah baris per halaman (1..{@link UkuranHalaman#UKURAN_MAKS})
     * @return satu halaman baris kerugian + total baris untuk metadata
     */
    @Transactional(readOnly = true)
    public Page<BarisKerugianStok> kerugianStok(Long sekolahId, LocalDate tanggal,
                                                OffsetDateTime dari, OffsetDateTime sampai,
                                                int halaman, int ukuran) {
        int ukuranAman = UkuranHalaman.validasi(ukuran, UkuranHalaman.UKURAN_MAKS);
        int halamanAman = Math.max(0, halaman);
        OffsetDateTime[] r = LaporanHelper.rentang(jam, tanggal, dari, sampai);

        Page<RiwayatStok> mutasi = mutasiStokRepo.kerugianRentang(sekolahId, JENIS_KERUGIAN,
                r[0], r[1], PageRequest.of(halamanAman, ukuranAman));

        // Nama menu & kategori diambil sekali per halaman (≤ 200 id) — bukan per baris.
        Map<Long, Menu> menuMap = petaMenu(sekolahId, mutasi.getContent());
        Map<Long, String> kategoriMap = petaKategori(sekolahId, menuMap.values());
        return mutasi.map(m -> keBarisKerugian(m, menuMap, kategoriMap));
    }

    /** Peta {@code menuId → menu} tenant-scoped untuk baris satu halaman. */
    private Map<Long, Menu> petaMenu(Long sekolahId, List<RiwayatStok> mutasi) {
        if (mutasi.isEmpty()) {
            return Map.of();
        }
        Set<Long> menuIds = mutasi.stream().map(RiwayatStok::getMenuId).collect(Collectors.toSet());
        return menuRepo.findAllById(menuIds).stream()
                .filter(m -> m.getSekolahId().equals(sekolahId))
                .collect(Collectors.toMap(Menu::getId, m -> m, (a, b) -> a));
    }

    /** Peta {@code kategoriId → nama} tenant-scoped untuk menu satu halaman. */
    private Map<Long, String> petaKategori(Long sekolahId, Collection<Menu> menus) {
        Set<Long> kategoriIds = menus.stream()
                .map(Menu::getKategoriId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (kategoriIds.isEmpty()) {
            return Map.of();
        }
        return kategoriRepo.findAllById(kategoriIds).stream()
                .filter(k -> k.getSekolahId().equals(sekolahId))
                .collect(Collectors.toMap(KategoriMenu::getId, KategoriMenu::getNama, (a, b) -> a));
    }

    /** Satu baris mutasi → DTO kontrak FE (nama menu/kategori dipetakan, fallback aman). */
    private BarisKerugianStok keBarisKerugian(RiwayatStok m, Map<Long, Menu> menuMap,
                                              Map<Long, String> kategoriMap) {
        Menu menu = menuMap.get(m.getMenuId());
        String namaMenu = menu != null ? menu.getNama() : "Menu #" + m.getMenuId();
        String kategoriNama = (menu != null && menu.getKategoriId() != null)
                ? kategoriMap.getOrDefault(menu.getKategoriId(), "Umum")
                : "Umum";
        long hpp = m.getHppSnapshot() != null ? m.getHppSnapshot() : 0L;
        long totalNilai = m.getQty() * hpp;
        String petugas = m.getAktorNama() != null ? m.getAktorNama() : "Petugas";

        return new BarisKerugianStok(
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
                petugas);
    }

    /**
     * Ringkasan kerugian stok pada periode yang sama dengan endpoint daftar (#226).
     *
     * <p><b>SEC-03 (issue #391):</b> dihitung lewat <b>agregasi SQL</b>
     * ({@code SUM(qty)}, {@code SUM(qty × hpp_snapshot)}, {@code COUNT}) — dulu
     * memanggil ulang daftar penuh lalu menjumlahkan di memori, sehingga satu
     * permintaan ringkasan (dipanggil paralel dengan daftar oleh FE) menggandakan
     * beban pemuatan entitas.
     *
     * @return total kerugian &amp; qty, dipecah opname keluar vs barang rusak
     */
    @Transactional(readOnly = true)
    public RingkasanKerugianStok ringkasanKerugianStok(Long sekolahId, LocalDate tanggal,
                                                       OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = LaporanHelper.rentang(jam, tanggal, dari, sampai);
        long kerugianOpname = 0;
        long qtyOpname = 0;
        long kerugianRusak = 0;
        long qtyRusak = 0;
        long jumlahKejadian = 0;

        for (AgregatKerugianStok a : mutasiStokRepo.agregatKerugian(sekolahId, JENIS_KERUGIAN,
                r[0], r[1])) {
            long qty = a.getQty() == null ? 0 : a.getQty();
            long nilai = a.getNilai() == null ? 0 : a.getNilai();
            jumlahKejadian += a.getJumlah() == null ? 0 : a.getJumlah();
            if (a.getJenis() == JenisMutasiStok.OPNAME_KELUAR) {
                kerugianOpname = nilai;
                qtyOpname = qty;
            } else if (a.getJenis() == JenisMutasiStok.BARANG_RUSAK) {
                kerugianRusak = nilai;
                qtyRusak = qty;
            }
        }

        return new RingkasanKerugianStok(kerugianOpname + kerugianRusak, qtyOpname + qtyRusak,
                jumlahKejadian, kerugianOpname, qtyOpname, kerugianRusak, qtyRusak);
    }

    /**
     * Barang masuk pada periode (PRD §9.5).
     *
     * @return mutasi stok jenis barang masuk pada rentang
     */
    @Transactional(readOnly = true)
    public List<BarisBarangMasuk> barangMasuk(Long sekolahId, LocalDate tanggal,
                                              OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = LaporanHelper.rentang(jam, tanggal, dari, sampai);
        return mutasiStokRepo.barangMasukRentang(sekolahId, r[0], r[1]).stream()
                .map(BarisBarangMasuk::dari)
                .toList();
    }

    /**
     * Kartu stok per item: riwayat mutasi satu menu (PRD §9.5), tanpa saldo berjalan.
     *
     * @param batas jumlah baris; {@code ≤0} atau {@code >500} dinormalkan ke 200
     * @return mutasi stok terbaru lebih dulu
     */
    @Transactional(readOnly = true)
    public List<BarisBarangMasuk> kartuStok(Long sekolahId, Long menuId, int batas) {
        int limit = (batas <= 0 || batas > MAKS_KARTU_STOK) ? BATAS_KARTU_STOK_DEFAULT : batas;
        return mutasiStokRepo.kartuStok(sekolahId, menuId, PageRequest.of(0, limit)).stream()
                .map(BarisBarangMasuk::dari)
                .toList();
    }

    /**
     * Laporan <b>Kartu stok per item</b> (PRD §9.5, issue #118): riwayat mutasi
     * satu menu + saldo stok berjalan. Menu divalidasi tenant-scoped — bila menu
     * bukan milik sekolah, kembalikan {@code null} (handler → 404).
     *
     * @param batas jumlah baris; {@code ≤0} atau {@code >500} dinormalkan ke 200
     * @return kartu stok, atau {@code null} bila menu tidak ada di sekolah ini
     */
    @Transactional(readOnly = true)
    public KartuStokItem kartuStokItem(Long sekolahId, Long menuId, int batas) {
        Menu menu = menuRepo.findByIdAndSekolahId(menuId, sekolahId).orElse(null);
        if (menu == null) {
            return null;
        }
        int limit = (batas <= 0 || batas > MAKS_KARTU_STOK) ? BATAS_KARTU_STOK_DEFAULT : batas;
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
}