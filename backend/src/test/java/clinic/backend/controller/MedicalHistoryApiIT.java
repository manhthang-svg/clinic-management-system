package clinic.backend.controller;

import java.sql.Date;
import java.sql.Timestamp;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-39 Update Medical History and Allergies. */
class MedicalHistoryApiIT extends PostgresIntegrationTest {

    private static final String PASSWORD = "medical-history-password";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StaffAccountRepository accounts;
    @Autowired PasswordHasher passwordHasher;
    @Autowired UuidV7Generator ids;
    @Autowired JdbcTemplate jdbc;

    @Test
    void doctorSavesHistoryAndAllergiesAndSeesThemAgain() throws Exception {
        Fixture fixture = fixture("IN_EXAMINATION");
        String token = accessToken(fixture.doctor());

        mockMvc.perform(get(path(fixture)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editable").value(true))
                .andExpect(jsonPath("$.data.history", hasSize(0)))
                .andExpect(jsonPath("$.data.updatedAt").value(nullValue()));

        String body = """
                {"history":[{"category":"CHRONIC_DISEASE","content":"Tăng huyết áp"}],
                 "allergies":[{"drug":"Penicillin","reaction":"Phát ban","severity":"SEVERE"}],
                 "noKnownDrugAllergy":false,"lastUpdatedAt":null}
                """;
        String saved = mockMvc.perform(put(path(fixture))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.history[0].content").value("Tăng huyết áp"))
                .andExpect(jsonPath("$.data.allergies[0].drug").value("Penicillin"))
                .andExpect(jsonPath("$.data.allergies[0].severity").value("SEVERE"))
                .andExpect(jsonPath("$.data.updatedByName").value(fixture.doctor().getFullName()))
                .andReturn().getResponse().getContentAsString();
        String updatedAt = objectMapper.readTree(saved).at("/data/updatedAt").asText();

        mockMvc.perform(get(path(fixture)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allergies[0].reaction").value("Phát ban"))
                .andExpect(jsonPath("$.data.updatedAt").value(updatedAt));

        Integer audits = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE entity_id = ? AND action = 'UPDATE'",
                Integer.class, fixture.patientId());
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void noKnownDrugAllergyIsStoredAndHiddenFromTheAllergyList() throws Exception {
        Fixture fixture = fixture("WAITING_CONCLUSION");
        String token = accessToken(fixture.doctor());

        mockMvc.perform(put(path(fixture))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"history\":[],\"allergies\":[],\"noKnownDrugAllergy\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.noKnownDrugAllergy").value(true))
                .andExpect(jsonPath("$.data.allergies", hasSize(0)));
    }

    @Test
    void staleLastUpdatedAtIsRejected() throws Exception {
        Fixture fixture = fixture("IN_EXAMINATION");
        String token = accessToken(fixture.doctor());
        String first = "{\"history\":[],\"allergies\":[{\"drug\":\"Aspirin\"}],\"noKnownDrugAllergy\":false}";
        mockMvc.perform(put(path(fixture)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isOk());

        // A second Doctor screen still thinks there is no data yet.
        mockMvc.perform(put(path(fixture)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        Fixture fixture = fixture("IN_EXAMINATION");
        String token = accessToken(fixture.doctor());
        String noDrug = "{\"history\":[],\"allergies\":[{\"drug\":\" \"}],\"noKnownDrugAllergy\":false}";
        String conflicting = "{\"history\":[],\"allergies\":[{\"drug\":\"Aspirin\"}],\"noKnownDrugAllergy\":true}";
        String tooLong = "{\"history\":[{\"category\":\"OTHER\",\"content\":\"" + "x".repeat(256)
                + "\"}],\"allergies\":[],\"noKnownDrugAllergy\":false}";
        for (String body : new String[] {noDrug, conflicting, tooLong}) {
            mockMvc.perform(put(path(fixture)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void doctorOfAnotherRoomSeesReadOnlyPanelAndCannotSave() throws Exception {
        Fixture fixture = fixture("IN_EXAMINATION");
        StaffAccount otherDoctor = account(Role.DOCTOR);
        assignToRoom(otherDoctor, insertRoom(insertDepartment(suffix()), "PX-" + suffix()));
        String token = accessToken(otherDoctor);

        mockMvc.perform(get(path(fixture)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editable").value(false))
                .andExpect(jsonPath("$.data.readOnlyReason").value("NOT_IN_EXAMINATION"));
        mockMvc.perform(put(path(fixture)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"history\":[],\"allergies\":[],\"noKnownDrugAllergy\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void closedMedicalRecordCannotBeChanged() throws Exception {
        Fixture fixture = fixture("IN_EXAMINATION");
        Timestamp now = Timestamp.from(Instant.now());
        UUID recordId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO medical_records (id, visit_id, soba, loaiba, status, opened_at, opened_by_account_id,
                    closed_at, closed_by_account_id, created_at, updated_at, version)
                VALUES (?, ?, ?, 'NGOAITRU', 'LOCKED', ?, ?, ?, ?, ?, ?, 0)
                """, recordId, fixture.visitId(), "BA-" + suffix(), now, fixture.doctor().getId(),
                now, fixture.doctor().getId(), now, now);
        try {
            String token = accessToken(fixture.doctor());

            mockMvc.perform(get(path(fixture)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(jsonPath("$.data.readOnlyReason").value("MEDICAL_RECORD_CLOSED"));
            mockMvc.perform(put(path(fixture)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"history\":[],\"allergies\":[],\"noKnownDrugAllergy\":true}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("MEDICAL_RECORD_CLOSED"));
        } finally {
            // Other suites reset medical_record_types and need no record pointing at them.
            jdbc.update("DELETE FROM medical_records WHERE id = ?", recordId);
        }
    }

    @Test
    void onlyDoctorsCanReadOrChangeHistory() throws Exception {
        Fixture fixture = fixture("IN_EXAMINATION");
        for (Role role : new Role[] {Role.RECEPTIONIST, Role.CASHIER, Role.TECHNICIAN, Role.ADMIN}) {
            mockMvc.perform(get(path(fixture))
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(account(role))))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get(path(fixture))).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownVisitIsNotFound() throws Exception {
        StaffAccount doctor = account(Role.DOCTOR);
        mockMvc.perform(get("/api/v1/visits/" + UUID.randomUUID() + "/medical-history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(doctor)))
                .andExpect(status().isNotFound());
    }

    private record Fixture(StaffAccount doctor, UUID patientId, UUID visitId) {
    }

    private static String path(Fixture fixture) {
        return "/api/v1/visits/" + fixture.visitId() + "/medical-history";
    }

    private Fixture fixture(String visitStatus) {
        StaffAccount doctor = account(Role.DOCTOR);
        UUID roomId = insertRoom(insertDepartment(suffix()), "PH-" + suffix());
        assignToRoom(doctor, roomId);
        Timestamp now = Timestamp.from(Instant.now());
        String suffix = suffix();
        UUID patientId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO patients (id, mabenhnhan, hoten, ngaysinh, gioitinh, diachi,
                    created_by_account_id, created_at, updated_at, version)
                VALUES (?, ?, ?, DATE '1990-01-01', 1, 'Hà Nội', ?, ?, ?, 0)
                """, patientId, "BN-" + suffix, "Bệnh nhân " + suffix, doctor.getId(), now, now);
        UUID visitId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO visits (id, sovaovien, patient_id, room_id, registered_by_account_id, thoigianvaovien,
                    doituongbn_loai, lydovaovien, is_priority, queue_date, queue_number, status, is_skipped,
                    created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, 'DV', 'Khám bệnh', false, ?, 1, ?, false, ?, ?, 0)
                """, visitId, "VV-" + suffix, patientId, roomId, doctor.getId(), now,
                Date.valueOf(LocalDate.now()), visitStatus, now, now);
        return new Fixture(doctor, patientId, visitId);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UUID insertDepartment(String suffix) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO departments (id, makhoa, tenkhoa, type, is_active, created_at, updated_at, version)
                VALUES (?, ?, ?, 'CLINICAL', true, ?, ?, 0)
                """, id, "K-" + suffix, "Khoa " + suffix, now, now);
        return id;
    }

    private UUID insertRoom(UUID departmentId, String code) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO rooms (id, department_id, service_group_id, maphongkham, phongkham, location,
                    type, status, created_at, updated_at, version)
                VALUES (?, ?, NULL, ?, ?, 'Tầng 1', 'EXAMINATION', 'ACTIVE', ?, ?, 0)
                """, id, departmentId, code, "Phòng " + code, now, now);
        return id;
    }

    private void assignToRoom(StaffAccount staff, UUID roomId) {
        jdbc.update("""
                INSERT INTO staff_room_assignments (id, staff_account_id, room_id, assigned_at, version)
                VALUES (?, ?, ?, ?, 0)
                """, UUID.randomUUID(), staff.getId(), roomId, Timestamp.from(Instant.now()));
    }

    private StaffAccount account(Role role) {
        String username = role.name().toLowerCase() + "." + suffix();
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
