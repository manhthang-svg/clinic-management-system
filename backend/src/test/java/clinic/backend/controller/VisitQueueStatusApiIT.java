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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-42 View Room Queue Status. */
class VisitQueueStatusApiIT extends PostgresIntegrationTest {

    private static final String PASSWORD = "queue-status-password";
    private static final LocalDate QUEUE_DATE = LocalDate.of(2026, 1, 15);

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StaffAccountRepository accounts;
    @Autowired PasswordHasher passwordHasher;
    @Autowired UuidV7Generator ids;
    @Autowired JdbcTemplate jdbc;

    @Test
    void receptionistSeesCountsPerActiveExaminationRoom() throws Exception {
        StaffAccount receptionist = account(Role.RECEPTIONIST);
        String suffix = suffix();
        UUID departmentId = insertDepartment(suffix);
        UUID roomId = insertRoom(departmentId, "PK-" + suffix, "ACTIVE");
        UUID emptyRoomId = insertRoom(departmentId, "PE-" + suffix, "ACTIVE");
        UUID inactiveRoomId = insertRoom(departmentId, "PI-" + suffix, "INACTIVE");

        insertVisit(receptionist, roomId, QUEUE_DATE, "WAITING", false);
        insertVisit(receptionist, roomId, QUEUE_DATE, "WAITING", false);
        insertVisit(receptionist, roomId, QUEUE_DATE, "WAITING", true);
        insertVisit(receptionist, roomId, QUEUE_DATE, "IN_EXAMINATION", false);
        insertVisit(receptionist, roomId, QUEUE_DATE, "WAITING_PARACLINICAL", false);
        insertVisit(receptionist, roomId, QUEUE_DATE, "WAITING_CONCLUSION", false);
        insertVisit(receptionist, roomId, QUEUE_DATE, "WAITING_FEE_PAYMENT", false);
        // Not part of the queue: finished visit, another day, and an inactive room.
        insertVisit(receptionist, roomId, QUEUE_DATE, "COMPLETED", false);
        insertVisit(receptionist, roomId, QUEUE_DATE.minusDays(1), "WAITING", false);
        insertVisit(receptionist, inactiveRoomId, QUEUE_DATE, "WAITING", false);

        String room = "$.data[?(@.roomId == '" + roomId + "')]";
        String emptyRoom = "$.data[?(@.roomId == '" + emptyRoomId + "')]";
        mockMvc.perform(get("/api/v1/visits/queue-status")
                        .param("date", QUEUE_DATE.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(room + ".roomCode", contains("PK-" + suffix)))
                .andExpect(jsonPath(room + ".roomName", contains("Phòng PK-" + suffix)))
                .andExpect(jsonPath(room + ".departmentName", contains("Khoa " + suffix)))
                .andExpect(jsonPath(room + ".hasDoctor", contains(false)))
                .andExpect(jsonPath(room + ".waitingPaymentCount", contains(1)))
                .andExpect(jsonPath(room + ".waitingCount", contains(2)))
                .andExpect(jsonPath(room + ".inExaminationCount", contains(1)))
                .andExpect(jsonPath(room + ".waitingParaclinicalCount", contains(1)))
                .andExpect(jsonPath(room + ".waitingConclusionCount", contains(1)))
                .andExpect(jsonPath(room + ".skippedCount", contains(1)))
                .andExpect(jsonPath(emptyRoom + ".waitingCount", contains(0)))
                .andExpect(jsonPath(emptyRoom + ".waitingPaymentCount", contains(0)))
                .andExpect(jsonPath(emptyRoom + ".skippedCount", contains(0)))
                .andExpect(jsonPath("$.data[?(@.roomId == '" + inactiveRoomId + "')]", empty()));
    }

    @Test
    void listsActiveAssignedDoctorsOfEachRoom() throws Exception {
        StaffAccount receptionist = account(Role.RECEPTIONIST);
        StaffAccount doctor = account(Role.DOCTOR);
        String suffix = suffix();
        UUID roomId = insertRoom(insertDepartment(suffix), "PD-" + suffix, "ACTIVE");
        assignToRoom(doctor, roomId);

        String room = "$.data[?(@.roomId == '" + roomId + "')]";
        mockMvc.perform(get("/api/v1/visits/queue-status")
                        .param("date", QUEUE_DATE.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(room + ".hasDoctor", contains(true)))
                .andExpect(jsonPath(room + ".doctorNames[0]", contains(doctor.getFullName())));
    }

    @Test
    void onlyReceptionistCanViewQueueStatus() throws Exception {
        for (Role role : new Role[] {Role.ADMIN, Role.DOCTOR, Role.CASHIER, Role.TECHNICIAN}) {
            mockMvc.perform(get("/api/v1/visits/queue-status")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(account(role))))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/visits/queue-status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMalformedDate() throws Exception {
        mockMvc.perform(get("/api/v1/visits/queue-status")
                        .param("date", "30/10/2026")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(account(Role.RECEPTIONIST))))
                .andExpect(status().isBadRequest());
    }

    private void assignToRoom(StaffAccount staff, UUID roomId) {
        jdbc.update("""
                INSERT INTO staff_room_assignments (id, staff_account_id, room_id, assigned_at, version)
                VALUES (?, ?, ?, ?, 0)
                """, UUID.randomUUID(), staff.getId(), roomId, Timestamp.from(Instant.now()));
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

    private UUID insertRoom(UUID departmentId, String code, String status) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO rooms (id, department_id, service_group_id, maphongkham, phongkham, location,
                    type, status, created_at, updated_at, version)
                VALUES (?, ?, NULL, ?, ?, 'Tầng 1', 'EXAMINATION', ?, ?, ?, 0)
                """, id, departmentId, code, "Phòng " + code, status, now, now);
        return id;
    }

    // One patient per visit: a patient may have only one active visit.
    private void insertVisit(StaffAccount registeredBy, UUID roomId, LocalDate queueDate,
                             String visitStatus, boolean skipped) {
        Timestamp now = Timestamp.from(Instant.now());
        String suffix = suffix();
        UUID patientId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO patients (id, mabenhnhan, hoten, ngaysinh, gioitinh, diachi,
                    created_by_account_id, created_at, updated_at, version)
                VALUES (?, ?, ?, DATE '1990-01-01', 1, 'Hà Nội', ?, ?, ?, 0)
                """, patientId, "BN-" + suffix, "Bệnh nhân " + suffix, registeredBy.getId(), now, now);
        Integer queueNumber = jdbc.queryForObject(
                "SELECT COALESCE(MAX(queue_number), 0) + 1 FROM visits WHERE room_id = ? AND queue_date = ?",
                Integer.class, roomId, Date.valueOf(queueDate));
        jdbc.update("""
                INSERT INTO visits (id, sovaovien, patient_id, room_id, registered_by_account_id, thoigianvaovien,
                    doituongbn_loai, lydovaovien, is_priority, queue_date, queue_number, status, is_skipped,
                    skipped_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, 'DV', 'Khám bệnh', false, ?, ?, ?, ?, ?, ?, ?, 0)
                """, UUID.randomUUID(), "VV-" + suffix, patientId, roomId, registeredBy.getId(), now,
                Date.valueOf(queueDate), queueNumber, visitStatus, skipped, skipped ? now : null, now, now);
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
