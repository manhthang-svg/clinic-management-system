package clinic.backend.config;

import clinic.backend.config.properties.BootstrapAdminProperties;
import clinic.backend.service.StaffAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Creates the first Admin at startup when none exists and bootstrap settings are present.
 */
@Slf4j
@Component
@Profile("!admin-recovery & !icd10-import")
@RequiredArgsConstructor
public class BootstrapAdminRunner implements ApplicationRunner {

    private final StaffAccountService staffAccountService;
    private final BootstrapAdminProperties properties;

    @Override
    public void run(ApplicationArguments arguments) {
        if (!properties.isConfigured()) {
            return;
        }
        staffAccountService
                .createFirstAdminIfMissing(properties.username(), properties.password(), properties.fullName())
                .ifPresent(admin -> log.info(
                        "Bootstrap Admin account created [accountId={}]; its password must be changed at first login",
                        admin.getId()));
    }
}
