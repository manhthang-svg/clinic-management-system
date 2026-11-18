package clinic.backend.controller;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-51, UC-52 against PostgreSQL (docs/uc-51-52-medical-record-view.md). */
class MedicalRecordApiIT extends PostgresIntegrationTest {

    private static final String PASSWORD = "medical-record-password";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StaffAccountRepository accounts;
    @Autowired PasswordHasher passwordHasher;
    @Autowired UuidV7Generator ids;
    @Autowired JdbcTemplate jdbc;

    private final List<UUID> recordIds = new ArrayList<>();
    private String tag;
    private StaffAccount doctor;
    private String token;
    private UUID patientA;
    private UUID completedVisit;
    private UUID cancelledVisit;
    private UUID patientB;

    /**
     * Patient A: a completed visit on 01/10/2026 with a conclusion, then a cancelled visit on 01/11/2026 without a
     * record. Patient B: one visit in examination on 02/11/2026. Every name carries a unique tag, so other suites'
     * patients never match.
     */
    @BeforeEach
    void setUp() throws Exception {
        tag = UUID.randomUUID().toString().substring(0, 8);
        doctor = account(Role.DOCTOR);
        token = accessToken(doctor);
        UUID room = insertRoom();
        String icd = insertIcd("Viêm họng cấp " + tag);

        patientA = insertPatient("Nguyễn Thị Hòa " + tag);
        completedVisit = insertVisit(patientA, room, "COMPLETED", LocalDate.of(2026, 10, 1), 1);
        UUID record = insertRecord(completedVisit, "LOCKED");
        insertConclusion(record, icd, "Viêm họng cấp " + tag);
        cancelledVisit = insertVisit(patientA, room, "CANCELLED", LocalDate.of(2026, 11, 1), 2);

        patientB = insertPatient("Trần Văn Bình " + tag);
        insertVisit(patientB, room, "IN_EXAMINATION", LocalDate.of(2026, 11, 2), 3);
    }

    @AfterEach
    void removeRecords() {
        // Other suites reset medical_record_types and need no record pointing at them.
        for (UUID id : recordIds) {
            jdbc.update("DELETE FROM conclusions WHERE medical_record_id = ?", id);
            jdbc.update("DELETE FROM medical_records WHERE id = ?", id);
        }
    }

    @Test
    void searchMatchesTheNameWithoutAccentsAndSummarisesThePatient() throws Exception {
        search("keyword=hoa " + tag)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].patientId").value(patientA.toString()))
                .andExpect(jsonPath("$.data.content[0].gender").value(1))
                .andExpect(jsonPath("$.data.content[0].totalVisits").value(2))
                .andExpect(jsonPath("$.data.content[0].lastVisitDate").value("2026-11-01"))
                // The newest visit has no conclusion, so the diagnosis comes from the completed one.
                .andExpect(jsonPath("$.data.content[0].lastDiagnosisName").value("Viêm họng cấp " + tag))
                .andExpect(jsonPath("$.data.content[0].citizenId").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].phoneNumber").doesNotExist());
    }

    @Test
    void statusFilterUsesTheFourGroups() throws Exception {
        search("keyword=" + tag + "&status=CANCELLED")
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].patientId").value(patientA.toString()));
        search("keyword=" + tag + "&status=ACTIVE")
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].patientId").value(patientB.toString()));
        search("keyword=" + tag)
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                // Newest last visit first.
                .andExpect(jsonPath("$.data.content[0].patientId").value(patientB.toString()));
    }

    @Test
    void everyVisitFilterMustHoldForTheSameVisit() throws Exception {
        // Patient A has a completed visit and a visit after 15/10, but not one visit that is both.
        search("keyword=" + tag + "&status=COMPLETED&from=2026-10-15")
                .andExpect(jsonPath("$.data.content", hasSize(0)));
        search("keyword=" + tag + "&status=COMPLETED&to=2026-10-15&icd10=viem hong")
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].patientId").value(patientA.toString()));
    }

    @Test
    void searchIsAuditedWithoutTheKeyword() throws Exception {
        search("keyword=" + tag + "&status=ACTIVE").andExpect(status().isOk());

        String values = jdbc.queryForObject("""
                SELECT new_value::text FROM audit_logs
                WHERE actor_account_id = ? AND action = 'VIEW_RECORD' AND entity_type = 'MEDICAL_RECORD_SEARCH'
                """, String.class, doctor.getId());
        assertThat(values).contains("\"keyword\"", "\"status\"", "\"resultCount\"").doesNotContain(tag);
    }

    @Test
    void visitListShowsThePatientAndEveryVisitNewestFirst() throws Exception {
        mockMvc.perform(get("/api/v1/medical-records/patients/{id}/visits", patientA).header(HttpHeaders.AUTHORIZATION,
                        bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patient.fullName").value("Nguyễn Thị Hòa " + tag))
                .andExpect(jsonPath("$.data.visits", hasSize(2)))
                .andExpect(jsonPath("$.data.visits[0].visitId").value(cancelledVisit.toString()))
                .andExpect(jsonPath("$.data.visits[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.visits[0].medicalRecordId").value(nullValue()))
                .andExpect(jsonPath("$.data.visits[1].finalDiagnosisName").value("Viêm họng cấp " + tag));
    }

    @Test
    void detailIsAuditedWithTheVisitNumberAndAVisitWithoutRecordIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/medical-records/by-visit/{id}", completedVisit)
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isEditable").value(false));
        String sovaovien = jdbc.queryForObject("""
                SELECT sovaovien FROM audit_logs
                WHERE actor_account_id = ? AND entity_type = 'MEDICAL_RECORD'
                """, String.class, doctor.getId());
        assertThat(sovaovien).isEqualTo("VV-" + tag + "-1");

        mockMvc.perform(get("/api/v1/medical-records/by-visit/{id}", cancelledVisit)
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDICAL_RECORD_NOT_FOUND"));
    }

    @Test
    void invalidFiltersAreRejected() throws Exception {
        search("keyword=a").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.keyword").value("TOO_SHORT"));
        search("status=WAITING").andExpect(status().isBadRequest());
        search("size=101").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.size").value("OUT_OF_RANGE"));
        search("from=2026-11-02&to=2026-11-01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.from").value("AFTER_TO"));
    }

    @Test
    void onlyDoctorsCanUseTheMedicalRecordScreens() throws Exception {
        for (Role role : new Role[] {Role.ADMIN, Role.RECEPTIONIST, Role.CASHIER, Role.TECHNICIAN}) {
            mockMvc.perform(get("/api/v1/medical-records/patients")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(account(role))))
                    .andExpect(status().isForbidden());
        }
    }

    /** {@code query} is unencoded: MockMvc encodes it, so a literal {@code %20} would arrive as {@code %2520}. */
    private ResultActions search(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/medical-records/patients?" + query)
                .header(HttpHeaders.AUTHORIZATION, bearer()));
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private UUID insertPatient(String fullName) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO patients (id, mabenhnhan, hoten, ngaysinh, gioitinh, diachi,
                    created_by_account_id, created_at, updated_at, version)
                VALUES (?, ?, ?, DATE '1990-01-01', 1, 'Hà Nội', ?, ?, ?, 0)
                """, id, "BN-" + UUID.randomUUID().toString().substring(0, 8), fullName, doctor.getId(), now, now);
        return id;
    }

    private UUID insertVisit(UUID patientId, UUID roomId, String visitStatus, LocalDate date, int number) {
        UUID id = UUID.randomUUID();
        Timestamp registered = Timestamp.from(date.atStartOfDay(java.time.ZoneOffset.ofHours(7)).toInstant()
                .plusSeconds(3600));
        jdbc.update("""
                INSERT INTO visits (id, sovaovien, patient_id, room_id, registered_by_account_id, thoigianvaovien,
                    doituongbn_loai, lydovaovien, is_priority, queue_date, queue_number, status, is_skipped,
                    created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, 'DV', 'Khám bệnh', false, ?, ?, ?, false, ?, ?, 0)
                """, id, "VV-" + tag + "-" + number, patientId, roomId, doctor.getId(), registered,
                Date.valueOf(date), number, visitStatus, registered, registered);
        return id;
    }

    private UUID insertRecord(UUID visitId, String recordStatus) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO medical_records (id, visit_id, soba, loaiba, status, opened_at, opened_by_account_id,
                    closed_at, closed_by_account_id, created_at, updated_at, version)
                VALUES (?, ?, ?, 'NGOAITRU', ?, ?, ?, ?, ?, ?, ?, 0)
                """, id, visitId, "BA-" + tag, recordStatus, now, doctor.getId(), now, doctor.getId(), now, now);
        recordIds.add(id);
        return id;
    }

    private void insertConclusion(UUID recordId, String icd, String icdName) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO conclusions (id, medical_record_id, doctor_account_id, chandoan, maicd, tenicd,
                    huongdieutri, ketquadieutri, loidanbacsi, concluded_at, created_at, updated_at, version)
                VALUES (?, ?, ?, 'Chẩn đoán', ?, ?, 'Điều trị ngoại trú', 1, 'Tái khám khi cần', ?, ?, ?, 0)
                """, UUID.randomUUID(), recordId, doctor.getId(), icd, icdName, now, now, now);
    }

    private String insertIcd(String name) {
        String code = "Z" + tag.substring(0, 6).toUpperCase();
        jdbc.update("INSERT INTO icd10 (maicd, tenicd, hieu_luc_tu) VALUES (?, ?, DATE '2020-01-01')", code, name);
        return code;
    }

    private UUID insertRoom() {
        UUID departmentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO departments (id, makhoa, tenkhoa, type, is_active, created_at, updated_at, version)
                VALUES (?, ?, ?, 'CLINICAL', true, ?, ?, 0)
                """, departmentId, "K-" + tag, "Khoa " + tag, now, now);
        jdbc.update("""
                INSERT INTO rooms (id, department_id, service_group_id, maphongkham, phongkham, location,
                    type, status, created_at, updated_at, version)
                VALUES (?, ?, NULL, ?, ?, 'Tầng 1', 'EXAMINATION', 'ACTIVE', ?, ?, 0)
                """, roomId, departmentId, "PH-" + tag, "Phòng " + tag, now, now);
        return roomId;
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
        JsonNode json = objectMapper.readTree(body);
        return json.get("data").get("accessToken").asText();
    }
}
