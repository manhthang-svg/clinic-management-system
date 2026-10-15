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
        this(errorCode, Map.of());
    }

    public AppException(ErrorCode errorCode, Map<String, String> details) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = Map.copyOf(details);
    }
}
