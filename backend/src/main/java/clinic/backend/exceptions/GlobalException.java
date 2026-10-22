package clinic.backend.exceptions;

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
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;
/**
 * Translates exceptions into the standard {@link ErrorResponse}. Messages never echo submitted values.
 */
@Slf4j
@RestControllerAdvice
public class GlobalException {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException exception, HttpServletRequest request) {
        log.debug("Business request rejected [code={}]", exception.getErrorCode().getCode());
        return response(exception.getErrorCode(), exception.getDetails(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception, HttpServletRequest request) {

        Map<String, String> details = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(fieldError -> details.putIfAbsent(
                fieldError.getField(),
                fieldError.getDefaultMessage() == null ? "The value is invalid." : fieldError.getDefaultMessage()));
        return response(ErrorCode.REQUEST_VALIDATION_FAILED, details, request);
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequestParameter(
            Exception exception, HttpServletRequest request) {
        return response(ErrorCode.REQUEST_PARAMETER_INVALID, Map.of(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return response(ErrorCode.MALFORMED_REQUEST, Map.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException exception, HttpServletRequest request) {
        return response(ErrorCode.ACCESS_DENIED, Map.of(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException exception, HttpServletRequest request) {
        return response(ErrorCode.NOT_FOUND, Map.of(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return response(ErrorCode.METHOD_NOT_ALLOWED, Map.of(), request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(
            OptimisticLockingFailureException exception, HttpServletRequest request) {
        return response(ErrorCode.CONCURRENT_MODIFICATION, Map.of(), request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request) {

        HttpStatus status = HttpStatus.valueOf(
                exception.getStatusCode().value()
        );

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(status.name())
                .message(exception.getReason() != null
                        ? exception.getReason()
                        : status.getReasonPhrase())
                .path(request.getRequestURI())
                .requestId(String.valueOf(
                        request.getAttribute(
                                RequestCorrelationFilter.ATTRIBUTE_NAME
                        )
                ))
                .timestamp(java.time.Instant.now())
                .build();

        return ResponseEntity.status(status).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUncategorized(Exception exception, HttpServletRequest request) {
        // Only the exception type is logged: messages may contain submitted values.
        log.error("Unexpected API failure [exception={}]", exception.getClass().getName());
        return response(ErrorCode.INTERNAL_SERVER_ERROR, Map.of(), request);
    }

    private static ResponseEntity<ErrorResponse> response(
            ErrorCode errorCode, Map<String, String> details, HttpServletRequest request) {

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(errorCode.getHttpStatus());
        if (errorCode.getHttpStatus() == HttpStatus.UNAUTHORIZED) {
            builder.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return builder.body(ErrorResponseFactory.create(errorCode, details, request));
    }
}
