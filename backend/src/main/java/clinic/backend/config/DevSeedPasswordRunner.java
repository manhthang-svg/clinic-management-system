package clinic.backend.config;

import clinic.backend.config.properties.DevSeedProperties;
import clinic.backend.service.StaffAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Sets the password of the seeded demo accounts from DEV_SEED_PASSWORD; development profile only.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevSeedPasswordRunner implements ApplicationRunner {

    private final StaffAccountService staffAccountService;
    private final DevSeedProperties properties;

    @Override
    public void run(ApplicationArguments arguments) {
        if (!properties.hasPassword()) {
            log.warn("DEV_SEED_PASSWORD is not set; seeded demo accounts cannot log in");
            return;
        }
        int updated = staffAccountService.assignDemoAccountPassword(properties.password());
        if (updated > 0) {
            log.info("Demo account password set [accounts={}]", updated);
        }
    }
}
