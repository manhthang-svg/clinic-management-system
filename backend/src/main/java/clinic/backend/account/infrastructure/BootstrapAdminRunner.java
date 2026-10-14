package clinic.backend.account.infrastructure;

import clinic.backend.account.application.AdminBootstrapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first Admin at startup when none exists and bootstrap settings are present (AQ-10).
 */
@Component
class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final AdminBootstrapService bootstrapService;
    private final BootstrapAdminProperties properties;

    BootstrapAdminRunner(AdminBootstrapService bootstrapService, BootstrapAdminProperties properties) {
        this.bootstrapService = bootstrapService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!properties.isConfigured()) {
            return;
        }
        bootstrapService
                .createFirstAdminIfMissing(properties.username(), properties.password(), properties.fullName())
                .ifPresent(admin -> LOGGER.info(
                        "Bootstrap Admin account created [accountId={}]; its password must be changed at first login",
                        admin.id()));
    }
}
