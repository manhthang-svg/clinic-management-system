package clinic.backend.security;

import java.io.IOException;
import java.net.URI;

import com.fasterxml.jackson.databind.ObjectMapper;
import clinic.backend.exception.ApiErrorCode;
import clinic.backend.exception.ApiProblemFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiProblemFactory problemFactory;
    private final ObjectMapper objectMapper;

    public ProblemAuthenticationEntryPoint(ApiProblemFactory problemFactory, ObjectMapper objectMapper) {
        this.problemFactory = problemFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("WWW-Authenticate", "Bearer");
        objectMapper.writeValue(
                response.getOutputStream(),
                problemFactory.create(
                        ApiErrorCode.UNAUTHORIZED,
                        "Authentication is required to access this resource.",
                        URI.create(request.getRequestURI())));
    }
}
