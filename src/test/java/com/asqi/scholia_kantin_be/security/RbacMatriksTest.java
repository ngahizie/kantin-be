package com.asqi.scholia_kantin_be.security;

import com.asqi.scholia_kantin_be.enums.AktorKantin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Penjaga matriks RBAC (issue <b>#250</b>) — {@code architecture/RBAC-MATRIX.md}
 * adalah <b>sumber kebenaran tunggal</b> untuk peran → endpoint.
 *
 * <p><b>Masalah yang dicegah:</b> anotasi {@code @PerluPeran} tumbuh panjang
 * dan longgar daripada matriks RBAC produk (PRD §9.5/§9.6) — admin/pengelola/TU
 * mendapat hak aksi uang, kasir, dan laporan yang tidak ada di PRD. Audit
 * manual tidak bisa menangkap endpoint baru yang diam-diam terlalu longgar.
 *
 * <p><b>Kontrak:</b> tiap baris tabel
 * {@code | `METHOD` | `/api/...` | peran | acuan PRD | keputusan |} menyatakan
 * satu endpoint. Uji <b>gagal</b> bila:
 * <ol>
 *   <li>ada endpoint di kode yang belum terdaftar di matriks;</li>
 *   <li>ada baris matriks yang tidak lagi ada di kode;</li>
 *   <li>himpunan peran pada baris ≠ anotasi {@code @PerluPeran} di kode;</li>
 *   <li>kolom <i>keputusan</i> kosong — endpoint baru wajib punya keputusan
 *       tertulis (PRD diikuti / di luar keputusan).</li>
 * </ol>
 *
 * <p><b>Cara memperbaiki bila gagal:</b> jalankan uji, baca daftar endpoint
 * yang tidak sinkron, lalu perbarui <b>anotasi di kode</b> dan baris matriks
 * secara berpasangan. Menambah hak akses tanpa keputusan produk = over-grant
 * yang kembali.
 */
@DisplayName("Matriks RBAC sinkron dengan anotasi @PerluPeran (#250)")
class RbacMatriksTest {

    /** Paket controller produksi yang dipindai. Shim `dev` di luar scope. */
    private static final List<String> PAKET_CONTROLLER = List.of(
            "com/asqi/scholia_kantin_be/controller");

    /** Lokasi dokumen relatif root proyek (Maven menjalankan test dari root). */
    private static final String[] KANDIDAT_DOKUMEN = {
            "architecture/RBAC-MATRIX.md",
            "../architecture/RBAC-MATRIX.md"};

    /** Baris tabel: | `GET` | `/api/...` | peran | acuan | keputusan | */
    private static final Pattern BARIS_MATRIX = Pattern.compile(
            "^\\|\\s*`(GET|POST|PUT|DELETE|PATCH)`\\s*\\|\\s*`([^`]+)`\\s*\\|([^|]*)\\|([^|]*)\\|([^|]*)\\|");

    /** Peran di matriks → {@link AktorKantin}. */
    private static final Map<String, AktorKantin> PERAN = Map.of(
            "kasir", AktorKantin.PETUGAS_KANTIN,
            "pengelola", AktorKantin.PENGELOLA_KANTIN,
            "tu", AktorKantin.TU_SEKOLAH,
            "admin", AktorKantin.ADMIN_SEKOLAH,
            "kepsek", AktorKantin.KEPSEK,
            "ortu", AktorKantin.ORANG_TUA);

    @Test
    @DisplayName("Anotasi @PerluPeran di kode == matriks RBAC.md")
    void matriksSinkronDenganKode() {
        Map<String, Set<AktorKantin>> dariKode = endpointDariKode();
        Map<String, Baris> dariDokumen = barisDariDokumen();

        Set<String> undocumented = new TreeSet<>(dariKode.keySet());
        undocumented.removeAll(dariDokumen.keySet());
        Set<String> sudahTidakAda = new TreeSet<>(dariDokumen.keySet());
        sudahTidakAda.removeAll(dariKode.keySet());

        assertThat(undocumented)
                .as("Endpoint di kode tapi belum ada di RBAC-MATRIX.md — tambahkan barisnya "
                        + "(peran + keputusan tertulis)")
                .isEmpty();
        assertThat(sudahTidakAda)
                .as("Baris RBAC-MATRIX.md tapi endpoint-nya sudah tidak ada di kode")
                .isEmpty();

        for (Map.Entry<String, Set<AktorKantin>> e : dariKode.entrySet()) {
            Baris baris = dariDokumen.get(e.getKey());
            if (baris == null) {
                continue;
            }
            assertThat(baris.peran())
                    .as("Peran di RBAC-MATRIX.md untuk %s", e.getKey())
                    .isEqualTo(e.getValue());
        }
    }

    @Test
    @DisplayName("Setiap baris matriks punya keputusan tertulis")
    void setiapBarisPunyaKeputusan() {
        Map<String, Baris> dariDokumen = barisDariDokumen();
        assertThat(dariDokumen).as("Matriks RBAC kosong — dokumen tidak terbaca").isNotEmpty();

        List<String> tanpaKeputusan = new ArrayList<>();
        for (Map.Entry<String, Baris> e : dariDokumen.entrySet()) {
            String keputusan = e.getValue().keputusan().trim();
            if (keputusan.isEmpty() || keputusan.equals("—")) {
                tanpaKeputusan.add(e.getKey());
            }
        }
        assertThat(tanpaKeputusan)
                .as("Baris tanpa keputusan tertulis — isi kolom keputusan "
                        + "(mis. 'PRD diikuti' atau 'di luar keputusan #250 — dipertahankan')")
                .isEmpty();
    }

    // ────────────────────────────────────────────────────────────────
    // Endpoint nyata (refleksi)
    // ────────────────────────────────────────────────────────────────

    private Map<String, Set<AktorKantin>> endpointDariKode() {
        Map<String, Set<AktorKantin>> hasil = new LinkedHashMap<>();
        for (Class<?> kelas : kelasController()) {
            String base = basePath(kelas);
            for (var metode : kelas.getDeclaredMethods()) {
                RequestMapping rm =
                        AnnotatedElementUtils.findMergedAnnotation(metode, RequestMapping.class);
                if (rm == null || rm.method().length == 0) {
                    continue;
                }
                String penuh = gabung(base, pathPertama(rm.path(), rm.value()));
                PerluPeran perluPeran =
                        AnnotatedElementUtils.findMergedAnnotation(metode, PerluPeran.class);
                Set<AktorKantin> peran = perluPeran == null
                        ? Set.of()
                        : new LinkedHashSet<>(Arrays.asList(perluPeran.value()));
                for (RequestMethod m : rm.method()) {
                    hasil.put(m.name() + " " + penuh, peran);
                }
            }
        }
        return hasil;
    }

    // ────────────────────────────────────────────────────────────────
    // Baris dokumen
    // ────────────────────────────────────────────────────────────────

    private record Baris(Set<AktorKantin> peran, String acuan, String keputusan) {
    }

    private Map<String, Baris> barisDariDokumen() {
        Path dokumen = Arrays.stream(KANDIDAT_DOKUMEN)
                .map(Paths::get)
                .filter(Files::exists)
                .findFirst()
                .orElseGet(() -> {
                    Path dir = Paths.get("").toAbsolutePath();
                    for (int i = 0; i < 5 && dir != null; i++) {
                        Path kandidat = dir.resolve("architecture/RBAC-MATRIX.md");
                        if (Files.exists(kandidat)) {
                            return kandidat;
                        }
                        dir = dir.getParent();
                    }
                    throw new AssertionError(
                            "architecture/RBAC-MATRIX.md tidak ditemukan dari "
                                    + Path.of(".").toAbsolutePath());
                });
        Map<String, Baris> hasil = new LinkedHashMap<>();
        try {
            for (String baris : Files.readAllLines(dokumen, StandardCharsets.UTF_8)) {
                Matcher m = BARIS_MATRIX.matcher(baris);
                if (!m.matches()) {
                    continue;
                }
                String key = m.group(1) + " " + m.group(2);
                hasil.put(key, new Baris(peranDariTeks(m.group(3)), m.group(4), m.group(5)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return hasil;
    }

    /** Teks peran matriks → himpunan aktor. `—` = tanpa `@PerluPeran`. */
    private Set<AktorKantin> peranDariTeks(String teks) {
        Set<AktorKantin> hasil = new LinkedHashSet<>();
        String isi = teks.trim();
        if (isi.isEmpty() || isi.equals("—")) {
            return hasil;
        }
        for (String nama : isi.split(",")) {
            String kunci = nama.trim();
            if (kunci.isEmpty()) {
                continue;
            }
            AktorKantin aktor = PERAN.get(kunci);
            assertThat(aktor)
                    .as("Peran '%s' di RBAC-MATRIX.md tidak dikenal — gunakan: %s",
                            kunci, PERAN.keySet())
                    .isNotNull();
            hasil.add(aktor);
        }
        return hasil;
    }

    // ────────────────────────────────────────────────────────────────
    // Pemindaian classpath
    // ────────────────────────────────────────────────────────────────

    private Set<Class<?>> kelasController() {
        var resolver = new PathMatchingResourcePatternResolver();
        Set<Class<?>> kelas = new LinkedHashSet<>();
        for (String paket : PAKET_CONTROLLER) {
            Resource[] resources;
            try {
                resources = resolver.getResources("classpath*:" + paket + "/*.class");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            for (Resource r : resources) {
                String nama = namaKelas(r, paket);
                if (nama == null) {
                    continue;
                }
                try {
                    Class<?> c = Class.forName(nama, false,
                            Thread.currentThread().getContextClassLoader());
                    if (c.isAnnotationPresent(RestController.class)
                            || c.isAnnotationPresent(Controller.class)) {
                        kelas.add(c);
                    }
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Kelas controller tak ditemukan: " + nama, e);
                }
            }
        }
        assertThat(kelas)
                .as("Tidak ada kelas controller yang terpindai — periksa PAKET_CONTROLLER.")
                .isNotEmpty();
        return kelas;
    }

    private String namaKelas(Resource r, String paket) {
        String path;
        try {
            path = r.getURL().getPath();
        } catch (IOException e) {
            return null;
        }
        int idx = path.indexOf(paket + "/");
        if (idx < 0) {
            return null;
        }
        String relatif = path.substring(idx + paket.length() + 1);
        if (!relatif.endsWith(".class") || relatif.contains("$")) {
            return null;
        }
        return paket.replace('/', '.') + "." + relatif.substring(0, relatif.length() - 6);
    }

    private String basePath(Class<?> kelas) {
        RequestMapping rm =
                AnnotatedElementUtils.findMergedAnnotation(kelas, RequestMapping.class);
        if (rm == null) {
            return "";
        }
        return pathPertama(rm.path(), rm.value());
    }

    private String pathPertama(String[] paths, String[] values) {
        if (paths != null && paths.length > 0 && !paths[0].isEmpty()) {
            return paths[0];
        }
        if (values != null && values.length > 0) {
            return values[0];
        }
        return "";
    }

    private String gabung(String base, String sub) {
        String b = base == null ? "" : base.trim();
        String s = sub == null ? "" : sub.trim();
        String penuh = (b + "/" + s).replaceAll("/{2,}", "/");
        if (penuh.startsWith("/")) {
            return penuh;
        }
        return "/" + penuh;
    }
}
