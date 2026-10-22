package clinic.backend.entity;

import java.util.List;
import java.util.UUID;

import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.FollowUpAppointmentStatus;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.MedicalRecordTypePhamVi;
import clinic.backend.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks the tables added from the approved schema (I1-D40): rows written with SQL are read back through
 * the entity mappings, and key constraints reject invalid rows.
 *
 * <p>Each test runs in a transaction that is rolled back, so the shared database stays clean.
 */
@Transactional
class ApprovedSchemaIT extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    // Set by newMedicalRecord().
    private UUID doctor;
    private UUID room;
    private UUID patient;
    private UUID visit;

    @Test
    void mapsMedicalRecordTypesAndAllowsOneDefault() {
        insertMedicalRecordType("15/BV1", true);

        MedicalRecordType type = entityManager.find(MedicalRecordType.class, "15/BV1");
        assertThat(type.getPhamVi()).isEqualTo(MedicalRecordTypePhamVi.OUTPATIENT);
        assertThat(type.isDefault()).isTrue();
        assertThat(type.getVersion()).isZero();

        assertThatThrownBy(() -> insertMedicalRecordType("16/BV1", true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void mapsIcd10Flags() {
        jdbcTemplate.update("""
                insert into icd10 (maicd, tenicd, khong_la_benh_chinh, hieu_luc_tu, is_chronic)
                values ('E11', 'Bệnh đái tháo đường type 2', false, date '2026-01-01', true)
                """);

        Icd10 code = entityManager.find(Icd10.class, "E11");
        assertThat(code.isChronic()).isTrue();
        assertThat(code.isKhongLaBenhChinh()).isFalse();
        assertThat(code.isActive()).isTrue();
        assertThat(code.isNew()).isFalse();
    }

    @Test
    void mapsTheMedicineCatalog() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into medicine_catalog (id, mathuocvattu_bv, tenthuocvattu, donvitinh, status,
                    created_at, updated_at, version)
                values (?, 'T-0001', 'Paracetamol 500mg viên nén', 'Viên', 'ACTIVE', now(), now(), 0)
                """, id);

        MedicineCatalog medicine = entityManager.find(MedicineCatalog.class, id);
        assertThat(medicine.getTenthuocvattu()).isEqualTo("Paracetamol 500mg viên nén");
        assertThat(medicine.getStatus()).isEqualTo(ActivationStatus.ACTIVE);
    }

    @Test
    void allowsOneClinicFacility() {
        UUID id = UUID.randomUUID();
        insertClinicFacility(id, "79001");

        assertThat(entityManager.find(ClinicFacility.class, id).getMaCskcb()).isEqualTo("79001");
        assertThatThrownBy(() -> insertClinicFacility(UUID.randomUUID(), "79002"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void mapsAMedicalRecordWithItsExaminationAndConclusion() {
        UUID record = newMedicalRecord();
        insertIcd10("K29");
        insertIcd10("E11");
        UUID examination = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into clinical_examinations (id, medical_record_id, sophieu, examined_by_account_id,
                    chandoanvaovien, khambenh_chandoanvaovienmaicd, created_at, updated_at, version)
                values (?, ?, ?, ?, 'Viêm dạ dày', 'K29', now(), now(), 0)
                """, examination, record, "PK-" + record, doctor);
        for (String organ : List.of("tieuhoa", "tuanhoan")) {
            jdbcTemplate.update("""
                    insert into examination_findings (id, examination_id, coquan, dauchung, created_at)
                    values (?, ?, ?, 'Bình thường', now())
                    """, UUID.randomUUID(), examination, organ);
        }
        UUID conclusion = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into conclusions (id, medical_record_id, doctor_account_id, chandoan, maicd, tenicd,
                    huongdieutri, ketquadieutri, loidanbacsi, concluded_at, created_at, updated_at, version)
                values (?, ?, ?, 'Viêm dạ dày', 'K29', 'Viêm dạ dày và tá tràng', 'Điều trị ngoại trú', 2,
                    'Tái khám sau 2 tuần', now(), now(), now(), 0)
                """, conclusion, record, doctor);
        insertSecondaryDiagnosis(conclusion, "E11");

        ClinicalExamination storedExamination = entityManager.find(ClinicalExamination.class, examination);
        assertThat(storedExamination.getFindings()).extracting(ExaminationFinding::getCoquan)
                .containsExactlyInAnyOrder("tieuhoa", "tuanhoan");
        Conclusion storedConclusion = entityManager.find(Conclusion.class, conclusion);
        assertThat(storedConclusion.getKetquadieutri()).isEqualTo((short) 2);
        assertThat(storedConclusion.getSecondaryDiagnoses()).singleElement()
                .satisfies(diagnosis -> assertThat(diagnosis.getMaicd()).isEqualTo("E11"));
        assertThat(entityManager.find(MedicalRecord.class, record).getStatus()).isEqualTo(MedicalRecordStatus.OPEN);

        assertThatThrownBy(() -> insertSecondaryDiagnosis(conclusion, "E11"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void requiresAReasonToUnlockAMedicalRecord() {
        UUID record = newMedicalRecord();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update medical_records set unlocked_at = now(), unlocked_by_account_id = ? where id = ?",
                doctor, record))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsOneInitialVitalSignPerRecord() {
        UUID record = newMedicalRecord();
        String insert = """
                insert into vital_signs (id, medical_record_id, is_initial, mach, nhietdo, thoidiem,
                    measured_by_account_id, created_at)
                values (?, ?, true, 80, 36.8, now(), ?, now())
                """;
        UUID vitalSign = UUID.randomUUID();
        jdbcTemplate.update(insert, vitalSign, record, doctor);

        assertThat(entityManager.find(VitalSign.class, vitalSign).getNhietdo()).isEqualByComparingTo("36.8");
        assertThatThrownBy(() -> jdbcTemplate.update(insert, UUID.randomUUID(), record, doctor))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void linksAFollowUpAppointmentToTheVisitThatCarriesItOut() {
        UUID record = newMedicalRecord();
        UUID appointment = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into follow_up_appointments (id, medical_record_id, patient_id, doctor_account_id,
                    follow_up_date, status, created_by_account_id, created_at, updated_at, version)
                values (?, ?, ?, ?, current_date + 14, 'SCHEDULED', ?, now(), now(), 0)
                """, appointment, record, patient, doctor, doctor);

        assertThat(entityManager.find(FollowUpAppointment.class, appointment).getStatus())
                .isEqualTo(FollowUpAppointmentStatus.SCHEDULED);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update visits set followup_appointment_id = ? where id = ?", UUID.randomUUID(), visit))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Creates a doctor, an examination room, a patient, a visit, and the visit's open medical record.
     */
    private UUID newMedicalRecord() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        doctor = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into staff_accounts (id, username, password_hash, full_name, role, status,
                    password_change_required, created_at, updated_at, version)
                values (?, ?, '{bcrypt}not-used', 'Bác sĩ Thử', 'DOCTOR', 'ACTIVE', false, now(), now(), 0)
                """, doctor, "bs." + suffix);
        UUID department = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into departments (id, makhoa, tenkhoa, type, created_at, updated_at, version)
                values (?, ?, ?, 'CLINICAL', now(), now(), 0)
                """, department, "K-" + suffix, "Khoa " + suffix);
        room = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into rooms (id, department_id, maphongkham, phongkham, location, type, status,
                    created_at, updated_at, version)
                values (?, ?, ?, 'Phòng khám thử', 'Tầng 1', 'EXAMINATION', 'ACTIVE', now(), now(), 0)
                """, room, department, "PK-" + suffix);
        patient = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into patients (id, mabenhnhan, hoten, ngaysinh, gioitinh, diachi, created_by_account_id,
                    created_at, updated_at, version)
                values (?, ?, 'Người Bệnh Thử', date '1980-01-01', 1, '1 Đường Số 1', ?, now(), now(), 0)
                """, patient, "BN-" + suffix, doctor);
        visit = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into visits (id, sovaovien, patient_id, room_id, registered_by_account_id, thoigianvaovien,
                    doituongbn_loai, lydovaovien, queue_date, queue_number, status, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, now(), 'DV', 'Đau bụng', current_date, 1, 'IN_EXAMINATION',
                    now(), now(), 0)
                """, visit, "DK-" + suffix, patient, room, doctor);
        insertMedicalRecordType("15/BV1", true);
        UUID record = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into medical_records (id, visit_id, soba, loaiba, status, opened_at, opened_by_account_id,
                    created_at, updated_at, version)
                values (?, ?, ?, '15/BV1', 'OPEN', now(), ?, now(), now(), 0)
                """, record, visit, "BA-" + suffix, doctor);
        return record;
    }

    private void insertIcd10(String code) {
        jdbcTemplate.update("insert into icd10 (maicd, tenicd, hieu_luc_tu) values (?, ?, date '2026-01-01')",
                code, "Bệnh " + code);
    }

    private void insertSecondaryDiagnosis(UUID conclusion, String code) {
        jdbcTemplate.update("""
                insert into conclusion_secondary_diagnoses (id, conclusion_id, maicd, tenicd, thu_tu, created_at)
                values (?, ?, ?, ?, 1, now())
                """, UUID.randomUUID(), conclusion, code, "Bệnh " + code);
    }

    private void insertMedicalRecordType(String loaiba, boolean isDefault) {
        jdbcTemplate.update("""
                insert into medical_record_types (loaiba, ten, pham_vi, van_ban, is_default,
                    created_at, updated_at, version)
                values (?, 'Bệnh án ngoại trú', 'OUTPATIENT', '32/2023/TT-BYT', ?, now(), now(), 0)
                """, loaiba, isDefault);
    }

    private void insertClinicFacility(UUID id, String code) {
        jdbcTemplate.update("""
                insert into clinic_facility (id, ma_cskcb, ten_cskcb, diachi, sodienthoai,
                    so_giay_phep_hoat_dong, hinh_thuc_to_chuc, created_at, updated_at, version)
                values (?, ?, 'Phòng khám thử nghiệm', '1 Đường Số 1', '0281234567', 'GP-001',
                    'PHONG_KHAM_DA_KHOA', now(), now(), 0)
                """, id, code);
    }
}
