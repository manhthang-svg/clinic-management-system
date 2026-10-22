package clinic.backend.controller;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import clinic.backend.dto.request.LoginRequest;
import clinic.backend.dto.request.UpdateProfileRequest;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileApiIT extends PostgresIntegrationTest {

    private static final String PASSWORD = "profile-password";

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
    void authenticatedStaffCanViewOnlyTheirOwnProfile() throws Exception {
        StaffAccount caller = account("0901234567", "caller@clinic.test", true);
        account("0911111111", "other@clinic.test", false);
        assignRoom(caller);

        getProfile(accessToken(login(caller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value(caller.getUsername()))
                .andExpect(jsonPath("$.data.fullName").value(caller.getFullName()))
                .andExpect(jsonPath("$.data.phoneNumber").value("0901234567"))
                .andExpect(jsonPath("$.data.email").value("caller@clinic.test"))
                .andExpect(jsonPath("$.data.roomName").value("Consultation Room 1"))
                .andExpect(jsonPath("$.data.departmentName").value("Internal Medicine"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.version").doesNotExist());
    }

    @Test
    void updatesContactAndWritesAuditInTheSameUseCase() throws Exception {
        StaffAccount caller = account("0901234567", "old@clinic.test", false);
        String token = accessToken(login(caller));

        updateProfile(token, new UpdateProfileRequest("0987654321", "NEW@Clinic.Test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phoneNumber").value("0987654321"))
                .andExpect(jsonPath("$.data.email").value("new@clinic.test"));

        StaffAccount saved = accounts.findById(caller.getId()).orElseThrow();
        assertThat(saved.getPhoneNumber()).isEqualTo("0987654321");
        assertThat(saved.getEmail()).isEqualTo("new@clinic.test");
        assertThat(accountUpdatedEvents(caller.getId())).isEqualTo(1);
        String[] changedFields = jdbcTemplate.queryForObject(
                "select changed_fields from audit_logs where entity_type = 'staff_accounts' and entity_id = ?",
                (resultSet, rowNumber) -> (String[]) resultSet.getArray("changed_fields").getArray(),
                caller.getId());
        assertThat(changedFields).containsExactly("phoneNumber", "email");
        assertThat(objectMapper.readTree(jdbcTemplate.queryForObject(
                "select old_value::text from audit_logs where entity_type = 'staff_accounts' and entity_id = ?",
                String.class, caller.getId())))
                .isEqualTo(objectMapper.readTree(
                        "{\"phoneNumber\":\"0901234567\",\"email\":\"old@clinic.test\"}"));
        assertThat(objectMapper.readTree(jdbcTemplate.queryForObject(
                "select new_value::text from audit_logs where entity_type = 'staff_accounts' and entity_id = ?",
                String.class, caller.getId())))
                .isEqualTo(objectMapper.readTree(
                        "{\"phoneNumber\":\"0987654321\",\"email\":\"new@clinic.test\"}"));
    }

    @Test
    void unchangedContactReturnsProfileWithoutAudit() throws Exception {
        StaffAccount caller = account("0901234567", "same@clinic.test", false);
        String token = accessToken(login(caller));

        updateProfile(token, new UpdateProfileRequest("0901234567", "SAME@clinic.test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("same@clinic.test"));

        assertThat(accountUpdatedEvents(caller.getId())).isZero();
    }

    @Test
    void duplicateEmailIsConflictAndChangesNothing() throws Exception {
        account("0900000000", "used@clinic.test", false);
        StaffAccount caller = account("0911111111", "original@clinic.test", false);

        updateProfile(accessToken(login(caller)), new UpdateProfileRequest("0922222222", "USED@clinic.test"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.details.email").value("ALREADY_EXISTS"));

        StaffAccount unchanged = accounts.findById(caller.getId()).orElseThrow();
        assertThat(unchanged.getPhoneNumber()).isEqualTo("0911111111");
        assertThat(unchanged.getEmail()).isEqualTo("original@clinic.test");
        assertThat(accountUpdatedEvents(caller.getId())).isZero();
    }

    @Test
    void validatesRequiredPhoneAndEmailFormats() throws Exception {
        StaffAccount caller = account("0901234567", "valid@clinic.test", false);
        String token = accessToken(login(caller));

        updateProfile(token, Map.of("phoneNumber", "123", "email", "not-an-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.phoneNumber").exists())
                .andExpect(jsonPath("$.details.email").exists());
        updateProfile(token, Map.of("phoneNumber", "0901234567", "email", "x".repeat(101) + "@test.com"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.email").exists());
        updateProfile(token, Map.of("phoneNumber", "", "email", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.phoneNumber").exists())
                .andExpect(jsonPath("$.details.email").exists());
    }

    @Test
    void rejectsAttemptsToChangeReadOnlyProfileFields() throws Exception {
        StaffAccount caller = account("0901234567", "readonly@clinic.test", false);

        updateProfile(accessToken(login(caller)), Map.of(
                        "phoneNumber", "0987654321",
                        "email", "new@clinic.test",
                        "fullName", "Changed outside UC-4"))
                .andExpect(status().isBadRequest());

        assertThat(accounts.findById(caller.getId()).orElseThrow().getFullName()).isEqualTo(caller.getFullName());
    }

    @Test
    void requiresAuthenticationAndRejectsAnAccountLockedAfterTokenIssuance() throws Exception {
        mockMvc.perform(get("/api/v1/me/profile")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateProfileRequest("0901234567", "user@clinic.test"))))
                .andExpect(status().isUnauthorized());

        StaffAccount caller = account("0901234567", "lock-after-login@clinic.test", false);
        String token = accessToken(login(caller));
        jdbcTemplate.update("update staff_accounts set status = 'LOCKED' where id = ?", caller.getId());

        getProfile(token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void passwordChangeRequiredSessionCanViewButCannotUpdateProfile() throws Exception {
        StaffAccount caller = account("0901234567", "restricted@clinic.test", true);
        String token = accessToken(login(caller));

        getProfile(token).andExpect(status().isOk());
        updateProfile(token, new UpdateProfileRequest("0987654321", "changed@clinic.test"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        StaffAccount unchanged = accounts.findById(caller.getId()).orElseThrow();
        assertThat(unchanged.getPhoneNumber()).isEqualTo("0901234567");
        assertThat(unchanged.getEmail()).isEqualTo("restricted@clinic.test");
        assertThat(accountUpdatedEvents(caller.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_logs where entity_type = 'staff_accounts' and entity_id = ?",
                Integer.class, caller.getId())).isZero();
    }

    private StaffAccount account(String phone, String email, boolean passwordChangeRequired) {
        String username = "profile." + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        return accounts.saveAndFlush(StaffAccount.builder()
                .id(ids.next())
                .username(username)
                .passwordHash(passwordHasher.hash(PASSWORD))
                .fullName("Profile " + username)
                .phoneNumber(phone)
                .email(email)
                .role(Role.DOCTOR)
                .status(AccountStatus.ACTIVE)
                .passwordChangeRequired(passwordChangeRequired)
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

    private void assignRoom(StaffAccount account) {
        UUID departmentId = ids.next();
        UUID roomId = ids.next();
        jdbcTemplate.update("""
                insert into departments (id, makhoa, tenkhoa, type, is_active, created_at, updated_at, version)
                values (?, ?, 'Internal Medicine', 'CLINICAL', true, now(), now(), 0)
                """, departmentId, "K-" + UUID.randomUUID().toString().substring(0, 8));
        jdbcTemplate.update("""
                insert into rooms (id, department_id, maphongkham, phongkham, location, type, status,
                                   created_at, updated_at, version)
                values (?, ?, ?, 'Consultation Room 1', 'Floor 1', 'EXAMINATION', 'ACTIVE', now(), now(), 0)
                """, roomId, departmentId, "R-" + UUID.randomUUID().toString().substring(0, 8));
        jdbcTemplate.update("""
                insert into staff_room_assignments (id, staff_account_id, room_id, assigned_at, version)
                values (?, ?, ?, now(), 0)
                """, ids.next(), account.getId(), roomId);
    }

    private ResultActions getProfile(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/v1/me/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private ResultActions updateProfile(String accessToken, Object request) throws Exception {
        return mockMvc.perform(put("/api/v1/me/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private String accessToken(MockHttpServletResponse response) throws Exception {
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        return body.get("data").get("accessToken").asText();
    }

    private int accountUpdatedEvents(UUID accountId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from security_audit_events where target_account_id = ? and event_type = 'ACCOUNT_UPDATED'",
                Integer.class, accountId);
        return count == null ? 0 : count;
    }
}
