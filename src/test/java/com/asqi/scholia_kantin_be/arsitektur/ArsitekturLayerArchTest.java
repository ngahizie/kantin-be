package com.asqi.scholia_kantin_be.arsitektur;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Penjaga batas antar layer selain isolasi tenant.
 *
 * <p>Aturan tenant ada di {@link ArsitekturTenantArchTest}; kelas ini
 * mencegah controller mengakses repository langsung, entity tumpah ke paket
 * lain, dan kelas berakhiran tertentu menumpang di paket yang salah.
 */
@AnalyzeClasses(
        packages = "com.asqi.scholia_kantin_be",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArsitekturLayerArchTest {
    /** Controller hanya boleh memanggil service, bukan repository. */
    @ArchTest
    static final ArchRule controllerTidakMenyentuhRepository = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .because("akses data wajib lewat service — controller tipis")
            .allowEmptyShould(false);

    /** Entity JPA hanya boleh didefinisikan di paket model. */
    @ArchTest
    static final ArchRule entityHanyaDiPaketModel = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..model..")
            .because("entity di luar model/ merusak batas layer")
            .allowEmptyShould(false);

    /** Endpoint HTTP hanya ada di controller produksi atau shim dev. */
    @ArchTest
    static final ArchRule controllerHanyaDiPaketControllerAtauDev = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAnyPackage("..controller..", "..dev..")
            .because("semua @RestController wajib berkumpul di controller/ atau dev/")
            .allowEmptyShould(false);

    /** Kontrak repository hanya boleh didefinisikan di paket repository. */
    @ArchTest
    static final ArchRule repositoryHanyaDiPaketRepository = classes()
            .that().haveSimpleNameEndingWith("Repository")
            .should().resideInAPackage("..repository..")
            .because("interface *Repository di luar repository/ sulit ditemukan")
            .allowEmptyShould(false);
}
