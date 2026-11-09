package clinic.backend.config;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import clinic.backend.service.LongTermPrescriptionDiseaseImportService;
import clinic.backend.service.LongTermPrescriptionDiseaseImportService.InvalidListException;
import clinic.backend.service.LongTermPrescriptionDiseaseImportService.Result;
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
 * Operator command that loads the long-term prescription disease list (TT 26/2025/TT-BYT Phụ lục VII) and exits
 * without opening an HTTP port (README, "Load the long-term prescription disease list"). Run it after the ICD-10
 * import. A rejected file is reported line by line and nothing is written.
 */
@Component
@Profile("long-term-disease-import")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LongTermDiseaseImportRunner implements ApplicationRunner {

    private final LongTermPrescriptionDiseaseImportService importService;
    private final ResourceLoader resourceLoader;
    private final ConfigurableApplicationContext applicationContext;
    private final String file;
    private final LocalDate effectiveFrom;
    private final String source;

    public LongTermDiseaseImportRunner(
            LongTermPrescriptionDiseaseImportService importService,
            ResourceLoader resourceLoader,
            ConfigurableApplicationContext applicationContext,
            @Value("${clinic.long-term-disease-import.file}") String file,
            @Value("${clinic.long-term-disease-import.effective-from}") LocalDate effectiveFrom,
            @Value("${clinic.long-term-disease-import.source}") String source) {
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
            throw new IllegalStateException("Long-term prescription disease file not found: " + file);
        }
        // Direct console output: this is a shell command whose operator reads the result.
        System.out.println("Importing long-term prescription disease list " + file + " (" + source
                + ", effective " + effectiveFrom + ")");
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            Result result = importService.importList(reader, effectiveFrom, source);
            System.out.printf("Import completed: %d rows in the file, %d created, %d updated, %d unchanged,"
                            + " %d retired; %d ICD-10 codes linked.%n",
                    result.total(), result.created(), result.updated(), result.unchanged(), result.retired(),
                    result.codes());
            if (!result.warnings().isEmpty()) {
                System.out.println("Codes not in the ICD-10 catalog (review the list):");
                result.warnings().forEach(warning -> System.out.println("  " + warning));
            }
        } catch (InvalidListException ex) {
            System.out.println("Import rejected, nothing was written:");
            ex.errors().forEach(error -> System.out.println("  " + error));
            System.out.flush();
            throw ex;
        }
        System.out.flush();
        applicationContext.close();
    }
}
