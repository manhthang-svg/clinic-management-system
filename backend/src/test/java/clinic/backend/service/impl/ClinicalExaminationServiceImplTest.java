package clinic.backend.service.impl;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.MedicalHistoryItemRequest;
import clinic.backend.dto.request.SaveClinicalExaminationRequest;
import clinic.backend.dto.request.VitalSignRequest;
import clinic.backend.entity.ClinicalExamination;
import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.MedicalHistory;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.entity.Visit;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ClinicalExaminationNextStep;
import clinic.backend.enums.ClinicalExaminationSaveMode;
import clinic.backend.enums.DutyShift;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalHistoryType;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.Role;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.mapper.ClinicalExaminationMapper;
import clinic.backend.repository.ClinicalExaminationRepository;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.entity.Icd10;
import clinic.backend.repository.Icd10Repository;
import clinic.backend.repository.MedicalHistoryRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.repository.VitalSignRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicalExaminationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-03T03:00:00Z");
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final UUID VISIT_ID = UUID.randomUUID();

    @Mock private VisitRepository visitRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private StaffAccountRepository staffAccountRepository;
    @Mock private StaffRoomAssignmentRepository assignmentRepository;
    @Mock private DutyScheduleRepository dutyScheduleRepository;
    @Mock private MedicalRecordRepository medicalRecordRepository;
    @Mock private ClinicalExaminationRepository examinationRepository;
    @Mock private VitalSignRepository vitalSignRepository;
    @Mock private MedicalHistoryRepository historyRepository;
    @Mock private Icd10Repository icd10Repository;
    @Mock private AuditLogService auditLogService;

    private ClinicalExaminationServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh"));
        ClinicalExaminationMapper mapper = Mappers.getMapper(ClinicalExaminationMapper.class);
        service = new ClinicalExaminationServiceImpl(
                visitRepository, patientRepository, roomRepository, staffAccountRepository, assignmentRepository,
                dutyScheduleRepository, medicalRecordRepository, examinationRepository, vitalSignRepository,
                historyRepository, icd10Repository,
                new Icd10SearchCache(icd10Repository, clock, Duration.ofHours(1), false), auditLogService, mapper,
                new UuidV7Generator(clock, new SecureRandom()), clock);
    }

    @Test
    void rejectsDraftWithoutAnyVitalSign() {
        stubEditableExamination();
        SaveClinicalExaminationRequest request = new SaveClinicalExaminationRequest(
                ClinicalExaminationSaveMode.DRAFT, null, "Đau đầu", null, null,
                new VitalSignRequest(null, null, null, null, null, null, null, null, NOW),
                List.of(), null, null, null, null);

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID, request))
                .isInstanceOfSatisfying(AppException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.VITAL_SIGNS_REQUIRED));
    }

    @Test
    void requiresPreliminaryDiagnosisBeforeContinuing() {
        stubEditableExamination();
        SaveClinicalExaminationRequest request = new SaveClinicalExaminationRequest(
                ClinicalExaminationSaveMode.CONTINUE, ClinicalExaminationNextStep.PARACLINICAL,
                "Đau đầu", null, null,
                new VitalSignRequest((short) 72, null, null, null, null, null, null, null, NOW),
                List.of(), " ", null, null, null);

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID, request))
                .isInstanceOfSatisfying(AppException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.PRELIMINARY_DIAGNOSIS_REQUIRED));
    }

    @Test
    void rejectsACategoryThatHasMoreDetailedCodes() {
        stubEditableExamination();
        when(icd10Repository.findById("A00")).thenReturn(Optional.of(icd10("A00", null, true, null)));

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID, draftWithIcd10("a00")))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.ICD10_NOT_DETAILED));
    }

    @Test
    void rejectsAFemaleOnlyCodeForAMalePatient() {
        stubEditableExamination();
        when(icd10Repository.findById("A34")).thenReturn(Optional.of(icd10("A34", null, false, (short) 2)));

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID, draftWithIcd10("A34")))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.ICD10_SEX_MISMATCH));
    }

    @Test
    void rejectsACodeNotYetInEffectOnTheVisitDay() {
        stubEditableExamination();
        when(icd10Repository.findById("U07.1")).thenReturn(Optional.of(
                icd10("U07.1", LocalDate.of(2026, 12, 1), false, null)));

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID, draftWithIcd10("U07.1")))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.ICD10_INVALID));
    }

    @Test
    void searchesOnlyCodesUsableOnTheClinicDayWithoutAccents() {
        when(icd10Repository.findSearchable()).thenReturn(List.of(
                Icd10SearchCacheTest.code("J00", "Viêm mũi họng cấp", LocalDate.of(2026, 7, 1), null),
                Icd10SearchCacheTest.code("J01.0", "Viêm xoang hàm cấp", LocalDate.of(2026, 12, 1), null)));

        var result = service.searchActiveIcd10("viem", 10);

        assertThat(result).singleElement().satisfies(option -> {
            assertThat(option.code()).isEqualTo("J00");
            assertThat(option.name()).isEqualTo("Viêm mũi họng cấp");
        });
    }

    @Test
    void doesNotSearchWithFewerThanTwoCharacters() {
        assertThat(service.searchActiveIcd10(" j ", 10)).isEmpty();
        verify(icd10Repository, never()).findSearchable();
    }

    private static SaveClinicalExaminationRequest draftWithIcd10(String code) {
        return new SaveClinicalExaminationRequest(
                ClinicalExaminationSaveMode.DRAFT, null, "Đau đầu", null, null,
                new VitalSignRequest((short) 72, null, null, null, null, null, null, null, NOW),
                List.of(), "Theo dõi", code, null, null);
    }

    private static Icd10 icd10(String code, LocalDate effectiveFrom, boolean moreDetailed, Short sex) {
        return Icd10.fromCatalog(
                new Icd10.CatalogEntry(code, "Bệnh " + code, null, false, false, moreDetailed, false, sex, null, null),
                effectiveFrom == null ? LocalDate.of(2026, 7, 1) : effectiveFrom, "TT 06/2026/TT-BYT");
    }

    @Test
    void addsDrugAllergyDuringExaminationAndAuditsIt() {
        Patient patient = stubEditableExamination();
        when(historyRepository.saveAndFlush(any(MedicalHistory.class))).thenAnswer(call -> call.getArgument(0));
        MedicalHistory saved = new MedicalHistory(
                UUID.randomUUID(), NOW, patient.getId(), null, MedicalHistoryType.DRUG_ALLERGY,
                "Penicillin", "Nổi mẩn", DOCTOR_ID);
        when(historyRepository.findAllByPatientIdOrderByCreatedAtAsc(patient.getId())).thenReturn(List.of(saved));

        var result = service.addMedicalHistory(principal(), VISIT_ID,
                new MedicalHistoryItemRequest(MedicalHistoryType.DRUG_ALLERGY, " Penicillin ", "Nổi mẩn", null));

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.type()).isEqualTo(MedicalHistoryType.DRUG_ALLERGY);
            assertThat(item.content()).isEqualTo("Penicillin");
        });
        verify(auditLogService).recordEntityChange(
                eq(DOCTOR_ID), any(UUID.class), eq(AuditLogAction.CREATE), eq("medical_histories"),
                anyMap(), anyMap(), eq(NOW));
    }

    @Test
    void rejectsUpdatingHistoryOfAnotherPatient() {
        stubEditableExamination();
        UUID historyId = UUID.randomUUID();
        MedicalHistory other = new MedicalHistory(
                historyId, NOW, UUID.randomUUID(), null, MedicalHistoryType.PERSONAL, "Tăng huyết áp", null, DOCTOR_ID);
        when(historyRepository.findById(historyId)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.updateMedicalHistory(principal(), VISIT_ID, historyId,
                new MedicalHistoryItemRequest(MedicalHistoryType.PERSONAL, "Đái tháo đường", null, null)))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        verify(historyRepository, never()).saveAndFlush(any(MedicalHistory.class));
    }

    private Patient stubEditableExamination() {
        StaffAccount doctor = StaffAccount.create(
                DOCTOR_ID, "doctor01", "hash", "Bác sĩ An", null, null, null, null, null, null,
                Role.DOCTOR, null, NOW);
        Room room = new Room(ROOM_ID, NOW, UUID.randomUUID(), null, "P101", "Phòng khám 101", "Tầng 1",
                RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        Patient patient = new Patient(
                UUID.randomUUID(), NOW, "BN001", null, null, "Nguyễn Văn An", LocalDate.of(1990, 1, 1),
                (short) 1, null, null, "Kinh", "01", "Việt Nam", "VN", "Hà Nội", null, null,
                "Hà Nội", "001", null, null, null, null, null, null, null, null, null, UUID.randomUUID());
        Visit visit = new Visit(
                VISIT_ID, NOW, "DK0001", patient.getId(), ROOM_ID, UUID.randomUUID(), NOW,
                VisitDoituongbnLoai.DV, null, null, null, "Đau đầu", false, null,
                LocalDate.ofInstant(NOW, ZoneId.of("Asia/Ho_Chi_Minh")), 1, VisitStatus.WAITING,
                VisitInsuranceVerifyStatus.NOT_APPLICABLE);
        visit.startExamination(DOCTOR_ID, NOW);
        MedicalRecord record = new MedicalRecord(
                UUID.randomUUID(), NOW, VISIT_ID, "BA-DK0001", "NGOAITRU", MedicalRecordStatus.OPEN, NOW, DOCTOR_ID);
        ClinicalExamination examination = new ClinicalExamination(
                UUID.randomUUID(), NOW, record.getId(), "KB-DK0001", DOCTOR_ID);
        StaffRoomAssignment assignment = new StaffRoomAssignment(
                UUID.randomUUID(), DOCTOR_ID, ROOM_ID, NOW, null, null, null, 0L);
        DutySchedule schedule = new DutySchedule(
                UUID.randomUUID(), NOW, LocalDate.ofInstant(NOW, ZoneId.of("Asia/Ho_Chi_Minh")),
                DutyShift.MORNING, DOCTOR_ID, ROOM_ID, null, null);

        when(staffAccountRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        when(assignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(DOCTOR_ID)).thenReturn(Optional.of(assignment));
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(dutyScheduleRepository.findByWorkDateAndShiftAndStaffAccountId(
                LocalDate.ofInstant(NOW, ZoneId.of("Asia/Ho_Chi_Minh")), DutyShift.MORNING, DOCTOR_ID))
                .thenReturn(Optional.of(schedule));
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.of(visit));
        when(patientRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(medicalRecordRepository.findByVisitId(VISIT_ID)).thenReturn(Optional.of(record));
        lenient().when(examinationRepository.findByMedicalRecordId(record.getId())).thenReturn(Optional.of(examination));
        return patient;
    }

    private static StaffPrincipal principal() {
        return new StaffPrincipal(DOCTOR_ID, UUID.randomUUID());
    }
}
