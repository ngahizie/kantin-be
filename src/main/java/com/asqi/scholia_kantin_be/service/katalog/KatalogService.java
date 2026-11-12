package com.asqi.scholia_kantin_be.service.katalog;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.dto.KategoriResponse;
import com.asqi.scholia_kantin_be.enums.SatuanMenu;
import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.KategoriMenu;
import com.asqi.scholia_kantin_be.model.Menu;
import com.asqi.scholia_kantin_be.model.Stok;
import com.asqi.scholia_kantin_be.repository.KategoriMenuRepository;
import com.asqi.scholia_kantin_be.repository.MenuRepository;
import com.asqi.scholia_kantin_be.repository.StokRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Katalog menu &amp; kategori (PRD §7.1).
 *
 * <p>Aturan yang ditegakkan:
 * <ul>
 *   <li><b>Soft delete</b> — item/kategori dinonaktifkan ({@code isActive=false}),
 *       riwayat transaksi tetap (transaksi menyimpan snapshot).</li>
 *   <li><b>Ubah harga jual tercatat di audit</b> (PRD §7.1, §11.7) — transaksi
 *       lama tidak berubah karena snapshot.</li>
 *   <li><b>Kategori terpakai hanya boleh dinonaktifkan</b>, tidak dihapus keras
 *       (FK {@code ON DELETE RESTRICT}).</li>
 *   <li>Semua baca/tulis <b>tenant-scoped</b> (PRD §11.4): sekolah lain ⇒ 404.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KatalogService {

    private final KategoriMenuRepository kategoriRepo;
    private final MenuRepository menuRepo;
    private final StokRepository stokRepo;
    private final IdGenerator idGenerator;
    private final JamKantin jam;
    private final AuditLogger auditLogger;

    // ────────────────────────────────────────────────────────────────
    // KATEGORI
    // ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<KategoriResponse> daftarKategori(Long sekolahId, boolean hanyaAktif) {
        List<KategoriMenu> kategori = kategoriRepo.daftar(sekolahId, hanyaAktif);
        // Satu query agregat untuk semua kategori (hindari N+1).
        Map<Long, Long> jumlahMenu = new HashMap<>();
        for (Object[] baris : menuRepo.hitungMenuPerKategori(sekolahId)) {
            Long kategoriId = ((Number) baris[0]).longValue();
            jumlahMenu.put(kategoriId, ((Number) baris[1]).longValue());
        }
        return kategori.stream()
                .map(k -> KategoriResponse.dari(k, jumlahMenu.getOrDefault(k.getId(), 0L)))
                .toList();
    }

    @Transactional
    public KategoriMenu buatKategori(Long sekolahId, String nama, Integer urutan, Long aktorId) {
        String bersih = normalisasiNama(nama);
        kategoriRepo.findBySekolahIdAndNamaIgnoreCaseAndDeletedAtIsNull(sekolahId, bersih).ifPresent(k -> {
            throw new InvalidOperationException("Kategori '" + bersih + "' sudah ada");
        });

        var sekarang = jam.sekarang();
        KategoriMenu kategori = KategoriMenu.builder()
                .id(idGenerator.berikutnyaLong())
                .sekolahId(sekolahId)
                .nama(bersih)
                .urutan(urutan == null ? 0 : urutan)
                .isActive(true)
                .createdAt(sekarang)
                .updatedAt(sekarang)
                .build();

        KategoriMenu tersimpan = kategoriRepo.save(kategori);
        auditLogger.catat(aktorId, sekolahId, "KATEGORI_DIBUAT", "KategoriMenu",
                String.valueOf(tersimpan.getId()), null, null, bersih);
        return tersimpan;
    }

    @Transactional
    public KategoriMenu ubahKategori(Long sekolahId, Long kategoriId, String nama,
                                     Integer urutan, Boolean aktif, Long aktorId) {
        KategoriMenu kategori = kategoriRepo.findByIdAndSekolahIdAndDeletedAtIsNull(kategoriId, sekolahId)
                .orElseThrow(() -> new NotFoundEntity("Kategori tidak ditemukan"));

        String lama = kategori.getNama();
        if (nama != null && !nama.isBlank()) {
            String bersih = normalisasiNama(nama);
            kategoriRepo.findBySekolahIdAndNamaIgnoreCaseAndDeletedAtIsNull(sekolahId, bersih)
                    .filter(k -> !k.getId().equals(kategoriId))
                    .ifPresent(k -> {
                        throw new InvalidOperationException("Kategori '" + bersih + "' sudah ada");
                    });
            kategori.setNama(bersih);
        }
        if (urutan != null) {
            kategori.setUrutan(urutan);
        }
        if (aktif != null) {
            kategori.setIsActive(aktif);
        }
        kategori.setUpdatedAt(jam.sekarang());

        KategoriMenu tersimpan = kategoriRepo.save(kategori);
        auditLogger.catat(aktorId, sekolahId, "KATEGORI_DIUBAH", "KategoriMenu",
                String.valueOf(kategoriId), null, lama, kategori.getNama());
        return tersimpan;
    }

    /**
     * Hapus kategori dari katalog (soft delete: {@code deleted_at} diisi).
     *
     * <p>Berbeda dari menonaktifkan ({@code is_active=false} lewat PUT), kategori
     * yang dihapus <b>hilang</b> dari {@code GET /api/katalog/kategori} dan
     * namanya boleh dipakai ulang. Riwayat transaksi tetap aman karena baris
     * kategori tidak dihapus keras.
     *
     * <p><b>Validasi:</b> ditolak bila masih ada menu terkait yang belum dihapus
     * — baik menu aktif maupun nonaktif — supaya tidak ada item menggantung
     * (§7.1).
     */
    @Transactional
    public KategoriResponse hapusKategori(Long sekolahId, Long kategoriId, Long aktorId) {
        KategoriMenu kategori = kategoriRepo.findByIdAndSekolahIdAndDeletedAtIsNull(kategoriId, sekolahId)
                .orElseThrow(() -> new NotFoundEntity("Kategori tidak ditemukan"));

        long dipakai = menuRepo.countByKategoriIdAndDeletedAtIsNull(kategoriId);
        if (dipakai > 0) {
            throw new InvalidOperationException(
                    "Kategori masih dipakai " + dipakai + " item — hapus atau pindahkan item dahulu");
        }

        kategori.setDeletedAt(jam.sekarang());
        kategori.setUpdatedAt(jam.sekarang());
        KategoriMenu tersimpan = kategoriRepo.save(kategori);
        auditLogger.catat(aktorId, sekolahId, "KATEGORI_DIHAPUS", "KategoriMenu",
                String.valueOf(kategoriId), null, "deleted_at=null", "deleted_at=" + tersimpan.getDeletedAt());
        return KategoriResponse.dari(tersimpan, 0);
    }

    // ────────────────────────────────────────────────────────────────
    // MENU
    // ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Menu> daftarMenu(Long sekolahId, Long kategoriId, boolean hanyaAktif) {
        return menuRepo.findAll(MenuRepository.daftarSpec(sekolahId, kategoriId, hanyaAktif));
    }

    @Transactional(readOnly = true)
    public Menu lihatMenu(Long sekolahId, Long menuId) {
        return menuRepo.findByIdAndSekolahIdAndDeletedAtIsNull(menuId, sekolahId)
                .orElseThrow(() -> new NotFoundEntity("Menu tidak ditemukan"));
    }

    /**
     * Stok berjalan untuk sekumpulan menu dalam <b>satu</b> query (tenant-scoped)
     * — dipakai controller mengisi {@code stokBerjalan} pada daftar menu agar
     * FE tak perlu memanggil {@code GET /api/stok/{menuId}} per item (hindari N+1).
     *
     * @return peta {@code menuId → stok}; menu tanpa baris stok tidak disertakan
     */
    @Transactional(readOnly = true)
    public Map<Long, Integer> stokBerjalan(Long sekolahId, Collection<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return Map.of();
        }
        return stokRepo.findBySekolahIdAndMenuIdIn(sekolahId, menuIds).stream()
                .collect(Collectors.toMap(
                        Stok::getMenuId,
                        c -> c.getStok() == null ? 0 : c.getStok(),
                        (a, b) -> a));
    }

    /** Stok berjalan satu menu (0 bila belum ada baris stok). */
    @Transactional(readOnly = true)
    public int stokBerjalan(Long sekolahId, Long menuId) {
        return stokRepo.findByMenuId(menuId)
                .filter(c -> c.getSekolahId().equals(sekolahId))
                .map(Stok::getStok)
                .orElse(0);
    }

    @Transactional
    public Menu buatMenu(Long sekolahId, Long kategoriId, String nama, Long hargaJual,
                         SatuanMenu satuan, String fotoUrl, Integer stokMinimum, Long aktorId) {
        return buatMenu(sekolahId, kategoriId, nama, hargaJual, satuan, fotoUrl, stokMinimum,
                null, aktorId);
    }

    @Transactional
    public Menu buatMenu(Long sekolahId, Long kategoriId, String nama, Long hargaJual,
                         SatuanMenu satuan, String fotoUrl, Integer stokMinimum,
                         String kodeBarcode, Long aktorId) {
        String bersih = normalisasiNama(nama);
        String barcodeBersih = normalisasiKodeBarcode(kodeBarcode);
        validasiHarga(hargaJual);
        if (kategoriId != null) {
            pastikanKategoriMilikSekolah(sekolahId, kategoriId);
        }
        if (barcodeBersih != null) {
            pastikanKodeBarcodeUnik(sekolahId, barcodeBersih, null);
        }

        var sekarang = jam.sekarang();
        Menu menu = Menu.builder()
                .id(idGenerator.berikutnyaLong())
                .sekolahId(sekolahId)
                .kategoriId(kategoriId)
                .nama(bersih)
                .kodeBarcode(barcodeBersih)
                .hargaJual(hargaJual)
                .satuan(satuan == null ? SatuanMenu.PCS : satuan)
                .fotoUrl(fotoUrl)
                .stokMinimum(stokMinimum == null ? 0 : stokMinimum)
                .isActive(true)
                .createdAt(sekarang)
                .updatedAt(sekarang)
                .build();

        Menu tersimpan = menuRepo.save(menu);
        // Ambang stok menipis ikut tersinkron ke stok lewat trigger DB
        // trg_menu_sinkron_stok_minimum (V23, issue #196) — tidak lagi bergantung
        // pada panggilan manual di sini, sehingga jalur tulis lain (seed, SQL
        // langsung, update massal) pun tetap sinkron.
        auditLogger.catat(aktorId, sekolahId, "MENU_DIBUAT", "Menu",
                String.valueOf(tersimpan.getId()), null, null,
                bersih + " @ " + hargaJual);
        return tersimpan;
    }

    /**
     * Ubah item. Perubahan <b>harga jual dicatat khusus</b> di audit (PRD §7.1)
     * agar jejak perubahan harga terbaca jelas.
     */
    @Transactional
    public Menu ubahMenu(Long sekolahId, Long menuId, Long kategoriId, String nama,
                         Long hargaJual, SatuanMenu satuan, String fotoUrl,
                         Integer stokMinimum, Boolean aktif, Long aktorId) {
        return ubahMenu(sekolahId, menuId, kategoriId, nama, hargaJual, satuan, fotoUrl,
                stokMinimum, aktif, null, aktorId);
    }

    /**
     * Ubah item termasuk kode barcode opsional. Jika {@code kodeBarcode == null},
     * barcode lama dipertahankan; jika string kosong/spasi, barcode dikosongkan.
     */
    @Transactional
    public Menu ubahMenu(Long sekolahId, Long menuId, Long kategoriId, String nama,
                         Long hargaJual, SatuanMenu satuan, String fotoUrl,
                         Integer stokMinimum, Boolean aktif, String kodeBarcode, Long aktorId) {
        Menu menu = menuRepo.findByIdAndSekolahIdAndDeletedAtIsNull(menuId, sekolahId)
                .orElseThrow(() -> new NotFoundEntity("Menu tidak ditemukan"));

        if (nama != null && !nama.isBlank()) {
            menu.setNama(normalisasiNama(nama));
        }
        if (kodeBarcode != null) {
            String barcodeBersih = normalisasiKodeBarcode(kodeBarcode);
            if (barcodeBersih != null) {
                pastikanKodeBarcodeUnik(sekolahId, barcodeBersih, menuId);
            }
            menu.setKodeBarcode(barcodeBersih);
        }
        if (kategoriId != null) {
            pastikanKategoriMilikSekolah(sekolahId, kategoriId);
            menu.setKategoriId(kategoriId);
        }
        if (hargaJual != null) {
            validasiHarga(hargaJual);
            long hargaLama = menu.getHargaJual();
            if (hargaLama != hargaJual) {
                // Audit khusus perubahan harga (PRD §7.1) — snapshot transaksi lama tetap.
                auditLogger.catat(aktorId, sekolahId, "UBAH_HARGA_JUAL", "Menu",
                        String.valueOf(menuId), null,
                        String.valueOf(hargaLama), String.valueOf(hargaJual));
                menu.setHargaJual(hargaJual);
            }
        }
        if (satuan != null) {
            menu.setSatuan(satuan);
        }
        if (fotoUrl != null) {
            menu.setFotoUrl(fotoUrl);
        }
        if (stokMinimum != null) {
            if (stokMinimum < 0) {
                throw new InvalidOperationException("Stok minimum tidak boleh negatif");
            }
            menu.setStokMinimum(stokMinimum);
        }
        if (aktif != null) {
            menu.setIsActive(aktif);
        }
        menu.setUpdatedAt(jam.sekarang());

        Menu tersimpan = menuRepo.save(menu);
        // Ambang stok menipis tersinkron otomatis oleh trigger DB (V23, #196).
        auditLogger.catat(aktorId, sekolahId, "MENU_DIUBAH", "Menu",
                String.valueOf(menuId), null, null, tersimpan.getNama());
        return tersimpan;
    }

    /** Nonaktifkan item sementara — item nonaktif tetap ada di katalog namun tidak bisa dijual (PRD §7.1). */
    @Transactional
    public Menu nonaktifkanMenu(Long sekolahId, Long menuId, Long aktorId) {
        Menu menu = menuRepo.findByIdAndSekolahIdAndDeletedAtIsNull(menuId, sekolahId)
                .orElseThrow(() -> new NotFoundEntity("Menu tidak ditemukan"));
        menu.setIsActive(false);
        menu.setUpdatedAt(jam.sekarang());
        Menu tersimpan = menuRepo.save(menu);
        auditLogger.catat(aktorId, sekolahId, "MENU_DINONAKTIFKAN", "Menu",
                String.valueOf(menuId), null, "aktif=true", "aktif=false");
        return tersimpan;
    }

    /**
     * Hapus item dari daftar katalog via soft-delete ({@code deletedAt}), sehingga
     * hilang dari katalog, kasir POS, dan stok menipis namun riwayat transaksi/stok tetap utuh.
     */
    @Transactional
    public Menu hapusMenu(Long sekolahId, Long menuId, Long aktorId) {
        Menu menu = menuRepo.findByIdAndSekolahIdAndDeletedAtIsNull(menuId, sekolahId)
                .orElseThrow(() -> new NotFoundEntity("Menu tidak ditemukan"));
        var sekarang = jam.sekarang();
        menu.setIsActive(false);
        menu.setDeletedAt(sekarang);
        menu.setUpdatedAt(sekarang);
        Menu tersimpan = menuRepo.save(menu);
        auditLogger.catat(aktorId, sekolahId, "MENU_DIHAPUS", "Menu",
                String.valueOf(menuId), null, "deletedAt=null", "deletedAt=" + sekarang);
        return tersimpan;
    }

    // ────────────────────────────────────────────────────────────────
    // HELPER
    // ────────────────────────────────────────────────────────────────

    private void pastikanKategoriMilikSekolah(Long sekolahId, Long kategoriId) {
        kategoriRepo.findByIdAndSekolahIdAndDeletedAtIsNull(kategoriId, sekolahId)
                .orElseThrow(() -> new NotFoundEntity("Kategori tidak ditemukan"));
    }

    private void validasiHarga(Long hargaJual) {
        if (hargaJual == null || hargaJual < 0) {
            throw new InvalidOperationException("Harga jual wajib dan tidak boleh negatif");
        }
    }

    private String normalisasiNama(String nama) {
        if (nama == null || nama.isBlank()) {
            throw new InvalidOperationException("Nama wajib diisi");
        }
        String bersih = nama.trim().replaceAll("\\s+", " ");
        if (bersih.length() > 150) {
            throw new InvalidOperationException("Nama maksimal 150 karakter");
        }
        return bersih;
    }

    private String normalisasiKodeBarcode(String kodeBarcode) {
        if (kodeBarcode == null || kodeBarcode.isBlank()) {
            return null;
        }
        String bersih = kodeBarcode.trim();
        if (bersih.length() > 64) {
            throw new InvalidOperationException("Kode barcode maksimal 64 karakter");
        }
        return bersih;
    }

    private void pastikanKodeBarcodeUnik(Long sekolahId, String kodeBarcode, Long kecualikanMenuId) {
        boolean sudahDipakai = kecualikanMenuId == null
                ? menuRepo.existsBySekolahIdAndKodeBarcodeAndDeletedAtIsNull(sekolahId, kodeBarcode)
                : menuRepo.existsBySekolahIdAndKodeBarcodeAndIdNotAndDeletedAtIsNull(
                        sekolahId, kodeBarcode, kecualikanMenuId);
        if (sudahDipakai) {
            throw new ConflictException("Kode barcode '" + kodeBarcode + "' sudah digunakan oleh menu lain");
        }
    }
}
