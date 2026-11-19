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
import com.asqi.scholia_kantin_be.repository.projection.JumlahMenuKategori;
import com.asqi.scholia_kantin_be.model.Stok;
import com.asqi.scholia_kantin_be.repository.KategoriMenuRepository;
import com.asqi.scholia_kantin_be.repository.MenuRepository;
import com.asqi.scholia_kantin_be.repository.StokRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

    /**
     * Basis URL publik object storage ({@code minio.url-final}); boleh kosong.
     * Dipakai untuk memeriksa URL absolut yang sebenarnya menunjuk storage
     * sendiri, agar path tenant lain tidak lolos lewat bentuk absolut.
     */
    @Value("${minio.url-final:}")
    private String urlFinal;

    // ────────────────────────────────────────────────────────────────
    // KATEGORI
    // ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<KategoriResponse> daftarKategori(Long sekolahId, boolean hanyaAktif) {
        List<KategoriMenu> kategori = kategoriRepo.daftar(sekolahId, hanyaAktif);
        // Satu query agregat untuk semua kategori (hindari N+1).
        Map<Long, Long> jumlahMenu = new HashMap<>();
        for (JumlahMenuKategori baris : menuRepo.hitungMenuPerKategori(sekolahId)) {
            jumlahMenu.put(baris.getKategoriId(), baris.getJumlah());
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
        pastikanNamaMenuUnik(sekolahId, bersih, null);
        String barcodeBersih = normalisasiKodeBarcode(kodeBarcode);
        validasiHarga(hargaJual);
        if (kategoriId != null) {
            pastikanKategoriMilikSekolah(sekolahId, kategoriId);
        }
        if (barcodeBersih != null) {
            pastikanKodeBarcodeUnik(sekolahId, barcodeBersih, null);
        }
        validasiFotoUrlMilikTenant(sekolahId, fotoUrl, urlFinal);

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
            String namaBaru = normalisasiNama(nama);
            pastikanNamaMenuUnik(sekolahId, namaBaru, menuId);
            menu.setNama(namaBaru);
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
            validasiFotoUrlMilikTenant(sekolahId, fotoUrl, urlFinal);
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

    /**
     * Pastikan path foto benar-benar milik tenant pemanggil (M-03), tanpa
     * memeriksa URL absolut terhadap basis publik.
     *
     * <p>Varian ini dipakai tes dan pemanggil yang tidak punya konfigurasi
     * {@code minio.url-final}. Jalur produksi memakai varian tiga argumen.
     *
     * @param sekolahId tenant pemanggil
     * @param fotoUrl   nilai dari request; {@code null}/kosong dianggap tanpa foto
     * @throws InvalidOperationException bila path relatif menunjuk tenant lain
     */
    static void validasiFotoUrlMilikTenant(Long sekolahId, String fotoUrl) {
        validasiFotoUrlMilikTenant(sekolahId, fotoUrl, null);
    }

    /**
     * Pastikan path foto benar-benar milik tenant pemanggil (M-03).
     *
     * <p>Anotasi {@code @Pattern} di {@link com.asqi.scholia_kantin_be.dto.MenuRequest}
     * hanya memeriksa bentuk; metode ini menutup celah path
     * {@code sekolah-<id-lain>/...} dan query {@code /api/storage/file?path=...}.
     * URL absolut http(s) ke host lain sengaja dibiarkan (dipakai data
     * demo/eksternal). Bila host-nya sama dengan {@code basisPublik}
     * ({@code minio.url-final}), URL itu menunjuk storage sendiri sehingga
     * prefix tenant-nya ikut diperiksa.
     *
     * <p>Deteksi bentuk memakai bentuk huruf kecil: {@code @Pattern} bersifat
     * case-insensitive ({@code (?i)}), jadi tanpa normalisasi ini
     * {@code SEKOLAH-9/menu/x.jpg} lolos tanpa masuk cabang pemeriksaan mana pun.
     *
     * @param sekolahId   tenant pemanggil
     * @param fotoUrl     nilai dari request; {@code null}/kosong dianggap tanpa foto
     * @param basisPublik basis URL publik storage; boleh kosong
     * @throws InvalidOperationException bila path menunjuk tenant lain
     */
    static void validasiFotoUrlMilikTenant(Long sekolahId, String fotoUrl, String basisPublik) {
        if (fotoUrl == null || fotoUrl.isBlank()) {
            return;
        }
        String url = fotoUrl.trim();
        // @Pattern memakai (?i); samakan bentuk untuk deteksi agar varian
        // huruf besar tidak melewati pemeriksaan tenant.
        String urlRendah = url.toLowerCase(Locale.ROOT);
        String prefixTenant = "sekolah-" + sekolahId + "/";

        if (urlRendah.startsWith("sekolah-")) {
            pastikanPathMilikTenant(urlRendah, prefixTenant);
            return;
        }
        if (urlRendah.startsWith("/api/storage/")) {
            pastikanPathMilikTenant(ambilPathDariUrlStorage(url), prefixTenant);
            return;
        }
        String path = pathRelatifTerhadapBasis(url, basisPublik);
        if (path != null) {
            pastikanPathMilikTenant(path.toLowerCase(Locale.ROOT), prefixTenant);
        }
    }

    /**
     * Pastikan path objek berada di namespace tenant dan tidak memuat segmen
     * traversal.
     *
     * @param path         path objek (sudah di-decode)
     * @param prefixTenant {@code sekolah-<id>/} milik pemanggil
     * @throws InvalidOperationException bila {@code null}, memuat {@code ..}/
     *                                   backslash, atau prefix tidak cocok
     */
    private static void pastikanPathMilikTenant(String path, String prefixTenant) {
        if (path == null || path.contains("..") || path.contains("\\")
                || !path.startsWith(prefixTenant)) {
            throw new InvalidOperationException("Foto menu harus berada di storage sekolah ini");
        }
    }

    /**
     * Ambil path objek dari URL absolut yang host-nya sama dengan basis publik
     * storage ({@code minio.url-final}).
     *
     * @param url         URL absolut dari request
     * @param basisPublik basis URL publik storage; kosong berarti tidak ada
     *                    URL absolut yang dianggap storage sendiri
     * @return path relatif terhadap basis, atau {@code null} bila host berbeda
     * @throws InvalidOperationException bila host sama tetapi path berada di
     *                                   luar prefix basis
     */
    private static String pathRelatifTerhadapBasis(String url, String basisPublik) {
        if (basisPublik == null || basisPublik.isBlank()) {
            return null;
        }
        URI kandidat;
        URI basis;
        try {
            kandidat = URI.create(url);
            basis = URI.create(basisPublik.trim());
        } catch (IllegalArgumentException e) {
            return null; // bentuk tidak valid → biarkan anotasi yang menolak
        }
        if (kandidat.getHost() == null || basis.getHost() == null
                || !kandidat.getHost().equalsIgnoreCase(basis.getHost())
                || !samaSkema(kandidat, basis)
                || portEfektif(kandidat) != portEfektif(basis)) {
            return null;
        }
        String basisPath = basis.getPath() == null ? "" : basis.getPath();
        String path = kandidat.getPath() == null ? "" : kandidat.getPath();
        String awalan = basisPath.isEmpty() || basisPath.endsWith("/") ? basisPath : basisPath + "/";
        if (!path.startsWith(awalan)) {
            throw new InvalidOperationException("Foto menu harus berada di storage sekolah ini");
        }
        String relatif = path.substring(awalan.length());
        return relatif.startsWith("/") ? relatif.substring(1) : relatif;
    }

    private static boolean samaSkema(URI a, URI b) {
        return a.getScheme() != null && b.getScheme() != null
                && a.getScheme().equalsIgnoreCase(b.getScheme());
    }

    /** Port efektif: skema tanpa port eksplisit memakai default 80/443. */
    private static int portEfektif(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /**
     * Ambil parameter {@code path} dari URL {@code /api/storage/file?path=...}.
     *
     * <p>Query dipecah per {@code &} lalu dicari parameter yang namanya persis
     * {@code path}; pencocokan substring lama membuat {@code ?xpath=...} ikut
     * cocok (temuan review #377).
     *
     * @param url URL storage internal
     * @return path ter-decode, atau {@code null} bila parameter tidak ada
     */
    private static String ambilPathDariUrlStorage(String url) {
        int tandaTanya = url.indexOf('?');
        if (tandaTanya < 0) {
            return null;
        }
        String query = url.substring(tandaTanya + 1);
        for (String bagian : query.split("&")) {
            int samaDengan = bagian.indexOf('=');
            if (samaDengan < 0) {
                continue;
            }
            if ("path".equals(bagian.substring(0, samaDengan))) {
                return URLDecoder.decode(bagian.substring(samaDengan + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
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

    /**
     * Nama menu harus unik di dalam satu sekolah. Tanpa ini, dua baris bernama sama
     * bisa dibuat lewat UI dan kasir tidak tahu harus memilih yang mana.
     *
     * <p>Cakupan sengaja per sekolah: "Ayam Geprek" yang dijual sekolah 1 dan
     * sekolah 10 adalah dua produk sah yang berbeda. Perbandingan tidak peka huruf
     * besar/kecil karena {@code normalisasiNama} hanya merapikan spasi, bukan kapital.
     */
    private void pastikanNamaMenuUnik(Long sekolahId, String nama, Long kecualikanMenuId) {
        boolean sudahDipakai = kecualikanMenuId == null
                ? menuRepo.existsBySekolahIdAndNamaIgnoreCaseAndDeletedAtIsNull(sekolahId, nama)
                : menuRepo.existsBySekolahIdAndNamaIgnoreCaseAndIdNotAndDeletedAtIsNull(
                        sekolahId, nama, kecualikanMenuId);
        if (sudahDipakai) {
            throw new ConflictException("Menu '" + nama + "' sudah ada");
        }
    }
}
