package clinic.backend.config;

import clinic.backend.service.DemoAccountPasswordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Sets the password of the seeded demo accounts from DEV_SEED_PASSWORD; development profile only.
 */
@Component
@Profile("dev")
class DevSeedPasswordRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DevSeedPasswordRunner.class);

    private final DemoAccountPasswordService passwordService;
    private final DevSeedProperties properties;

    DevSeedPasswordRunner(DemoAccountPasswordService passwordService, DevSeedProperties properties) {
        this.passwordService = passwordService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!properties.hasPassword()) {
            LOGGER.warn("DEV_SEED_PASSWORD is not set; seeded demo accounts cannot log in");
            return;
        }
        int updated = passwordService.assignPassword(properties.password());
        if (updated > 0) {
            LOGGER.info("Demo account password set [accounts={}]", updated);
        }
    }
}
