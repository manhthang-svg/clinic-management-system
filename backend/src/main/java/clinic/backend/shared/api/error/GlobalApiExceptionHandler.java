package clinic.backend.shared.api.error;

import java.net.URI;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);
    private static final String INVALID_VALUE = "INVALID_VALUE";

    private final ApiProblemFactory problemFactory;

    public GlobalApiExceptionHandler(ApiProblemFactory problemFactory) {
        this.problemFactory = problemFactory;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        List<ApiFieldError> errors = exception.getBindingResult().getAllErrors().stream()
                .map(error -> new ApiFieldError(
                        error instanceof FieldError fieldError ? fieldError.getField() : "request",
                        INVALID_VALUE,
                        error.getDefaultMessage() == null ? "The value is invalid." : error.getDefaultMessage()))
                .toList();

        ProblemDetail problem = problemFactory.create(
                ApiErrorCode.REQUEST_VALIDATION_FAILED,
                "One or more request fields are invalid.",
                requestUri(request),
                errors);

        return response(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {

        List<ApiFieldError> errors = exception.getConstraintViolations().stream()
                .map(violation -> new ApiFieldError(
                        violation.getPropertyPath().toString(),
                        INVALID_VALUE,
                        violation.getMessage()))
                .toList();

        ProblemDetail problem = problemFactory.create(
                ApiErrorCode.REQUEST_VALIDATION_FAILED,
                "One or more request values are invalid.",
                requestUri(request),
                errors);

        return response(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {

        ProblemDetail problem = problemFactory.create(
                ApiErrorCode.MALFORMED_REQUEST,
                "The request body is missing or malformed.",
                requestUri(request));

        return response(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {

        LOGGER.error(
                "Unexpected API failure [traceId={}, exception={}]",
                MDC.get(CorrelationIdFilter.MDC_KEY),
                exception.getClass().getName());

        ProblemDetail problem = problemFactory.create(
                ApiErrorCode.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.",
                requestUri(request));

        return response(problem);
    }

    private ResponseEntity<ProblemDetail> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private URI requestUri(HttpServletRequest request) {
        return URI.create(request.getRequestURI());
    }
}
