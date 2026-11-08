package clinic.backend.exceptions;

import java.util.Map;

import clinic.backend.enums.ErrorCode;
import lombok.Getter;

/**
 * A business rejection that the API reports with the given error code.
 */
@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Map<String, String> details;

    public AppException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), Map.of());
    }

    public AppException(ErrorCode errorCode, Map<String, String> details) {
        this(errorCode, errorCode.getMessage(), details);
    }

    public AppException(ErrorCode errorCode, String message) {
        this(errorCode, message, Map.of());
    }

    public AppException(ErrorCode errorCode, String message, Map<String, String> details) {
        super(message != null ? message : errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = Map.copyOf(details);
    }
}
