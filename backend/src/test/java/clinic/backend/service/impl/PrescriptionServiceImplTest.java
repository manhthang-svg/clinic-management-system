package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.SavePrescriptionRequest;
import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.Conclusion;
import clinic.backend.entity.MedicalHistory;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.MedicineCatalog;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Prescription;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalHistoryType;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.MedicineControlGroup;
import clinic.backend.enums.Role;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.ConclusionRepository;
import clinic.backend.repository.LongTermPrescriptionDiseaseCodeRepository;
import clinic.backend.repository.MedicalHistoryRepository;
import clinic.backend.repository.MedicineCatalogRepository;
import clinic.backend.repository.PrescriptionRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    @Mock private AuditLogService auditLogService;

    private PrescriptionServiceImpl service;
    private ConcludingVisitGuard.Context context;
    private final MedicineCatalog amoxicillin = medicine("AMO500", "Amoxicilin 500mg", "Amoxicilin", MedicineControlGroup.THUONG);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZONE);
        service = new PrescriptionServiceImpl(
                guard, conclusionRepository, prescriptionRepository, medicineRepository, historyRepository,
                longTermCodeRepository, clinicFacilityRepository, staffAccountRepository, auditLogService,
                new UuidV7Generator(clock, new SecureRandom()), clock);
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
        context = new ConcludingVisitGuard.Context(visit, patient, record, doctor);
        when(guard.require(any(), eq(VISIT_ID))).thenReturn(context);
        lenient().when(historyRepository.findAllByPatientIdOrderByCreatedAtAsc(patient.getId())).thenReturn(List.of());
    }

    @Test
    void asksForTheConclusionBeforePrescribing() {
        when(conclusionRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID, request(line(amoxicillin, 7), false)))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PRESCRIPTION_CONCLUSION_REQUIRED));
    }

    @Test
    void createsTheVisitPrescriptionWithACodeOfTheFacilityAndAuditsIt() {
        stubConclusion();
        stubMedicines(amoxicillin);
        ClinicFacility facility = mock(ClinicFacility.class);
        when(facility.getMaCskcb()).thenReturn("79001");
        when(clinicFacilityRepository.findCurrent()).thenReturn(Optional.of(facility));
        when(prescriptionRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.empty());
        when(prescriptionRepository.saveAndFlush(any(Prescription.class))).thenAnswer(call -> call.getArgument(0));

        var response = service.save(principal(), VISIT_ID, request(line(amoxicillin, 7), false));

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

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID, request(line(amoxicillin, 60), false)))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PRESCRIPTION_LONG_TERM_NOT_ALLOWED));
        verify(prescriptionRepository, never()).saveAndFlush(any(Prescription.class));
    }

    @Test
    void asksBeforePrescribingAMedicineTheyAreAllergicTo() {
        stubConclusion();
        stubMedicines(amoxicillin);
        when(prescriptionRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.empty());
        when(historyRepository.findAllByPatientIdOrderByCreatedAtAsc(context.patient().getId())).thenReturn(List.of(
                new MedicalHistory(UUID.randomUUID(), NOW, context.patient().getId(), null,
                        MedicalHistoryType.DRUG_ALLERGY, "Amoxicilin", null, DOCTOR_ID)));

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID, request(line(amoxicillin, 7), false)))
                .isInstanceOfSatisfying(AppException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PRESCRIPTION_ALLERGY_WARNING);
                    assertThat(error.getDetails()).containsEntry("allergies", "Amoxicilin (Amoxicilin 500mg)");
                });
    }

    @Test
    void refusesAMedicineTwiceOnTheSamePrescription() {
        stubConclusion();
        when(prescriptionRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID,
                new SavePrescriptionRequest(List.of(line(amoxicillin, 7), line(amoxicillin, 5)), null, null, false, null)))
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

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID, request(line(morphine, 3), false)))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PRESCRIPTION_MEDICINE_UNAVAILABLE));
    }

    private void stubConclusion() {
        Conclusion conclusion = new Conclusion(UUID.randomUUID(), NOW, context.record().getId());
        conclusion.record(DOCTOR_ID, "Viêm họng cấp", "J02.9", "Viêm họng cấp", "Điều trị", (short) 2, "Dặn", null,
                null, NOW);
        when(conclusionRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.of(conclusion));
        lenient().when(longTermCodeRepository.existsByMaicdIn(any())).thenReturn(false);
    }

    private void stubMedicines(MedicineCatalog... medicines) {
        when(medicineRepository.findAllById(any())).thenReturn(List.of(medicines));
    }

    private static SavePrescriptionRequest request(SavePrescriptionRequest.Line line, boolean confirmAllergy) {
        return new SavePrescriptionRequest(List.of(line), "Tái khám nếu sốt", "Uống nhiều nước", confirmAllergy, null);
    }

    private static SavePrescriptionRequest.Line line(MedicineCatalog medicine, int days) {
        return new SavePrescriptionRequest.Line(
                medicine.getId(), BigDecimal.valueOf(3L * days), "1 viên", (short) 3, (short) days, "Uống sau ăn", null);
    }

    private static MedicineCatalog medicine(String code, String name, String ingredient, MedicineControlGroup group) {
        return new MedicineCatalog(UUID.randomUUID(), NOW, new MedicineCatalog.CatalogEntry(
                code, "VD-" + code, null, name, ingredient, "500mg", "Uống", "Viên", group));
    }

    private static StaffPrincipal principal() {
        return new StaffPrincipal(DOCTOR_ID, UUID.randomUUID());
    }
}
