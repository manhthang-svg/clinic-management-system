package clinic.backend.security;

public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);

    /**
     * Performs a comparison with the same cost as {@link #matches} against a fixed hash, so that
     * a login for an unknown or blocked account takes as long as a real password check.
     */
    void simulateMatch(String rawPassword);
}
