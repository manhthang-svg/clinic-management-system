package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import clinic.backend.dto.request.SubmitPrescriptionRequest;
import clinic.backend.dto.response.PrescriptionResponse;
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
import clinic.backend.entity.VitalSign;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.FollowUpAppointmentStatus;
import clinic.backend.enums.MedicineControlGroup;
import clinic.backend.enums.PrescriptionSubmissionResult;
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
import clinic.backend.service.PrescriptionSubmissionService;
import clinic.backend.service.impl.ConcludingVisitGuard.Context;
import clinic.backend.utils.UuidV7Generator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-90 Submit e-Prescription (docs/uc-59-e-prescription.md BR-59-07 to BR-59-12, BR-59-17): the prescription is
 * checked again, the Doctor's national sign-in is reused or made with the password the Doctor types (QĐ 808 VI), and
 * the prescription is sent without a digital signature (out of scope, BR-47).
 *
 * <p>Nothing is written before the sign-in succeeds. The send runs in one transaction that keeps business rejections
 * ({@code noRollbackFor}), so a send without an answer keeps its submission row (AF-02).
 */
@Service
@RequiredArgsConstructor
public class PrescriptionSubmissionServiceImpl implements PrescriptionSubmissionService {

    private final ConcludingVisitGuard guard;
    private final PrescriptionServiceImpl prescriptions;
    private final PrescriptionRepository prescriptionRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final ConclusionRepository conclusionRepository;
    private final MedicineCatalogRepository medicineRepository;
    private final VitalSignRepository vitalSignRepository;
    private final FollowUpAppointmentRepository followUpRepository;
    private final PrescriptionSubmissionRepository submissionRepository;
    private final NationalPrescriptionSystem nationalSystem;
    private final NationalPrescriptionSessions sessions;
    private final UuidV7Generator ids;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();

    @Override
    @Transactional(noRollbackFor = AppException.class)
    public PrescriptionResponse submit(
            StaffPrincipal principal, UUID prescriptionId, SubmitPrescriptionRequest request) {
        char[] password = request == null ? null : request.password();
        try {
            return send(principal, prescriptionId, request, password);
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    private PrescriptionResponse send(
            StaffPrincipal principal, UUID prescriptionId, SubmitPrescriptionRequest request, char[] password) {
        Prescription prescription = prescription(prescriptionId);
        Context context = guard.require(principal, visitId(prescription));
        if (!prescription.canBeSubmitted()) {
            throw new AppException(ErrorCode.PRESCRIPTION_ALREADY_SENT);
        }
        if (request == null || request.version() == null || !request.version().equals(prescription.getVersion())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }

        // Step 3: the checks of UC-59 against the conclusion and vital signs as they are now.
        Conclusion conclusion = conclusionRepository.findByMedicalRecordId(context.record().getId())
                .orElseThrow(() -> new AppException(ErrorCode.PRESCRIPTION_CONCLUSION_REQUIRED));
        ClinicFacility facility = prescriptions.requireLinkCodes(context);
        // BR-59-17 (QĐ 808 II.2): only a Doctor the facility added can sign in to send.
        if (context.doctor().getLienthongAddedAt() == null) {
            throw new AppException(ErrorCode.PRESCRIBER_NOT_ADDED_TO_FACILITY);
        }
        recheckLines(prescription, conclusion);
        BigDecimal weight = vitalSignRepository
                .findFirstByMedicalRecordIdAndCannangIsNotNullOrderByThoidiemDesc(context.record().getId())
                .map(VitalSign::getCannang)
                .orElse(null);
        String accompanyingPerson = requireChildData(prescription, context, weight);
        // BR-59-07: dia_chi is at most 500 characters (QĐ 808 VII.3 row 11); it is never cut.
        if (PrescriptionPayloadBuilder.address(context.patient()).length() > PrescriptionPayloadBuilder.TEXT_MAX) {
            throw new AppException(ErrorCode.PRESCRIPTION_INVALID, Map.of("field", "address", "reason", "TOO_LONG"));
        }

        // BR-59-17: the Doctor's token, or a sign-in with the password the Doctor typed (cách B).
        String facilityLinkCode = facility.getMaLienthongDonthuoc().strip();
        String prescriberLinkCode = context.doctor().getMaLienthongBacsi().strip();
        String token = sessions.prescriberToken(facilityLinkCode, prescriberLinkCode, password);

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
        byte[] payload = PrescriptionPayloadBuilder.payload(
                prescription, context.visit(), context.patient(), conclusion, weight, accompanyingPerson,
                followUpDate);

        // Steps 5-7: one more submission with the national system's answer.
        short attemptNo = (short) (submissionRepository.findFirstByPrescriptionIdOrderByAttemptNoDesc(prescription.getId())
                .map(PrescriptionSubmission::getAttemptNo).orElse((short) 0) + 1);
        Instant submittedAt = clock.instant();
        NationalPrescriptionSystem.Answer answer;
        try {
            answer = nationalSystem.submit(token, payload);
        } catch (AppException exception) {
            if (exception.getErrorCode() != ErrorCode.NATIONAL_PRESCRIPTION_SYSTEM_UNAVAILABLE) {
                throw exception;
            }
            // AF-02: SENT, MSG86; the attempt is kept (noRollbackFor).
            submissionRepository.save(new PrescriptionSubmission(ids.next(), clock.instant(), prescription.getId(),
                    attemptNo, principal.accountId(), submittedAt, PrescriptionSubmissionResult.NO_RESPONSE, null,
                    null));
            prescription.recordSubmission(PrescriptionSubmissionResult.NO_RESPONSE, clock.instant());
            prescriptionRepository.save(prescription);
            throw exception;
        }
        if (answer.tokenInvalid()) {
            // The token ended early on the national side: nothing was received, so sign in again.
            sessions.forgetPrescriber(facilityLinkCode, prescriberLinkCode);
            throw new AppException(ErrorCode.NATIONAL_PRESCRIPTION_SIGN_IN_REQUIRED);
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
        return prescriptions.describe(context, saved);
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
                Map<String, String> unavailable = new LinkedHashMap<>();
                unavailable.put("line", String.valueOf(index));
                unavailable.put("reason", reason);
                unavailable.put("medicineName", detail.getTenthuocvattu());
                throw new AppException(ErrorCode.PRESCRIPTION_MEDICINE_UNAVAILABLE, unavailable);
            }
            if (detail.getSoNgayDung() > PrescriptionServiceImpl.MAX_DAYS_WITHOUT_LONG_TERM_DISEASE && !longTermAllowed) {
                throw new AppException(ErrorCode.PRESCRIPTION_LONG_TERM_NOT_ALLOWED,
                        Map.of("line", String.valueOf(index)));
            }
            // BR-59-11: cach_dung (route, dose and usage) is at most 500 characters; it is never cut.
            if (PrescriptionPayloadBuilder.usage(detail).length() > PrescriptionPayloadBuilder.TEXT_MAX) {
                throw new AppException(ErrorCode.PRESCRIPTION_INVALID,
                        Map.of("field", "usage", "line", String.valueOf(index), "reason", "TOO_LONG"));
            }
        }
    }

    /**
     * BR-59-07, EF-05: a child under 72 months on the prescribing date needs the weight and the accompanying person
     * (MSG107; TT 26/2025 Điều 6 khoản 4, QĐ 808 VII.3 row 10). Returns the accompanying person's text for the
     * payload, null for an older patient.
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
        String accompanyingPerson = PrescriptionPayloadBuilder.accompanyingPerson(context.patient());
        if (accompanyingPerson.length() > PrescriptionPayloadBuilder.TEXT_MAX) {
            throw new AppException(ErrorCode.PRESCRIPTION_INVALID,
                    Map.of("field", "accompanyingPerson", "reason", "TOO_LONG"));
        }
        return accompanyingPerson;
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
}
