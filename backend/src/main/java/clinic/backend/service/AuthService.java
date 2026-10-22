package clinic.backend.service;

import clinic.backend.dto.request.LoginRequest;
import clinic.backend.dto.response.SessionResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * UC-01 Login with supporting session refresh, and UC-02 Logout (Report 3).
 *
 * <p>The refresh token travels only in an HttpOnly cookie, which these operations read and write.
 */
public interface AuthService {

    /**
     * Every rejection is reported as {@code INVALID_CREDENTIALS}, whatever the reason.
     */
    SessionResponse login(LoginRequest request, HttpServletResponse response);

    /**
     * Rotates the refresh token of the cookie. A rejection clears the cookie.
     */
    SessionResponse refresh(HttpServletRequest request, HttpServletResponse response);

    /**
     * Ends the session of the cookie and clears the cookie. Idempotent.
     */
    void logout(HttpServletRequest request, HttpServletResponse response);
}
