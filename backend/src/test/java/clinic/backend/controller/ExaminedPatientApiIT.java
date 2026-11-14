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

/** UC-34 View Examined Patient List through the HTTP API on PostgreSQL (BR-39). */
@Sql("/db/iteration1-schema-fixture.sql")
class ExaminedPatientApiIT extends PostgresIntegrationTest {

    private static final String PASSWORD = "examined-patients-password";
    private static final String PATH = "/api/v1/examined-patients";
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
    void aDoctorSeesOnlyTheVisitsTheyExamined() throws Exception {
        StaffAccount me = account(Role.DOCTOR);
        StaffAccount colleague = account(Role.DOCTOR);
        UUID concluded = visit("BN00000003", LocalDate.of(2004, 2, 2), "COMPLETED", me);
        visit("BN00000004", LocalDate.of(2004, 2, 3), "IN_EXAMINATION", me);
        visit("BN00000003", LocalDate.of(2004, 2, 5), "COMPLETED", me);
        visit("BN00000008", LocalDate.of(2004, 2, 3), "COMPLETED", colleague);
        visit("BN00000008", LocalDate.of(2004, 3, 1), "COMPLETED", me); // outside the period
        conclude(concluded, me);
        String token = accessToken(me);

        mockMvc.perform(get(PATH).param("from", "2004-02-01").param("to", "2004-02-29")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.examinedVisits").value(3))
                .andExpect(jsonPath("$.data.distinctPatients").value(2))
                .andExpect(jsonPath("$.data.visits.page.totalElements").value(3))
                .andExpect(jsonPath("$.data.visits.content", hasSize(3)))
                // Newest first.
                .andExpect(jsonPath("$.data.visits.content[0].queueDate").value("2004-02-05"))
                .andExpect(jsonPath("$.data.visits.content[1].patientName").value("Phạm Thị Hoa"))
                .andExpect(jsonPath("$.data.visits.content[1].status").value("IN_EXAMINATION"))
                .andExpect(jsonPath("$.data.visits.content[1].icdCode").doesNotExist())
                .andExpect(jsonPath("$.data.visits.content[2].patientCode").value("BN00000003"))
                .andExpect(jsonPath("$.data.visits.content[2].icdCode").value("U96.1"))
                .andExpect(jsonPath("$.data.visits.content[2].roomName").value("Phòng khám Nội tổng quát"));

        // Accent-insensitive name search; the totals still cover the whole period.
        mockMvc.perform(get(PATH).param("from", "2004-02-01").param("to", "2004-02-29").param("keyword", "le van dung")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.examinedVisits").value(3))
                .andExpect(jsonPath("$.data.visits.page.totalElements").value(2));
        mockMvc.perform(get(PATH).param("from", "2004-02-01").param("to", "2004-02-29").param("size", "1").param("page", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.visits.content", hasSize(1)))
                .andExpect(jsonPath("$.data.visits.page.totalPages").value(3));
    }

    @Test
    void onlyDoctorsUseTheListAndBadParametersAreRejected() throws Exception {
        for (Role role : new Role[] {Role.ADMIN, Role.RECEPTIONIST, Role.CASHIER, Role.TECHNICIAN}) {
            mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(account(role))))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());

        String token = accessToken(account(Role.DOCTOR));
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visits.content", hasSize(0)));
        mockMvc.perform(get(PATH).param("from", "2026-11-10").param("to", "2026-11-01")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("size", "500").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    private UUID visit(String mabenhnhan, LocalDate queueDate, String status, StaffAccount doctor) {
        UUID patient = jdbc.queryForObject("select id from patients where mabenhnhan = ?", UUID.class, mabenhnhan);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into visits (id, sovaovien, patient_id, room_id, registered_by_account_id, thoigianvaovien,
                    doituongbn_loai, insurance_verify_status, lydovaovien, is_priority, queue_date, queue_number,
                    status, doctor_account_id, denkhambenhluc, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, now(), 'DV', 'NOT_APPLICABLE', 'Khám bệnh', false, ?, ?, ?, ?, now(), now(), now(), 0)
                """, id, "VV-" + id.toString().substring(0, 12), patient, ROOM, RECEPTIONIST, queueDate,
                ++queueNumber, status, doctor.getId());
        return id;
    }

    private void conclude(UUID visit, StaffAccount doctor) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                insert into icd10 (maicd, tenicd, hieu_luc_tu) values ('U96.1', 'Bệnh thử UC-34', date '2000-01-01')
                on conflict (maicd) do nothing
                """);
        String recordType = "EP-" + suffix;
        jdbc.update("""
                insert into medical_record_types (loaiba, ten, pham_vi, van_ban, is_default, created_at, updated_at,
                    version)
                values (?, 'Bệnh án ngoại trú', 'OUTPATIENT', '32/2023/TT-BYT', false, now(), now(), 0)
                """, recordType);
        UUID record = UUID.randomUUID();
        jdbc.update("""
                insert into medical_records (id, visit_id, soba, loaiba, status, opened_at, opened_by_account_id,
                    created_at, updated_at, version)
                values (?, ?, ?, ?, 'LOCKED', now(), ?, now(), now(), 0)
                """, record, visit, "BA-" + suffix, recordType, doctor.getId());
        jdbc.update("""
                insert into conclusions (id, medical_record_id, doctor_account_id, chandoan, maicd, tenicd, huongdieutri,
                    ketquadieutri, loidanbacsi, concluded_at, created_at, updated_at, version)
                values (?, ?, ?, 'Bệnh thử', 'U96.1', 'Bệnh thử UC-34', 'Điều trị ngoại trú', 1, 'Tái khám khi cần',
                    now(), now(), now(), 0)
                """, UUID.randomUUID(), record, doctor.getId());
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
