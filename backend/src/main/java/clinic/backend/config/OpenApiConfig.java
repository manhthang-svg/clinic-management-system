package clinic.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    public static final String BEARER_SECURITY_SCHEME = "bearerAuth";

    @Bean
    OpenAPI clinicManagementOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Clinic Management API")
                        .description("Backend contract for Report 3 UC-01 to UC-11. Successful bodies use "
                                + "ApiResponse; failures use ErrorResponse. Refresh tokens are HttpOnly cookies.")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(
                        BEARER_SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
