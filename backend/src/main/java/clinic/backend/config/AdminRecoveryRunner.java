package clinic.backend.config;

import clinic.backend.service.AdminRecoveryResult;
import clinic.backend.service.StaffAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Shell-only maintenance command for the Admin recovery procedure defined in docs/security.md. */
@Component
@Profile("admin-recovery")
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AdminRecoveryRunner implements ApplicationRunner {

    private final StaffAccountService staffAccountService;
    private final ConfigurableApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments arguments) {
        AdminRecoveryResult result = staffAccountService.recoverAdmin();

        // This is intentionally direct console output, not application logging. The password is never persisted.
        System.out.println("Admin recovery completed.");
        System.out.println("Username: " + result.username());
        System.out.println("Temporary password (shown once): " + result.temporaryPassword());
        System.out.println("Sign in and change this password immediately.");
        System.out.flush();

        applicationContext.close();
    }
}
