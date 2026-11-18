package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.dto.BarisBarangMasuk;
import com.asqi.scholia_kantin_be.dto.BarisKerugianStok;
import com.asqi.scholia_kantin_be.dto.BarisStok;
import com.asqi.scholia_kantin_be.dto.KartuStokItem;
import com.asqi.scholia_kantin_be.dto.RingkasanKerugianStok;
import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.KategoriMenu;
import com.asqi.scholia_kantin_be.model.Menu;
import com.asqi.scholia_kantin_be.model.RiwayatStok;
import com.asqi.scholia_kantin_be.model.Stok;
import com.asqi.scholia_kantin_be.repository.KategoriMenuRepository;
import com.asqi.scholia_kantin_be.repository.MenuRepository;
import com.asqi.scholia_kantin_be.repository.RiwayatStokRepository;
import com.asqi.scholia_kantin_be.repository.StokRepository;
import com.asqi.scholia_kantin_be.service.stok.HppService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
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
     * @return satu baris per menu yang punya cache stok di sekolah ini
     */
    @Transactional(readOnly = true)
    public List<BarisStok> laporanStok(Long sekolahId, boolean hanyaMenipis) {
        // Sumber kebenaran nama, kategori & stok minimum = katalog menu (PRD §7.1).
        // (stok.stok_minimum tidak disinkron dari menu, jadi jangan dipakai.)
        Map<Long, Menu> menuById = new HashMap<>();
        menuRepo.findAll(MenuRepository.daftarSpec(sekolahId, null, false)).forEach(m -> menuById.put(m.getId(), m));

        List<BarisStok> hasil = new ArrayList<>();
        for (Stok s : stokCacheRepo.findBySekolahIdAndMenuIdIn(
                sekolahId, menuById.keySet().isEmpty() ? Set.of(-1L) : menuById.keySet())) {
            Menu menu = menuById.get(s.getMenuId());
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
                    menu.getNama(), s.getStok(), menu.getKodeBarcode()));
        }
        return hasil;
    }

    /**
     * Kerugian stok: opname keluar &amp; barang rusak pada periode (PRD §9.5).
     *
     * @return satu baris per mutasi kerugian; list kosong bila tidak ada
     */
    @Transactional(readOnly = true)
    public List<BarisKerugianStok> kerugianStok(Long sekolahId, LocalDate tanggal,
                                                OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = LaporanHelper.rentang(jam, tanggal, dari, sampai);
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

    /**
     * Ringkasan kerugian stok dari agregasi yang sama dengan endpoint daftar (#226).
     *
     * @return total kerugian &amp; qty, dipecah opname keluar vs barang rusak
     */
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