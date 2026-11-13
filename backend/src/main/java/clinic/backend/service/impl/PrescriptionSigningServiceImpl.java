package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import clinic.backend.dto.request.RequestSignatureRequest;
import clinic.backend.dto.response.PrescriptionResponse;
import clinic.backend.dto.response.SigningRequestResponse;
import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.Conclusion;
import clinic.backend.entity.ConclusionSecondaryDiagnosis;
import clinic.backend.entity.FollowUpAppointment;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.MedicineCatalog;
import clinic.backend.entity.Prescription;
import clinic.backend.entity.PrescriptionDetail;
import clinic.backend.entity.PrescriptionDiagnosis;
import clinic.backend.entity.PrescriptionSubmission;
import clinic.backend.entity.RecordSignature;
import clinic.backend.entity.SigningRequest;
import clinic.backend.entity.StaffSigningCertificate;
import clinic.backend.entity.VitalSign;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.FollowUpAppointmentStatus;
import clinic.backend.enums.MedicineControlGroup;
import clinic.backend.enums.PrescriptionSubmissionResult;
import clinic.backend.enums.RecordSignatureDocumentType;
import clinic.backend.enums.SigningCertificateStatus;
import clinic.backend.enums.SigningRequestStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.config.properties.SigningProperties;
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
import clinic.backend.service.PrescriptionSigningService;
import clinic.backend.service.RemoteSigningGateway;
import clinic.backend.service.RemoteSigningGateway.SigningResult;
import clinic.backend.service.impl.ConcludingVisitGuard.Context;
import clinic.backend.utils.UuidV7Generator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-90 Submit e-Prescription (docs/uc-59-e-prescription.md BR-59-07 to BR-59-12): the Doctor signs the prescription
 * with the public digital signature by remote signing, the system verifies the signature, keeps it with the signed
 * payload and sends the prescription to the national prescription system. The clinic never holds the Doctor's key
 * or signing credential.
 *
 * <p>{@link #complete} runs in one transaction that keeps business rejections ({@code noRollbackFor}): a refused
 * signature stays {@code FAILED}, and a send without an answer keeps its signature and submission rows. Any other
 * failure rolls back, leaving the request {@code PENDING} so asking again retries (EF-14).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionSigningServiceImpl implements PrescriptionSigningService {

    private static final String AUDIT_ENTITY = "prescriptions";
    private static final RecordSignatureDocumentType DOCUMENT_TYPE = RecordSignatureDocumentType.DON_THUOC;

    private final ConcludingVisitGuard guard;
    private final PrescriptionServiceImpl prescriptions;
    private final PrescriptionRepository prescriptionRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final ConclusionRepository conclusionRepository;
    private final MedicineCatalogRepository medicineRepository;
    private final VitalSignRepository vitalSignRepository;
    private final FollowUpAppointmentRepository followUpRepository;
    private final StaffSigningCertificateRepository certificateRepository;
    private final SigningRequestRepository signingRequestRepository;
    private final RecordSignatureRepository recordSignatureRepository;
    private final PrescriptionSubmissionRepository submissionRepository;
    private final RemoteSigningGateway signingGateway;
    private final DigitalSignatureVerifier verifier;
    private final NationalPrescriptionSystem nationalSystem;
    private final AuditLogService auditLogService;
    private final SigningProperties signingProperties;
    private final UuidV7Generator ids;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();

    @Override
    @Transactional
    public SigningRequestResponse requestSignature(
            StaffPrincipal principal, UUID prescriptionId, RequestSignatureRequest request) {
        Prescription prescription = prescription(prescriptionId);
        Context context = guard.require(principal, visitId(prescription));
        if (!prescription.canBeSubmitted()) {
            throw new AppException(ErrorCode.PRESCRIPTION_ALREADY_SENT);
        }
        if (request == null || request.version() == null || !request.version().equals(prescription.getVersion())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        signingRequestRepository.findByDocumentTypeAndDocumentIdAndStatus(
                        DOCUMENT_TYPE, prescription.getId(), SigningRequestStatus.PENDING)
                .ifPresent(pending -> {
                    throw new AppException(ErrorCode.SIGNING_REQUEST_PENDING,
                            Map.of("requestId", pending.getId().toString()));
                });

        // UC-90 step 3: the checks of UC-59 against the conclusion and vital signs as they are now.
        Conclusion conclusion = conclusionRepository.findByMedicalRecordId(context.record().getId())
                .orElseThrow(() -> new AppException(ErrorCode.PRESCRIPTION_CONCLUSION_REQUIRED));
        prescriptions.requireLinkCodes(context);
        recheckLines(prescription, conclusion);
        BigDecimal weight = vitalSignRepository
                .findFirstByMedicalRecordIdAndCannangIsNotNullOrderByThoidiemDesc(context.record().getId())
                .map(VitalSign::getCannang)
                .orElse(null);
        String accompanyingPerson = requireChildData(prescription, context, weight);

        // EF-10, EF-11: the Doctor's registered certificate and the pre-check of NĐ 23 Điều 15.
        StaffSigningCertificate registered = certificateRepository
                .findByStaffAccountIdAndStatus(principal.accountId(), SigningCertificateStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.SIGNING_CERTIFICATE_NOT_REGISTERED));
        verifier.preCheck(verifier.certificate(registered.getCertificateDer()));

        // BR-59-08: the diagnoses and follow-up date as they are sent.
        Instant now = clock.instant();
        LocalDate followUpDate = followUpRepository.findByMedicalRecordId(context.record().getId())
                .filter(appointment -> appointment.getStatus() == FollowUpAppointmentStatus.SCHEDULED)
                .map(FollowUpAppointment::getFollowUpDate)
                .orElse(null);
        prescription.replaceDiagnoses(List.of(), prescription.getNgayTaiKham(), now);
        prescription = prescriptionRepository.saveAndFlush(prescription);
        prescription.replaceDiagnoses(diagnoses(prescription, conclusion, now), followUpDate, now);
        prescription = prescriptionRepository.saveAndFlush(prescription);

        // BR-59-10 steps 2-3: the exact bytes to sign and the request to the CA.
        byte[] payload = PrescriptionPayloadBuilder.canonicalPayload(
                prescription, context.visit(), context.patient(), conclusion, weight, accompanyingPerson,
                followUpDate);
        Instant expiresAt = now.plus(signingProperties.approvalTimeout());
        String transactionId = signingGateway.requestSignature(
                registered.getProviderUserId(), registered.getSerialNumber(), DigitalSignatureVerifier.sha256(payload),
                "Đơn thuốc " + prescription.getMadonthuoc(), expiresAt);
        SigningRequest signing = signingRequestRepository.save(new SigningRequest(
                ids.next(), now, DOCUMENT_TYPE, prescription.getId(), prescription.getVersion(),
                DigitalSignatureVerifier.sha256Hex(payload), payload, principal.accountId(), registered.getId(),
                transactionId, expiresAt));
        return response(signing, null);
    }

    @Override
    @Transactional(readOnly = true)
    public SigningRequestResponse currentRequest(StaffPrincipal principal, UUID prescriptionId) {
        Prescription prescription = prescription(prescriptionId);
        guard.require(principal, visitId(prescription));
        return signingRequestRepository.findByDocumentTypeAndDocumentIdAndStatus(
                        DOCUMENT_TYPE, prescription.getId(), SigningRequestStatus.PENDING)
                .map(signing -> response(signing, null))
                .orElse(null);
    }

    @Override
    @Transactional(noRollbackFor = AppException.class)
    public SigningRequestResponse complete(StaffPrincipal principal, UUID prescriptionId, UUID requestId) {
        Prescription prescription = prescription(prescriptionId);
        Context context = guard.require(principal, visitId(prescription));
        SigningRequest signing = lockedRequest(prescription, requestId);
        if (!signing.isPending()) {
            return finalResponse(context, signing);
        }
        Instant now = clock.instant();
        if (prescription.getVersion() != signing.getDocumentVersion() || !prescription.canBeSubmitted()) {
            // K06: the signed version no longer exists.
            cancelAtCa(signing);
            signing.markCancelled(now);
            return response(signingRequestRepository.save(signing), null);
        }

        SigningResult result = signingGateway.result(signing.getProviderTransactionId());
        switch (result.status()) {
            case PENDING -> {
                if (!signing.isExpiredAt(now)) {
                    return response(signing, null);
                }
                cancelAtCa(signing);
                signing.markExpired(now); // K05, AF-05
                return response(signingRequestRepository.save(signing), null);
            }
            case EXPIRED -> {
                signing.markExpired(now);
                return response(signingRequestRepository.save(signing), null);
            }
            case DECLINED -> {
                signing.markDeclined(now); // K04, AF-04
                return response(signingRequestRepository.save(signing), null);
            }
            case SIGNED -> {
                return signAndSend(principal, context, prescription, signing, result.signedData());
            }
            default -> throw new IllegalStateException("Unknown signing status " + result.status());
        }
    }

    @Override
    @Transactional
    public void cancel(StaffPrincipal principal, UUID prescriptionId, UUID requestId) {
        Prescription prescription = prescription(prescriptionId);
        guard.require(principal, visitId(prescription));
        SigningRequest signing = lockedRequest(prescription, requestId);
        if (signing.isPending()) {
            cancelAtCa(signing);
            signing.markCancelled(clock.instant()); // K06, AF-03
            signingRequestRepository.save(signing);
        }
    }

    /** UC-90 steps 5-8. */
    private SigningRequestResponse signAndSend(
            StaffPrincipal principal, Context context, Prescription prescription, SigningRequest signing,
            byte[] signedData) {
        StaffSigningCertificate registered = certificateRepository.findById(signing.getCertificateId())
                .orElseThrow(() -> new IllegalStateException("Missing certificate " + signing.getCertificateId()));
        X509Certificate certificate = verifier.certificate(registered.getCertificateDer());
        byte[] payload = signing.getPayload();
        DigitalSignatureVerifier.Verification verification = verifier.verify(signedData, payload, certificate);
        Instant now = clock.instant();
        if (!verification.isValid()) {
            // K03, EF-16: nothing is stored or sent.
            signing.markFailed(verification.failureReason(), now);
            signingRequestRepository.save(signing);
            throw new AppException(ErrorCode.SIGNATURE_INVALID, Map.of("reason", verification.failureReason()));
        }

        // BR-59-10 step 7, BR-59-14: the signature with its evidence, and the SIGN audit row.
        signing.markSigned(now);
        signingRequestRepository.save(signing);
        recordSignatureRepository.save(RecordSignature.staffDigitalSignature(
                ids.next(), now, prescription.getMedicalRecordId(), DOCUMENT_TYPE, prescription.getId(),
                signing.getDocumentVersion(), signing.getDocumentSha256(), principal.accountId(),
                context.doctor().getFullName(), registered.getSerialNumber(), registered.getIssuerDn(),
                DigitalSignatureVerifier.SIGNATURE_FORMAT, verification.signedAt(), verification.timestampToken(),
                signedData, payload, verification.certificateChain(), verification.evidence(), signing.getId()));
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("signingRequestId", signing.getId().toString());
        audit.put("documentVersion", signing.getDocumentVersion());
        audit.put("documentSha256", signing.getDocumentSha256());
        audit.put("certificateSerial", registered.getSerialNumber());
        audit.put("certificateIssuer", registered.getIssuerDn());
        audit.put("signedAt", verification.signedAt().toString());
        auditLogService.recordBusinessChange(principal.accountId(), AuditLogAction.SIGN, AUDIT_ENTITY,
                prescription.getId(), context.visit().getSovaovien(), audit, now);

        // UC-90 steps 5-7: one more submission with the national system's answer.
        ClinicFacility facility = prescriptions.requireLinkCodes(context);
        short attemptNo = (short) (submissionRepository.findFirstByPrescriptionIdOrderByAttemptNoDesc(prescription.getId())
                .map(PrescriptionSubmission::getAttemptNo).orElse((short) 0) + 1);
        NationalPrescriptionSystem.Credentials credentials = new NationalPrescriptionSystem.Credentials(
                facility.getMaCskcb().strip(), facility.getMaLienthongDonthuoc().strip(),
                context.doctor().getMaLienthongBacsi().strip());
        Instant submittedAt = clock.instant();
        NationalPrescriptionSystem.Answer answer;
        try {
            answer = nationalSystem.submit(credentials, PrescriptionPayloadBuilder.withSignature(payload, signedData));
        } catch (AppException exception) {
            if (exception.getErrorCode() != ErrorCode.NATIONAL_PRESCRIPTION_SYSTEM_UNAVAILABLE) {
                throw exception;
            }
            // AF-02: SENT, MSG86; the signature and the attempt are kept (noRollbackFor).
            submissionRepository.save(new PrescriptionSubmission(ids.next(), clock.instant(), prescription.getId(),
                    attemptNo, principal.accountId(), submittedAt, PrescriptionSubmissionResult.NO_RESPONSE, null,
                    null));
            prescription.recordSubmission(PrescriptionSubmissionResult.NO_RESPONSE, clock.instant());
            prescriptionRepository.save(prescription);
            throw exception;
        }
        Instant respondedAt = clock.instant();
        PrescriptionSubmissionResult outcome = answer.accepted()
                ? PrescriptionSubmissionResult.ACCEPTED : PrescriptionSubmissionResult.REJECTED;
        submissionRepository.save(new PrescriptionSubmission(ids.next(), respondedAt, prescription.getId(), attemptNo,
                principal.accountId(), submittedAt, outcome, answer.accepted() ? null : errorsJson(answer.errors()),
                respondedAt));
        // P04, P05: ACCEPTED locks the prescription and, through UC-56, the diagnoses (BR-59-08).
        prescription.recordSubmission(outcome, respondedAt);
        Prescription saved = prescriptionRepository.saveAndFlush(prescription);
        return response(signing, prescriptions.describe(context, saved));
    }

    /** A final request answers the same way every time (idempotent). */
    private SigningRequestResponse finalResponse(Context context, SigningRequest signing) {
        if (signing.getStatus() == SigningRequestStatus.SIGNED) {
            return response(signing, prescriptions.describe(context, prescription(signing.getDocumentId())));
        }
        return response(signing, null);
    }

    /** BR-59-03, BR-59-05: every medicine is still an active ordinary one and long courses still qualify. */
    private void recheckLines(Prescription prescription, Conclusion conclusion) {
        List<PrescriptionDetail> details = prescription.getDetails().stream()
                .sorted(Comparator.comparing(PrescriptionDetail::getMachidinh))
                .toList();
        if (details.isEmpty()) {
            throw new AppException(ErrorCode.PRESCRIPTION_INVALID, Map.of("field", "lines", "reason", "REQUIRED"));
        }
        Map<UUID, MedicineCatalog> medicines = new LinkedHashMap<>();
        medicineRepository.findAllById(details.stream().map(PrescriptionDetail::getMedicineId).toList())
                .forEach(medicine -> medicines.put(medicine.getId(), medicine));
        boolean longTermAllowed = prescriptions.longTermAllowed(conclusion);
        for (int index = 0; index < details.size(); index++) {
            PrescriptionDetail detail = details.get(index);
            MedicineCatalog medicine = medicines.get(detail.getMedicineId());
            String reason = medicine == null || medicine.getStatus() != ActivationStatus.ACTIVE ? "INACTIVE"
                    : medicine.getNhomKiemSoat() != MedicineControlGroup.THUONG ? "NOT_ORDINARY" : null;
            if (reason != null) {
                Map<String, String> details2 = new LinkedHashMap<>();
                details2.put("line", String.valueOf(index));
                details2.put("reason", reason);
                details2.put("medicineName", detail.getTenthuocvattu());
                throw new AppException(ErrorCode.PRESCRIPTION_MEDICINE_UNAVAILABLE, details2);
            }
            if (detail.getSoNgayDung() > PrescriptionServiceImpl.MAX_DAYS_WITHOUT_LONG_TERM_DISEASE && !longTermAllowed) {
                throw new AppException(ErrorCode.PRESCRIPTION_LONG_TERM_NOT_ALLOWED,
                        Map.of("line", String.valueOf(index)));
            }
        }
    }

    /**
     * BR-59-07, EF-05: a child under 72 months on the prescribing date needs the weight and the accompanying person
     * (MSG107). Returns the accompanying person's text for the payload, null for an older patient.
     */
    private String requireChildData(Prescription prescription, Context context, BigDecimal weight) {
        PrescriptionResponse.Child child = prescriptions.childData(context, prescription);
        if (child == null) {
            return null;
        }
        if (weight == null) {
            throw new AppException(ErrorCode.PRESCRIPTION_CHILD_DATA_REQUIRED, Map.of("item", "WEIGHT"));
        }
        if (child.accompanyingPerson() == null) {
            throw new AppException(ErrorCode.PRESCRIPTION_CHILD_DATA_REQUIRED, Map.of("item", "ACCOMPANYING_PERSON"));
        }
        return PrescriptionPayloadBuilder.accompanyingPerson(context.patient());
    }

    /** BR-59-08: the conclusion's main diagnosis (thu_tu 1), then its secondary diagnoses in order. */
    private List<PrescriptionDiagnosis> diagnoses(Prescription prescription, Conclusion conclusion, Instant now) {
        List<PrescriptionDiagnosis> values = new ArrayList<>();
        values.add(new PrescriptionDiagnosis(
                ids.next(), now, prescription, conclusion.getMaicd(), conclusion.getTenicd(), (short) 1));
        List<ConclusionSecondaryDiagnosis> secondary = conclusion.getSecondaryDiagnoses().stream()
                .sorted(Comparator.comparingInt(ConclusionSecondaryDiagnosis::getThuTu))
                .toList();
        short order = 2;
        for (ConclusionSecondaryDiagnosis item : secondary) {
            if (item.getMaicd().equals(conclusion.getMaicd())) {
                continue;
            }
            values.add(new PrescriptionDiagnosis(ids.next(), now, prescription, item.getMaicd(), item.getTenicd(),
                    order++));
        }
        return values;
    }

    private SigningRequest lockedRequest(Prescription prescription, UUID requestId) {
        return signingRequestRepository.findByIdForUpdate(requestId)
                .filter(signing -> signing.getDocumentType() == DOCUMENT_TYPE)
                .filter(signing -> signing.getDocumentId().equals(prescription.getId()))
                .orElseThrow(() -> new AppException(ErrorCode.SIGNING_REQUEST_NOT_FOUND));
    }

    /** Best effort: the local state is authoritative, and the CA lets an unanswered request expire anyway. */
    private void cancelAtCa(SigningRequest signing) {
        try {
            signingGateway.cancel(signing.getProviderTransactionId());
        } catch (AppException exception) {
            log.info("Signing request {} could not be withdrawn at the CA ({})", signing.getId(),
                    exception.getErrorCode());
        }
    }

    private Prescription prescription(UUID prescriptionId) {
        return prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
    }

    private UUID visitId(Prescription prescription) {
        return medicalRecordRepository.findById(prescription.getMedicalRecordId())
                .map(MedicalRecord::getVisitId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
    }

    private String errorsJson(List<String> errors) {
        try {
            return json.writeValueAsString(errors.isEmpty() ? List.of("Không có mô tả lỗi.") : errors);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("The error list cannot be written", exception);
        }
    }

    private static SigningRequestResponse response(SigningRequest signing, PrescriptionResponse prescription) {
        return new SigningRequestResponse(signing.getId(), signing.getStatus().name(), signing.getDocumentVersion(),
                signing.getExpiresAt(), signing.getFailureReason(), prescription);
    }
}
