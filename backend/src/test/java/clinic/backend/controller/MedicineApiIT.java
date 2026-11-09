package clinic.backend.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-81 to UC-84 through the HTTP API (I1-D46). */
class MedicineApiIT extends PostgresIntegrationTest {

    private static final String PASSWORD = "medicine-api-password";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StaffAccountRepository accounts;
    @Autowired PasswordHasher passwordHasher;
    @Autowired UuidV7Generator ids;
    @Autowired JdbcTemplate jdbc;

    private String suffix;
    private String adminToken;

    @BeforeEach
    void signIn() throws Exception {
        suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        adminToken = accessToken(account(Role.ADMIN));
    }

    @Test
    void adminCreatesAMedicineAndOtherRolesAreForbidden() throws Exception {
        String id = create(medicine("t-" + suffix, registration(1), "Paracetamol " + suffix, "Uống", "THUONG"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID medicineId = UUID.fromString(objectMapper.readTree(id).get("data").get("medicineId").asText());

        mockMvc.perform(get("/api/v1/medicines/{id}", medicineId).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("T-" + suffix))
                .andExpect(jsonPath("$.data.registrationNumber").value(registration(1)))
                .andExpect(jsonPath("$.data.insuranceCode").doesNotExist())
                .andExpect(jsonPath("$.data.route").value("Uống"))
                .andExpect(jsonPath("$.data.controlGroup").value("THUONG"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(0));
        assertThat(auditCount(medicineId, "CREATE")).isEqualTo(1);

        String doctorToken = accessToken(account(Role.DOCTOR));
        mockMvc.perform(get("/api/v1/medicines").header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/medicines")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/medicines/{id}", UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDICINE_NOT_FOUND"));
    }

    @Test
    void rejectsDuplicateCodesRegistrationNumbersAndInvalidFields() throws Exception {
        create(medicine("T-" + suffix, registration(1), "Thuốc " + suffix, "Uống", "THUONG"))
                .andExpect(status().isCreated());

        create(medicine("t-" + suffix, registration(2), "Thuốc khác", "Uống", "THUONG"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEDICINE_CODE_ALREADY_EXISTS"));
        create(medicine("U-" + suffix, registration(1), "Thuốc khác", "Uống", "THUONG"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEDICINE_REGISTRATION_NUMBER_ALREADY_EXISTS"));
        create(medicine("V-" + suffix, registration(3), "Thuốc khác", "Uống qua miệng", "THUONG"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.route").value("NOT_IN_ROUTE_LIST"));
        create(medicine("W " + suffix, registration(4), "Thuốc khác", "Uống", "THUONG"))
                .andExpect(status().isBadRequest());
        Map<String, Object> missing = medicine("X-" + suffix, registration(5), "Thuốc khác", "Uống", "THUONG");
        missing.remove("activeIngredient");
        create(missing).andExpect(status().isBadRequest());
    }

    @Test
    void searchesIgnoringAccentsAndFilters() throws Exception {
        create(medicine("A-" + suffix, registration(1), "Siro ho " + suffix, "Uống", "THUONG"))
                .andExpect(status().isCreated());
        create(medicine("B-" + suffix, registration(2), "Kem bôi " + suffix, "Bôi ngoài da", "THUONG"))
                .andExpect(status().isCreated());
        create(medicine("C-" + suffix, registration(3), "An thần " + suffix, "Uống", "HUONG_THAN"))
                .andExpect(status().isCreated());

        list(Map.of("keyword", "  KEM BOI " + suffix.toLowerCase() + " "))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].code").value("B-" + suffix));
        list(Map.of("keyword", registration(3)))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].controlGroup").value("HUONG_THAN"));
        list(Map.of("keyword", suffix, "route", "Uống", "sort", "code,desc"))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].code").value("C-" + suffix));
        list(Map.of("keyword", suffix, "controlGroup", "HUONG_THAN"))
                .andExpect(jsonPath("$.data.content.length()").value(1));
        list(Map.of("keyword", "không có " + suffix))
                .andExpect(jsonPath("$.data.content.length()").value(0));

        mockMvc.perform(get("/api/v1/medicines").param("size", "15").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/medicines/form-options").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routes", hasItem("Bôi ngoài da")))
                .andExpect(jsonPath("$.data.units", hasItem("Viên")));
    }

    @Test
    void updatesEveryFieldButTheCodeAndChecksTheVersion() throws Exception {
        UUID medicineId = createdId(medicine("D-" + suffix, registration(1), "Thuốc " + suffix, "Uống", "THUONG"));
        create(medicine("E-" + suffix, registration(2), "Thuốc khác " + suffix, "Uống", "THUONG"))
                .andExpect(status().isCreated());

        Map<String, Object> update = medicine(null, registration(3), "Thuốc mới " + suffix, "Tiêm", "GAY_NGHIEN");
        update.remove("code");
        update.put("insuranceCode", "40.17");
        update.put("version", 0);
        update(medicineId, update)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("D-" + suffix))
                .andExpect(jsonPath("$.data.registrationNumber").value(registration(3)))
                .andExpect(jsonPath("$.data.insuranceCode").value("40.17"))
                .andExpect(jsonPath("$.data.route").value("Tiêm"))
                .andExpect(jsonPath("$.data.controlGroup").value("GAY_NGHIEN"))
                .andExpect(jsonPath("$.data.version").value(1));
        assertThat(auditCount(medicineId, "UPDATE")).isEqualTo(1);

        update.put("name", "Tên khác " + suffix);
        update(medicineId, update)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        update.put("version", 1);
        update.put("registrationNumber", registration(2));
        update(medicineId, update)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEDICINE_REGISTRATION_NUMBER_ALREADY_EXISTS"));
    }

    @Test
    void deactivatesAndReactivatesAMedicine() throws Exception {
        UUID medicineId = createdId(medicine("F-" + suffix, registration(1), "Thuốc " + suffix, "Uống", "THUONG"));

        changeStatus(medicineId, "INACTIVE", 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
        list(Map.of("keyword", suffix, "status", "INACTIVE"))
                .andExpect(jsonPath("$.data.content.length()").value(1));
        changeStatus(medicineId, "ACTIVE", 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        changeStatus(medicineId, "ACTIVE", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        assertThat(auditCount(medicineId, "UPDATE")).isEqualTo(2);
    }

    private Map<String, Object> medicine(String code, String registration, String name, String route, String group) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("registrationNumber", registration);
        body.put("name", name);
        body.put("activeIngredient", "Hoạt chất " + suffix);
        body.put("strength", "500mg");
        body.put("route", route);
        body.put("unit", "Viên");
        body.put("controlGroup", group);
        return body;
    }

    private String registration(int n) {
        return "R" + suffix + n;
    }

    private ResultActions create(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/v1/medicines")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private UUID createdId(Map<String, Object> body) throws Exception {
        String response = create(body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("data").get("medicineId").asText());
    }

    private ResultActions update(UUID medicineId, Map<String, Object> body) throws Exception {
        return mockMvc.perform(put("/api/v1/medicines/{id}", medicineId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions changeStatus(UUID medicineId, String status, long version) throws Exception {
        return mockMvc.perform(patch("/api/v1/medicines/{id}/status", medicineId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", status, "version", version))));
    }

    private ResultActions list(Map<String, String> params) throws Exception {
        var request = get("/api/v1/medicines").header(HttpHeaders.AUTHORIZATION, bearer());
        params.forEach(request::param);
        return mockMvc.perform(request).andExpect(status().isOk());
    }

    private long auditCount(UUID medicineId, String action) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs
                WHERE entity_type = 'medicine_catalog' AND entity_id = ? AND action = ?
                """, Long.class, medicineId, action);
    }

    private String bearer() {
        return "Bearer " + adminToken;
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
