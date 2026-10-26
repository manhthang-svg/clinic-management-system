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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MedicalServiceApiIT extends PostgresIntegrationTest {

    private static final String PASSWORD = "service-api-password";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StaffAccountRepository accounts;
    @Autowired PasswordHasher passwordHasher;
    @Autowired UuidV7Generator ids;
    @Autowired JdbcTemplate jdbc;

    @Test
    void adminCanFilterServicesAndNonAdminIsForbidden() throws Exception {
        StaffAccount admin = account(Role.ADMIN);
        StaffAccount receptionist = account(Role.RECEPTIONIST);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID departmentId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        insertCatalog(departmentId, groupId, suffix);

        mockMvc.perform(get("/api/v1/services")
                        .param("keyword", suffix)
                        .param("type", "LAB")
                        .param("group", groupId.toString())
                        .param("department", departmentId.toString())
                        .param("status", "ACTIVE")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "code,asc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].code").value("XN-" + suffix))
                .andExpect(jsonPath("$.data.content[0].departmentName").value("Khoa " + suffix))
                .andExpect(jsonPath("$.data.content[0].serviceGroupName").value("Nhóm " + suffix))
                .andExpect(jsonPath("$.data.content[0].insurancePrice").doesNotExist())
                .andExpect(jsonPath("$.data.page.size").value(10));

        mockMvc.perform(get("/api/v1/services")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(receptionist)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanViewServiceDetailWithOnlyActiveRoomsInTheSameGroup() throws Exception {
        StaffAccount admin = account(Role.ADMIN);
        StaffAccount receptionist = account(Role.RECEPTIONIST);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID departmentId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID serviceId = insertCatalog(departmentId, groupId, suffix);
        insertRoom(departmentId, groupId, "A-" + suffix, "ACTIVE");
        insertRoom(departmentId, groupId, "B-" + suffix, "INACTIVE");

        String adminToken = accessToken(admin);
        mockMvc.perform(get("/api/v1/services/{serviceId}", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(serviceId.toString()))
                .andExpect(jsonPath("$.data.code").value("XN-" + suffix))
                .andExpect(jsonPath("$.data.departmentName").value("Khoa " + suffix))
                .andExpect(jsonPath("$.data.serviceGroupName").value("Nhóm " + suffix))
                .andExpect(jsonPath("$.data.insurancePrice").doesNotExist())
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.performingRooms.length()").value(1))
                .andExpect(jsonPath("$.data.performingRooms[0].code").value("A-" + suffix));

        mockMvc.perform(get("/api/v1/services/{serviceId}", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(receptionist)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/services/{serviceId}", serviceId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/services/{serviceId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/services/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    private UUID insertCatalog(UUID departmentId, UUID groupId, String suffix) {
        Timestamp now = Timestamp.from(Instant.now());
        UUID serviceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO departments (id, makhoa, tenkhoa, type, is_active, created_at, updated_at, version)
                VALUES (?, ?, ?, 'PARACLINICAL', true, ?, ?, 0)
                """, departmentId, "K-" + suffix, "Khoa " + suffix, now, now);
        jdbc.update("""
                INSERT INTO service_groups (id, loaimachidinh, loaichidinh, type, created_at, updated_at, version)
                VALUES (?, ?, ?, 'LAB', ?, ?, 0)
                """, groupId, "N-" + suffix, "Nhóm " + suffix, now, now);
        jdbc.update("""
                INSERT INTO services (id, code, tenchidinh, type, department_id, service_group_id,
                    clinic_price, insurance_price, status, created_at, updated_at, version)
                VALUES (?, ?, ?, 'LAB', ?, ?, 120000, NULL, 'ACTIVE', ?, ?, 0)
                """, serviceId, "XN-" + suffix, "Xét nghiệm " + suffix,
                departmentId, groupId, now, now);
        return serviceId;
    }

    private void insertRoom(UUID departmentId, UUID groupId, String code, String status) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO rooms (id, department_id, service_group_id, maphongkham, phongkham, location,
                    type, status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, 'PARACLINICAL', ?, ?, ?, 0)
                """, UUID.randomUUID(), departmentId, groupId, code, "Phòng " + code, "Tầng 2", status, now, now);
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
