package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.SavePrescriptionRequest;
import clinic.backend.dto.response.PrescriptionResponse;
import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.Conclusion;
import clinic.backend.entity.FollowUpAppointment;
import clinic.backend.entity.MedicalHistory;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.MedicineCatalog;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Prescription;
import clinic.backend.entity.PrescriptionDetail;
import clinic.backend.entity.PrescriptionSubmission;
import clinic.backend.entity.RecordSignature;
import clinic.backend.entity.SigningRequest;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.entity.VitalSign;
import clinic.backend.enums.FollowUpAppointmentStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalHistoryType;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.MedicineControlGroup;
import clinic.backend.enums.PrescriptionSubmissionResult;
import clinic.backend.enums.RecordSignatureDocumentType;
import clinic.backend.enums.Role;
import clinic.backend.enums.SigningRequestStatus;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.ConclusionRepository;
import clinic.backend.repository.FollowUpAppointmentRepository;
import clinic.backend.repository.LongTermPrescriptionDiseaseCodeRepository;
import clinic.backend.repository.MedicalHistoryRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.MedicineCatalogRepository;
import clinic.backend.repository.PrescriptionRepository;
import clinic.backend.repository.PrescriptionSubmissionRepository;
import clinic.backend.repository.RecordSignatureRepository;
import clinic.backend.repository.SigningRequestRepository;
import clinic.backend.repository.StaffSigningCertificateRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.VitalSignRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.RemoteSigningGateway;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrescriptionServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW = Instant.parse("2026-11-03T03:00:00Z");
    private static final Instant EARLIER = NOW.minusSeconds(3_600);
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID VISIT_ID = UUID.randomUUID();

    @Mock private ConcludingVisitGuard guard;
    @Mock private ConclusionRepository conclusionRepository;
    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private MedicineCatalogRepository medicineRepository;
    @Mock private MedicalHistoryRepository historyRepository;
    @Mock private LongTermPrescriptionDiseaseCodeRepository longTermCodeRepository;
    @Mock private ClinicFacilityRepository clinicFacilityRepository;
    @Mock private StaffAccountRepository staffAccountRepository;
    @Mock private VitalSignRepository vitalSignRepository;
    @Mock private FollowUpAppointmentRepository followUpRepository;
    @Mock private AuditLogService auditLogService;

    @Mock
    private SigningRequestRepository signingRequestRepository;
    @Mock
    private RemoteSigningGateway signingGateway;
    @Mock private PrescriptionSubmissionRepository submissionRepository;
    @Mock private RecordSignatureRepository recordSignatureRepository;
    @Mock private MedicalRecordRepository medicalRecordRepository;
    @Mock private DigitalSignatureVerifier signatureVerifier;
    private PrescriptionServiceImpl service;
    private ConcludingVisitGuard.Context context;
    private ClinicFacility facility;
    private final MedicineCatalog amoxicillin = medicine("AMO500", "Amoxicilin 500mg", "Amoxicilin", MedicineControlGroup.THUONG);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZONE);
        service = new PrescriptionServiceImpl(
                guard, conclusionRepository, prescriptionRepository, medicineRepository, historyRepository,
                longTermCodeRepository, clinicFacilityRepository, staffAccountRepository, vitalSignRepository,
                followUpRepository, auditLogService, mock(StaffSigningCertificateRepository.class),
                signingRequestRepository, submissionRepository, recordSignatureRepository, signingGateway,
                medicalRecordRepository, signatureVerifier, new UuidV7Generator(clock, new SecureRandom()), clock);
        Patient patient = new Patient(
                UUID.randomUUID(), NOW, "BN001", null, null, "Nguyễn Văn An", LocalDate.of(1990, 1, 1),
                (short) 1, null, null, "Kinh", "01", "Việt Nam", "VN", "Hà Nội", null, null,
                "Hà Nội", "001", null, null, null, null, null, null, null, null, null, UUID.randomUUID());
        Visit visit = new Visit(
                VISIT_ID, NOW, "DK0001", patient.getId(), UUID.randomUUID(), UUID.randomUUID(), NOW,
                VisitDoituongbnLoai.DV, null, null, null, "Đau họng", false, null,
                LocalDate.ofInstant(NOW, ZONE), 1, VisitStatus.WAITING_CONCLUSION, VisitInsuranceVerifyStatus.NOT_APPLICABLE);
        MedicalRecord record = new MedicalRecord(
                UUID.randomUUID(), NOW, VISIT_ID, "BA-DK0001", "NGOAITRU", MedicalRecordStatus.OPEN, NOW, DOCTOR_ID);
        StaffAccount doctor = StaffAccount.create(
                DOCTOR_ID, "doctor01", "hash", "Bác sĩ An", null, null, null, null, null, null, Role.DOCTOR, null, NOW);
        doctor.updateMaLienthongBacsi("BS0001", NOW);
        facility = mock(ClinicFacility.class);
        lenient().when(facility.getMaCskcb()).thenReturn("79001");
        lenient().when(facility.getMaLienthongDonthuoc()).thenReturn("LT79001");
        lenient().when(clinicFacilityRepository.findCurrent()).thenReturn(Optional.of(facility));
        context = new ConcludingVisitGuard.Context(visit, patient, record, doctor);
        lenient().when(guard.require(any(), eq(VISIT_ID))).thenReturn(context);
        lenient().when(historyRepository.findAllByPatientIdOrderByCreatedAtAsc(patient.getId())).thenReturn(List.of());
    }

    @Test
    void asksForTheConclusionBeforePrescribing() {
        when(conclusionRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID, request(line(amoxicillin, 7))))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PRESCRIPTION_CONCLUSION_REQUIRED));
    }

    @Test
    void createsTheVisitPrescriptionWithACodeOfTheFacilityAndAuditsIt() {
        stubConclusion();
        stubMedicines(amoxicillin);
        when(prescriptionRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.empty());
        when(prescriptionRepository.saveAndFlush(any(Prescription.class))).thenAnswer(call -> call.getArgument(0));

        var response = service.save(principal(), VISIT_ID, request(line(amoxicillin, 7)));

        assertThat(response.prescriptionCode()).matches("^79001[0-9a-z]{7}-c$");
        assertThat(response.slipNumber()).isEqualTo("DT-DK0001");
        assertThat(response.lines()).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("Amoxicilin 500mg");
            assertThat(item.quantity()).isEqualByComparingTo("21");
            assertThat(item.days()).isEqualTo((short) 7);
        });
        verify(auditLogService).recordEntityChange(
                eq(DOCTOR_ID), any(UUID.class), eq(AuditLogAction.CREATE), eq("prescriptions"),
                anyMap(), anyMap(), eq(NOW));
    }

    @Test
    void allowsMoreThanThirtyDaysOnlyForALongTermDisease() {
        stubConclusion();
        stubMedicines(amoxicillin);
        when(prescriptionRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.empty());
        when(longTermCodeRepository.existsByMaicdIn(any())).thenReturn(false);

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID, request(line(amoxicillin, 60))))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PRESCRIPTION_LONG_TERM_NOT_ALLOWED));
        verify(prescriptionRepository, never()).saveAndFlush(any(Prescription.class));
    }

    @Test
    void savesWithoutCheckingTheRecordedDrugAllergiesButStillShowsThem() {
        stubConclusion();
        stubMedicines(amoxicillin);
        when(prescriptionRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.empty());
        when(prescriptionRepository.saveAndFlush(any(Prescription.class))).thenAnswer(call -> call.getArgument(0));
        when(historyRepository.findAllByPatientIdOrderByCreatedAtAsc(context.patient().getId())).thenReturn(List.of(
                new MedicalHistory(UUID.randomUUID(), NOW, context.patient().getId(), null,
                        MedicalHistoryType.DRUG_ALLERGY, "Amoxicilin", null, DOCTOR_ID)));

        var response = service.save(principal(), VISIT_ID, request(line(amoxicillin, 7)));

        assertThat(response.lines()).singleElement().satisfies(item -> assertThat(item.name()).isEqualTo("Amoxicilin 500mg"));
        assertThat(response.allergies()).containsExactly("Amoxicilin");
    }

    @Test
    @SuppressWarnings("unchecked")
    void auditsOnlyTheFieldsChangedOnALaterSave() {
        stubConclusion();
        stubMedicines(amoxicillin);
        Prescription existing = existingPrescription();
        when(prescriptionRepository.saveAndFlush(any(Prescription.class))).thenAnswer(call -> call.getArgument(0));

        service.save(principal(), VISIT_ID, new SavePrescriptionRequest(
                List.of(line(amoxicillin, 7)), "Tái khám sau 3 ngày", "Uống nhiều nước", existing.getVersion()));

        ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).recordEntityChange(
                eq(DOCTOR_ID), eq(existing.getId()), eq(AuditLogAction.UPDATE), eq("prescriptions"),
                before.capture(), after.capture(), eq(NOW));
        assertThat(before.getValue()).containsOnlyKeys("notes").containsEntry("notes", "Tái khám nếu sốt");
        assertThat(after.getValue()).containsOnlyKeys("notes").containsEntry("notes", "Tái khám sau 3 ngày");
        assertThat(existing.getPrescribedAt()).isEqualTo(EARLIER); // set by the first save only
    }

    @Test
    void refusesALaterSaveWithoutTheLoadedVersion() {
        stubConclusion();
        existingPrescription();

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID, request(line(amoxicillin, 7))))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
    }

    @Test
    void needsThePrescriberLinkCodeOfTheDoctor() {
        stubConclusion();
        context.doctor().updateMaLienthongBacsi(null, NOW);

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID, request(line(amoxicillin, 7))))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PRESCRIBER_LINK_CODE_REQUIRED));
    }

    @Test
    void needsThePrescriptionLinkCodeOfTheFacility() {
        stubConclusion();
        when(facility.getMaLienthongDonthuoc()).thenReturn(null);

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID, request(line(amoxicillin, 7))))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FACILITY_LINK_CODE_REQUIRED));
    }

    @Test
    void refusesAQuantityThatIsNotAWholeNumberOfTheUnit() {
        stubConclusion();
        stubMedicines(amoxicillin);
        when(prescriptionRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.empty());

        for (String quantity : List.of("0.12", "10.5", "0", "10000")) {
            assertThatThrownBy(() -> service.save(principal(), VISIT_ID, request(line(amoxicillin, 7, quantity))))
                    .as(quantity)
                    .isInstanceOfSatisfying(AppException.class, error -> {
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PRESCRIPTION_INVALID);
                        assertThat(error.getDetails()).containsEntry("field", "quantity").containsEntry("reason", "RANGE");
                    });
        }
    }

    @Test
    void acceptsAWholeQuantityWrittenWithZeroDecimals() {
        stubConclusion();
        stubMedicines(amoxicillin);
        when(prescriptionRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.empty());
        when(prescriptionRepository.saveAndFlush(any(Prescription.class))).thenAnswer(call -> call.getArgument(0));

        var saved = service.save(principal(), VISIT_ID, request(line(amoxicillin, 7, "21.00")));

        assertThat(saved.lines().getFirst().quantity()).isEqualByComparingTo("21");
    }

    @Test
    void showsTheChildDataAndTheFollowUpDate() {
        Patient child = mock(Patient.class);
        when(child.getId()).thenReturn(context.patient().getId());
        when(child.getNgaysinh()).thenReturn(LocalDate.of(2025, 5, 3)); // 18 months on 03/11/2026
        when(child.getHotennguoithan()).thenReturn("Trần Thị Mẹ");
        context = new ConcludingVisitGuard.Context(context.visit(), child, context.record(), context.doctor());
        when(guard.require(any(), eq(VISIT_ID))).thenReturn(context);
        when(prescriptionRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.empty());
        when(conclusionRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.empty());
        VitalSign vitals = mock(VitalSign.class);
        when(vitals.getCannang()).thenReturn(new BigDecimal("10.5"));
        when(vitalSignRepository.findFirstByMedicalRecordIdAndCannangIsNotNullOrderByThoidiemDesc(
                context.record().getId())).thenReturn(Optional.of(vitals));
        FollowUpAppointment appointment = mock(FollowUpAppointment.class);
        when(appointment.getStatus()).thenReturn(FollowUpAppointmentStatus.SCHEDULED);
        when(appointment.getFollowUpDate()).thenReturn(LocalDate.of(2026, 11, 10));
        when(followUpRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.of(appointment));

        var response = service.get(principal(), VISIT_ID);

        assertThat(response.child()).isEqualTo(new PrescriptionResponse.Child(18, new BigDecimal("10.5"), "Trần Thị Mẹ"));
        assertThat(response.followUpDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(response.prescriberLinkCodeMissing()).isFalse();
        assertThat(response.facilityLinkCodeMissing()).isFalse();
    }

    @Test
    void hasNoChildDataFromSeventyTwoMonths() {
        when(prescriptionRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.empty());
        when(conclusionRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.empty());

        assertThat(service.get(principal(), VISIT_ID).child()).isNull();
    }

    @Test
    void writesNoAuditRowWhenALaterSaveChangesNothing() {
        stubConclusion();
        stubMedicines(amoxicillin);
        Prescription existing = existingPrescription();
        when(prescriptionRepository.saveAndFlush(any(Prescription.class))).thenAnswer(call -> call.getArgument(0));

        service.save(principal(), VISIT_ID, new SavePrescriptionRequest(
                List.of(line(amoxicillin, 7)), "Tái khám nếu sốt", "Uống nhiều nước", existing.getVersion()));

        verify(auditLogService, never()).recordEntityChange(any(), any(), any(), any(), anyMap(), anyMap(), any());
    }

    @Test
    void doesNotShowTheNoKnownDrugAllergyStatementAsAnAllergy() {
        when(prescriptionRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.empty());
        when(conclusionRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.empty());
        when(historyRepository.findAllByPatientIdOrderByCreatedAtAsc(context.patient().getId())).thenReturn(List.of(
                MedicalHistory.noKnownDrugAllergy(UUID.randomUUID(), NOW, context.patient().getId(), null, DOCTOR_ID)));

        assertThat(service.get(principal(), VISIT_ID).allergies()).isEmpty();
    }

    @Test
    void refusesAMedicineTwiceOnTheSamePrescription() {
        stubConclusion();
        when(prescriptionRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID,
                new SavePrescriptionRequest(List.of(line(amoxicillin, 7), line(amoxicillin, 5)), null, null, null)))
                .isInstanceOfSatisfying(AppException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PRESCRIPTION_INVALID);
                    assertThat(error.getDetails()).containsEntry("field", "medicine").containsEntry("line", "1");
                });
    }

    @Test
    void refusesAControlledMedicine() {
        stubConclusion();
        MedicineCatalog morphine = medicine("MOR10", "Morphin 10mg", "Morphin", MedicineControlGroup.GAY_NGHIEN);
        stubMedicines(morphine);
        when(prescriptionRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID, request(line(morphine, 3))))
                .isInstanceOfSatisfying(AppException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PRESCRIPTION_MEDICINE_UNAVAILABLE);
                    assertThat(error.getDetails()).containsEntry("reason", "NOT_ORDINARY"); // MSG106
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void deletesAPendingPrescriptionAndAuditsTheRemovedValues() {
        Prescription prescription = prescriptionById();

        service.delete(principal(), prescription.getId());

        verify(prescriptionRepository).delete(prescription);
        ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).recordEntityChange(
                eq(DOCTOR_ID), eq(prescription.getId()), eq(AuditLogAction.DELETE), eq("prescriptions"),
                before.capture(), after.capture(), eq(NOW));
        assertThat(before.getValue())
                .containsEntry("prescriptionCode", "79001abc1234-c")
                .containsEntry("notes", "Tái khám nếu sốt")
                .containsKey("lines");
        assertThat(after.getValue()).containsOnlyKeys(before.getValue().keySet()).containsValue(null);
    }

    @Test
    void cancelsThePendingSigningRequestOfADeletedPrescription() {
        Prescription prescription = prescriptionById();
        SigningRequest pending = mock(SigningRequest.class);
        when(pending.getProviderTransactionId()).thenReturn("tx-1");
        when(signingRequestRepository.findByDocumentTypeAndDocumentIdAndStatus(
                RecordSignatureDocumentType.DON_THUOC, prescription.getId(), SigningRequestStatus.PENDING))
                .thenReturn(Optional.of(pending));

        service.delete(principal(), prescription.getId());

        verify(signingGateway).cancel("tx-1");
        verify(pending).markCancelled(NOW);
    }

    @Test
    void refusesToDeleteAPrescriptionThatWasSent() {
        Prescription prescription = prescriptionById();
        prescription.recordSubmission(PrescriptionSubmissionResult.REJECTED, NOW);

        assertThatThrownBy(() -> service.delete(principal(), prescription.getId()))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PRESCRIPTION_ALREADY_SENT));
        verify(prescriptionRepository, never()).delete(any(Prescription.class));
    }

    @Test
    void refusesToDeleteAPendingPrescriptionThatHasASignature() {
        Prescription prescription = prescriptionById();
        when(recordSignatureRepository.existsByDocumentTypeAndDocumentId(
                RecordSignatureDocumentType.DON_THUOC, prescription.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.delete(principal(), prescription.getId()))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PRESCRIPTION_ALREADY_SENT));
        verify(prescriptionRepository, never()).delete(any(Prescription.class));
    }

    @Test
    void listsEverySendNewestFirstWithItsOwnSignatureVerifiedAgain() {
        Prescription prescription = prescriptionById();
        Instant firstSent = NOW.minusSeconds(600);
        PrescriptionSubmission second = new PrescriptionSubmission(UUID.randomUUID(), NOW, prescription.getId(),
                (short) 2, DOCTOR_ID, NOW, PrescriptionSubmissionResult.ACCEPTED, null, NOW);
        PrescriptionSubmission first = new PrescriptionSubmission(UUID.randomUUID(), firstSent, prescription.getId(),
                (short) 1, DOCTOR_ID, firstSent, PrescriptionSubmissionResult.REJECTED, "[\"Sai mã bác sĩ\"]",
                firstSent);
        when(submissionRepository.findAllByPrescriptionIdOrderByAttemptNoDesc(prescription.getId()))
                .thenReturn(List.of(second, first));
        RecordSignature firstSignature = signature(firstSent, "01");
        RecordSignature secondSignature = signature(NOW, "02");
        when(recordSignatureRepository.findAllByDocumentTypeAndDocumentIdOrderByCreatedAtAsc(
                RecordSignatureDocumentType.DON_THUOC, prescription.getId()))
                .thenReturn(List.of(firstSignature, secondSignature));
        when(staffAccountRepository.findAllById(any())).thenReturn(List.of(context.doctor()));
        when(signatureVerifier.reverify(any(), any(), eq("01"), any(), any())).thenReturn("CERTIFICATE_REVOKED");
        when(signatureVerifier.reverify(any(), any(), eq("02"), any(), any())).thenReturn(null);

        var history = service.submissions(principal(), prescription.getId());

        assertThat(history).extracting(PrescriptionResponse.Submission::attemptNo).containsExactly((short) 2, (short) 1);
        assertThat(history.get(0).certificateSerial()).isEqualTo("02");
        assertThat(history.get(0).signatureValid()).isTrue();
        assertThat(history.get(0).submittedBy()).isEqualTo("Bác sĩ An");
        assertThat(history.get(1).certificateSerial()).isEqualTo("01");
        assertThat(history.get(1).signatureValid()).isFalse();
        assertThat(history.get(1).signatureFailureReason()).isEqualTo("CERTIFICATE_REVOKED");
        assertThat(history.get(1).errors()).containsExactly("Sai mã bác sĩ");
    }

    private static RecordSignature signature(Instant createdAt, String serial) {
        RecordSignature signature = mock(RecordSignature.class);
        when(signature.getCreatedAt()).thenReturn(createdAt);
        when(signature.getCertificateSerial()).thenReturn(serial);
        lenient().when(signature.getSignedAt()).thenReturn(createdAt);
        return signature;
    }

    private void stubConclusion() {
        Conclusion conclusion = new Conclusion(UUID.randomUUID(), NOW, context.record().getId());
        conclusion.record(DOCTOR_ID, "Viêm họng cấp", "J02.9", "Viêm họng cấp", "Điều trị", "Đỡ", "Dặn", null,
                null, NOW);
        when(conclusionRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.of(conclusion));
        lenient().when(longTermCodeRepository.existsByMaicdIn(any())).thenReturn(false);
    }

    /** A saved prescription of the visit: amoxicillin for 7 days with the notes and advice of {@link #request}. */
    private Prescription existingPrescription() {
        Prescription prescription = storedPrescription();
        when(prescriptionRepository.findByMedicalRecordId(context.record().getId()))
                .thenReturn(Optional.of(prescription));
        return prescription;
    }

    /** {@link #existingPrescription()} found by its id (UC-59 AF-03, UC-91). */
    private Prescription prescriptionById() {
        Prescription prescription = storedPrescription();
        when(prescriptionRepository.findById(prescription.getId())).thenReturn(Optional.of(prescription));
        when(medicalRecordRepository.findById(context.record().getId())).thenReturn(Optional.of(context.record()));
        return prescription;
    }

    private Prescription storedPrescription() {
        Prescription prescription = new Prescription(
                UUID.randomUUID(), EARLIER, "DT-DK0001", "79001abc1234-c", context.record().getId());
        prescription.record(DOCTOR_ID, "Viêm họng cấp", (short) 2, "Tái khám nếu sốt", "Uống nhiều nước", EARLIER);
        // Quantity 21.00: NUMERIC(10,2) as read back from the database, while a request carries 21.
        prescription.replaceDetails(List.of(new PrescriptionDetail(
                UUID.randomUUID(), NOW, prescription, "DT-DK0001-01", amoxicillin, new BigDecimal("21.00"), "1 viên",
                (short) 3, (short) 7, "Uống sau ăn", null, LocalDate.ofInstant(NOW, ZONE))));
        // Version 2, as loaded from the database.
        org.springframework.test.util.ReflectionTestUtils.setField(prescription, "version", 2L);
        return prescription;
    }

    private void stubMedicines(MedicineCatalog... medicines) {
        when(medicineRepository.findAllById(any())).thenReturn(List.of(medicines));
    }

    private static SavePrescriptionRequest request(SavePrescriptionRequest.Line line) {
        return new SavePrescriptionRequest(List.of(line), "Tái khám nếu sốt", "Uống nhiều nước", null);
    }

    private static SavePrescriptionRequest.Line line(MedicineCatalog medicine, int days) {
        return line(medicine, days, String.valueOf(3L * days));
    }

    private static SavePrescriptionRequest.Line line(MedicineCatalog medicine, int days, String quantity) {
        return new SavePrescriptionRequest.Line(
                medicine.getId(), new BigDecimal(quantity), "1 viên", (short) 3, (short) days, "Uống sau ăn", null);
    }

    private static MedicineCatalog medicine(String code, String name, String ingredient, MedicineControlGroup group) {
        return new MedicineCatalog(UUID.randomUUID(), NOW, new MedicineCatalog.CatalogEntry(
                code, "VD-" + code, null, name, ingredient, "500mg", "Uống", "Viên", group));
    }

    private static StaffPrincipal principal() {
        return new StaffPrincipal(DOCTOR_ID, UUID.randomUUID());
    }
}
