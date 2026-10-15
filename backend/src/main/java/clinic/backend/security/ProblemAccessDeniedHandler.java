package clinic.backend.security;

import java.io.IOException;
import java.net.URI;

import com.fasterxml.jackson.databind.ObjectMapper;
import clinic.backend.exception.ApiErrorCode;
import clinic.backend.exception.ApiProblemFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiProblemFactory problemFactory;
    private final ObjectMapper objectMapper;

    public ProblemAccessDeniedHandler(ApiProblemFactory problemFactory, ObjectMapper objectMapper) {
        this.problemFactory = problemFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                problemFactory.create(
                        ApiErrorCode.FORBIDDEN,
                        "You do not have permission to access this resource.",
                        URI.create(request.getRequestURI())));
    }
}
