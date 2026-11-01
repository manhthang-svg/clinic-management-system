package clinic.backend.controller;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.dto.request.LoginRequest;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.Role;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.security.PasswordHasher;
import clinic.backend.support.PostgresIntegrationTest;
import clinic.backend.utils.UuidV7Generator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InsuranceVerificationApiIT extends PostgresIntegrationTest {

    private static final String PASSWORD = "insurance-verification-password";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StaffAccountRepository accounts;
    @Autowired PasswordHasher passwordHasher;
    @Autowired UuidV7Generator ids;

    @Test
    void receptionistPassesHttpAndMethodAuthorization() throws Exception {
        verify(UUID.randomUUID(), accessToken(account(Role.RECEPTIONIST)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
    }

    @Test
    void rejectsOtherRolesAndAnonymousRequests() throws Exception {
        UUID patientId = UUID.randomUUID();
        for (Role role : new Role[] {Role.ADMIN, Role.DOCTOR, Role.CASHIER, Role.TECHNICIAN}) {
            verify(patientId, accessToken(account(role)))
                    .andExpect(status().isForbidden());
        }
        verify(patientId, null).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions verify(UUID patientId, String accessToken)
            throws Exception {
        var request = post("/api/v1/insurance/verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "patientId", patientId,
                        "insuranceCode", "0000000104")));
        if (accessToken != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return mockMvc.perform(request);
    }

    private StaffAccount account(Role role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "ins." + role.name().toLowerCase() + "." + suffix;
        Instant now = Instant.now();
        return accounts.saveAndFlush(StaffAccount.builder()
                .id(ids.next())
                .username(username)
                .passwordHash(passwordHasher.hash(PASSWORD))
                .fullName("Account " + username)
                .phoneNumber(String.format("09%08d", Math.abs(username.hashCode() % 100_000_000L)))
                .role(role)
                .status(AccountStatus.ACTIVE)
                .passwordChangeRequired(false)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private String accessToken(StaffAccount account) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(account.getUsername(), PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("accessToken").asText();
    }
}
