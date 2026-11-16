package com.asqi.scholia_kantin_be.arsitektur;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

/**
 * Penjaga aturan isolasi tenant (OWASP A01 — Broken Access Control).
 *
 * <p>Seluruh endpoint data WAJIB mengambil {@code sekolahId} dari
 * {@code TenantContext} (diisi dari JWT), bukan dari klien. Aturan ini dulu
 * hanya kesepakatan lisan: controller baru yang lupa menyentuh TenantContext
 * akan lolos review dan membuka kebocoran lintas sekolah. Sekarang build gagal.
 *
 * <p>Dua controller sengaja dikecualikan karena berjalan SEBELUM token ada:
 * {@code AuthController} (login) dan {@code DevLoginController} (shim lokal).
 */
@AnalyzeClasses(
        packages = "com.asqi.scholia_kantin_be",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArsitekturTenantArchTest {

    @ArchTest
    static final ArchRule controllerWajibMenyentuhTenantContext = classes()
            .that().areAnnotatedWith(RestController.class)
            .and().haveSimpleNameNotEndingWith("AuthController")
            .and().haveSimpleNameNotEndingWith("DevLoginController")
            .should().dependOnClassesThat().haveSimpleName("TenantContext")
            .because("sekolahId harus berasal dari JWT (TenantContext), bukan dari klien — A01")
            .allowEmptyShould(false);
}
