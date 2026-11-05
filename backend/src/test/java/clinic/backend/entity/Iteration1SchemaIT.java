package clinic.backend.entity;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.AuditOutcome;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.Gender;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.PriceType;
import clinic.backend.enums.Role;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitStatus;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.RoomQueueCounterRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Applies all production migrations to a fresh PostgreSQL, loads an isolated test fixture before each test,
 * then checks entity mappings and key database constraints of the Iteration 1 schema.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Sql("/db/iteration1-schema-fixture.sql")
class Iteration1SchemaIT {

    private static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        POSTGRESQL.start();
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("clinic.security.jwt.signing-key", Iteration1SchemaIT::randomSigningKey);
        registry.add("clinic.security.password.bcrypt-strength", () -> "4");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private StaffAccountRepository staffAccounts;

    @Autowired
    private RoomQueueCounterRepository queueCounters;

    @Test
    void allocatesQueueNumberAfterExistingVisitWhenCounterIsMissing() {
        insertVisit("DK-QUEUE-RECOVERY", "BN00000003", "DV", false, null, null, null);
        UUID roomId = jdbcTemplate.queryForObject(
                "select id from rooms where maphongkham = 'PK-NOI-TQ'", UUID.class);
        LocalDate queueDate = jdbcTemplate.queryForObject(
                "select queue_date from visits where sovaovien = 'DK-QUEUE-RECOVERY'", LocalDate.class);
        int currentMaximum = jdbcTemplate.queryForObject(
                "select max(queue_number) from visits where room_id = ? and queue_date = ?",
                Integer.class, roomId, queueDate);
        jdbcTemplate.update(
                "delete from room_queue_counters where room_id = ? and queue_date = ?", roomId, queueDate);

        int allocated = transactionTemplate.execute(
                status -> queueCounters.takeNext(roomId, queueDate));

        assertThat(allocated).isEqualTo(currentMaximum + 1);
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
                .practiceLicenseNo("000123/HCM-CCHN")
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
        assertThat(restored.getPracticeLicenseNo()).isEqualTo("000123/HCM-CCHN");
        assertThat(jdbcTemplate.queryForList("""
                select column_name
                from information_schema.columns
                where table_schema = current_schema()
                  and table_name = 'staff_accounts'
                  and column_name in ('account_expires_at', 'access_schedule')
                """, String.class)).isEmpty();
    }

    @Test
    void mapsCatalogEntitiesWithTheirEnums() {
        List<Room> rooms = entityManager
                .createQuery("select room from Room room where room.roomCode = 'XN-SH'", Room.class)
                .getResultList();
        assertThat(rooms).singleElement().satisfies(room -> {
            assertThat(room.getType()).isEqualTo(RoomType.PARACLINICAL);
            assertThat(room.getStatus()).isEqualTo(ActivationStatus.ACTIVE);
            assertThat(room.getRoomName()).isEqualTo("Phòng Xét nghiệm Sinh hóa");
            assertThat(room.isNew()).isFalse();
        });

        Department department = entityManager
                .createQuery("select department from Department department where department.departmentCode = 'TEST-XN'",
                        Department.class)
                .getSingleResult();
        assertThat(department.getDepartmentName()).isEqualTo("Khoa Xét nghiệm");
        assertThat(department.isActive()).isTrue();

        ServiceGroup group = entityManager
                .createQuery("select serviceGroup from ServiceGroup serviceGroup"
                        + " where serviceGroup.serviceGroupCode = 'MRI'", ServiceGroup.class)
                .getSingleResult();
        assertThat(group.getServiceGroupName()).isEqualTo("Chụp cộng hưởng từ (MRI)");

        MedicalService examination = entityManager.createQuery(
                        "select service from MedicalService service where service.type = :type",
                        MedicalService.class)
                .setParameter("type", MedicalServiceType.EXAMINATION)
                .getSingleResult();
        assertThat(examination.getClinicPrice()).isEqualByComparingTo("150000");
        assertThat(examination.getDepartmentId()).isNull();
        assertThat(examination.getTenchidinh()).isEqualTo("Khám bệnh");

        HealthInsuranceRecord card = entityManager.find(
                HealthInsuranceRecord.class, UUID.fromString("0192a000-0018-7000-8000-000000000003"));
        assertThat(card.getIdentityNumber()).isEqualTo("079058000003");
        assertThat(card.getCoverageRate()).isEqualTo((short) 95);
        assertThat(card.getStatus()).isEqualTo((short) 1);
    }

    @Test
    void storesAuditLogArraysAndJson() {
        UUID id = UUID.randomUUID();
        UUID actor = jdbcTemplate.queryForObject(
                "select id from staff_accounts where username = 'lt.nguyenthihong'", UUID.class);
        UUID room = jdbcTemplate.queryForObject("select id from rooms where maphongkham = 'PK-NGOAI'", UUID.class);
        jdbcTemplate.update("""
                insert into audit_logs (id, occurred_at, actor_account_id, action, entity_type, entity_id, outcome,
                    changed_fields, old_value, new_value)
                values (?, now(), ?, 'UPDATE', 'rooms', ?, 'SUCCESS', ?, ?::jsonb, ?::jsonb)
                """, id, actor, room, new String[] {"phongkham"}, "{\"phongkham\": \"A\"}",
                "{\"phongkham\": \"B\"}");

        AuditLog stored = transactionTemplate.execute(status -> entityManager.find(AuditLog.class, id));
        assertThat(stored.getAction()).isEqualTo(AuditLogAction.UPDATE);
        assertThat(stored.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(stored.getChangedFields()).containsExactly("phongkham");
        assertThat(stored.getOldValue()).contains("\"A\"");
        assertThat(stored.getNewValue()).contains("\"B\"");
    }

    @Test
    void mapsPatientEmail() {
        Patient patient = entityManager.createQuery(
                        "select patient from Patient patient where patient.mabenhnhan = 'BN00000003'",
                        Patient.class)
                .getSingleResult();

        assertThat(patient.getEmail()).isEqualTo("patient@example.com");
    }

    @Test
    void allowsMultipleActiveExaminationServicesAtDatabaseLevel() {
        assertThat(jdbcTemplate.update("""
                insert into services (id, code, tenchidinh, type, clinic_price, status, created_at, updated_at, version)
                values (?, 'KB-002', 'Khám bệnh 2', 'EXAMINATION', 100000, 'ACTIVE', now(), now(), 0)
                """, UUID.randomUUID()))
                .isEqualTo(1);
    }

    @Test
    void keepsDepartmentCodesAndNamesUnique() {
        String insert = """
                insert into departments (id, makhoa, tenkhoa, type, created_at, updated_at, version)
                values (?, ?, ?, 'CLINICAL', now(), now(), 0)
                """;

        assertThatThrownBy(() -> jdbcTemplate.update(insert, UUID.randomUUID(), "TEST-NOI", "Khoa Mới"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(insert, UUID.randomUUID(), "TEST-MOI", "khoa nội"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void keepsPracticeLicenseNumbersUnique() {
        String setLicense = "update staff_accounts set practice_license_no = '000999/HCM-CCHN' where username = ?";
        jdbcTemplate.update(setLicense, "bs.tranvanan");

        assertThatThrownBy(() -> jdbcTemplate.update(setLicense, "bs.lethibinh"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsOnlyOneActiveRoomPerStaffAccount() {
        UUID doctor = jdbcTemplate.queryForObject(
                "select id from staff_accounts where username = 'bs.tranvanan'", UUID.class);
        UUID otherRoom = jdbcTemplate.queryForObject("select id from rooms where maphongkham = 'PK-NGOAI'", UUID.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into staff_room_assignments (id, staff_account_id, room_id, assigned_at, version)
                values (?, ?, ?, now(), 0)
                """, UUID.randomUUID(), doctor, otherRoom))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void mapsVisitsPaymentsAndTheirLines() {
        UUID payment = insertPendingPayment();
        UUID service = jdbcTemplate.queryForObject("select id from services where code = 'KB-001'", UUID.class);
        jdbcTemplate.update("""
                insert into payment_items (id, payment_id, service_id, service_code_snapshot, tenchidinh, price_type,
                    dongia, coverage_rate, thanhtien, bhyt_chitra, benhnhanphaitra, created_at)
                values (?, ?, ?, 'KB-001', 'Khám bệnh', 'INSURANCE', 50600, 95, 50600, 48070, 2530, now())
                """, UUID.randomUUID(), payment, service);

        transactionTemplate.executeWithoutResult(status -> {
            Payment stored = entityManager.find(Payment.class, payment);
            assertThat(stored.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(stored.getItems()).singleElement().satisfies(line -> {
                assertThat(line.getPriceType()).isEqualTo(PriceType.INSURANCE);
                assertThat(line.getBenhnhanphaitra()).isEqualByComparingTo("2530");
                assertThat(line.isCancelled()).isFalse();
            });

            Visit visit = entityManager.find(Visit.class, stored.getVisitId());
            assertThat(visit.getDoituongbnLoai()).isEqualTo(VisitDoituongbnLoai.BHYT);
            assertThat(visit.getStatus()).isEqualTo(VisitStatus.COMPLETED);
            assertThat(visit.getInsuranceCoverageRate()).isEqualTo((short) 95);
        });
    }

    @Test
    void rejectsPaymentLinesThatDoNotReconcile() {
        UUID payment = insertPendingPayment();
        UUID service = jdbcTemplate.queryForObject("select id from services where code = 'KB-001'", UUID.class);
        String insertLine = """
                insert into payment_items (id, payment_id, service_id, service_code_snapshot, tenchidinh, price_type,
                    dongia, coverage_rate, thanhtien, bhyt_chitra, benhnhanphaitra, created_at)
                values (?, ?, ?, 'KB-001', 'Khám bệnh', ?, ?, ?, ?, ?, ?, now())
                """;

        assertThatThrownBy(() -> jdbcTemplate.update(insertLine, UUID.randomUUID(), payment, service, "INSURANCE",
                new BigDecimal("50600"), 95, new BigDecimal("50600"), new BigDecimal("48070"), new BigDecimal("2000")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(insertLine, UUID.randomUUID(), payment, service, "CLINIC",
                new BigDecimal("150000"), null, new BigDecimal("150000"), new BigDecimal("1000"),
                new BigDecimal("149000")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(insertLine, UUID.randomUUID(), payment, service, "INSURANCE",
                new BigDecimal("50600"), null, new BigDecimal("50600"), new BigDecimal("0"), new BigDecimal("50600")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAPriorityVisitWithoutAReason() {
        assertThatThrownBy(() -> insertVisit("DK-TEST-1", "BN00000008", "DV", true, null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsPortalBenefitDataWithoutDerivingItFromTheCardPrefix() {
        assertThat(insertVisit("DK-TEST-3", "BN00000004", "BHYT", false, "DN4790000000004", 3, 80))
                .isNotNull();
    }

    private UUID insertPendingPayment() {
        String code = "DK-" + UUID.randomUUID();
        UUID visit = insertVisit(code, "BN00000003", "BHYT", false, "HT3790000000003", 3, 95);
        UUID payment = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into payments (id, payment_code, visit_id, type, status, total_amount,
                    insurance_total_amount, patient_total_amount, created_at, updated_at, version)
                values (?, ?, ?, 'PARACLINICAL_FEE', 'PENDING', 50600, 48070, 2530, now(), now(), 0)
                """, payment, "HD-" + code.substring(3, 20), visit);
        return payment;
    }

    private UUID insertVisit(String sovaovien, String mabenhnhan, String doituongbnLoai, boolean priority,
            String mabhyt, Integer benefitCode, Integer coverageRate) {
        UUID patient = jdbcTemplate.queryForObject(
                "select id from patients where mabenhnhan = ?", UUID.class, mabenhnhan);
        UUID receptionist = jdbcTemplate.queryForObject(
                "select id from staff_accounts where username = 'lt.nguyenthihong'", UUID.class);
        UUID room = jdbcTemplate.queryForObject("select id from rooms where maphongkham = 'PK-NOI-TQ'", UUID.class);
        int queueNumber = jdbcTemplate.queryForObject(
                "select coalesce(max(queue_number), 0) + 1 from visits", Integer.class);
        UUID visit = UUID.randomUUID();
        boolean insured = "BHYT".equals(doituongbnLoai);
        String socialNumber = insured ? mabhyt.substring(mabhyt.length() - 10) : null;
        LocalDate validFrom = insured ? LocalDate.of(2026, 1, 1) : null;
        LocalDate validTo = insured ? LocalDate.of(2026, 12, 31) : null;
        String facilityCode = insured ? "79014" : null;
        String facilityName = insured ? "Bệnh viện Đa khoa Sài Gòn" : null;
        String verifyStatus = insured ? "VALID" : "NOT_APPLICABLE";
        Timestamp verifiedAt = insured ? Timestamp.from(Instant.now()) : null;
        // Completed, so the one-active-visit-per-patient index (BR-17) does not couple the tests.
        jdbcTemplate.update("""
                insert into visits (id, sovaovien, patient_id, room_id, registered_by_account_id, thoigianvaovien,
                    doituongbn_loai, mabhyt, insurance_benefit_code, insurance_coverage_rate,
                    insurance_social_number, tungaybhyt, denngaybhyt, insurance_primary_facility_code,
                    noidangkykcbbd, insurance_verify_status, insurance_verified_at, lydovaovien,
                    is_priority, queue_date, queue_number, status, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, now(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Khám định kỳ', ?, current_date, ?,
                    'COMPLETED', now(), now(), 0)
                """, visit, sovaovien, patient, room, receptionist, doituongbnLoai, mabhyt, benefitCode,
                coverageRate, socialNumber, validFrom, validTo, facilityCode, facilityName, verifyStatus, verifiedAt,
                priority, queueNumber);
        return visit;
    }

    private static String randomSigningKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
