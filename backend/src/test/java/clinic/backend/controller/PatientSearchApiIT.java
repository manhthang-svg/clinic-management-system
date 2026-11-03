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
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void searchesByExactCitizenIdPatientCodeAndPhone() throws Exception {
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
        mockMvc.perform(search(receptionist, "PHONE", phone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].mabenhnhan", contains(code)));
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
    void rejectsKeywordWithWrongFormatForItsType() throws Exception {
        StaffAccount receptionist = account(Role.RECEPTIONIST);

        mockMvc.perform(search(receptionist, "CITIZEN_ID", "12345"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.keyword").value("INVALID_FORMAT"));
        mockMvc.perform(search(receptionist, "NAME", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.keyword").value("REQUIRED"));
    }

    @Test
    void returnsAtMostFiftyResults() throws Exception {
        mockMvc.perform(get("/api/v1/patients")
                        .param("size", "500")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(account(Role.RECEPTIONIST))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size", lessThanOrEqualTo(50)));
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
