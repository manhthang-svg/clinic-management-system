package clinic.backend.exception;

import java.net.URI;
import java.util.List;

import clinic.backend.config.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class ApiProblemFactoryTest {

    private final ApiProblemFactory problemFactory = new ApiProblemFactory();

    @AfterEach
    void clearMappedDiagnosticContext() {
        MDC.clear();
    }

    @Test
    void createsProblemDetailsWithStableExtensions() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "test-trace-id");
        List<ApiFieldError> errors = List.of(
                new ApiFieldError("fieldName", "INVALID_VALUE", "The value is invalid."));

        ProblemDetail problem = problemFactory.create(
                ApiErrorCode.REQUEST_VALIDATION_FAILED,
                "One or more request fields are invalid.",
                URI.create("/api/v1/resources"),
                errors);

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Request validation failed");
        assertThat(problem.getInstance()).isEqualTo(URI.create("/api/v1/resources"));
        assertThat(problem.getProperties())
                .containsEntry("code", "REQUEST_VALIDATION_FAILED")
                .containsEntry("traceId", "test-trace-id")
                .containsEntry("errors", errors);
    }
}
