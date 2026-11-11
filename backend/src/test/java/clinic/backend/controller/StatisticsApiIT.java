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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-26 View Statistics Dashboard through the HTTP API; runs every aggregate query on PostgreSQL. */
class StatisticsApiIT extends PostgresIntegrationTest {

    private static final String PASSWORD = "statistics-api-password";
    private static final String DASHBOARD = "/api/v1/statistics/dashboard";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StaffAccountRepository accounts;
    @Autowired PasswordHasher passwordHasher;
    @Autowired UuidV7Generator ids;

    @Test
    void adminSeesAnEmptyPeriodWithEveryDayListed() throws Exception {
        String token = accessToken(account(Role.ADMIN));

        mockMvc.perform(get(DASHBOARD).param("from", "2001-02-01").param("to", "2001-02-28")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.from").value("2001-02-01"))
                .andExpect(jsonPath("$.data.to").value("2001-02-28"))
                .andExpect(jsonPath("$.data.visits.total").value(0))
                .andExpect(jsonPath("$.data.distinctPatients").value(0))
                .andExpect(jsonPath("$.data.paraclinicalServicesCompleted").value(0))
                .andExpect(jsonPath("$.data.revenue.paymentCount").value(0))
                .andExpect(jsonPath("$.data.revenue.insuranceAmount").value(0))
                .andExpect(jsonPath("$.data.revenue.patientAmount").value(0))
                .andExpect(jsonPath("$.data.dailyVisits", hasSize(28)))
                .andExpect(jsonPath("$.data.topRooms", hasSize(0)))
                .andExpect(jsonPath("$.data.topDiagnoses", hasSize(0)));
    }

    @Test
    void defaultPeriodWorksWithoutParameters() throws Exception {
        String token = accessToken(account(Role.ADMIN));

        mockMvc.perform(get(DASHBOARD).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visits.total").isNumber());
    }

    @Test
    void rejectsInvalidPeriods() throws Exception {
        String token = accessToken(account(Role.ADMIN));

        mockMvc.perform(get(DASHBOARD).param("from", "2026-11-10").param("to", "2026-11-01")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"));
        mockMvc.perform(get(DASHBOARD).param("from", "not-a-date")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdminMayViewTheDashboard() throws Exception {
        for (Role role : new Role[] {Role.DOCTOR, Role.RECEPTIONIST, Role.CASHIER, Role.TECHNICIAN}) {
            String token = accessToken(account(role));
            mockMvc.perform(get(DASHBOARD).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get(DASHBOARD)).andExpect(status().isUnauthorized());
    }

    private StaffAccount account(Role role) {
        String username = role.name().toLowerCase() + "." + UUID.randomUUID().toString().substring(0, 8);
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
