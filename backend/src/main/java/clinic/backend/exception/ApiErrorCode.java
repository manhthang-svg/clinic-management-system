package clinic.backend.exception;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {
    REQUEST_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Concurrent modification"),
    CURRENT_PASSWORD_INCORRECT(HttpStatus.UNPROCESSABLE_ENTITY, "Current password incorrect"),
    PASSWORD_POLICY_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "Password policy violation"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus status;
    private final String title;

    ApiErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
