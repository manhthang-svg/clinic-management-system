package clinic.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * First-Admin bootstrap settings. Both username and password must be set for bootstrap to run.
 */
@ConfigurationProperties(prefix = "clinic.bootstrap-admin")
public record BootstrapAdminProperties(String username, String password, String fullName) {

    public boolean isConfigured() {
        return username != null && !username.isBlank() && password != null && !password.isEmpty();
    }

    @Override
    public String toString() {
        return "BootstrapAdminProperties[username=" + username + ", password=redacted]";
    }
}
