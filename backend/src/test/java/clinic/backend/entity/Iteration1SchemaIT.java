package clinic.backend.entity;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.LoginRequest;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditOutcome;
import clinic.backend.enums.AuditedEntityType;
import clinic.backend.enums.BusinessAuditEventType;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.Gender;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.enums.Role;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.SimulatedInsuranceCardStatus;
import clinic.backend.repository.StaffAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Applies V2 and the development seed to a fresh PostgreSQL with the real dev profile, then checks the
 * seed, the entity mappings, and key database constraints of the Iteration 1 schema.
 *
 * <p>Uses its own container so the seed never leaks into other integration tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class Iteration1SchemaIT {

    private static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine");
    private static final String DEMO_PASSWORD = "demo-password-for-tests";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        POSTGRESQL.start();
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("clinic.security.jwt.signing-key", Iteration1SchemaIT::randomSigningKey);
        registry.add("clinic.security.password.bcrypt-strength", () -> "4");
        registry.add("clinic.dev-seed.password", () -> DEMO_PASSWORD);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StaffAccountRepository staffAccounts;

    @Test
    void loadsTheDemoMasterData() {
        assertThat(count("departments")).isEqualTo(5);
        assertThat(count("service_groups")).isEqualTo(9);
        assertThat(count("services")).isEqualTo(17);
        assertThat(count("rooms")).isEqualTo(16);
        assertThat(count("staff_accounts where username like '%.%' and role <> 'ADMIN'")).isEqualTo(12);
        assertThat(count("staff_room_assignments")).isEqualTo(5);
        assertThat(count("simulated_insurance_cards")).isEqualTo(9);
        assertThat(jdbcTemplate.queryForList("select patient_code from patients order by patient_code", String.class))
                .containsExactly("BN00000001", "BN00000002", "BN00000003", "BN00000004",
                        "BN00000005", "BN00000006", "BN00000007", "BN00000008");
    }

    @Test
    void demoAccountsLogInWithTheConfiguredPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("bs.tranvanan", DEMO_PASSWORD))))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "select last_login_at is not null from staff_accounts where username = 'bs.tranvanan'",
                Boolean.class)).isTrue();
    }

    @Test
    void mapsAllStaffAccountFieldsToTheDatabase() {
        UUID id = UUID.randomUUID();
        UUID actorId = jdbcTemplate.queryForObject(
                "select id from staff_accounts order by username limit 1", UUID.class);
        Instant createdAt = Instant.parse("2026-10-15T01:00:00Z");
        Instant lastLoginAt = createdAt.plusSeconds(60);
        Instant passwordChangedAt = createdAt.plusSeconds(120);
        Instant lockedAt = createdAt.plusSeconds(180);

        StaffAccount account = StaffAccount.builder()
                .id(id)
                .username("mapped_account")
                .passwordHash("{bcrypt}mapped-hash")
                .fullName("Mapped Account")
                .academicTitle("BSCKI")
                .dateOfBirth(LocalDate.of(1990, 5, 20))
                .gender(Gender.FEMALE)
                .phoneNumber("0912345678")
                .email("mapped.account@example.com")
                .address("1 Nguyen Trai")
                .role(Role.DOCTOR)
                .status(AccountStatus.LOCKED)
                .createdByAccountId(actorId)
                .lastLoginAt(lastLoginAt)
                .passwordChangedAt(passwordChangedAt)
                .lockReason("Temporary lock")
                .lockedAt(lockedAt)
                .lockedByAccountId(actorId)
                .createdAt(createdAt)
                .updatedAt(lockedAt)
                .build();

        staffAccounts.save(account);
        StaffAccount restored = staffAccounts.findById(id).orElseThrow();

        assertThat(restored.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 20));
        assertThat(restored.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(restored.getAddress()).isEqualTo("1 Nguyen Trai");
        assertThat(restored.getCreatedByAccountId()).isEqualTo(actorId);
        assertThat(restored.getLastLoginAt()).isEqualTo(lastLoginAt);
        assertThat(restored.getPasswordChangedAt()).isEqualTo(passwordChangedAt);
        assertThat(restored.getLockReason()).isEqualTo("Temporary lock");
        assertThat(restored.getLockedAt()).isEqualTo(lockedAt);
        assertThat(restored.getLockedByAccountId()).isEqualTo(actorId);
    }

    @Test
    void mapsCatalogEntitiesWithTheirEnums() {
        List<Room> rooms = entityManager
                .createQuery("select room from Room room where room.code = 'XN-SH'", Room.class)
                .getResultList();
        assertThat(rooms).singleElement().satisfies(room -> {
            assertThat(room.getType()).isEqualTo(RoomType.PARACLINICAL);
            assertThat(room.getStatus()).isEqualTo(ActivationStatus.ACTIVE);
        });

        MedicalService examination = entityManager.createQuery(
                        "select service from MedicalService service where service.type = :type",
                        MedicalService.class)
                .setParameter("type", MedicalServiceType.EXAMINATION)
                .getSingleResult();
        assertThat(examination.getClinicPrice()).isEqualByComparingTo("150000");
        assertThat(examination.getDepartmentId()).isNull();

        SimulatedInsuranceCard card = entityManager.find(SimulatedInsuranceCard.class, "HT3790000000003");
        assertThat(card.getCoverageRate()).isEqualTo((short) 95);
        assertThat(card.getStatus()).isEqualTo(SimulatedInsuranceCardStatus.VALID);
    }

    @Test
    void storesBusinessAuditArraysAndJson() {
        UUID id = UUID.randomUUID();
        UUID actor = jdbcTemplate.queryForObject(
                "select id from staff_accounts where username = 'lt.nguyenthihong'", UUID.class);
        UUID room = jdbcTemplate.queryForObject("select id from rooms where code = 'PK-NGOAI'", UUID.class);

        transactionTemplate.executeWithoutResult(status -> entityManager.persist(new BusinessAuditEvent(
                id, Instant.now(), actor, BusinessAuditEventType.ROOM_UPDATED, AuditedEntityType.ROOM, room,
                AuditOutcome.SUCCESS, new String[] {"name"},
                "{\"name\": {\"old\": \"A\", \"new\": \"B\"}}", null)));

        BusinessAuditEvent stored = transactionTemplate.execute(
                status -> entityManager.find(BusinessAuditEvent.class, id));
        assertThat(stored.getChangedFields()).containsExactly("name");
        assertThat(stored.getChanges()).contains("\"old\"");
    }

    @Test
    void allowsOnlyOneActiveExaminationService() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into services (id, code, name, type, clinic_price, status, created_at, updated_at, version)
                values (?, 'KB-002', 'Khám bệnh 2', 'EXAMINATION', 100000, 'ACTIVE', now(), now(), 0)
                """, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsOnlyOneActiveRoomPerStaffAccount() {
        UUID doctor = jdbcTemplate.queryForObject(
                "select id from staff_accounts where username = 'bs.tranvanan'", UUID.class);
        UUID otherRoom = jdbcTemplate.queryForObject("select id from rooms where code = 'PK-NGOAI'", UUID.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into staff_room_assignments (id, staff_account_id, room_id, assigned_at, version)
                values (?, ?, ?, now(), 0)
                """, UUID.randomUUID(), doctor, otherRoom))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsPaymentLinesThatDoNotReconcile() {
        UUID payment = insertPendingPayment();
        UUID service = jdbcTemplate.queryForObject("select id from services where code = 'KB-001'", UUID.class);
        String insertLine = """
                insert into payment_items (id, payment_id, service_id, service_code_snapshot, service_name_snapshot,
                    price_type, applied_price, coverage_rate, insurance_amount, patient_amount)
                values (?, ?, ?, 'KB-001', 'Khám bệnh', ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.update(insertLine, UUID.randomUUID(), payment, service, "INSURANCE",
                new BigDecimal("50600"), 95, new BigDecimal("48070"), new BigDecimal("2530"));

        assertThatThrownBy(() -> jdbcTemplate.update(insertLine, UUID.randomUUID(), payment, service, "INSURANCE",
                new BigDecimal("50600"), 95, new BigDecimal("48070"), new BigDecimal("2000")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(insertLine, UUID.randomUUID(), payment, service, "CLINIC",
                new BigDecimal("150000"), null, new BigDecimal("1000"), new BigDecimal("149000")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(insertLine, UUID.randomUUID(), payment, service, "INSURANCE",
                new BigDecimal("50600"), null, new BigDecimal("0"), new BigDecimal("50600")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAPriorityVisitWithoutAReason() {
        UUID patient = jdbcTemplate.queryForObject(
                "select id from patients where patient_code = 'BN00000008'", UUID.class);
        UUID receptionist = jdbcTemplate.queryForObject(
                "select id from staff_accounts where username = 'lt.tranvanquang'", UUID.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into visits (id, registration_code, patient_id, registered_by_account_id, visit_type, priority,
                    reason_for_visit, status, registered_at, created_at, updated_at, version)
                values (?, 'DK-TEST-1', ?, ?, 'SELF_PAID', true, 'Đau đầu', 'WAITING', now(), now(), now(), 0)
                """, UUID.randomUUID(), patient, receptionist))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertPendingPayment() {
        UUID patient = jdbcTemplate.queryForObject(
                "select id from patients where patient_code = 'BN00000003'", UUID.class);
        UUID receptionist = jdbcTemplate.queryForObject(
                "select id from staff_accounts where username = 'lt.nguyenthihong'", UUID.class);
        UUID visit = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into visits (id, registration_code, patient_id, registered_by_account_id, visit_type, priority,
                    reason_for_visit, insurance_card_number, insurance_benefit_code, insurance_expiry_date,
                    insurance_coverage_rate, insurance_verified_at, status, registered_at, created_at, updated_at,
                    version)
                values (?, 'DK-TEST-2', ?, ?, 'INSURANCE', false, 'Khám định kỳ', 'HT3790000000003', 3,
                    date '2026-12-31', 95, now(), 'WAITING_FEE_PAYMENT', now(), now(), now(), 0)
                """, visit, patient, receptionist);
        UUID payment = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into payments (id, invoice_number, visit_id, type, status, total_amount,
                    insurance_total_amount, patient_total_amount, created_at, updated_at, version)
                values (?, 'HD-TEST-1', ?, 'EXAMINATION_FEE', 'PENDING', 50600, 48070, 2530, now(), now(), 0)
                """, payment, visit);
        return payment;
    }

    private int count(String tableAndCondition) {
        return jdbcTemplate.queryForObject("select count(*) from " + tableAndCondition, Integer.class);
    }

    private static String randomSigningKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
