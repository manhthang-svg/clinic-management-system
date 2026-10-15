package clinic.backend.utils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import clinic.backend.config.properties.ClinicSecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Reads and writes the refresh-token cookie with the attributes decided in docs/security.md.
 */
@Component
public class CookieUtils {

    public static final String REFRESH_TOKEN_COOKIE_PATH = "/api/v1/auth";

    private final String name;
    private final boolean secure;
    private final Clock clock;

    public CookieUtils(ClinicSecurityProperties properties, Clock clock) {
        this.name = properties.session().cookieName();
        this.secure = properties.session().cookieSecure();
        this.clock = clock;
    }

    public Optional<String> readRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    /**
     * The cookie lives until the absolute session expiry, never longer.
     */
    public ResponseCookie buildRefreshTokenCookie(String refreshToken, Instant sessionExpiresAt) {
        Duration maxAge = Duration.between(clock.instant(), sessionExpiresAt);
        return base(refreshToken).maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge).build();
    }

    public ResponseCookie clearRefreshTokenCookie() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(REFRESH_TOKEN_COOKIE_PATH);
    }
}
