package clinic.backend.shared.security;

import clinic.backend.shared.configuration.ClinicSecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Targeted CSRF protection for endpoints that authenticate through the refresh-token cookie:
 * the request must carry an {@code Origin} header equal to the configured frontend origin.
 */
@Component
public class RequestOriginVerifier {

    private final String allowedOrigin;

    public RequestOriginVerifier(ClinicSecurityProperties properties) {
        this.allowedOrigin = stripTrailingSlash(properties.frontendOrigin());
    }

    public boolean isAllowed(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        return origin != null && allowedOrigin.equalsIgnoreCase(stripTrailingSlash(origin));
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
