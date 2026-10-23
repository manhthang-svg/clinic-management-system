package clinic.backend.controller;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.time.temporal.ChronoUnit;

import clinic.backend.dto.request.LoginRequest;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.Role;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.security.PasswordHasher;
import clinic.backend.support.PostgresIntegrationTest;
import clinic.backend.utils.UuidV7Generator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountAdministrationApiIT extends PostgresIntegrationTest {

    private static final String PASSWORD = "admin-api-password";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StaffAccountRepository accounts;
    @Autowired PasswordHasher passwordHasher;
    @Autowired UuidV7Generator ids;

    @Test
    void onlyAdminCanListAccounts() throws Exception {
        StaffAccount admin = account(Role.ADMIN, false, null);
        StaffAccount receptionist = account(Role.RECEPTIONIST, false, null);

        mockMvc.perform(get("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(login(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.size").value(20));

        mockMvc.perform(get("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(login(receptionist))))
                .andExpect(status().isForbidden());
    }

    @Test
    void manualUnlockDoesNotClearTemporarySignInBlock() throws Exception {
        StaffAccount admin = account(Role.ADMIN, false, null);
        Instant blockedUntil = Instant.now().plusSeconds(600).truncatedTo(ChronoUnit.MICROS);
        StaffAccount target = account(Role.RECEPTIONIST, false, blockedUntil);
        String token = accessToken(login(admin));

        MockHttpServletResponse locked = command(token, target.getId(), "lock", Map.of(
                "reason", "Security review",
                "expectedStatus", "ACTIVE",
                "expectedVersion", target.getVersion()));
        JsonNode lockedBody = objectMapper.readTree(locked.getContentAsString());
        long lockedVersion = lockedBody.get("data").get("version").asLong();

        command(token, target.getId(), "unlock", Map.of(
                "expectedStatus", "LOCKED",
                "expectedVersion", lockedVersion));

        StaffAccount saved = accounts.findById(target.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.getLoginBlockedUntil()).isEqualTo(blockedUntil);
    }

    @Test
    void resettingALockedAccountKeepsItLockedAndClearsTheTemporaryBlock() throws Exception {
        StaffAccount admin = account(Role.ADMIN, false, null);
        StaffAccount target = account(Role.RECEPTIONIST, true, Instant.now().plusSeconds(600));

        MockHttpServletResponse response = command(accessToken(login(admin)), target.getId(), "reset-password", Map.of(
                "mode", "GENERATE",
                "expectedVersion", target.getVersion()));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(response.getContentAsString()).get("data").get("temporaryPassword").asText())
                .hasSize(12);
        StaffAccount saved = accounts.findById(target.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(AccountStatus.LOCKED);
        assertThat(saved.getLoginBlockedUntil()).isNull();
        assertThat(saved.isPasswordChangeRequired()).isTrue();
    }

    @Test
    void createRejectsAFullNameContainingDigits() throws Exception {
        StaffAccount admin = account(Role.ADMIN, false, null);

        mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(login(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "invalid.name",
                                "fullName", "Doctor 123",
                                "phoneNumber", "0371234567",
                                "role", "RECEPTIONIST",
                                "passwordMode", "GENERATE",
                                "submissionId", UUID.randomUUID()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.fullName").exists());
    }

    private StaffAccount account(Role role, boolean locked, Instant blockedUntil) {
        String prefix = role == Role.ADMIN ? "admin." : "staff.";
        String username = prefix + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        return accounts.saveAndFlush(StaffAccount.builder()
                .id(ids.next())
                .username(username)
                .passwordHash(passwordHasher.hash(PASSWORD))
                .fullName("Account " + username)
                .phoneNumber(String.format("09%08d", Math.abs(username.hashCode() % 100_000_000L)))
                .role(role)
                .status(locked ? AccountStatus.LOCKED : AccountStatus.ACTIVE)
                .passwordChangeRequired(false)
                .loginBlockedUntil(blockedUntil)
                .lockReason(locked ? "Existing lock" : null)
                .lockedAt(locked ? now : null)
                .lockedByAccountId(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private MockHttpServletResponse login(StaffAccount account) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(account.getUsername(), PASSWORD))))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse command(String token, UUID accountId, String command, Object body) throws Exception {
        return mockMvc.perform(post("/api/v1/accounts/{accountId}/commands/{command}", accountId, command)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse();
    }

    private String accessToken(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsString()).get("data").get("accessToken").asText();
    }
}
