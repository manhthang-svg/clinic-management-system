package clinic.backend.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Errors returned by the API. {@code code} is the stable, machine-readable value clients rely on; several
 * entries may share a code when only the message differs.
 */
@Getter
public enum ErrorCode {
    REQUEST_VALIDATION_FAILED("REQUEST_VALIDATION_FAILED", "One or more request fields are invalid.",
            HttpStatus.BAD_REQUEST),
    REQUEST_PARAMETER_INVALID("REQUEST_VALIDATION_FAILED", "One or more request values are invalid.",
            HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST("MALFORMED_REQUEST", "The request body is missing or malformed.", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("UNAUTHORIZED", "Authentication is required to access this resource.", HttpStatus.UNAUTHORIZED),
    SESSION_INVALID("UNAUTHORIZED", "The session is no longer valid. Log in again.", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "Invalid username or password.", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("FORBIDDEN", "You do not have permission to access this resource.", HttpStatus.FORBIDDEN),
    ORIGIN_NOT_ALLOWED("FORBIDDEN", "The request origin is not allowed.", HttpStatus.FORBIDDEN),
    NOT_FOUND("NOT_FOUND", "The requested resource does not exist.", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "The request method is not supported for this resource.",
            HttpStatus.METHOD_NOT_ALLOWED),
    EMAIL_ALREADY_EXISTS("EMAIL_ALREADY_EXISTS", "The email is already used by another account.",
            HttpStatus.CONFLICT),
    PHONE_ALREADY_EXISTS("PHONE_ALREADY_EXISTS", "The phone number is already used by another account.",
            HttpStatus.CONFLICT),
    USERNAME_ALREADY_EXISTS("USERNAME_ALREADY_EXISTS", "The username is already used by another account.",
            HttpStatus.CONFLICT),
    ACCOUNT_NOT_FOUND("ACCOUNT_NOT_FOUND", "The staff account does not exist.", HttpStatus.NOT_FOUND),
    ADMIN_ACCOUNT_PROTECTED("ADMIN_ACCOUNT_PROTECTED", "The seeded Admin account cannot be changed by this operation.",
            HttpStatus.UNPROCESSABLE_ENTITY),
    SELF_ADMINISTRATION_FORBIDDEN("SELF_ADMINISTRATION_FORBIDDEN",
            "Use My Profile or Change Password for your own Admin account.", HttpStatus.FORBIDDEN),
    ROLE_CHANGE_NOT_ALLOWED("ROLE_CHANGE_NOT_ALLOWED", "The requested role change is not allowed.",
            HttpStatus.UNPROCESSABLE_ENTITY),
    WORK_IN_PROGRESS("WORK_IN_PROGRESS", "The account has work in progress.", HttpStatus.CONFLICT),
    ACCOUNT_STATE_CONFLICT("ACCOUNT_STATE_CONFLICT", "The account state changed. Reload it and try again.",
            HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION("CONCURRENT_MODIFICATION",
            "The resource was changed by another request. Reload it and try again.", HttpStatus.CONFLICT),
    CURRENT_PASSWORD_INCORRECT("CURRENT_PASSWORD_INCORRECT", "The current password is incorrect.",
            HttpStatus.UNPROCESSABLE_ENTITY),
    PASSWORD_POLICY_VIOLATION("PASSWORD_POLICY_VIOLATION", "The new password does not satisfy the password policy.",
            HttpStatus.UNPROCESSABLE_ENTITY),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "An unexpected error occurred.",
            HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
