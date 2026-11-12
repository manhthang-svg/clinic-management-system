package clinic.backend.service.impl;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.SaveConclusionRequest;
import clinic.backend.entity.Conclusion;
import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.Icd10;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.entity.Visit;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.DutyShift;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.Role;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicalExaminationRepository;
import clinic.backend.repository.ConclusionRepository;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.Icd10Repository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.PrescriptionRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.utils.UuidV7Generator;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConclusionServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
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
    @Mock private ConclusionRepository conclusionRepository;
    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private Icd10Repository icd10Repository;
    @Mock private AuditLogService auditLogService;

    private ConclusionServiceImpl service;
    private MedicalRecord record;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZONE);
        ConcludingVisitGuard guard = new ConcludingVisitGuard(
                visitRepository, patientRepository, roomRepository, staffAccountRepository, assignmentRepository,
                dutyScheduleRepository, new DutyShiftConfigServiceImpl(new ObjectMapper().findAndRegisterModules()),
                medicalRecordRepository, clock);
        service = new ConclusionServiceImpl(
                guard, staffAccountRepository, examinationRepository, conclusionRepository, prescriptionRepository,
                icd10Repository, auditLogService, new UuidV7Generator(clock, new SecureRandom()), clock);
    }

    @Test
    void savesANewConclusionWithItsSecondaryDiagnosesAndAuditsIt() {
        stubVisit(VisitStatus.WAITING_CONCLUSION);
        stubIcd(icd("J02.9", false, false), icd("K29.7", false, false));
        when(conclusionRepository.findByMedicalRecordId(record.getId())).thenReturn(Optional.empty());
        when(conclusionRepository.saveAndFlush(any(Conclusion.class))).thenAnswer(call -> call.getArgument(0));

        var response = service.save(principal(), VISIT_ID, request("J02.9", List.of("K29.7"), false));

        assertThat(response.mainDiagnosis().code()).isEqualTo("J02.9");
        assertThat(response.secondaryDiagnoses()).extracting(item -> item.code()).containsExactly("K29.7");
        assertThat(response.treatmentResult()).isEqualTo((short) 2);
        ArgumentCaptor<Conclusion> saved = ArgumentCaptor.forClass(Conclusion.class);
        verify(conclusionRepository, org.mockito.Mockito.atLeastOnce()).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getLoidanbacsi()).isEqualTo("Uống nhiều nước");
        verify(auditLogService).recordEntityChange(
                eq(DOCTOR_ID), any(UUID.class), eq(AuditLogAction.CREATE), eq("conclusions"),
                anyMap(), anyMap(), eq(NOW));
    }

    @Test
    void asksForTheEmptyRequiredFieldBeforeSaving() {
        stubVisit(VisitStatus.IN_EXAMINATION);
        when(conclusionRepository.findByMedicalRecordId(record.getId())).thenReturn(Optional.empty());

        SaveConclusionRequest request = new SaveConclusionRequest(
                "Viêm họng cấp", "J02.9", List.of(), "Điều trị ngoại trú", (short) 2, "  ", null, null, false, null);

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID, request))
                .isInstanceOfSatisfying(AppException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONCLUSION_INVALID);
                    assertThat(error.getDetails()).containsEntry("field", "advice").containsEntry("reason", "REQUIRED");
                });
        verify(conclusionRepository, never()).saveAndFlush(any(Conclusion.class));
    }

    @Test
    void refusesACodeThatCannotBeAMainDiagnosis() {
        stubVisit(VisitStatus.IN_EXAMINATION);
        stubIcd(icd("Y40.0", true, false));
        when(conclusionRepository.findByMedicalRecordId(record.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID, request("Y40.0", List.of(), false)))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONCLUSION_MAIN_DIAGNOSIS_NOT_ALLOWED));
    }

    @Test
    void asksToConfirmADiscouragedMainDiagnosisAndKeepsItOnceConfirmed() {
        stubVisit(VisitStatus.IN_EXAMINATION);
        stubIcd(icd("R50.9", false, true));
        when(conclusionRepository.findByMedicalRecordId(record.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID, request("R50.9", List.of(), false)))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONCLUSION_MAIN_DIAGNOSIS_DISCOURAGED));

        when(conclusionRepository.saveAndFlush(any(Conclusion.class))).thenAnswer(call -> call.getArgument(0));
        assertThat(service.save(principal(), VISIT_ID, request("R50.9", List.of(), true)).mainDiagnosis().code())
                .isEqualTo("R50.9");
    }

    @Test
    void refusesTheMainDiagnosisRepeatedAsASecondaryOne() {
        stubVisit(VisitStatus.IN_EXAMINATION);
        stubIcd(icd("J02.9", false, false));
        when(conclusionRepository.findByMedicalRecordId(record.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID, request("J02.9", List.of("j02.9"), false)))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONCLUSION_MAIN_DIAGNOSIS_REQUIRED));
    }

    @Test
    void keepsTheDiagnosesSentWithThePrescription() {
        stubVisit(VisitStatus.IN_EXAMINATION);
        stubIcd(icd("J02.9", false, false), icd("J03.9", false, false));
        Conclusion saved = new Conclusion(UUID.randomUUID(), NOW, record.getId());
        saved.record(DOCTOR_ID, "Viêm họng", "J02.9", "Bệnh J02.9", "Điều trị", (short) 2, "Dặn", null, null, NOW);
        when(conclusionRepository.findByMedicalRecordId(record.getId())).thenReturn(Optional.of(saved));
        when(prescriptionRepository.existsByMedicalRecordIdAndLienthongStatusIn(eq(record.getId()), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID, request("J03.9", List.of(), false)))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONCLUSION_DIAGNOSES_LOCKED));
    }

    @Test
    void refusesAVisitStillWaitingForParaclinicalResults() {
        stubVisit(VisitStatus.WAITING_PARACLINICAL);

        assertThatThrownBy(() -> service.get(principal(), VISIT_ID))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VISIT_NOT_IN_EXAMINATION));
    }

    private static SaveConclusionRequest request(String main, List<String> secondary, boolean confirm) {
        return new SaveConclusionRequest(
                " Viêm họng cấp ", main, secondary, "Điều trị ngoại trú 5 ngày", (short) 2, "Uống nhiều nước",
                null, null, confirm, null);
    }

    private static Icd10 icd(String code, boolean notMain, boolean discouraged) {
        return Icd10.fromCatalog(
                new Icd10.CatalogEntry(code, "Bệnh " + code, null, notMain, discouraged, false, false, null, null, null),
                LocalDate.of(2026, 7, 1), "TT 06/2026/TT-BYT");
    }

    private void stubIcd(Icd10... codes) {
        for (Icd10 code : codes) {
            lenient().when(icd10Repository.findById(code.getMaicd())).thenReturn(Optional.of(code));
        }
    }

    private void stubVisit(VisitStatus status) {
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
                VisitDoituongbnLoai.DV, null, null, null, "Đau họng", false, null,
                LocalDate.ofInstant(NOW, ZONE), 1, status, VisitInsuranceVerifyStatus.NOT_APPLICABLE);
        org.springframework.test.util.ReflectionTestUtils.setField(visit, "doctorAccountId", DOCTOR_ID);
        record = new MedicalRecord(
                UUID.randomUUID(), NOW, VISIT_ID, "BA-DK0001", "NGOAITRU", MedicalRecordStatus.OPEN, NOW, DOCTOR_ID);
        StaffRoomAssignment assignment = new StaffRoomAssignment(
                UUID.randomUUID(), DOCTOR_ID, ROOM_ID, NOW, null, null, null, 0L);
        DutySchedule schedule = new DutySchedule(
                UUID.randomUUID(), NOW, LocalDate.ofInstant(NOW, ZONE), DutyShift.MORNING, DOCTOR_ID, ROOM_ID,
                null, null);

        when(staffAccountRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        when(assignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(DOCTOR_ID)).thenReturn(Optional.of(assignment));
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(dutyScheduleRepository.findByWorkDateAndShiftAndStaffAccountId(
                LocalDate.ofInstant(NOW, ZONE), DutyShift.MORNING, DOCTOR_ID)).thenReturn(Optional.of(schedule));
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.of(visit));
        lenient().when(patientRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        lenient().when(medicalRecordRepository.findByVisitId(VISIT_ID)).thenReturn(Optional.of(record));
        lenient().when(examinationRepository.findByMedicalRecordId(record.getId())).thenReturn(Optional.empty());
    }

    private static StaffPrincipal principal() {
        return new StaffPrincipal(DOCTOR_ID, UUID.randomUUID());
    }
}
