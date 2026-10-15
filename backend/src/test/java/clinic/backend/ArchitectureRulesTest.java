package clinic.backend;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "clinic.backend",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    private static final String CONTROLLER = "clinic.backend.controller..";
    private static final String DTO = "clinic.backend.dto..";
    private static final String SERVICE = "clinic.backend.service..";
    private static final String SERVICE_IMPL = "clinic.backend.service.impl..";
    private static final String REPOSITORY = "clinic.backend.repository..";
    private static final String ENTITY = "clinic.backend.entity..";
    private static final String ENUMS = "clinic.backend.enums..";
    private static final String MAPPER = "clinic.backend.mapper..";
    private static final String CONFIG = "clinic.backend.config..";
    private static final String SECURITY = "clinic.backend.security..";
    private static final String EXCEPTIONS = "clinic.backend.exceptions..";
    private static final String UTILS = "clinic.backend.utils..";

    @ArchTest
    static final ArchRule controllers_must_not_depend_on_persistence = noClasses()
            .that().resideInAPackage(CONTROLLER)
            .should().dependOnClassesThat().resideInAnyPackage(REPOSITORY, ENTITY)
            .because("controllers must remain thin and must never access repositories or JPA entities directly");

    @ArchTest
    static final ArchRule controllers_must_use_service_interfaces = noClasses()
            .that().resideInAPackage(CONTROLLER)
            .should().dependOnClassesThat().resideInAPackage(SERVICE_IMPL)
            .because("controllers depend on service interfaces, not on their implementations");

    @ArchTest
    static final ArchRule only_services_may_use_repositories = noClasses()
            .that().resideOutsideOfPackages(SERVICE, REPOSITORY)
            .should().dependOnClassesThat().resideInAPackage(REPOSITORY)
            .because("repositories are accessed only through services, which own the transaction boundary");

    @ArchTest
    static final ArchRule services_must_not_depend_on_controllers = noClasses()
            .that().resideInAPackage(SERVICE)
            .should().dependOnClassesThat().resideInAPackage(CONTROLLER)
            .because("services must not know about the web layer that calls them");

    @ArchTest
    static final ArchRule repositories_must_only_know_entities = noClasses()
            .that().resideInAPackage(REPOSITORY)
            .should().dependOnClassesThat().resideInAnyPackage(
                    CONTROLLER, DTO, SERVICE, MAPPER, CONFIG, SECURITY, EXCEPTIONS, UTILS)
            .because("repositories are persistence contracts over entities and enums only");

    @ArchTest
    static final ArchRule entities_must_not_depend_on_upper_layers = noClasses()
            .that().resideInAPackage(ENTITY)
            .should().dependOnClassesThat().resideInAnyPackage(
                    CONTROLLER, DTO, SERVICE, REPOSITORY, MAPPER, CONFIG, SECURITY, EXCEPTIONS, UTILS)
            .because("JPA entities hold persistent state and business rules, not web or use-case code");

    @ArchTest
    static final ArchRule enums_must_not_depend_on_application_layers = noClasses()
            .that().resideInAPackage(ENUMS)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta.persistence..",
                    CONTROLLER, DTO, SERVICE, REPOSITORY, ENTITY, MAPPER, CONFIG, SECURITY, EXCEPTIONS, UTILS)
            .because("shared enums must remain stable, dependency-free types");

    @ArchTest
    static final ArchRule dtos_must_not_expose_persistence_or_other_layers = noClasses()
            .that().resideInAPackage(DTO)
            .should().dependOnClassesThat().resideInAnyPackage(
                    CONTROLLER, SERVICE, REPOSITORY, ENTITY, MAPPER, CONFIG, SECURITY)
            .because("API contracts must stay independent from persistence models and implementation classes");

    @ArchTest
    static final ArchRule mappers_must_only_translate = noClasses()
            .that().resideInAPackage(MAPPER)
            .should().dependOnClassesThat().resideInAnyPackage(CONTROLLER, REPOSITORY, SERVICE_IMPL, CONFIG)
            .because("mappers convert between models and DTOs without performing use-case work");

    @ArchTest
    static final ArchRule utils_must_not_depend_on_business_layers = noClasses()
            .that().resideInAPackage(UTILS)
            .should().dependOnClassesThat().resideInAnyPackage(
                    CONTROLLER, DTO, SERVICE, REPOSITORY, ENTITY, MAPPER, EXCEPTIONS)
            .because("utils hold small technical helpers, never business behavior");

    @ArchTest
    static final ArchRule nothing_but_configuration_may_depend_on_controllers = noClasses()
            .that().resideOutsideOfPackages(CONTROLLER, CONFIG)
            .should().dependOnClassesThat().resideInAPackage(CONTROLLER)
            .because("controllers are the outermost layer");
}
