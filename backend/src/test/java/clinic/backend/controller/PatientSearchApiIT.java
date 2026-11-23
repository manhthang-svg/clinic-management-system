package clinic.backend.controller;

import java.sql.Timestamp;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-35 Search Patient. */
class PatientSearchApiIT extends PostgresIntegrationTest {

    private static final String PASSWORD = "patient-search-password";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StaffAccountRepository accounts;
    @Autowired PasswordHasher passwordHasher;
    @Autowired UuidV7Generator ids;
    @Autowired JdbcTemplate jdbc;

    @Test
    void searchesByNameIgnoringCaseAndVietnameseAccents() throws Exception {
        StaffAccount receptionist = account(Role.RECEPTIONIST);
        String suffix = suffix();
        String code = insertPatient(receptionist, "Nguyễn Thị Đào " + suffix, null, null);

        mockMvc.perform(search(receptionist, "NAME", "nguyen thi dao " + suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].mabenhnhan", contains(code)));
    }

    @Test
    void searchesByFullCitizenIdAndByPartOfPatientCodeAndPhone() throws Exception {
        StaffAccount receptionist = account(Role.RECEPTIONIST);
        String citizenId = digits(12);
        String phone = "09" + digits(8);
        String code = insertPatient(receptionist, "Trần Văn " + suffix(), citizenId, phone);

        mockMvc.perform(search(receptionist, "CITIZEN_ID", citizenId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].mabenhnhan", contains(code)));
        mockMvc.perform(search(receptionist, "PATIENT_CODE", code.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].mabenhnhan", contains(code)));
        mockMvc.perform(search(receptionist, "PHONE", phone.substring(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].mabenhnhan", contains(code)));
    }

    @Test
    void freeSearchMatchesTheFullCitizenIdOnlyButAPartOfCodeAndPhone() throws Exception {
        StaffAccount receptionist = account(Role.RECEPTIONIST);
        String citizenId = digits(12);
        String phone = "09" + digits(8);
        String code = insertPatient(receptionist, "Lê Văn " + suffix(), citizenId, phone);

        mockMvc.perform(freeSearch(receptionist, citizenId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].mabenhnhan", hasItem(code)));
        mockMvc.perform(freeSearch(receptionist, citizenId.substring(0, 11)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].mabenhnhan", not(hasItem(code))));
        mockMvc.perform(freeSearch(receptionist, code.substring(3).toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].mabenhnhan", hasItem(code)));
        mockMvc.perform(freeSearch(receptionist, phone.substring(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].mabenhnhan", hasItem(code)));
    }

    @Test
    void searchesByPassportAndByInsuranceCardOfPreviousVisits() throws Exception {
        StaffAccount receptionist = account(Role.RECEPTIONIST);
        String passport = "C" + digits(7);
        String passportCode = insertPatient(receptionist, "John " + suffix(), null, null, passport);
        String card = "DN40" + digits(11);
        UUID cardPatient = UUID.randomUUID();
        String cardCode = insertPatient(cardPatient, receptionist, "Lê Văn " + suffix(), null, null, null);
        insertVisit(receptionist, cardPatient, card, "COMPLETED");

        mockMvc.perform(search(receptionist, "PASSPORT", passport.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].mabenhnhan", contains(passportCode)));
        mockMvc.perform(search(receptionist, "INSURANCE_CARD", card))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].mabenhnhan", contains(cardCode)));
    }

    @Test
    void showsLastVisitAndActiveVisit() throws Exception {
        StaffAccount receptionist = account(Role.RECEPTIONIST);
        String citizenId = digits(12);
        UUID patientId = UUID.randomUUID();
        insertPatient(patientId, receptionist, "Phạm Thị " + suffix(), citizenId, null, null);
        insertVisit(receptionist, patientId, null, "COMPLETED");
        String activeCode = insertVisit(receptionist, patientId, null, "WAITING");

        mockMvc.perform(search(receptionist, "CITIZEN_ID", citizenId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].lastVisitAt").isNotEmpty())
                .andExpect(jsonPath("$.data.content[0].activeVisit.registrationCode").value(activeCode))
                .andExpect(jsonPath("$.data.content[0].activeVisit.status").value("WAITING"))
                .andExpect(jsonPath("$.data.content[0].activeVisit.roomName").isNotEmpty());
    }

    @Test
    void overviewShowsVisitHistoryAndRecordsTheView() throws Exception {
        StaffAccount receptionist = account(Role.RECEPTIONIST);
        UUID patientId = UUID.randomUUID();
        insertPatient(patientId, receptionist, "Đỗ Thị " + suffix(), null, null, null);
        String card = "DN40" + digits(11);
        String doneCode = insertVisit(receptionist, patientId, card, "COMPLETED");
        String activeCode = insertVisit(receptionist, patientId, null, "WAITING");

        mockMvc.perform(get("/api/v1/patients/" + patientId + "/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patient.id").value(patientId.toString()))
                .andExpect(jsonPath("$.data.lastInsuranceCard").value(card))
                .andExpect(jsonPath("$.data.activeVisit.registrationCode").value(activeCode))
                .andExpect(jsonPath("$.data.visits[*].registrationCode", contains(activeCode, doneCode)))
                .andExpect(jsonPath("$.data.visits[0].roomName").isNotEmpty());

        Integer views = jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs
                WHERE action = 'VIEW_RECORD' AND entity_type = 'patients' AND entity_id = ? AND actor_account_id = ?
                """, Integer.class, patientId, receptionist.getId());
        org.assertj.core.api.Assertions.assertThat(views).isEqualTo(1);
    }

    @Test
    void overviewOfUnknownPatientIsNotFoundAndCashierIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/patients/" + UUID.randomUUID() + "/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(account(Role.RECEPTIONIST))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/patients/" + UUID.randomUUID() + "/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(account(Role.CASHIER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createsAProfileAndRecordsTheCreation() throws Exception {
        StaffAccount receptionist = account(Role.RECEPTIONIST);
        String citizenId = digits(12);

        String body = mockMvc.perform(post("/api/v1/patients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(receptionist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Hoàng Văn An", "1990-05-20", citizenId, "09" + digits(8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cccdSo").value(citizenId))
                .andReturn().getResponse().getContentAsString();
        UUID patientId = UUID.fromString(objectMapper.readTree(body).get("data").get("id").asText());

        Integer created = jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs WHERE action = 'CREATE' AND entity_type = 'patients' AND entity_id = ?
                """, Integer.class, patientId);
        org.assertj.core.api.Assertions.assertThat(created).isEqualTo(1);
    }

    @Test
    void rejectsAnExistingCitizenIdWithTheProfileToOpen() throws Exception {
        StaffAccount receptionist = account(Role.RECEPTIONIST);
        String citizenId = digits(12);
        UUID existingId = UUID.randomUUID();
        String existingCode = insertPatient(existingId, receptionist, "Lý Thị " + suffix(), citizenId, null, null);

        mockMvc.perform(post("/api/v1/patients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(receptionist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Người khác", "1990-05-20", citizenId, "09" + digits(8))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PATIENT_IDENTIFICATION_EXISTS"))
                .andExpect(jsonPath("$.details.patientId").value(existingId.toString()))
                .andExpect(jsonPath("$.details.patientCode").value(existingCode));
    }

    @Test
    void rejectsAnAdultWithoutPhoneAndAWrongCitizenId() throws Exception {
        mockMvc.perform(post("/api/v1/patients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(account(Role.RECEPTIONIST)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Người lớn", "1990-05-20", "123", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.cccdSo").value("INVALID_FORMAT"));
        mockMvc.perform(post("/api/v1/patients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(account(Role.RECEPTIONIST)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Người lớn", "1990-05-20", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.sodienthoai").value("REQUIRED"));
    }

    @Test
    void listsPossibleDuplicatesBySameNameAndBirthDate() throws Exception {
        StaffAccount receptionist = account(Role.RECEPTIONIST);
        String name = "Trùng Tên " + suffix();
        String code = insertPatient(receptionist, name, null, null);

        mockMvc.perform(get("/api/v1/patients/possible-duplicates")
                        .param("hoten", name.toUpperCase())
                        .param("ngaysinh", "1990-01-01")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].mabenhnhan", contains(code)));
    }

    @Test
    void updatesAProfileAndRecordsTheChangedFields() throws Exception {
        StaffAccount receptionist = account(Role.RECEPTIONIST);
        UUID patientId = UUID.randomUUID();
        String citizenId = digits(12);
        insertPatient(patientId, receptionist, "Vũ Văn " + suffix(), citizenId, "09" + digits(8), null);

        mockMvc.perform(put("/api/v1/patients/" + patientId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(receptionist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Vũ Văn Đổi Tên", citizenId, "0912345678", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hoten").value("Vũ Văn Đổi Tên"));

        String changes = jdbc.queryForObject("""
                SELECT new_value::text FROM audit_logs
                WHERE action = 'UPDATE' AND entity_type = 'patients' AND entity_id = ?
                """, String.class, patientId);
        org.assertj.core.api.Assertions.assertThat(changes).contains("Vũ Văn Đổi Tên", "0912345678");
    }

    @Test
    void rejectsAnUpdateWithTheCitizenIdOfAnotherPatient() throws Exception {
        StaffAccount receptionist = account(Role.RECEPTIONIST);
        String takenId = digits(12);
        insertPatient(UUID.randomUUID(), receptionist, "Người có CCCD", takenId, null, null);
        UUID patientId = UUID.randomUUID();
        insertPatient(patientId, receptionist, "Người sửa", null, "09" + digits(8), null);

        mockMvc.perform(put("/api/v1/patients/" + patientId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(receptionist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Người sửa", takenId, "0912345678", 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PATIENT_IDENTIFICATION_EXISTS"))
                .andExpect(jsonPath("$.details.field").value("cccdSo"));
    }

    private String updateBody(String name, String citizenId, String phone, long version) throws Exception {
        java.util.Map<String, Object> body = objectMapper.readValue(
                createBody(name, "1990-01-01", citizenId, phone), java.util.HashMap.class);
        body.put("version", version);
        return objectMapper.writeValueAsString(body);
    }

    private String createBody(String name, String birthDate, String citizenId, String phone) throws Exception {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("hoten", name);
        body.put("ngaysinh", birthDate);
        body.put("gioitinh", 1);
        body.put("cccdSo", citizenId);
        body.put("dantoc", "Kinh");
        body.put("dantocMa", "01");
        body.put("diachi", "Hà Nội");
        body.put("sodienthoai", phone);
        return objectMapper.writeValueAsString(body);
    }

    @Test
    void rejectsKeywordWithWrongFormatForItsType() throws Exception {
        StaffAccount receptionist = account(Role.RECEPTIONIST);

        mockMvc.perform(search(receptionist, "CITIZEN_ID", "07905"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.keyword").value("INVALID_FORMAT"));
        mockMvc.perform(search(receptionist, "CITIZEN_ID", "12345A"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.keyword").value("INVALID_FORMAT"));
        mockMvc.perform(search(receptionist, "NAME", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.keyword").value("REQUIRED"));
    }

    @Test
    void returnsTwentyPatientsPerPage() throws Exception {
        mockMvc.perform(get("/api/v1/patients")
                        .param("size", "500")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(account(Role.RECEPTIONIST))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.content.length()", lessThanOrEqualTo(20)));
    }

    @Test
    void onlyReceptionistAndDoctorCanSearchPatients() throws Exception {
        mockMvc.perform(get("/api/v1/patients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(account(Role.DOCTOR))))
                .andExpect(status().isOk());
        for (Role role : new Role[] {Role.ADMIN, Role.CASHIER, Role.TECHNICIAN}) {
            mockMvc.perform(get("/api/v1/patients")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(account(role))))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/patients"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.RequestBuilder search(
            StaffAccount account, String type, String keyword) throws Exception {
        return get("/api/v1/patients")
                .param("type", type)
                .param("keyword", keyword)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(account));
    }

    private org.springframework.test.web.servlet.RequestBuilder freeSearch(
            StaffAccount account, String keyword) throws Exception {
        return get("/api/v1/patients")
                .param("keyword", keyword)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(account));
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String digits(int length) {
        StringBuilder value = new StringBuilder();
        while (value.length() < length) {
            value.append(Math.abs(UUID.randomUUID().getLeastSignificantBits()));
        }
        return value.substring(0, length);
    }

    private String insertPatient(StaffAccount createdBy, String name, String citizenId, String phone) {
        return insertPatient(UUID.randomUUID(), createdBy, name, citizenId, phone, null);
    }

    private String insertPatient(StaffAccount createdBy, String name, String citizenId, String phone,
                                 String passport) {
        return insertPatient(UUID.randomUUID(), createdBy, name, citizenId, phone, passport);
    }

    private String insertPatient(UUID id, StaffAccount createdBy, String name, String citizenId, String phone,
                                 String passport) {
        Timestamp now = Timestamp.from(Instant.now());
        String code = "BN-" + suffix().toUpperCase();
        jdbc.update("""
                INSERT INTO patients (id, mabenhnhan, cccd_so, hochieu_so, hoten, ngaysinh, gioitinh, diachi,
                    sodienthoai, created_by_account_id, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, DATE '1990-01-01', 1, 'Hà Nội', ?, ?, ?, ?, 0)
                """, id, code, citizenId, passport, name, phone, createdBy.getId(), now, now);
        return code;
    }

    // Same insert as VisitQueueStatusApiIT, in a room of its own.
    private String insertVisit(StaffAccount registeredBy, UUID patientId, String insuranceCard, String visitStatus) {
        Timestamp now = Timestamp.from(Instant.now());
        String suffix = suffix();
        UUID departmentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO departments (id, makhoa, tenkhoa, type, is_active, created_at, updated_at, version)
                VALUES (?, ?, ?, 'CLINICAL', true, ?, ?, 0)
                """, departmentId, "K-" + suffix, "Khoa " + suffix, now, now);
        UUID roomId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO rooms (id, department_id, service_group_id, maphongkham, phongkham, location,
                    type, status, created_at, updated_at, version)
                VALUES (?, ?, NULL, ?, ?, 'Tầng 1', 'EXAMINATION', 'ACTIVE', ?, ?, 0)
                """, roomId, departmentId, "PK-" + suffix, "Phòng PK-" + suffix, now, now);
        String code = "VV-" + suffix;
        if (insuranceCard == null) {
            jdbc.update("""
                    INSERT INTO visits (id, sovaovien, patient_id, room_id, registered_by_account_id,
                        thoigianvaovien, doituongbn_loai, lydovaovien, is_priority, queue_date, queue_number, status,
                        is_skipped, created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, 'DV', 'Khám bệnh', false, CURRENT_DATE, 1, ?, false, ?, ?, 0)
                    """, UUID.randomUUID(), code, patientId, roomId, registeredBy.getId(), now, visitStatus, now, now);
            return code;
        }
        // A verified insurance visit carries the full card snapshot (V28 ck_visits_insurance_snapshot).
        jdbc.update("""
                INSERT INTO visits (id, sovaovien, patient_id, room_id, registered_by_account_id, thoigianvaovien,
                    doituongbn_loai, mabhyt, insurance_social_number, tungaybhyt, denngaybhyt, noidangkykcbbd,
                    insurance_primary_facility_code, insurance_coverage_rate, insurance_verify_status,
                    insurance_verified_at, lydovaovien, is_priority, queue_date, queue_number, status, is_skipped,
                    created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, 'BHYT', ?, '0123456789', DATE '2026-01-01', DATE '2030-12-31',
                    'Bệnh viện thử', '01001', 80, 'VALID', ?, 'Khám bệnh', false, CURRENT_DATE, 1, ?, false,
                    ?, ?, 0)
                """, UUID.randomUUID(), code, patientId, roomId, registeredBy.getId(), now, insuranceCard, now,
                visitStatus, now, now);
        return code;
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
        return objectMapper.readTree(body).get("data").get("accessToken").asText();
    }
}
