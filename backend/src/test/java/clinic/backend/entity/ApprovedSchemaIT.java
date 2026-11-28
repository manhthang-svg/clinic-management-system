package clinic.backend.entity;

import java.util.List;
import java.util.UUID;

import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.ExportFormat;
import clinic.backend.enums.FollowUpAppointmentStatus;
import clinic.backend.enums.ImagingResultStatus;
import clinic.backend.enums.InstrumentResultType;
import clinic.backend.enums.LabResultStatus;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.MedicalRecordTypePhamVi;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.ParaclinicalOrderStatus;
import clinic.backend.enums.MedicineControlGroup;
import clinic.backend.enums.PrescriptionLienthongStatus;
import clinic.backend.enums.PrescriptionSubmissionResult;
import clinic.backend.enums.RecordArchiveStatus;
import clinic.backend.enums.RecordSignatureDocumentType;
import clinic.backend.enums.RecordSignatureSignerType;
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

    // Set by newOrderDetail().
    private UUID medicalRecord;
    private UUID payment;
    private UUID order;

    @Test
    void mapsMedicalRecordTypesAndAllowsOneDefault() {
        newMedicalRecord();
        // Move the default without deleting a type that existing records reference.
        jdbcTemplate.update("""
                update medical_record_types set is_default = false
                where pham_vi = 'OUTPATIENT' and is_default
                """);
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
        UUID id = insertMedicine("T-0001", "893110012345");

        MedicineCatalog medicine = entityManager.find(MedicineCatalog.class, id);
        assertThat(medicine.getTenthuocvattu()).isEqualTo("Thuốc T-0001");
        assertThat(medicine.getHoatchat()).isEqualTo("Paracetamol");
        assertThat(medicine.getNhomKiemSoat()).isEqualTo(MedicineControlGroup.THUONG);
        assertThat(medicine.getStatus()).isEqualTo(ActivationStatus.ACTIVE);
    }

    @Test
    void rejectsADuplicateRegistrationNumber() {
        insertMedicine("T-0001", "893110012345");

        assertThatThrownBy(() -> insertMedicine("T-0002", "893110012345"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsALowerCaseMedicineCode() {
        assertThatThrownBy(() -> insertMedicine("t-0001", "893110012345"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void mapsALongTermPrescriptionDiseaseWithItsCodes() {
        insertIcd10("E11");
        UUID disease = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into long_term_prescription_diseases (id, stt, ma_tt, ten_benh, ma_icd_goc, van_ban,
                    hieu_luc_tu, created_at, updated_at, version)
                values (?, 1, '4.13', 'Đái tháo đường', 'E10-E14', 'TT 26/2025/TT-BYT', date '2025-07-01',
                    now(), now(), 0)
                """, disease);
        UUID code = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into long_term_prescription_disease_codes (id, disease_id, maicd, created_at)
                values (?, ?, 'E11', now())
                """, code, disease);

        assertThat(entityManager.find(LongTermPrescriptionDisease.class, disease).getMaIcdGoc()).isEqualTo("E10-E14");
        assertThat(entityManager.find(LongTermPrescriptionDiseaseCode.class, code).getDiseaseId()).isEqualTo(disease);
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
                values (?, ?, ?, 'Viêm dạ dày', 'K29', 'Viêm dạ dày và tá tràng', 'Điều trị ngoại trú', 'Đỡ đau',
                    'Tái khám sau 2 tuần', now(), now(), now(), 0)
                """, conclusion, record, doctor);
        insertSecondaryDiagnosis(conclusion, "E11");

        ClinicalExamination storedExamination = entityManager.find(ClinicalExamination.class, examination);
        assertThat(storedExamination.getFindings()).extracting(ExaminationFinding::getCoquan)
                .containsExactlyInAnyOrder("tieuhoa", "tuanhoan");
        Conclusion storedConclusion = entityManager.find(Conclusion.class, conclusion);
        // V63: optional free text.
        assertThat(storedConclusion.getKetquadieutri()).isEqualTo("Đỡ đau");
        assertThat(jdbcTemplate.queryForObject("""
                select is_nullable || ':' || data_type from information_schema.columns
                where table_name = 'conclusions' and column_name = 'ketquadieutri'
                """, String.class)).isEqualTo("YES:text");
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

    @Test
    void mapsAnOrderWithItsLabResultAndItems() {
        UUID detail = newOrderDetail();
        UUID labResult = insertLabResult(detail);
        UUID analyzer = insertInstrument("HEMA-01", "LAB");
        for (String flag : List.of("0", "1")) {
            jdbcTemplate.update("""
                    insert into lab_result_items (id, lab_result_id, pkqxn_tenxetnghiem, ketqua, ketquabatthuong,
                        instrument_id, instrument_name_snapshot)
                    values (?, ?, 'Glucose', '5.4', ?, ?, 'Máy HEMA-01')
                    """, UUID.randomUUID(), labResult, flag, analyzer);
        }

        ParaclinicalOrder storedOrder = entityManager.find(ParaclinicalOrder.class, order);
        assertThat(storedOrder.getStatus()).isEqualTo(ParaclinicalOrderStatus.RELEASED);
        assertThat(storedOrder.getDetails()).singleElement().satisfies(line -> {
            assertThat(line.getStatus()).isEqualTo(ParaclinicalOrderDetailStatus.COMPLETED);
            assertThat(line.getOrder()).isSameAs(storedOrder);
        });
        LabResult stored = entityManager.find(LabResult.class, labResult);
        assertThat(stored.getStatus()).isEqualTo(LabResultStatus.SUBMITTED);
        assertThat(stored.getItems()).extracting(LabResultItem::getKetquabatthuong).containsExactlyInAnyOrder("0", "1");
        assertThat(stored.getItems()).extracting(LabResultItem::getInstrumentId).containsOnly(analyzer);
        ParaclinicalInstrument instrument = entityManager.find(ParaclinicalInstrument.class, analyzer);
        assertThat(instrument.getResultType()).isEqualTo(InstrumentResultType.LAB);
        assertThat(instrument.isActive()).isTrue();
    }

    @Test
    void attachesImagesToAnImagingResultInOrder() {
        UUID imagingResult = insertImagingResult(newOrderDetail());
        UUID ultrasound = insertInstrument("SA-01", "IMAGING");
        jdbcTemplate.update("update imaging_results set instrument_id = ?, instrument_name_snapshot = 'Máy SA-01' "
                + "where id = ?", ultrasound, imagingResult);
        UUID image = insertImage(imagingResult, "image/jpeg", 1);
        insertImage(imagingResult, "image/png", 2);

        ImagingResult stored = entityManager.find(ImagingResult.class, imagingResult);
        assertThat(stored.getStatus()).isEqualTo(ImagingResultStatus.DRAFT);
        assertThat(stored.getInstrumentId()).isEqualTo(ultrasound);
        ImagingResultImage storedImage = entityManager.find(ImagingResultImage.class, image);
        assertThat(storedImage.getImagingResultId()).isEqualTo(imagingResult);
        assertThat(storedImage.getFileSha256()).hasSize(64);
        assertThatThrownBy(() -> insertImage(imagingResult, "image/jpeg", 2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsOnlyImages() {
        UUID imagingResult = insertImagingResult(newOrderDetail());

        assertThatThrownBy(() -> insertImage(imagingResult, "application/pdf", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void requiresAnImagingResultForEveryImage() {
        newOrderDetail();

        assertThatThrownBy(() -> insertImage(null, "image/jpeg", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAnImagingInstrumentOnALabValue() {
        UUID labResult = insertLabResult(newOrderDetail());
        UUID ultrasound = insertInstrument("SA-02", "IMAGING");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into lab_result_items (id, lab_result_id, pkqxn_tenxetnghiem, ketqua, instrument_id)
                values (?, ?, 'Glucose', '5.4', ?)
                """, UUID.randomUUID(), labResult, ultrasound))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsALabInstrumentOnAnImagingResult() {
        UUID imagingResult = insertImagingResult(newOrderDetail());
        UUID analyzer = insertInstrument("HEMA-02", "LAB");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update imaging_results set instrument_id = ? where id = ?", analyzer, imagingResult))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsADuplicateInstrumentCode() {
        newOrderDetail();
        insertInstrument("XQ-01", "IMAGING");

        assertThatThrownBy(() -> insertInstrument("XQ-01", "LAB"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void requiresTheSubmissionTimeOfASubmittedResult() {
        UUID detail = newOrderDetail();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into imaging_results (id, order_detail_id, performer_account_id, sophieu, kqcls_mota,
                    kqcls_ketluan, status, created_at, updated_at, version)
                values (?, ?, ?, 'CDHA-X', 'Mô tả', 'Kết luận', 'SUBMITTED', now(), now(), 0)
                """, UUID.randomUUID(), detail, doctor))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void paysEachOrderWithItsOwnPayment() {
        newOrderDetail();

        assertThatThrownBy(() -> insertOrder(UUID.randomUUID(), medicalRecord, "PCD-2"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void mapsAPrescriptionWithItsLinesDiagnosesAndSubmissions() {
        UUID prescription = insertPrescription(newMedicalRecord(), "79001abc1234-c");
        UUID medicine = insertMedicine("T-0001", "893110012345");
        insertPrescriptionLine(prescription, medicine, 5);
        insertIcd10("E11");
        jdbcTemplate.update("""
                insert into prescription_diagnoses (id, prescription_id, maicd, tenicd, thu_tu, created_at)
                values (?, ?, 'E11', 'Bệnh E11', 1, now())
                """, UUID.randomUUID(), prescription);
        UUID submission = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into prescription_submissions (id, prescription_id, attempt_no, submitted_by_account_id,
                    submitted_at, result, errors, responded_at, created_at)
                values (?, ?, 1, ?, now(), 'REJECTED', '[{"field":"ma_thuoc"}]'::jsonb, now(), now())
                """, submission, prescription, doctor);

        Prescription stored = entityManager.find(Prescription.class, prescription);
        assertThat(stored.getLienthongStatus()).isEqualTo(PrescriptionLienthongStatus.PENDING);
        assertThat(stored.getLoaidon()).isEqualTo("c");
        assertThat(stored.getDetails()).singleElement().satisfies(line -> {
            assertThat(line.getSoLanNgay()).isEqualTo((short) 2);
            assertThat(line.getSoNgayDung()).isEqualTo((short) 5);
            assertThat(line.getNhomKiemSoat()).isEqualTo(MedicineControlGroup.THUONG);
        });
        assertThat(stored.getDiagnoses()).singleElement()
                .satisfies(diagnosis -> assertThat(diagnosis.getMaicd()).isEqualTo("E11"));
        PrescriptionSubmission storedSubmission = entityManager.find(PrescriptionSubmission.class, submission);
        assertThat(storedSubmission.getResult()).isEqualTo(PrescriptionSubmissionResult.REJECTED);
        assertThat(storedSubmission.getErrors()).contains("ma_thuoc");
    }

    @Test
    void rejectsAnUpperCasePrescriptionCode() {
        UUID record = newMedicalRecord();

        assertThatThrownBy(() -> insertPrescription(record, "79001ABC1234-C"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsANarcoticPrescriptionCode() {
        UUID record = newMedicalRecord();

        assertThatThrownBy(() -> insertPrescription(record, "79001abc1234-n"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsOnePrescriptionPerMedicalRecord() {
        UUID record = newMedicalRecord();
        insertPrescription(record, "79001abc1234-c");

        assertThatThrownBy(() -> insertPrescription(record, "79001xyz9876-c"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAMedicineRepeatedOnAPrescription() {
        UUID prescription = insertPrescription(newMedicalRecord(), "79001abc1234-c");
        UUID medicine = insertMedicine("T-0001", "893110012345");
        insertPrescriptionLine(prescription, medicine, 90);

        assertThatThrownBy(() -> insertPrescriptionLine(prescription, medicine, 5))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsMoreThanNinetyDays() {
        UUID prescription = insertPrescription(newMedicalRecord(), "79001abc1234-c");
        UUID medicine = insertMedicine("T-0001", "893110012345");

        assertThatThrownBy(() -> insertPrescriptionLine(prescription, medicine, 91))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void requiresTheErrorListOfARejectedSubmissionOnly() {
        UUID prescription = insertPrescription(newMedicalRecord(), "79001abc1234-c");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into prescription_submissions (id, prescription_id, attempt_no, submitted_by_account_id,
                    submitted_at, result, responded_at, created_at)
                values (?, ?, 1, ?, now(), 'REJECTED', now(), now())
                """, UUID.randomUUID(), prescription, doctor))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void mapsASignedExportedAndArchivedRecord() {
        UUID record = newMedicalRecord();
        UUID exportLog = insertExportLog(record, "SUCCESS", null);
        UUID archive = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into record_archives (id, medical_record_id, soluutru, export_log_id, package_uri,
                    package_sha256, expires_on, status, archived_at)
                values (?, ?, ?, ?, 'archives/ba.zip', repeat('b', 64), current_date + 3650, 'ARCHIVED', now())
                """, archive, record, "LT-" + record, exportLog);
        UUID signature = insertSignature(record, "STAFF", doctor, null);

        assertThat(entityManager.find(ExportLog.class, exportLog).getExportFormat()).isEqualTo(ExportFormat.XML);
        RecordArchive storedArchive = entityManager.find(RecordArchive.class, archive);
        assertThat(storedArchive.getRetentionYears()).isEqualTo((short) 10);
        assertThat(storedArchive.getStatus()).isEqualTo(RecordArchiveStatus.ARCHIVED);
        RecordSignature storedSignature = entityManager.find(RecordSignature.class, signature);
        assertThat(storedSignature.getSignerType()).isEqualTo(RecordSignatureSignerType.STAFF);
        assertThat(storedSignature.getDocumentType()).isEqualTo(RecordSignatureDocumentType.KET_LUAN);
    }

    @Test
    void requiresTheIdNumberOfASigningPatient() {
        UUID record = newMedicalRecord();

        assertThatThrownBy(() -> insertSignature(record, "PATIENT", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void requiresTheReasonOfAFailedExport() {
        UUID record = newMedicalRecord();

        assertThatThrownBy(() -> insertExportLog(record, "FAILED", null))
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
        UUID record = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into medical_records (id, visit_id, soba, loaiba, status, opened_at, opened_by_account_id,
                    created_at, updated_at, version)
                values (?, ?, ?, 'NGOAITRU', 'OPEN', now(), ?, now(), now(), 0)
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

    /**
     * Creates a medical record, a paraclinical payment, a lab service, a released order paid by that
     * payment, and one completed ordered service with its payment line.
     */
    private UUID newOrderDetail() {
        medicalRecord = newMedicalRecord();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        payment = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into payments (id, payment_code, visit_id, type, status, total_amount,
                    insurance_total_amount, patient_total_amount, paid_at, created_at, updated_at, version)
                values (?, ?, ?, 'PARACLINICAL_FEE', 'PAID', 40000, 0, 40000, now(), now(), now(), 0)
                """, payment, "HD-" + suffix, visit);
        UUID department = jdbcTemplate.queryForObject(
                "select department_id from rooms where id = ?", UUID.class, room);
        UUID group = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into service_groups (id, loaimachidinh, loaichidinh, type, created_at, updated_at, version)
                values (?, ?, 'Sinh hóa', 'LAB', now(), now(), 0)
                """, group, "SH-" + suffix);
        UUID service = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into services (id, code, tenchidinh, type, department_id, service_group_id, clinic_price,
                    status, created_at, updated_at, version)
                values (?, ?, 'Định lượng Glucose máu', 'LAB', ?, ?, 40000, 'ACTIVE', now(), now(), 0)
                """, service, "XN-" + suffix, department, group);
        order = UUID.randomUUID();
        insertOrder(order, medicalRecord, "PCD-" + suffix);
        UUID detail = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into paraclinical_order_details (id, order_id, service_id, machidinh, ngaychidinh,
                    tenchidinh, dongia, thanhtien, benhnhanphaitra, status, created_at, updated_at, version)
                values (?, ?, ?, ?, now(), 'Định lượng Glucose máu', 40000, 40000, 40000, 'COMPLETED',
                    now(), now(), 0)
                """, detail, order, service, "CD-" + suffix);
        jdbcTemplate.update("""
                insert into payment_items (id, payment_id, service_id, order_detail_id, service_code_snapshot,
                    tenchidinh, price_type, dongia, thanhtien, benhnhanphaitra, created_at)
                values (?, ?, ?, ?, ?, 'Định lượng Glucose máu', 'CLINIC', 40000, 40000, 40000, now())
                """, UUID.randomUUID(), payment, service, detail, "XN-" + suffix);
        return detail;
    }

    private void insertOrder(UUID id, UUID record, String sophieu) {
        jdbcTemplate.update("""
                insert into paraclinical_orders (id, sophieu, medical_record_id, doctor_account_id, payment_id,
                    doituongbn, noichidinh, tongcong, status, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, 2, 'Phòng khám thử', 40000, 'RELEASED', now(), now(), 0)
                """, id, sophieu, record, doctor, payment);
    }

    private UUID insertLabResult(UUID detail) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into lab_results (id, order_detail_id, performer_account_id, sophieu, status, submitted_at,
                    created_at, updated_at, version)
                values (?, ?, ?, ?, 'SUBMITTED', now(), now(), now(), 0)
                """, id, detail, doctor, "KQ-" + detail);
        return id;
    }

    private UUID insertImagingResult(UUID detail) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into imaging_results (id, order_detail_id, performer_account_id, sophieu, kqcls_mota,
                    kqcls_ketluan, status, created_at, updated_at, version)
                values (?, ?, ?, ?, 'Gan bình thường', 'Bình thường', 'DRAFT', now(), now(), 0)
                """, id, detail, doctor, "CDHA-" + detail);
        return id;
    }

    private UUID insertImage(UUID imagingResult, String mimeType, int displayOrder) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into imaging_result_images (id, imaging_result_id, hinhanh, file_name, mime_type,
                    file_sha256, uploaded_by_account_id, uploaded_at, display_order)
                values (?, ?, 'results/anh.jpg', 'anh.jpg', ?, repeat('a', 64), ?, now(), ?)
                """, id, imagingResult, mimeType, doctor, displayOrder);
        return id;
    }

    private UUID insertInstrument(String code, String resultType) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into paraclinical_instruments (id, code, name, result_type, room_id, created_at, updated_at,
                    version)
                values (?, ?, ?, ?, ?, now(), now(), 0)
                """, id, code, "Máy " + code, resultType, room);
        return id;
    }

    private UUID insertPrescription(UUID record, String madonthuoc) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into prescriptions (id, sophieu, madonthuoc, medical_record_id, doctor_account_id,
                    doituongbn, prescribed_at, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, 2, now(), now(), now(), 0)
                """, id, madonthuoc, madonthuoc, record, doctor);
        return id;
    }

    private UUID insertMedicine(String code, String registrationNumber) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into medicine_catalog (id, mathuocvattu_bv, so_dang_ky, tenthuocvattu, hoatchat,
                    hamluong, duongdung, donvitinh, nhom_kiem_soat, status, created_at, updated_at, version)
                values (?, ?, ?, ?, 'Paracetamol', '500mg', 'Uống', 'Viên', 'THUONG', 'ACTIVE', now(), now(), 0)
                """, id, code, registrationNumber, "Thuốc " + code);
        return id;
    }

    private void insertPrescriptionLine(UUID prescription, UUID medicine, int days) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into prescription_details (id, prescription_id, medicine_id, machidinh, tenthuocvattu,
                    so_dang_ky, hoatchat, hamluong, duongdung, nhom_kiem_soat, donvitinh, soluongthuocvattu,
                    cachdung, lieu_moi_lan, so_lan_ngay, so_ngay_dung, ngaykedonthuoc, created_at)
                values (?, ?, ?, ?, 'Paracetamol 500mg', '893110012345', 'Paracetamol', '500mg', 'Uống', 'THUONG',
                    'Viên', 10, 'Uống sau ăn', '1 viên', 2, ?, current_date, now())
                """, id, prescription, medicine, "YL-" + id, days);
    }

    private UUID insertExportLog(UUID record, String status, String errorMessage) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into export_logs (id, medical_record_id, sovaovien, export_format, purpose, status,
                    error_message, exported_at)
                values (?, ?, 'DK-0000000001', 'XML', 'LUU_TRU', ?, ?, now())
                """, id, record, status, errorMessage);
        return id;
    }

    private UUID insertSignature(UUID record, String signerType, UUID signerAccount, String signerIdNumber) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into record_signatures (id, medical_record_id, document_type, document_id, document_version,
                    document_sha256, signer_type, signer_account_id, signer_name, signer_id_number,
                    signature_method, signed_at, created_at)
                values (?, ?, 'KET_LUAN', ?, 0, repeat('c', 64), ?, ?, 'Người Ký', ?, 'ELECTRONIC_CONFIRMATION',
                    now(), now())
                """, id, record, UUID.randomUUID(), signerType, signerAccount, signerIdNumber);
        return id;
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
