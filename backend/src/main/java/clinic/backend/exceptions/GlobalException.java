
package clinic.backend.exceptions;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import clinic.backend.config.RequestCorrelationFilter;
import clinic.backend.dto.response.ErrorResponse;
import clinic.backend.enums.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Translates exceptions into the standard ErrorResponse.
 */
@Slf4j
@RestControllerAdvice
public class GlobalException {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(
            AppException exception,
            HttpServletRequest request) {

        log.warn(
                "Business exception [code={}, method={}, path={}, requestId={}]",
                exception.getErrorCode().getCode(),
                request.getMethod(),
                request.getRequestURI(),
                getRequestId(request)
        );

        String message = exception.getMessage() != null
                ? exception.getMessage()
                : exception.getErrorCode().getMessage();

        ResponseEntity.BodyBuilder builder =
                ResponseEntity.status(exception.getErrorCode().getHttpStatus());

        if (exception.getErrorCode().getHttpStatus() == HttpStatus.UNAUTHORIZED) {
            builder.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }

        return builder.body(
                ErrorResponseFactory.create(exception.getErrorCode(), message, exception.getDetails(), request)
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        Map<String, String> details = new LinkedHashMap<>();

        exception.getBindingResult().getFieldErrors().forEach(fieldError ->
                details.putIfAbsent(
                        fieldError.getField(),
                        fieldError.getDefaultMessage() == null
                                ? "The value is invalid."
                                : fieldError.getDefaultMessage()
                )
        );

        log.warn(
                "Request body validation failed [method={}, path={}, fields={}, requestId={}]",
                request.getMethod(),
                request.getRequestURI(),
                details.keySet(),
                getRequestId(request)
        );

        return response(
                ErrorCode.REQUEST_VALIDATION_FAILED,
                details,
                request
        );
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequestParameter(
            Exception exception,
            HttpServletRequest request) {

        log.warn(
                "Invalid request parameter [exception={}, method={}, path={}, requestId={}]",
                exception.getClass().getSimpleName(),
                request.getMethod(),
                request.getRequestURI(),
                getRequestId(request)
        );

        return response(
                ErrorCode.REQUEST_PARAMETER_INVALID,
                Map.of(),
                request
        );
    }

    // anh qua 10 MB (gioi han multipart) -> 400 RESULT_IMAGE_INVALID
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request) {
        log.warn("Upload too large [method={}, path={}, requestId={}]",
                request.getMethod(), request.getRequestURI(), getRequestId(request));
        return response(ErrorCode.RESULT_IMAGE_INVALID, Map.of("file", "TOO_LARGE"), request);
    }

    // thieu phan file trong multipart -> 400
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(
            MissingServletRequestPartException exception,
            HttpServletRequest request) {
        return response(ErrorCode.REQUEST_PARAMETER_INVALID, Map.of(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {

        log.warn(
                "Malformed request body [method={}, path={}, requestId={}]",
                request.getMethod(),
                request.getRequestURI(),
                getRequestId(request)
        );

        return response(
                ErrorCode.MALFORMED_REQUEST,
                Map.of(),
                request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request) {

        log.warn(
                "Access denied [method={}, path={}, requestId={}]",
                request.getMethod(),
                request.getRequestURI(),
                getRequestId(request)
        );

        return response(
                ErrorCode.ACCESS_DENIED,
                Map.of(),
                request
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException exception,
            HttpServletRequest request) {

        log.warn(
                "Resource not found [method={}, path={}, requestId={}]",
                request.getMethod(),
                request.getRequestURI(),
                getRequestId(request)
        );

        return response(
                ErrorCode.NOT_FOUND,
                Map.of(),
                request
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request) {

        log.warn(
                "HTTP method not supported [method={}, path={}, requestId={}]",
                request.getMethod(),
                request.getRequestURI(),
                getRequestId(request)
        );

        return response(
                ErrorCode.METHOD_NOT_ALLOWED,
                Map.of(),
                request
        );
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(
            OptimisticLockingFailureException exception,
            HttpServletRequest request) {

        log.warn(
                "Concurrent modification detected [method={}, path={}, requestId={}]",
                request.getMethod(),
                request.getRequestURI(),
                getRequestId(request),
                exception
        );

        return response(
                ErrorCode.CONCURRENT_MODIFICATION,
                Map.of(),
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException exception,
            HttpServletRequest request) {

        log.warn(
                "Illegal argument rejected [method={}, path={}, message={}, requestId={}]",
                request.getMethod(),
                request.getRequestURI(),
                exception.getMessage(),
                getRequestId(request)
        );

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(HttpStatus.BAD_REQUEST.name())
                .message(
                        exception.getMessage() != null
                                ? exception.getMessage()
                                : HttpStatus.BAD_REQUEST.getReasonPhrase()
                )
                .path(request.getRequestURI())
                .requestId(getRequestId(request))
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request) {

        HttpStatus status = HttpStatus.valueOf(
                exception.getStatusCode().value()
        );

        log.warn(
                "HTTP request rejected [status={}, method={}, path={}, requestId={}]",
                status.value(),
                request.getMethod(),
                request.getRequestURI(),
                getRequestId(request)
        );

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(status.name())
                .message(
                        exception.getReason() != null
                                ? exception.getReason()
                                : status.getReasonPhrase()
                )
                .path(request.getRequestURI())
                .requestId(getRequestId(request))
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(status).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUncategorized(
            Exception exception,
            HttpServletRequest request) {

        // Stack trace is logged for debugging.
        // Do not log request bodies or submitted personal data.
        log.error(
                "Unexpected API failure [exception={}, method={}, path={}, requestId={}]",
                exception.getClass().getName(),
                request.getMethod(),
                request.getRequestURI(),
                getRequestId(request),
                exception
        );

        return response(
                ErrorCode.INTERNAL_SERVER_ERROR,
                Map.of(),
                request
        );
    }

    private static String getRequestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(
                RequestCorrelationFilter.ATTRIBUTE_NAME
        );

        return requestId == null ? "unknown" : requestId.toString();
    }

    private static ResponseEntity<ErrorResponse> response(
            ErrorCode errorCode,
            Map<String, String> details,
            HttpServletRequest request) {

        ResponseEntity.BodyBuilder builder =
                ResponseEntity.status(errorCode.getHttpStatus());

        if (errorCode.getHttpStatus() == HttpStatus.UNAUTHORIZED) {
            builder.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }

        return builder.body(
                ErrorResponseFactory.create(errorCode, details, request)
        );
    }
}
