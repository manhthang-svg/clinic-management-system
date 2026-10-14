package clinic.backend.support;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for integration tests against a real PostgreSQL with Flyway migrations applied.
 *
 * <p>One container is shared by all test classes so cached Spring contexts stay valid. Tests are
 * skipped when Docker is unavailable. The JWT key is random per run and never committed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresIntegrationTest {

    public static final String FRONTEND_ORIGIN = "http://localhost:5173";

    private static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine");
    private static final String SIGNING_KEY = randomSigningKey();

    @DynamicPropertySource
    static void integrationProperties(DynamicPropertyRegistry registry) {
        POSTGRESQL.start();
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("clinic.security.jwt.signing-key", () -> SIGNING_KEY);
        registry.add("clinic.security.frontend-origin", () -> FRONTEND_ORIGIN);
        // Low BCrypt cost keeps tests fast; production uses the configured strength.
        registry.add("clinic.security.password.bcrypt-strength", () -> "4");
    }

    private static String randomSigningKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
