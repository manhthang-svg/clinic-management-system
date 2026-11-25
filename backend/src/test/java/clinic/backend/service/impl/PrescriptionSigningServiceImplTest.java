package clinic.backend.service.impl;

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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.config.properties.SigningProperties;
import clinic.backend.dto.request.RequestSignatureRequest;
import clinic.backend.dto.response.SigningRequestResponse;
import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.Conclusion;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.MedicineCatalog;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Prescription;
import clinic.backend.entity.PrescriptionDetail;
import clinic.backend.entity.PrescriptionSubmission;
import clinic.backend.entity.RecordSignature;
import clinic.backend.entity.SigningRequest;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.StaffSigningCertificate;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.MedicineControlGroup;
import clinic.backend.enums.PrescriptionLienthongStatus;
import clinic.backend.enums.PrescriptionSubmissionResult;
import clinic.backend.enums.RecordSignatureDocumentType;
import clinic.backend.enums.RecordSignatureMethod;
import clinic.backend.enums.Role;
import clinic.backend.enums.SigningCertificateStatus;
import clinic.backend.enums.SigningRequestStatus;
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
import clinic.backend.repository.RecordSignatureRepository;
import clinic.backend.repository.SigningRequestRepository;
import clinic.backend.repository.StaffSigningCertificateRepository;
import clinic.backend.repository.VitalSignRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.NationalPrescriptionSystem;
import clinic.backend.service.RemoteSigningGateway;
import clinic.backend.service.RemoteSigningGateway.SigningResult;
import clinic.backend.service.RemoteSigningGateway.Status;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** UC-90 Submit e-Prescription: signing request, verification, storage and send (BR-59-10 to BR-59-12). */
@ExtendWith(MockitoExtension.class)
class PrescriptionSigningServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW = Instant.parse("2026-11-03T03:00:00Z");
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID VISIT_ID = UUID.randomUUID();
    private static final byte[] SIGNED_DATA = {1, 2, 3};
    /** The CMS after the time stamp was added (CAdES-T); this is stored and sent. */
    private static final byte[] STAMPED_DATA = {4, 5, 6};

    @Mock private ConcludingVisitGuard guard;
    @Mock private PrescriptionServiceImpl prescriptions;
    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private MedicalRecordRepository medicalRecordRepository;
    @Mock private ConclusionRepository conclusionRepository;
    @Mock private MedicineCatalogRepository medicineRepository;
    @Mock private VitalSignRepository vitalSignRepository;
    @Mock private FollowUpAppointmentRepository followUpRepository;
    @Mock private StaffSigningCertificateRepository certificateRepository;
    @Mock private SigningRequestRepository signingRequestRepository;
    @Mock private RecordSignatureRepository recordSignatureRepository;
    @Mock private PrescriptionSubmissionRepository submissionRepository;
    @Mock private RemoteSigningGateway signingGateway;
    @Mock private DigitalSignatureVerifier verifier;
    @Mock private NationalPrescriptionSystem nationalSystem;
    @Mock private AuditLogService auditLogService;

    private PrescriptionSigningServiceImpl service;
    private ConcludingVisitGuard.Context context;
    private Prescription prescription;
    private StaffSigningCertificate certificate;
    private final MedicineCatalog amoxicillin = new MedicineCatalog(UUID.randomUUID(), NOW,
            new MedicineCatalog.CatalogEntry("AMO500", "VD-AMO500", null, "Amoxicilin 500mg", "Amoxicilin", "500mg",
                    "Uống", "Viên", MedicineControlGroup.THUONG));

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZONE);
        service = new PrescriptionSigningServiceImpl(
                guard, prescriptions, prescriptionRepository, medicalRecordRepository, conclusionRepository,
                medicineRepository, vitalSignRepository, followUpRepository, certificateRepository,
                signingRequestRepository, recordSignatureRepository, submissionRepository, signingGateway, verifier,
                nationalSystem, auditLogService,
                new SigningProperties(Duration.ofMinutes(5), Duration.ofSeconds(10), List.of()),
                new UuidV7Generator(clock, new SecureRandom()), clock);
        Patient patient = new Patient(
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
        StaffAccount doctor = StaffAccount.create(
                DOCTOR_ID, "doctor01", "hash", "Bác sĩ An", null, null, null, null, null, null, Role.DOCTOR, null, NOW);
        doctor.updateMaLienthongBacsi("BS0001", NOW);
        doctor.updateCitizenId("079090001234", NOW);
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
        lenient().when(signingRequestRepository.save(any(SigningRequest.class)))
                .thenAnswer(call -> call.getArgument(0));

        certificate = new StaffSigningCertificate(UUID.randomUUID(), NOW, DOCTOR_ID, "SIMULATED_CA", "079090001234",
                "abc123", "CN=CA ký số từ xa (giả lập)", "CN=Bác sĩ An", NOW.minusSeconds(86_400),
                NOW.plusSeconds(86_400 * 365L), new byte[] {9});
        ClinicFacility facility = mock(ClinicFacility.class);
        lenient().when(facility.getMaCskcb()).thenReturn("79001");
        lenient().when(facility.getMaLienthongDonthuoc()).thenReturn("LT79001");
        lenient().when(prescriptions.requireLinkCodes(context)).thenReturn(facility);
        lenient().when(verifier.certificate(any())).thenReturn(mock(X509Certificate.class));
        lenient().when(verifier.citizenId(any())).thenReturn(Optional.of("079090001234"));
    }

    @Test
    void refusesToSignWithoutARegisteredCertificate() {
        stubSendChecks();
        when(certificateRepository.findByStaffAccountIdAndStatus(DOCTOR_ID, SigningCertificateStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestSignature(principal(), prescription.getId(), new RequestSignatureRequest(2L)))
                .isInstanceOfSatisfying(AppException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(ErrorCode.SIGNING_CERTIFICATE_NOT_REGISTERED));
        verify(signingGateway, never()).requestSignature(any(), any(), any(), any(), any());
    }

    @Test
    void refusesACertificateThatNamesAnotherPerson() {
        stubSendChecks();
        when(certificateRepository.findByStaffAccountIdAndStatus(DOCTOR_ID, SigningCertificateStatus.ACTIVE))
                .thenReturn(Optional.of(certificate));
        when(verifier.citizenId(any())).thenReturn(Optional.of("079090009999"));

        assertThatThrownBy(() -> service.requestSignature(principal(), prescription.getId(), new RequestSignatureRequest(2L)))
                .isInstanceOfSatisfying(AppException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.SIGNING_CERTIFICATE_INVALID);
                    assertThat(error.getDetails()).containsEntry("reason", "CERTIFICATE_NOT_DOCTOR");
                });
        verify(signingGateway, never()).requestSignature(any(), any(), any(), any(), any());
    }

    @Test
    void refusesASecondPendingRequest() {
        SigningRequest pending = pendingRequest();
        when(signingRequestRepository.findByDocumentTypeAndDocumentIdAndStatus(
                RecordSignatureDocumentType.DON_THUOC, prescription.getId(), SigningRequestStatus.PENDING))
                .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.requestSignature(principal(), prescription.getId(), new RequestSignatureRequest(2L)))
                .isInstanceOfSatisfying(AppException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.SIGNING_REQUEST_PENDING);
                    assertThat(error.getDetails()).containsEntry("requestId", pending.getId().toString());
                });
    }

    @Test
    void refusesAnAcceptedPrescription() {
        prescription.recordSubmission(PrescriptionSubmissionResult.ACCEPTED, NOW);

        assertThatThrownBy(() -> service.requestSignature(principal(), prescription.getId(), new RequestSignatureRequest(2L)))
                .isInstanceOfSatisfying(AppException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(ErrorCode.PRESCRIPTION_ALREADY_SENT));
    }

    @Test
    void copiesTheDiagnosesAndAsksTheCaToSignTheHashOfThePayload() {
        stubSendChecks();
        when(certificateRepository.findByStaffAccountIdAndStatus(DOCTOR_ID, SigningCertificateStatus.ACTIVE))
                .thenReturn(Optional.of(certificate));
        when(signingGateway.requestSignature(eq("079090001234"), eq("abc123"), any(), any(), eq(NOW.plusSeconds(300))))
                .thenReturn("tx-1");

        SigningRequestResponse response =
                service.requestSignature(principal(), prescription.getId(), new RequestSignatureRequest(2L));

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(prescription.getDiagnoses()).singleElement().satisfies(diagnosis -> {
            assertThat(diagnosis.getMaicd()).isEqualTo("J02.9");
            assertThat(diagnosis.getThuTu()).isEqualTo((short) 1);
        });
        verify(verifier).preCheck(any());
        ArgumentCaptor<SigningRequest> saved = ArgumentCaptor.forClass(SigningRequest.class);
        verify(signingRequestRepository).save(saved.capture());
        String payload = new String(saved.getValue().getPayload(), StandardCharsets.UTF_8);
        assertThat(payload)
                .startsWith("{\"loai_don_thuoc\":\"c\",\"ma_don_thuoc\":\"79001abc1234-c\"")
                .contains("\"gioi_tinh\":2")
                .contains("\"chan_doan\":[{\"ma_benh\":\"J02.9\"")
                .contains("\"cach_dung\":\"1 viên × 3/ngày × 7 ngày, Uống sau ăn\"")
                .doesNotContain("signature");
        assertThat(saved.getValue().getDocumentSha256())
                .isEqualTo(DigitalSignatureVerifier.sha256Hex(saved.getValue().getPayload()));
        verify(signingGateway).requestSignature(eq("079090001234"), eq("abc123"),
                eq(DigitalSignatureVerifier.sha256(saved.getValue().getPayload())), eq("Đơn thuốc 79001abc1234-c"),
                eq(NOW.plusSeconds(300)));
    }

    @Test
    void keepsWaitingWhileTheCaHasNoAnswer() {
        SigningRequest pending = stubPendingRequest();
        when(signingGateway.result("tx-1")).thenReturn(SigningResult.of(Status.PENDING));

        SigningRequestResponse response = service.complete(principal(), prescription.getId(), pending.getId());

        assertThat(response.status()).isEqualTo("PENDING");
        verify(nationalSystem, never()).submit(any(), any());
    }

    @Test
    void recordsADeclinedSignature() {
        SigningRequest pending = stubPendingRequest();
        when(signingGateway.result("tx-1")).thenReturn(SigningResult.of(Status.DECLINED));

        SigningRequestResponse response = service.complete(principal(), prescription.getId(), pending.getId());

        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(pending.getStatus()).isEqualTo(SigningRequestStatus.DECLINED);
        verify(nationalSystem, never()).submit(any(), any());
    }

    @Test
    void refusesAnInvalidSignatureAndSendsNothing() {
        SigningRequest pending = stubSignedRequest();
        when(verifier.verify(any(), any(), any()))
                .thenReturn(DigitalSignatureVerifier.Verification.failed("SIGNATURE_MISMATCH"));

        assertThatThrownBy(() -> service.complete(principal(), prescription.getId(), pending.getId()))
                .isInstanceOfSatisfying(AppException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.SIGNATURE_INVALID);
                    assertThat(error.getDetails()).containsEntry("reason", "SIGNATURE_MISMATCH");
                });
        assertThat(pending.getStatus()).isEqualTo(SigningRequestStatus.FAILED);
        verify(recordSignatureRepository, never()).save(any());
        verify(nationalSystem, never()).submit(any(), any());
    }

    @Test
    void storesTheVerifiedSignatureAndTheAcceptedSend() {
        SigningRequest pending = stubSignedRequest();
        stubValidSignature();
        when(nationalSystem.submit(any(), any()))
                .thenReturn(new NationalPrescriptionSystem.Answer(true, "Gửi đơn thuốc thành công", List.of()));

        SigningRequestResponse response = service.complete(principal(), prescription.getId(), pending.getId());

        assertThat(response.status()).isEqualTo("SIGNED");
        assertThat(prescription.getLienthongStatus()).isEqualTo(PrescriptionLienthongStatus.ACCEPTED);
        assertThat(prescription.getLienthongSentAt()).isEqualTo(NOW);
        ArgumentCaptor<RecordSignature> signature = ArgumentCaptor.forClass(RecordSignature.class);
        verify(recordSignatureRepository).save(signature.capture());
        assertThat(signature.getValue().getSignatureMethod()).isEqualTo(RecordSignatureMethod.DIGITAL_SIGNATURE);
        assertThat(signature.getValue().getSignedPayload()).isEqualTo(pending.getPayload());
        assertThat(signature.getValue().getSignatureValue()).isEqualTo(STAMPED_DATA);
        assertThat(signature.getValue().getSignatureFormat()).isEqualTo("CAdES-T");
        assertThat(signature.getValue().getCertificateSerial()).isEqualTo("abc123");
        assertThat(signature.getValue().getSigningRequestId()).isEqualTo(pending.getId());
        verify(auditLogService).recordBusinessChange(eq(DOCTOR_ID), eq(AuditLogAction.SIGN), eq("prescriptions"),
                eq(prescription.getId()), eq("DK0001"), anyMap(), eq(NOW));
        ArgumentCaptor<byte[]> sent = ArgumentCaptor.forClass(byte[].class);
        verify(nationalSystem).submit(eq(new NationalPrescriptionSystem.Credentials("79001", "LT79001", "BS0001")),
                sent.capture());
        assertThat(new String(sent.getValue(), StandardCharsets.UTF_8)).endsWith(",\"signature\":\"BAUG\"}");
        ArgumentCaptor<PrescriptionSubmission> submission = ArgumentCaptor.forClass(PrescriptionSubmission.class);
        verify(submissionRepository).save(submission.capture());
        assertThat(submission.getValue().getResult()).isEqualTo(PrescriptionSubmissionResult.ACCEPTED);
        assertThat(submission.getValue().getAttemptNo()).isEqualTo((short) 1);
    }

    @Test
    void keepsTheRejectionErrorsAndLeavesThePrescriptionEditable() {
        SigningRequest pending = stubSignedRequest();
        stubValidSignature();
        when(nationalSystem.submit(any(), any())).thenReturn(new NationalPrescriptionSystem.Answer(
                false, "Đơn thuốc không hợp lệ", List.of("Thiếu thông tin dia_chi.")));

        service.complete(principal(), prescription.getId(), pending.getId());

        assertThat(prescription.getLienthongStatus()).isEqualTo(PrescriptionLienthongStatus.REJECTED);
        ArgumentCaptor<PrescriptionSubmission> submission = ArgumentCaptor.forClass(PrescriptionSubmission.class);
        verify(submissionRepository).save(submission.capture());
        assertThat(submission.getValue().getResult()).isEqualTo(PrescriptionSubmissionResult.REJECTED);
        assertThat(submission.getValue().getErrors()).isEqualTo("[\"Thiếu thông tin dia_chi.\"]");
    }

    @Test
    void keepsTheSignatureWhenTheNationalSystemDoesNotAnswer() {
        SigningRequest pending = stubSignedRequest();
        stubValidSignature();
        when(nationalSystem.submit(any(), any()))
                .thenThrow(new AppException(ErrorCode.NATIONAL_PRESCRIPTION_SYSTEM_UNAVAILABLE));

        assertThatThrownBy(() -> service.complete(principal(), prescription.getId(), pending.getId()))
                .isInstanceOfSatisfying(AppException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(ErrorCode.NATIONAL_PRESCRIPTION_SYSTEM_UNAVAILABLE));
        assertThat(pending.getStatus()).isEqualTo(SigningRequestStatus.SIGNED);
        assertThat(prescription.getLienthongStatus()).isEqualTo(PrescriptionLienthongStatus.SENT);
        verify(recordSignatureRepository).save(any());
        ArgumentCaptor<PrescriptionSubmission> submission = ArgumentCaptor.forClass(PrescriptionSubmission.class);
        verify(submissionRepository).save(submission.capture());
        assertThat(submission.getValue().getResult()).isEqualTo(PrescriptionSubmissionResult.NO_RESPONSE);
        assertThat(submission.getValue().getRespondedAt()).isNull();
    }

    @Test
    void answersAFinalRequestAgainWithoutAskingTheCa() {
        SigningRequest declined = stubPendingRequest();
        declined.markDeclined(NOW);

        SigningRequestResponse response = service.complete(principal(), prescription.getId(), declined.getId());

        assertThat(response.status()).isEqualTo("DECLINED");
        verify(signingGateway, never()).result(any());
    }

    @Test
    void cancelsAPendingRequest() {
        SigningRequest pending = stubPendingRequest();

        service.cancel(principal(), prescription.getId(), pending.getId());

        assertThat(pending.getStatus()).isEqualTo(SigningRequestStatus.CANCELLED);
        verify(signingGateway).cancel("tx-1");
    }

    private void stubSendChecks() {
        Conclusion conclusion = new Conclusion(UUID.randomUUID(), NOW, context.record().getId());
        conclusion.record(DOCTOR_ID, "Viêm họng cấp", "J02.9", "Viêm họng cấp", "Điều trị", "Đỡ", "Dặn", null,
                null, NOW);
        when(conclusionRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.of(conclusion));
        when(medicineRepository.findAllById(any())).thenReturn(List.of(amoxicillin));
    }

    private SigningRequest pendingRequest() {
        return new SigningRequest(UUID.randomUUID(), NOW, RecordSignatureDocumentType.DON_THUOC, prescription.getId(),
                2L, DigitalSignatureVerifier.sha256Hex(payload()), payload(), DOCTOR_ID, certificate.getId(), "tx-1",
                NOW.plusSeconds(300));
    }

    private SigningRequest stubPendingRequest() {
        SigningRequest pending = pendingRequest();
        when(signingRequestRepository.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));
        return pending;
    }

    private SigningRequest stubSignedRequest() {
        SigningRequest pending = stubPendingRequest();
        when(signingGateway.result("tx-1")).thenReturn(new SigningResult(Status.SIGNED, SIGNED_DATA));
        when(certificateRepository.findById(certificate.getId())).thenReturn(Optional.of(certificate));
        return pending;
    }

    private void stubValidSignature() {
        when(verifier.verify(any(), any(), any())).thenReturn(new DigitalSignatureVerifier.Verification(
                null, STAMPED_DATA, mock(X509Certificate.class), NOW, new byte[] {7}, new byte[] {8}, "{}"));
    }

    private static byte[] payload() {
        return "{\"loai_don_thuoc\":\"c\"}".getBytes(StandardCharsets.UTF_8);
    }

    private static StaffPrincipal principal() {
        return new StaffPrincipal(DOCTOR_ID, UUID.randomUUID());
    }
}
