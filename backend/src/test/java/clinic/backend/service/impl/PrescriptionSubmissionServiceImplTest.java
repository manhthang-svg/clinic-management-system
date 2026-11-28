package clinic.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.SubmitPrescriptionRequest;
import clinic.backend.dto.response.PrescriptionResponse;
import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.Conclusion;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.MedicineCatalog;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Prescription;
import clinic.backend.entity.PrescriptionDetail;
import clinic.backend.entity.PrescriptionSubmission;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.MedicineControlGroup;
import clinic.backend.enums.PrescriptionLienthongStatus;
import clinic.backend.enums.PrescriptionSubmissionResult;
import clinic.backend.enums.Role;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ConclusionRepository;
import clinic.backend.repository.FollowUpAppointmentRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.MedicineCatalogRepository;
import clinic.backend.repository.PrescriptionRepository;
import clinic.backend.repository.PrescriptionSubmissionRepository;
import clinic.backend.repository.VitalSignRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.NationalPrescriptionSystem;
import clinic.backend.service.NationalPrescriptionSystem.Answer;
import clinic.backend.service.NationalPrescriptionSystem.SignIn;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * UC-90 Submit e-Prescription: the checks before sending, the Doctor's national sign-in (cách B: the password only
 * when no token is kept) and the send without a digital signature (BR-59-07 to BR-59-12, BR-59-17).
 */
@ExtendWith(MockitoExtension.class)
class PrescriptionSubmissionServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW = Instant.parse("2026-11-03T03:00:00Z");
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID VISIT_ID = UUID.randomUUID();

    @Mock private ConcludingVisitGuard guard;
    @Mock private PrescriptionServiceImpl prescriptions;
    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private MedicalRecordRepository medicalRecordRepository;
    @Mock private ConclusionRepository conclusionRepository;
    @Mock private MedicineCatalogRepository medicineRepository;
    @Mock private VitalSignRepository vitalSignRepository;
    @Mock private FollowUpAppointmentRepository followUpRepository;
    @Mock private PrescriptionSubmissionRepository submissionRepository;
    @Mock private NationalPrescriptionSystem nationalSystem;

    private PrescriptionSubmissionServiceImpl service;
    private ConcludingVisitGuard.Context context;
    private Prescription prescription;
    private Patient patient;
    private StaffAccount doctor;
    private final MedicineCatalog amoxicillin = new MedicineCatalog(UUID.randomUUID(), NOW,
            new MedicineCatalog.CatalogEntry("AMO500", "VD-AMO500", null, "Amoxicilin 500mg", "Amoxicilin", "500mg",
                    "Uống", "Viên", MedicineControlGroup.THUONG));

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZONE);
        NationalPrescriptionSessions sessions = new NationalPrescriptionSessions(nationalSystem, clock);
        service = new PrescriptionSubmissionServiceImpl(
                guard, prescriptions, prescriptionRepository, medicalRecordRepository, conclusionRepository,
                medicineRepository, vitalSignRepository, followUpRepository, submissionRepository, nationalSystem,
                sessions, new UuidV7Generator(clock, new SecureRandom()), clock);
        patient = new Patient(
                UUID.randomUUID(), NOW, "BN001", null, null, "Nguyễn Văn An", LocalDate.of(1990, 1, 1),
                (short) 1, null, null, "Kinh", "01", "Việt Nam", "VN", "Hà Nội", null, null,
                "Hà Nội", "001", null, null, null, null, null, null, null, null, null, UUID.randomUUID());
        Visit visit = new Visit(
                VISIT_ID, NOW, "DK0001", patient.getId(), UUID.randomUUID(), UUID.randomUUID(), NOW,
                VisitDoituongbnLoai.DV, null, null, null, "Đau họng", false, null,
                LocalDate.ofInstant(NOW, ZONE), 1, VisitStatus.WAITING_CONCLUSION,
                VisitInsuranceVerifyStatus.NOT_APPLICABLE);
        MedicalRecord record = new MedicalRecord(
                UUID.randomUUID(), NOW, VISIT_ID, "BA-DK0001", "NGOAITRU", MedicalRecordStatus.OPEN, NOW, DOCTOR_ID);
        doctor = StaffAccount.create(
                DOCTOR_ID, "doctor01", "hash", "Bác sĩ An", null, null, null, null, null, null, Role.DOCTOR, null, NOW);
        doctor.updateMaLienthongBacsi("BS0001", NOW);
        doctor.markAddedToNationalPrescriptionSystem(NOW);
        context = new ConcludingVisitGuard.Context(visit, patient, record, doctor);
        lenient().when(guard.require(any(), eq(VISIT_ID))).thenReturn(context);
        lenient().when(medicalRecordRepository.findById(record.getId())).thenReturn(Optional.of(record));

        prescription = new Prescription(UUID.randomUUID(), NOW, "DT-DK0001", "79001abc1234-c", record.getId());
        prescription.record(DOCTOR_ID, "Viêm họng cấp", (short) 2, null, null, NOW);
        prescription.replaceDetails(List.of(new PrescriptionDetail(
                UUID.randomUUID(), NOW, prescription, "DT-DK0001-01", amoxicillin, new BigDecimal("21"), "1 viên",
                (short) 3, (short) 7, "Uống sau ăn", null, LocalDate.ofInstant(NOW, ZONE))));
        ReflectionTestUtils.setField(prescription, "version", 2L);
        lenient().when(prescriptionRepository.findById(prescription.getId())).thenReturn(Optional.of(prescription));
        lenient().when(prescriptionRepository.saveAndFlush(any(Prescription.class)))
                .thenAnswer(call -> call.getArgument(0));
        lenient().when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(call -> call.getArgument(0));

        ClinicFacility facility = mock(ClinicFacility.class);
        lenient().when(facility.getMaCskcb()).thenReturn("79001");
        lenient().when(facility.getMaLienthongDonthuoc()).thenReturn("LT79001");
        lenient().when(prescriptions.requireLinkCodes(context)).thenReturn(facility);
        lenient().when(nationalSystem.signInPrescriber(eq("BS0001"), eq("LT79001"), any()))
                .thenReturn(new SignIn("token-bs", List.of()));
    }

    @Test
    void refusesAnAcceptedPrescription() {
        prescription.recordSubmission(PrescriptionSubmissionResult.ACCEPTED, NOW);

        assertError(() -> service.submit(principal(), prescription.getId(), request(2L, "mat-khau")),
                ErrorCode.PRESCRIPTION_ALREADY_SENT);
    }

    @Test
    void refusesAStaleVersion() {
        assertError(() -> service.submit(principal(), prescription.getId(), request(1L, "mat-khau")),
                ErrorCode.CONCURRENT_MODIFICATION);
    }

    @Test
    void refusesADoctorTheFacilityHasNotAdded() {
        stubSendChecks();
        doctor.markRemovedFromNationalPrescriptionSystem(NOW);

        assertError(() -> service.submit(principal(), prescription.getId(), request(2L, "mat-khau")),
                ErrorCode.PRESCRIBER_NOT_ADDED_TO_FACILITY);
        verify(nationalSystem, never()).signInPrescriber(any(), any(), any());
        verify(nationalSystem, never()).submit(any(), any());
    }

    @Test
    void asksForTheNationalPasswordWhenNoTokenIsKeptAndWritesNothing() {
        stubSendChecks();

        assertError(() -> service.submit(principal(), prescription.getId(), request(2L, null)),
                ErrorCode.NATIONAL_PRESCRIPTION_SIGN_IN_REQUIRED);
        verify(nationalSystem, never()).signInPrescriber(any(), any(), any());
        verify(prescriptionRepository, never()).saveAndFlush(any());
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void signsInWithThePasswordThenSendsTheAcceptedPrescriptionWithoutASignature() {
        stubSendChecks();
        when(nationalSystem.submit(eq("token-bs"), any())).thenReturn(Answer.accepted("Gửi đơn thuốc thành công"));
        SubmitPrescriptionRequest request = request(2L, "mat-khau");

        service.submit(principal(), prescription.getId(), request);

        assertThat(request.password()).containsOnly('\0'); // cleared after use
        assertThat(prescription.getLienthongStatus()).isEqualTo(PrescriptionLienthongStatus.ACCEPTED);
        assertThat(prescription.getDiagnoses()).singleElement()
                .satisfies(diagnosis -> assertThat(diagnosis.getMaicd()).isEqualTo("J02.9"));
        ArgumentCaptor<byte[]> sent = ArgumentCaptor.forClass(byte[].class);
        verify(nationalSystem).submit(eq("token-bs"), sent.capture());
        assertThat(new String(sent.getValue(), StandardCharsets.UTF_8))
                .startsWith("{\"loai_don_thuoc\":\"c\",\"ma_don_thuoc\":\"79001abc1234-c\"")
                .contains("\"chan_doan\":[{\"ma_benh\":\"J02.9\"")
                .contains("\"ten_thuoc\":\"Amoxicilin 500mg\"")
                .contains("\"cach_dung\":\"Uống: 1 viên × 3/ngày × 7 ngày, Uống sau ăn\"")
                .doesNotContain("signature");
        ArgumentCaptor<PrescriptionSubmission> submission = ArgumentCaptor.forClass(PrescriptionSubmission.class);
        verify(submissionRepository).save(submission.capture());
        assertThat(submission.getValue().getResult()).isEqualTo(PrescriptionSubmissionResult.ACCEPTED);
        assertThat(submission.getValue().getAttemptNo()).isEqualTo((short) 1);
    }

    @Test
    void reusesTheKeptTokenWithoutAskingForThePasswordAgain() {
        stubSendChecks();
        when(nationalSystem.submit(eq("token-bs"), any()))
                .thenReturn(Answer.rejected("Đơn thuốc không hợp lệ", List.of("Thiếu thông tin dia_chi.")));
        service.submit(principal(), prescription.getId(), request(2L, "mat-khau"));
        ReflectionTestUtils.setField(prescription, "version", 3L);

        service.submit(principal(), prescription.getId(), request(3L, null));

        verify(nationalSystem, times(1)).signInPrescriber(any(), any(), any());
        verify(nationalSystem, times(2)).submit(eq("token-bs"), any());
    }

    @Test
    void reportsARefusedSignInAndSendsNothing() {
        stubSendChecks();
        when(nationalSystem.signInPrescriber(eq("BS0001"), eq("LT79001"), any()))
                .thenReturn(new SignIn(null, List.of("Mã liên thông hoặc mật khẩu không đúng.")));

        assertThatThrownBy(() -> service.submit(principal(), prescription.getId(), request(2L, "sai")))
                .isInstanceOfSatisfying(AppException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NATIONAL_PRESCRIPTION_SIGN_IN_FAILED);
                    assertThat(error.getDetails()).containsEntry("errors", "Mã liên thông hoặc mật khẩu không đúng.");
                });
        verify(nationalSystem, never()).submit(any(), any());
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void asksToSignInAgainWhenTheNationalSystemRefusesTheToken() {
        stubSendChecks();
        when(nationalSystem.submit(eq("token-bs"), any())).thenReturn(Answer.invalidToken());

        assertError(() -> service.submit(principal(), prescription.getId(), request(2L, "mat-khau")),
                ErrorCode.NATIONAL_PRESCRIPTION_SIGN_IN_REQUIRED);
        verify(submissionRepository, never()).save(any());
        assertError(() -> service.submit(principal(), prescription.getId(), request(2L, null)),
                ErrorCode.NATIONAL_PRESCRIPTION_SIGN_IN_REQUIRED);
    }

    @Test
    void keepsTheRejectionErrorsAndLeavesThePrescriptionEditable() {
        stubSendChecks();
        when(nationalSystem.submit(eq("token-bs"), any()))
                .thenReturn(Answer.rejected("Đơn thuốc không hợp lệ", List.of("Thiếu thông tin dia_chi.")));

        service.submit(principal(), prescription.getId(), request(2L, "mat-khau"));

        assertThat(prescription.getLienthongStatus()).isEqualTo(PrescriptionLienthongStatus.REJECTED);
        ArgumentCaptor<PrescriptionSubmission> submission = ArgumentCaptor.forClass(PrescriptionSubmission.class);
        verify(submissionRepository).save(submission.capture());
        assertThat(submission.getValue().getResult()).isEqualTo(PrescriptionSubmissionResult.REJECTED);
        assertThat(submission.getValue().getErrors()).isEqualTo("[\"Thiếu thông tin dia_chi.\"]");
    }

    @Test
    void keepsTheAttemptWhenTheNationalSystemDoesNotAnswer() {
        stubSendChecks();
        when(nationalSystem.submit(eq("token-bs"), any()))
                .thenThrow(new AppException(ErrorCode.NATIONAL_PRESCRIPTION_SYSTEM_UNAVAILABLE));

        assertError(() -> service.submit(principal(), prescription.getId(), request(2L, "mat-khau")),
                ErrorCode.NATIONAL_PRESCRIPTION_SYSTEM_UNAVAILABLE);
        assertThat(prescription.getLienthongStatus()).isEqualTo(PrescriptionLienthongStatus.SENT);
        ArgumentCaptor<PrescriptionSubmission> submission = ArgumentCaptor.forClass(PrescriptionSubmission.class);
        verify(submissionRepository).save(submission.capture());
        assertThat(submission.getValue().getResult()).isEqualTo(PrescriptionSubmissionResult.NO_RESPONSE);
        assertThat(submission.getValue().getRespondedAt()).isNull();
    }

    @Test
    void refusesAnAddressLongerThan500CharactersInsteadOfCuttingIt() {
        stubSendChecks();
        ReflectionTestUtils.setField(patient, "diachi", "x".repeat(495));

        assertThatThrownBy(() -> service.submit(principal(), prescription.getId(), request(2L, "mat-khau")))
                .isInstanceOfSatisfying(AppException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PRESCRIPTION_INVALID);
                    assertThat(error.getDetails()).containsEntry("field", "address");
                });
        verify(nationalSystem, never()).submit(any(), any());
    }

    @Test
    void refusesAChildWithoutAWeight() {
        stubSendChecks();
        when(prescriptions.childData(context, prescription))
                .thenReturn(new PrescriptionResponse.Child(18, null, "Nguyễn Thị Mẹ"));

        assertThatThrownBy(() -> service.submit(principal(), prescription.getId(), request(2L, "mat-khau")))
                .isInstanceOfSatisfying(AppException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PRESCRIPTION_CHILD_DATA_REQUIRED);
                    assertThat(error.getDetails()).containsEntry("item", "WEIGHT");
                });
        verify(nationalSystem, never()).submit(any(), any());
    }

    private void stubSendChecks() {
        Conclusion conclusion = new Conclusion(UUID.randomUUID(), NOW, context.record().getId());
        conclusion.record(DOCTOR_ID, "Viêm họng cấp", "J02.9", "Viêm họng cấp", "Điều trị", "Đỡ", "Dặn", null,
                null, NOW);
        when(conclusionRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.of(conclusion));
        lenient().when(medicineRepository.findAllById(any())).thenReturn(List.of(amoxicillin));
    }

    private static SubmitPrescriptionRequest request(Long version, String password) {
        return new SubmitPrescriptionRequest(version, password == null ? null : password.toCharArray());
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(AppException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(code));
    }

    private static StaffPrincipal principal() {
        return new StaffPrincipal(DOCTOR_ID, UUID.randomUUID());
    }
}
