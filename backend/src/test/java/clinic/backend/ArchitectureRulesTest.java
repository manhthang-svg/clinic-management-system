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

    @ArchTest
    static final ArchRule domain_must_not_depend_on_framework_or_outer_layers = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "..api..",
                    "..infrastructure..")
            .because("domain code must remain independent from frameworks and outer layers")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule application_must_not_depend_on_api_or_infrastructure = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure..")
            .because("application code may depend on domain code and define ports, but not on adapters")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule api_must_not_depend_on_infrastructure = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .because("API adapters must invoke application contracts instead of infrastructure")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule controllers_must_not_depend_on_repositories = noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
            .because("controllers must remain thin and must never access repositories directly")
            .allowEmptyShould(true);
}
