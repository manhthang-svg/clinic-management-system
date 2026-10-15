package clinic.backend.security;

/**
 * Claim names of the access token defined in docs/security.md.
 */
public final class AccessTokenClaims {

    public static final String ROLE = "role";
    public static final String SESSION_ID = "sid";
    public static final String PASSWORD_CHANGE_REQUIRED = "pwd_change";

    /**
     * Authority granted instead of the role while the password must be changed.
     */
    public static final String PASSWORD_CHANGE_REQUIRED_AUTHORITY = "PASSWORD_CHANGE_REQUIRED";

    public static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

    private AccessTokenClaims() {
    }
}
