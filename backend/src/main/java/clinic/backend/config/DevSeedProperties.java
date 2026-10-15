package clinic.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Development seed settings (docs/demo-master-data.md). The password comes from DEV_SEED_PASSWORD.
 */
@ConfigurationProperties(prefix = "clinic.dev-seed")
public record DevSeedProperties(String password) {

    boolean hasPassword() {
        return password != null && !password.isEmpty();
    }

    @Override
    public String toString() {
        return "DevSeedProperties[password=redacted]";
    }
}
