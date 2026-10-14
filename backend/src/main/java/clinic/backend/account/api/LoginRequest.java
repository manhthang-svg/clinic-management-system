package clinic.backend.account.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login form input. Only transport limits are checked here; credential checks belong to the use case.
 */
public record LoginRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(max = 200) String password) {

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=redacted]";
    }
}
