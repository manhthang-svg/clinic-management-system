package clinic.backend.config;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import clinic.backend.service.Icd10ImportService;
import clinic.backend.service.Icd10ImportService.InvalidCatalogException;
import clinic.backend.service.Icd10ImportService.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Operator command that loads the ICD-10 list and exits without opening an HTTP port (README, "Load the ICD-10
 * list"). A rejected file is reported line by line and nothing is written.
 */
@Component
@Profile("icd10-import")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class Icd10ImportRunner implements ApplicationRunner {

    private final Icd10ImportService importService;
    private final ResourceLoader resourceLoader;
    private final ConfigurableApplicationContext applicationContext;
    private final String file;
    private final LocalDate effectiveFrom;
    private final String source;

    public Icd10ImportRunner(
            Icd10ImportService importService,
            ResourceLoader resourceLoader,
            ConfigurableApplicationContext applicationContext,
            @Value("${clinic.icd10-import.file}") String file,
            @Value("${clinic.icd10-import.effective-from}") LocalDate effectiveFrom,
            @Value("${clinic.icd10-import.source}") String source) {
        this.importService = importService;
        this.resourceLoader = resourceLoader;
        this.applicationContext = applicationContext;
        this.file = file;
        this.effectiveFrom = effectiveFrom;
        this.source = source;
    }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        Resource resource = resourceLoader.getResource(file);
        if (!resource.exists()) {
            throw new IllegalStateException("ICD-10 file not found: " + file);
        }
        // Direct console output: this is a shell command whose operator reads the result.
        System.out.println("Importing ICD-10 list " + file + " (" + source + ", effective " + effectiveFrom + ")");
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            Result result = importService.importCatalog(reader, effectiveFrom, source);
            System.out.printf("ICD-10 import completed: %d codes in the file, %d created, %d updated,"
                            + " %d unchanged, %d retired.%n",
                    result.total(), result.created(), result.updated(), result.unchanged(), result.retired());
        } catch (InvalidCatalogException ex) {
            System.out.println("ICD-10 import rejected, nothing was written:");
            ex.errors().forEach(error -> System.out.println("  " + error));
            System.out.flush();
            throw ex;
        }
        System.out.flush();
        applicationContext.close();
    }
}
