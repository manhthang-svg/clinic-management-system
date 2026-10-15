package clinic.backend.controller;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.dto.request.ChangePasswordRequest;
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
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChangePasswordApiIT extends PostgresIntegrationTest {

    private static final String CURRENT = "current-password";
    private static final String NEW = "brand-new-password";
    private static final String COOKIE = "clinic_refresh";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StaffAccountRepository accounts;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private UuidV7Generator ids;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void firstLoginPasswordChangeUnlocksTheAccountAndKeepsOnlyTheCurrentSession() throws Exception {
        StaffAccount admin = account(Role.ADMIN, true);
        MockHttpServletResponse otherComputer = login(admin, CURRENT);
        MockHttpServletResponse thisComputer = login(admin, CURRENT);
        assertThat(body(thisComputer).get("data").get("passwordChangeRequired").asBoolean()).isTrue();

        changePassword(accessToken(thisComputer), CURRENT, NEW).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(admin.getUsername(), CURRENT))))
                .andExpect(status().isUnauthorized());
        refresh(refreshCookie(otherComputer)).andExpect(status().isUnauthorized());
        refresh(refreshCookie(thisComputer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passwordChangeRequired").value(false));
        assertThat(login(admin, NEW).getStatus()).isEqualTo(200);

        assertThat(jdbcTemplate.queryForList(
                "select event_type from security_audit_events where target_account_id = ?",
                String.class, admin.getId())).contains("PASSWORD_CHANGED");
    }

    @Test
    void tokenRequiringAPasswordChangeIsRefusedElsewhere() throws Exception {
        StaffAccount admin = account(Role.ADMIN, true);
        String token = accessToken(login(admin, CURRENT));

        mockMvc.perform(get("/actuator/health").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongCurrentPasswordIsRejectedWithoutChangingAnything() throws Exception {
        StaffAccount doctor = account(Role.DOCTOR, false);
        String token = accessToken(login(doctor, CURRENT));

        changePassword(token, "wrong-password", NEW)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"));

        assertThat(login(doctor, CURRENT).getStatus()).isEqualTo(200);
    }

    @Test
    void newPasswordViolatingThePolicyIsRejectedWithTheViolatedRule() throws Exception {
        StaffAccount doctor = account(Role.DOCTOR, false);
        String token = accessToken(login(doctor, CURRENT));

        changePassword(token, CURRENT, "short")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"))
                .andExpect(jsonPath("$.details.newPassword").value("TOO_SHORT"));
        changePassword(token, CURRENT, CURRENT)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details.newPassword").value("SAME_AS_CURRENT"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/me/commands/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest(CURRENT, NEW))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void responsesNeverEchoPasswords() throws Exception {
        StaffAccount doctor = account(Role.DOCTOR, false);
        String token = accessToken(login(doctor, CURRENT));

        String response = changePassword(token, "wrong-password", "short")
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("wrong-password", "short\"", CURRENT);
    }

    private StaffAccount account(Role role, boolean passwordChangeRequired) {
        String username = role.name().toLowerCase() + "." + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        return accounts.save(StaffAccount.builder()
                .id(ids.next())
                .username(username)
                .passwordHash(passwordHasher.hash(CURRENT))
                .fullName("Test " + role)
                .role(role)
                .status(AccountStatus.ACTIVE)
                .passwordChangeRequired(passwordChangeRequired)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private MockHttpServletResponse login(StaffAccount account, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(account.getUsername(), password))))
                .andReturn().getResponse();
    }

    private ResultActions changePassword(String accessToken, String current, String next) throws Exception {
        return mockMvc.perform(post("/api/v1/me/commands/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangePasswordRequest(current, next))));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie(COOKIE, refreshToken))
                .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN));
    }

    private String accessToken(MockHttpServletResponse response) throws Exception {
        return body(response).get("data").get("accessToken").asText();
    }

    private static String refreshCookie(MockHttpServletResponse response) {
        return response.getCookie(COOKIE).getValue();
    }

    private JsonNode body(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsString());
    }
}
