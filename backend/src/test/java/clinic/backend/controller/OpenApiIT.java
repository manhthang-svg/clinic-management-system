package clinic.backend.controller;

import clinic.backend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
class OpenApiIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesTheFrontendContractWithoutAuthenticationWhenExplicitlyEnabled() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Clinic Management API"))
                .andExpect(jsonPath("$.info.description").value(org.hamcrest.Matchers.containsString("UC-01 to UC-11")))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.parameters[?(@.name == 'Origin')]").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/profile'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/profile'].put.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/profile'].put.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/commands/change-password'].post.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts'].get.summary").value("UC-05 View and search staff accounts"))
                .andExpect(jsonPath("$.paths['/api/v1/accounts'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/departments'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/{accountId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/{accountId}/work-in-progress'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/{accountId}/commands/change-role'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/{accountId}/commands/lock'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/{accountId}/commands/unlock'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/{accountId}/commands/reset-password'].post").exists());
    }
}
