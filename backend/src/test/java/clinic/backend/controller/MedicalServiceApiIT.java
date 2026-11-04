package clinic.backend.controller;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Test
    void adminCanLoadFormOptionsAndCreateService() throws Exception {
        StaffAccount admin = account(Role.ADMIN);
        StaffAccount receptionist = account(Role.RECEPTIONIST);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID departmentId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID parameterId = insertLabParameter(suffix);
        insertCatalog(departmentId, groupId, suffix);
        insertRoom(departmentId, groupId, "R-" + suffix, "ACTIVE");
        String adminToken = accessToken(admin);

        mockMvc.perform(get("/api/v1/services/form-options")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.types", hasItem("LAB")))
                .andExpect(jsonPath("$.data.serviceGroups[*].id", hasItem(groupId.toString())))
                .andExpect(jsonPath("$.data.departments[*].id", hasItem(departmentId.toString())))
                .andExpect(jsonPath("$.data.labParameters[*].id", hasItem(parameterId.toString())));

        String code = ("NEW-" + suffix).toUpperCase(Locale.ROOT);
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "code", code.toLowerCase(),
                "name", "Dich vu " + suffix,
                "type", "LAB",
                "serviceGroupId", groupId,
                "departmentId", departmentId,
                "clinicPrice", 150000,
                "description", "Mo ta",
                "labParameters", java.util.List.of(Map.of(
                        "parameterId", parameterId,
                        "displayOrder", 1))));
        String responseBody = mockMvc.perform(post("/api/v1/services")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.serviceId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        UUID serviceId = UUID.fromString(
                objectMapper.readTree(responseBody).path("data").path("serviceId").asText());

        Map<String, Object> createdService = jdbc.queryForMap(
                "SELECT code, status FROM services WHERE id = ?", serviceId);
        assertThat(createdService)
                .containsEntry("code", code)
                .containsEntry("status", "ACTIVE");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM service_lab_parameters WHERE service_id = ? AND parameter_id = ?",
                Long.class, serviceId, parameterId)).isEqualTo(1L);
        mockMvc.perform(post("/api/v1/services")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SERVICE_CODE_ALREADY_EXISTS"));
        mockMvc.perform(post("/api/v1/services")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(receptionist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanConfigureLabReferenceRangesAndNonAdminIsForbidden() throws Exception {
        StaffAccount admin = account(Role.ADMIN);
        StaffAccount receptionist = account(Role.RECEPTIONIST);
        UUID parameterId = insertLabParameter(UUID.randomUUID().toString().substring(0, 8));
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "referenceType", "RANGE",
                "lowerBound", 4,
                "upperBound", 10,
                "lowerInclusive", true,
                "upperInclusive", true,
                "effectiveFrom", "2026-01-01",
                "source", "Clinic laboratory catalog",
                "active", true));

        mockMvc.perform(post("/api/v1/lab-parameters/{parameterId}/reference-ranges", parameterId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parameterId").value(parameterId.toString()))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.displayText").isNotEmpty());

        mockMvc.perform(get("/api/v1/lab-parameters/{parameterId}/reference-ranges", parameterId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(get("/api/v1/lab-parameters/{parameterId}/reference-ranges", parameterId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(receptionist)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createServiceRejectsInvalidAssignmentAndPrice() throws Exception {
        StaffAccount admin = account(Role.ADMIN);
        String token = accessToken(admin);
        String invalidAssignment = objectMapper.writeValueAsString(Map.of(
                "code", "BAD-" + UUID.randomUUID().toString().substring(0, 8),
                "name", "Invalid assignment",
                "type", "LAB",
                "clinicPrice", 100000));
        mockMvc.perform(post("/api/v1/services")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidAssignment))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SERVICE_ASSIGNMENT_INVALID"));

        String invalidPrice = objectMapper.writeValueAsString(Map.of(
                "code", "PRICE-" + UUID.randomUUID().toString().substring(0, 8),
                "name", "Invalid price",
                "type", "EXAMINATION",
                "clinicPrice", 100000001));
        mockMvc.perform(post("/api/v1/services")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPrice))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"));
    }

    @Test
    void adminCanUpdateServiceInformationWithOptimisticLocking() throws Exception {
        StaffAccount admin = account(Role.ADMIN);
        StaffAccount receptionist = account(Role.RECEPTIONIST);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID departmentId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID serviceId = insertCatalog(departmentId, groupId, suffix);
        String adminToken = accessToken(admin);
        String updatedName = "Updated service " + suffix;
        String updatedDescription = "Updated description";
        String updateBody = objectMapper.writeValueAsString(Map.of(
                "name", updatedName,
                "description", updatedDescription,
                "version", 0));

        mockMvc.perform(put("/api/v1/services/{serviceId}", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(updatedName))
                .andExpect(jsonPath("$.data.description").value(updatedDescription))
                .andExpect(jsonPath("$.data.version").value(1));

        assertThat(jdbc.queryForObject(
                "SELECT version FROM services WHERE id = ?", Long.class, serviceId)).isEqualTo(1L);

        String noChangeBody = objectMapper.writeValueAsString(Map.of(
                "name", " " + updatedName + " ",
                "description", updatedDescription,
                "version", 1));
        mockMvc.perform(put("/api/v1/services/{serviceId}", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noChangeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));
        String staleBody = objectMapper.writeValueAsString(Map.of(
                "name", "Another name",
                "description", updatedDescription,
                "version", 0));
        mockMvc.perform(put("/api/v1/services/{serviceId}", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staleBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        mockMvc.perform(put("/api/v1/services/{serviceId}", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(receptionist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noChangeBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanUpdateAndRemoveServicePricesWithOptimisticLocking() throws Exception {
        StaffAccount admin = account(Role.ADMIN);
        StaffAccount receptionist = account(Role.RECEPTIONIST);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID serviceId = insertCatalog(UUID.randomUUID(), UUID.randomUUID(), suffix);
        String adminToken = accessToken(admin);
        String updateBody = objectMapper.writeValueAsString(Map.of(
                "clinicPrice", 150000,
                "insurancePrice", 50000,
                "version", 0));

        mockMvc.perform(put("/api/v1/services/{serviceId}/prices", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clinicPrice").value(150000))
                .andExpect(jsonPath("$.data.insurancePrice").value(50000))
                .andExpect(jsonPath("$.data.version").value(1));

        var removeInsuranceBody = objectMapper.createObjectNode()
                .put("clinicPrice", 150000)
                .putNull("insurancePrice")
                .put("version", 1)
                .toString();
        mockMvc.perform(put("/api/v1/services/{serviceId}/prices", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(removeInsuranceBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clinicPrice").value(150000))
                .andExpect(jsonPath("$.data.insurancePrice").doesNotExist())
                .andExpect(jsonPath("$.data.version").value(2));

        assertThat(jdbc.queryForObject(
                "SELECT insurance_price FROM services WHERE id = ?", Object.class, serviceId)).isNull();
        String staleBody = objectMapper.writeValueAsString(Map.of(
                "clinicPrice", 160000,
                "version", 1));
        mockMvc.perform(put("/api/v1/services/{serviceId}/prices", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staleBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        mockMvc.perform(put("/api/v1/services/{serviceId}/prices", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(receptionist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(removeInsuranceBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDeactivateAndReactivateServiceAfterRequiredChecks() throws Exception {
        StaffAccount admin = account(Role.ADMIN);
        StaffAccount receptionist = account(Role.RECEPTIONIST);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID departmentId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID serviceId = insertCatalog(departmentId, groupId, suffix);
        String adminToken = accessToken(admin);

        mockMvc.perform(get("/api/v1/services/{serviceId}/deactivation-check", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.targetStatus").value("INACTIVE"))
                .andExpect(jsonPath("$.data.unfinishedOrderCount").value(0));

        String deactivateBody = objectMapper.writeValueAsString(Map.of(
                "status", "INACTIVE",
                "version", 0));
        mockMvc.perform(patch("/api/v1/services/{serviceId}/status", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deactivateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(get("/api/v1/services/{serviceId}/activation-check", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SERVICE_GROUP_HAS_NO_ACTIVE_ROOM"));

        insertRoom(departmentId, groupId, "P-" + suffix, "ACTIVE");
        mockMvc.perform(get("/api/v1/services/{serviceId}/activation-check", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetStatus").value("ACTIVE"));

        String activateBody = objectMapper.writeValueAsString(Map.of(
                "status", "ACTIVE",
                "version", 1));
        mockMvc.perform(patch("/api/v1/services/{serviceId}/status", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(2));

        mockMvc.perform(patch("/api/v1/services/{serviceId}/status", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deactivateBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        mockMvc.perform(get("/api/v1/services/{serviceId}/deactivation-check", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(receptionist)))
                .andExpect(status().isForbidden());
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

    private UUID insertLabParameter(String suffix) {
        UUID parameterId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO lab_parameters (id, code, name, value_type, unit, is_active,
                    created_at, updated_at, version)
                VALUES (?, ?, ?, 'NUMERIC', 'mmol/L', true, ?, ?, 0)
                """, parameterId, "LP-" + suffix, "Thông số " + suffix, now, now);
        return parameterId;
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
