package clinic.backend.controller;

import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-26 and UC-27 statistics through the HTTP API; runs every aggregate query on PostgreSQL. */
@Sql("/db/iteration1-schema-fixture.sql")
class StatisticsApiIT extends PostgresIntegrationTest {

    private static final String PASSWORD = "statistics-api-password";
    private static final String DASHBOARD = "/api/v1/statistics/dashboard";
    private static final String VISITS = "/api/v1/statistics/visits";
    private static final String DOCTORS = "/api/v1/statistics/doctors";
    private static final String DEPARTMENTS = "/api/v1/statistics/departments";
    private static final UUID RECEPTIONIST = UUID.fromString("0192a000-0005-7000-8000-000000000007");
    private static final UUID ROOM = UUID.fromString("0192a000-0004-7000-8000-000000000004");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StaffAccountRepository accounts;
    @Autowired PasswordHasher passwordHasher;
    @Autowired UuidV7Generator ids;
    @Autowired JdbcTemplate jdbc;

    private int queueNumber;

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

    @Test
    void visitStatisticsGroupByDayAndMonthOnPostgres() throws Exception {
        visit("BN00000003", LocalDate.of(2003, 3, 30), "COMPLETED");
        visit("BN00000004", LocalDate.of(2003, 3, 30), "CANCELLED");
        visit("BN00000008", LocalDate.of(2003, 3, 31), "ABANDONED");
        visit("BN00000003", LocalDate.of(2003, 4, 2), "WAITING");
        String token = accessToken(account(Role.ADMIN));

        mockMvc.perform(get(VISITS).param("from", "2003-03-30").param("to", "2003-04-02")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupBy").value("DAY"))
                .andExpect(jsonPath("$.data.summary.total").value(4))
                .andExpect(jsonPath("$.data.summary.completed").value(1))
                .andExpect(jsonPath("$.data.summary.cancelled").value(1))
                .andExpect(jsonPath("$.data.summary.abandoned").value(1))
                .andExpect(jsonPath("$.data.summary.inProgress").value(1))
                .andExpect(jsonPath("$.data.summary.selfPay").value(4))
                .andExpect(jsonPath("$.data.summary.followUp").value(0))
                .andExpect(jsonPath("$.data.series", hasSize(4)))
                .andExpect(jsonPath("$.data.series[0].period").value("2003-03-30"))
                .andExpect(jsonPath("$.data.series[0].counts.total").value(2))
                .andExpect(jsonPath("$.data.series[2].period").value("2003-04-01"))
                .andExpect(jsonPath("$.data.series[2].counts.total").value(0));

        mockMvc.perform(get(VISITS).param("from", "2003-03-30").param("to", "2003-04-02").param("groupBy", "MONTH")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.series", hasSize(2)))
                .andExpect(jsonPath("$.data.series[0].period").value("2003-03-01"))
                .andExpect(jsonPath("$.data.series[0].counts.total").value(3))
                .andExpect(jsonPath("$.data.series[1].period").value("2003-04-01"))
                .andExpect(jsonPath("$.data.series[1].counts.inProgress").value(1));

        mockMvc.perform(get(VISITS).param("groupBy", "WEEK").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(VISITS).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(account(Role.DOCTOR))))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorStatisticsCountTheVisitsEachDoctorExaminedOnPostgres() throws Exception {
        StaffAccount an = account(Role.DOCTOR);
        StaffAccount binh = account(Role.DOCTOR);
        visit("BN00000003", LocalDate.of(2003, 5, 2), "COMPLETED", an.getId());
        visit("BN00000004", LocalDate.of(2003, 5, 2), "ABANDONED", an.getId());
        visit("BN00000003", LocalDate.of(2003, 5, 3), "COMPLETED", an.getId());
        visit("BN00000008", LocalDate.of(2003, 5, 3), "COMPLETED", binh.getId());
        visit("BN00000004", LocalDate.of(2003, 5, 4), "WAITING");
        String token = accessToken(account(Role.ADMIN));

        mockMvc.perform(get(DOCTORS).param("from", "2003-05-01").param("to", "2003-05-31")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.examinedVisits").value(4))
                .andExpect(jsonPath("$.data.visitsWithoutDoctor").value(1))
                .andExpect(jsonPath("$.data.doctors[0].doctorId").value(an.getId().toString()))
                .andExpect(jsonPath("$.data.doctors[0].visits").value(3))
                .andExpect(jsonPath("$.data.doctors[0].patients").value(2))
                .andExpect(jsonPath("$.data.doctors[0].completed").value(2))
                .andExpect(jsonPath("$.data.doctors[0].abandoned").value(1))
                .andExpect(jsonPath("$.data.doctors[1].doctorId").value(binh.getId().toString()))
                .andExpect(jsonPath("$.data.doctors[1].visits").value(1));

        mockMvc.perform(get(DOCTORS).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(account(Role.DOCTOR))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(DOCTORS).param("from", "2026-11-10").param("to", "2026-11-01")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void departmentStatisticsGroupRoomWorkloadByDepartmentOnPostgres() throws Exception {
        visit("BN00000003", LocalDate.of(2003, 6, 2), "COMPLETED");
        visit("BN00000004", LocalDate.of(2003, 6, 2), "COMPLETED");
        visit("BN00000008", LocalDate.of(2003, 6, 3), "WAITING");
        String token = accessToken(account(Role.ADMIN));

        mockMvc.perform(get(DEPARTMENTS).param("from", "2003-06-01").param("to", "2003-06-30")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visits").value(3))
                .andExpect(jsonPath("$.data.services").value(0))
                .andExpect(jsonPath("$.data.departments[0].departmentCode").value("TEST-NOI"))
                .andExpect(jsonPath("$.data.departments[0].type").value("CLINICAL"))
                .andExpect(jsonPath("$.data.departments[0].visits").value(3))
                .andExpect(jsonPath("$.data.departments[0].completedVisits").value(2))
                .andExpect(jsonPath("$.data.departments[0].rooms[0].roomCode").value("PK-NOI-TQ"))
                .andExpect(jsonPath("$.data.departments[0].rooms[0].visits").value(3))
                .andExpect(jsonPath("$.data.departments[0].rooms[0].inProgressVisits").value(1))
                .andExpect(jsonPath("$.data.departments[0].rooms[1].roomCode").value("PK-NGOAI"))
                .andExpect(jsonPath("$.data.departments[0].rooms[1].visits").value(0));

        mockMvc.perform(get(DEPARTMENTS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(account(Role.TECHNICIAN))))
                .andExpect(status().isForbidden());
    }

    private void visit(String mabenhnhan, LocalDate queueDate, String status) {
        visit(mabenhnhan, queueDate, status, null);
    }

    private void visit(String mabenhnhan, LocalDate queueDate, String status, UUID doctor) {
        UUID patient = jdbc.queryForObject("select id from patients where mabenhnhan = ?", UUID.class, mabenhnhan);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into visits (id, sovaovien, patient_id, room_id, registered_by_account_id, thoigianvaovien,
                    doituongbn_loai, insurance_verify_status, lydovaovien, is_priority, queue_date, queue_number,
                    status, doctor_account_id, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, now(), 'DV', 'NOT_APPLICABLE', 'Khám bệnh', false, ?, ?, ?, ?, now(), now(), 0)
                """, id, "VV-" + id.toString().substring(0, 12), patient, ROOM, RECEPTIONIST, queueDate,
                ++queueNumber, status, doctor);
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
