package clinic.backend.exceptions;

import java.time.Instant;
import java.util.Map;

import clinic.backend.config.RequestCorrelationFilter;
import clinic.backend.dto.response.ErrorResponse;
import clinic.backend.enums.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Builds the standard error body, shared by the exception handler and the security handlers.
 */
public final class ErrorResponseFactory {

    private ErrorResponseFactory() {
    }

    public static ErrorResponse create(ErrorCode errorCode, HttpServletRequest request) {
        return create(errorCode, Map.of(), request);
    }

    public static ErrorResponse create(
            ErrorCode errorCode, Map<String, String> details, HttpServletRequest request) {

        Object requestId = request.getAttribute(RequestCorrelationFilter.ATTRIBUTE_NAME);
        return ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .details(details.isEmpty() ? null : Map.copyOf(details))
                .path(request.getRequestURI())
                .requestId(requestId == null ? null : requestId.toString())
                .timestamp(Instant.now())
                .build();
    }
}
