package clinic.backend.shared.api.error;

import java.net.URI;
import java.util.List;

import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

@Component
public class ApiProblemFactory {

    public ProblemDetail create(ApiErrorCode errorCode, String detail, URI instance) {
        return create(errorCode, detail, instance, List.of());
    }

    public ProblemDetail create(
            ApiErrorCode errorCode,
            String detail,
            URI instance,
            List<ApiFieldError> errors) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
        problem.setTitle(errorCode.title());
        problem.setType(URI.create("about:blank"));
        problem.setInstance(instance);
        problem.setProperty("code", errorCode.name());

        String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        if (!errors.isEmpty()) {
            problem.setProperty("errors", List.copyOf(errors));
        }

        return problem;
    }
}
