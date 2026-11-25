package com.asqi.scholia_kantin_be.arsitektur;

import com.asqi.scholia_kantin_be.payload.response.Response;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.ResolvableType;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Menjaga kontrak API: entity JPA tidak boleh menjadi payload respons.
 *
 * <p>{@code CONVENTIONS.md §2} melarang entity keluar dari layer service.
 * Aturan ArchUnit di {@link ArsitekturLayerArchTest} mengecek paket dan
 * ketergantungan antar kelas; tuple generik pada {@code ResponseEntity} tidak
 * terlihat dari bytecode, jadi pemeriksaan ini memakai reflection.
 */
@DisplayName("Kontrak respons API bebas entity JPA")
class ArsitekturResponsApiTest {
    /** Paket yang dipindai untuk kelas beranotasi {@link RestController}. */
    private static final String PAKET_AKAR = "com.asqi.scholia_kantin_be";

    /**
     * Memastikan semua endpoint mengembalikan DTO, bukan entity.
     *
     * @throws ClassNotFoundException bila kelas hasil pemindaian tidak bisa dimuat
     */
    @Test
    @DisplayName("Response<T> tidak boleh berupa entity atau memuat field entity")
    void responsTidakMemuatEntity() throws ClassNotFoundException {
        List<String> pelanggaran = new ArrayList<>();
        for (Class<?> controller : daftarController()) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!ResponseEntity.class.isAssignableFrom(method.getReturnType())) {
                    continue;
                }
                ResolvableType amplop = ResolvableType.forMethodReturnType(method).getGeneric(0);
                Class<?> tipeAmplop = amplop.resolve();
                if (tipeAmplop == null || !Response.class.isAssignableFrom(tipeAmplop)) {
                    continue;
                }
                Class<?> payload = amplop.getGeneric(0).resolve();
                if (mengandungEntity(payload)) {
                    pelanggaran.add(controller.getSimpleName() + "#" + method.getName()
                            + " → " + (payload == null ? "?" : payload.getSimpleName()));
                }
            }
        }
        assertThat(pelanggaran)
                .as("Endpoint yang mengirim entity JPA langsung (CONVENTIONS.md §2)")
                .isEmpty();
    }

    /**
     * Cari controller produksi dan shim dev dari classpath.
     *
     * @return kumpulan kelas beranotasi RestController
     * @throws ClassNotFoundException bila kelas hasil pemindaian tidak bisa dimuat
     */
    private Set<Class<?>> daftarController() throws ClassNotFoundException {
        Set<Class<?>> hasil = new TreeSet<>(java.util.Comparator.comparing(Class::getName));
        var pemindai = new ClassPathScanningCandidateComponentProvider(false);
        pemindai.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (var kandidat : pemindai.findCandidateComponents(PAKET_AKAR)) {
            hasil.add(Class.forName(kandidat.getBeanClassName()));
        }
        assertThat(hasil).as("Tidak ada controller yang terpindai").isNotEmpty();
        return hasil;
    }

    /**
     * Cek apakah tipe payload adalah entity atau memiliki field entity.
     *
     * @param tipe tipe payload; boleh null
     * @return true bila entity ditemukan pada tipe atau field-nya sendiri
     */
    private boolean mengandungEntity(Class<?> tipe) {
        if (tipe == null || tipe.getName().startsWith("java.")) {
            return false;
        }
        if (tipe.isAnnotationPresent(Entity.class)) {
            return true;
        }
        for (Field field : tipe.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    && field.getType().isAnnotationPresent(Entity.class)) {
                return true;
            }
        }
        return false;
    }
}
