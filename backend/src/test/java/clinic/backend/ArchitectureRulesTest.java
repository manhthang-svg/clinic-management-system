package clinic.backend;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "clinic.backend",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    private static final String CONTROLLER = "clinic.backend.controller..";
    private static final String DTO = "clinic.backend.dto..";
    private static final String SERVICE = "clinic.backend.service..";
    private static final String REPOSITORY = "clinic.backend.repository..";
    private static final String MODEL = "clinic.backend.model..";
    private static final String ENUMS = "clinic.backend.enums..";
    private static final String ENTITY = "clinic.backend.entity..";
    private static final String CONFIG = "clinic.backend.config..";
    private static final String SECURITY = "clinic.backend.security..";
    private static final String EXCEPTION = "clinic.backend.exception..";

    @ArchTest
    static final ArchRule model_must_not_depend_on_framework_or_other_layers = noClasses()
            .that().resideInAPackage(MODEL)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    CONTROLLER, DTO, SERVICE, REPOSITORY, ENTITY, CONFIG, SECURITY, EXCEPTION)
            .because("domain models must remain independent from frameworks and other layers");

    @ArchTest
    static final ArchRule enums_must_not_depend_on_framework_or_application_layers = noClasses()
            .that().resideInAPackage(ENUMS)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    CONTROLLER, DTO, SERVICE, REPOSITORY, MODEL, ENTITY, CONFIG, SECURITY, EXCEPTION)
            .because("shared domain enums must remain dependency-free stable types");

    @ArchTest
    static final ArchRule services_must_not_depend_on_web_or_persistence_details = noClasses()
            .that().resideInAPackage(SERVICE)
            .should().dependOnClassesThat().resideInAnyPackage(CONTROLLER, DTO, ENTITY, CONFIG, EXCEPTION)
            .because("services orchestrate use cases through models and repository/security contracts only");

    @ArchTest
    static final ArchRule services_must_use_repository_contracts_not_jpa_implementations = noClasses()
            .that().resideInAPackage(SERVICE)
            .should().dependOnClassesThat().haveSimpleNameStartingWith("Jpa")
            .orShould().dependOnClassesThat().haveSimpleNameEndingWith("JpaRepository")
            .because("services must depend on repository interfaces, not on JPA adapters");

    @ArchTest
    static final ArchRule controllers_must_not_depend_on_persistence = noClasses()
            .that().resideInAPackage(CONTROLLER)
            .should().dependOnClassesThat().resideInAnyPackage(REPOSITORY, ENTITY)
            .because("controllers must remain thin and must never access repositories or JPA entities directly");

    @ArchTest
    static final ArchRule controllers_must_not_depend_on_repositories = noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
            .because("controllers must remain thin and must never access repositories directly");

    @ArchTest
    static final ArchRule dtos_must_not_expose_persistence_or_web_layers = noClasses()
            .that().resideInAPackage(DTO)
            .should().dependOnClassesThat().resideInAnyPackage(CONTROLLER, REPOSITORY, ENTITY, CONFIG)
            .because("API contracts must stay independent from persistence models");

    @ArchTest
    static final ArchRule entities_must_not_depend_on_upper_layers = noClasses()
            .that().resideInAPackage(ENTITY)
            .should().dependOnClassesThat().resideInAnyPackage(CONTROLLER, DTO, SERVICE, CONFIG)
            .because("JPA entities are persistence details and must not know about web or use-case code");

    @ArchTest
    static final ArchRule nothing_but_web_layers_may_depend_on_controllers = noClasses()
            .that().resideOutsideOfPackages(CONTROLLER, CONFIG)
            .should().dependOnClassesThat().resideInAPackage(CONTROLLER)
            .because("controllers are the outermost layer");
}
