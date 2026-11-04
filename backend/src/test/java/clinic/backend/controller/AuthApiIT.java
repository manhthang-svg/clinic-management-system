package clinic.backend.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import clinic.backend.dto.request.LoginRequest;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.Role;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.security.PasswordHasher;
import clinic.backend.security.jwt.AccessTokenClaims;
import clinic.backend.support.PostgresIntegrationTest;
import clinic.backend.utils.UuidV7Generator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiIT extends PostgresIntegrationTest {

    private static final String PASSWORD = "correct-password";
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
    private JwtDecoder jwtDecoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void loginReturnsAnAccessTokenAndAStrictHttpOnlyRefreshCookie() throws Exception {
        StaffAccount doctor = account(Role.DOCTOR, AccountStatus.ACTIVE, false);

        MockHttpServletResponse response = login(doctor.getUsername().toUpperCase(), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.passwordChangeRequired").value(false))
                .andExpect(jsonPath("$.data.account.id").value(doctor.getId().toString()))
                .andExpect(jsonPath("$.data.account.role").value("DOCTOR"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn().getResponse();

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith(COOKIE + "=")
                .contains("Path=/api/v1/auth", "HttpOnly", "Secure", "SameSite=Strict")
                // 12-hour absolute session lifetime, allowing for the time the request took.
                .containsPattern("Max-Age=43(1[89]\\d|200);");

        Jwt jwt = jwtDecoder.decode(body(response).get("data").get("accessToken").asText());
        assertThat(jwt.getSubject()).isEqualTo(doctor.getId().toString());
        assertThat(jwt.getClaimAsString(AccessTokenClaims.ROLE)).isEqualTo("DOCTOR");
        assertThat(jwt.getClaims()).doesNotContainKey(AccessTokenClaims.PASSWORD_CHANGE_REQUIRED);
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(15 * 60));
        assertThat(auditEvents(doctor.getId())).containsExactly("LOGIN_SUCCEEDED");
    }

    @Test
    void wrongPasswordAndUnknownUsernameProduceTheSameResponse() throws Exception {
        StaffAccount cashier = account(Role.CASHIER, AccountStatus.ACTIVE, false);

        JsonNode wrongPassword = body(login(cashier.getUsername(), "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn().getResponse());
        JsonNode unknownUser = body(login("no.such.user", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse());

        assertThat(wrongPassword.get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(withoutRequestSpecificFields(wrongPassword))
                .isEqualTo(withoutRequestSpecificFields(unknownUser));
        assertThat(auditEvents(cashier.getId())).containsExactly("LOGIN_FAILED");
    }

    @Test
    void lockedAccountCannotLogInWithTheCorrectPassword() throws Exception {
        StaffAccount locked = account(Role.RECEPTIONIST, AccountStatus.LOCKED, false);

        login(locked.getUsername(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void fiveFailedLoginsBlockTheAccountTemporarilyAndAreAudited() throws Exception {
        StaffAccount technician = account(Role.TECHNICIAN, AccountStatus.ACTIVE, false);
        for (int attempt = 0; attempt < 4; attempt++) {
            login(technician.getUsername(), "wrong-password")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        login(technician.getUsername(), "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_TEMPORARILY_BLOCKED"));

        login(technician.getUsername(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_TEMPORARILY_BLOCKED"));

        StaffAccount stored = accounts.findById(technician.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(stored.getLoginBlockedUntil()).isAfter(Instant.now().plusSeconds(14 * 60));
        assertThat(auditEvents(technician.getId())).contains("LOGIN_THROTTLED");
    }

    @Test
    void concurrentFailedLoginsAreAllCountedWithoutServerErrors() throws Exception {
        StaffAccount technician = account(Role.TECHNICIAN, AccountStatus.ACTIVE, false);
        int parallelRequests = 5;
        ExecutorService executor = Executors.newFixedThreadPool(parallelRequests);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> attempt = () -> {
                start.await();
                return login(technician.getUsername(), "wrong-password")
                        .andReturn().getResponse().getStatus();
            };
            List<Future<Integer>> results = new ArrayList<>();
            for (int index = 0; index < parallelRequests; index++) {
                results.add(executor.submit(attempt));
            }
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }

            assertThat(statuses).containsOnly(401);
            StaffAccount stored = accounts.findById(technician.getId()).orElseThrow();
            assertThat(stored.getFailedLoginAttempts()).isZero();
            assertThat(stored.getLoginBlockedUntil()).isAfter(Instant.now().plusSeconds(14 * 60));
            assertThat(auditEvents(technician.getId()))
                    .filteredOn("LOGIN_FAILED"::equals)
                    .hasSize(parallelRequests);
            assertThat(auditEvents(technician.getId()))
                    .filteredOn("LOGIN_THROTTLED"::equals)
                    .hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void passwordChangeRequiredIsCarriedInTheToken() throws Exception {
        StaffAccount admin = account(Role.ADMIN, AccountStatus.ACTIVE, true);

        MockHttpServletResponse response = login(admin.getUsername(), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passwordChangeRequired").value(true))
                .andReturn().getResponse();

        Jwt jwt = jwtDecoder.decode(body(response).get("data").get("accessToken").asText());
        assertThat(jwt.getClaimAsBoolean(AccessTokenClaims.PASSWORD_CHANGE_REQUIRED)).isTrue();
    }

    @Test
    void refreshRotatesTheCookieAndDetectsReuse() throws Exception {
        StaffAccount doctor = account(Role.DOCTOR, AccountStatus.ACTIVE, false);
        String firstToken = refreshCookie(login(doctor.getUsername(), PASSWORD).andReturn().getResponse());

        MockHttpServletResponse refreshed = refresh(firstToken, FRONTEND_ORIGIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.id").value(doctor.getId().toString()))
                .andReturn().getResponse();
        String secondToken = refreshCookie(refreshed);
        assertThat(secondToken).isNotEqualTo(firstToken);

        refresh(firstToken, FRONTEND_ORIGIN)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        refresh(secondToken, FRONTEND_ORIGIN).andExpect(status().isUnauthorized());

        assertThat(auditEvents(doctor.getId())).contains("REFRESH_TOKEN_REUSE_DETECTED");
        assertThat(jdbcTemplate.queryForObject(
                "select revocation_reason from auth_sessions where staff_account_id = ?",
                String.class, doctor.getId())).isEqualTo("REUSE_DETECTED");
    }

    @Test
    void refreshRequiresTheFrontendOrigin() throws Exception {
        StaffAccount doctor = account(Role.DOCTOR, AccountStatus.ACTIVE, false);
        String token = refreshCookie(login(doctor.getUsername(), PASSWORD).andReturn().getResponse());

        refresh(token, null).andExpect(status().isForbidden());
        refresh(token, "https://evil.example").andExpect(status().isForbidden());

        refresh(token, FRONTEND_ORIGIN).andExpect(status().isOk());
    }

    @Test
    void refreshFailsAfterTheAccountIsLocked() throws Exception {
        StaffAccount receptionist = account(Role.RECEPTIONIST, AccountStatus.ACTIVE, false);
        String token = refreshCookie(login(receptionist.getUsername(), PASSWORD).andReturn().getResponse());
        jdbcTemplate.update("update staff_accounts set status = 'LOCKED' where id = ?", receptionist.getId());

        refresh(token, FRONTEND_ORIGIN).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentRefreshesWithTheSameTokenSucceedAtMostOnce() throws Exception {
        StaffAccount doctor = account(Role.DOCTOR, AccountStatus.ACTIVE, false);
        String token = refreshCookie(login(doctor.getUsername(), PASSWORD).andReturn().getResponse());

        int parallelRequests = 4;
        ExecutorService executor = Executors.newFixedThreadPool(parallelRequests);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> attempt = () -> {
                start.await();
                return refresh(token, FRONTEND_ORIGIN).andReturn().getResponse().getStatus();
            };
            List<Future<Integer>> results = new ArrayList<>();
            for (int index = 0; index < parallelRequests; index++) {
                results.add(executor.submit(attempt));
            }
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsOnly(200, 401);
            assertThat(statuses).filteredOn(code -> code == 200).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void logoutRevokesTheSessionAndClearsTheCookie() throws Exception {
        StaffAccount doctor = account(Role.DOCTOR, AccountStatus.ACTIVE, false);
        String token = refreshCookie(login(doctor.getUsername(), PASSWORD).andReturn().getResponse());

        logout(token, FRONTEND_ORIGIN)
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        refresh(token, FRONTEND_ORIGIN).andExpect(status().isUnauthorized());
        assertThat(auditEvents(doctor.getId())).containsExactly("LOGIN_SUCCEEDED", "LOGOUT");
        assertThat(jdbcTemplate.queryForObject(
                "select revocation_reason from auth_sessions where staff_account_id = ?",
                String.class, doctor.getId())).isEqualTo("LOGOUT");
    }

    @Test
    void logoutIsIdempotentAndKeepsOtherSessions() throws Exception {
        StaffAccount doctor = account(Role.DOCTOR, AccountStatus.ACTIVE, false);
        String firstComputer = refreshCookie(login(doctor.getUsername(), PASSWORD).andReturn().getResponse());
        String secondComputer = refreshCookie(login(doctor.getUsername(), PASSWORD).andReturn().getResponse());

        logout(firstComputer, FRONTEND_ORIGIN).andExpect(status().isNoContent());
        logout(firstComputer, FRONTEND_ORIGIN).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN))
                .andExpect(status().isNoContent());

        refresh(secondComputer, FRONTEND_ORIGIN).andExpect(status().isOk());
    }

    @Test
    void logoutRequiresTheFrontendOrigin() throws Exception {
        StaffAccount doctor = account(Role.DOCTOR, AccountStatus.ACTIVE, false);
        String token = refreshCookie(login(doctor.getUsername(), PASSWORD).andReturn().getResponse());

        logout(token, null).andExpect(status().isForbidden());
        logout(token, "https://evil.example").andExpect(status().isForbidden());

        refresh(token, FRONTEND_ORIGIN).andExpect(status().isOk());
    }

    private ResultActions logout(String refreshToken, String origin) throws Exception {
        var request = post("/api/v1/auth/logout").cookie(new Cookie(COOKIE, refreshToken));
        if (origin != null) {
            request.header(HttpHeaders.ORIGIN, origin);
        }
        return mockMvc.perform(request);
    }

    private StaffAccount account(Role role, AccountStatus status, boolean passwordChangeRequired) {
        String username = role.name().toLowerCase() + "." + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        return accounts.save(StaffAccount.builder()
                .id(ids.next())
                .username(username)
                .passwordHash(passwordHasher.hash(PASSWORD))
                .fullName("Test " + role)
                .role(role)
                .status(status)
                .passwordChangeRequired(passwordChangeRequired)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password))));
    }

    private ResultActions refresh(String refreshToken, String origin) throws Exception {
        var request = post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, refreshToken));
        if (origin != null) {
            request.header(HttpHeaders.ORIGIN, origin);
        }
        return mockMvc.perform(request);
    }

    private static String refreshCookie(MockHttpServletResponse response) {
        Cookie cookie = response.getCookie(COOKIE);
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }

    private JsonNode body(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsString());
    }

    private static JsonNode withoutRequestSpecificFields(JsonNode error) {
        ObjectNode copy = error.deepCopy();
        copy.remove("requestId");
        copy.remove("timestamp");
        return copy;
    }

    private List<String> auditEvents(UUID accountId) {
        return jdbcTemplate.queryForList(
                "select event_type from security_audit_events where target_account_id = ? order by id",
                String.class, accountId);
    }
}
