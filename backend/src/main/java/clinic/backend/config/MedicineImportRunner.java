package clinic.backend.config;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import clinic.backend.service.MedicineCatalogImportService;
import clinic.backend.service.MedicineCatalogImportService.InvalidListException;
import clinic.backend.service.MedicineCatalogImportService.Result;
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
 * Operator command that loads a medicine list into the medicine catalog and exits without opening an HTTP port
 * (README, "Load the medicine catalog"). A rejected file is reported line by line and nothing is written.
 */
@Component
@Profile("medicine-import")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MedicineImportRunner implements ApplicationRunner {

    private final MedicineCatalogImportService importService;
    private final ResourceLoader resourceLoader;
    private final ConfigurableApplicationContext applicationContext;
    private final String file;

    public MedicineImportRunner(
            MedicineCatalogImportService importService,
            ResourceLoader resourceLoader,
            ConfigurableApplicationContext applicationContext,
            @Value("${clinic.medicine-import.file}") String file) {
        this.importService = importService;
        this.resourceLoader = resourceLoader;
        this.applicationContext = applicationContext;
        this.file = file;
    }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        Resource resource = resourceLoader.getResource(file);
        if (!resource.exists()) {
            throw new IllegalStateException("Medicine list not found: " + file);
        }
        // Direct console output: this is a shell command whose operator reads the result.
        System.out.println("Importing medicine list " + file);
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            Result result = importService.importList(reader);
            System.out.printf("Medicine import completed: %d medicines in the file, %d created, %d updated,"
                    + " %d unchanged.%n", result.total(), result.created(), result.updated(), result.unchanged());
        } catch (InvalidListException ex) {
            System.out.println("Medicine import rejected, nothing was written:");
            ex.errors().forEach(error -> System.out.println("  " + error));
            System.out.flush();
            throw ex;
        }
        System.out.flush();
        applicationContext.close();
    }
}
