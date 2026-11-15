package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import clinic.backend.dto.request.SavePrescriptionRequest;
import clinic.backend.dto.response.Icd10OptionResponse;
import clinic.backend.dto.response.PrescribableMedicineResponse;
import clinic.backend.dto.response.PrescriptionResponse;
import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.Conclusion;
import clinic.backend.entity.ConclusionSecondaryDiagnosis;
import clinic.backend.entity.FollowUpAppointment;
import clinic.backend.entity.MedicalHistory;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.MedicineCatalog;
import clinic.backend.entity.Prescription;
import clinic.backend.entity.PrescriptionDetail;
import clinic.backend.entity.PrescriptionSubmission;
import clinic.backend.entity.RecordSignature;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.VitalSign;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.FollowUpAppointmentStatus;
import clinic.backend.enums.MedicalHistoryType;
import clinic.backend.enums.MedicineControlGroup;
import clinic.backend.enums.RecordSignatureDocumentType;
import clinic.backend.enums.SigningCertificateStatus;
import clinic.backend.enums.SigningRequestStatus;
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
import clinic.backend.service.PrescriptionService;
import clinic.backend.service.RemoteSigningGateway;
import clinic.backend.service.impl.ConcludingVisitGuard.Context;
import clinic.backend.utils.UuidV7Generator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-59 Prescribe Medication: one ordinary prescription (type c, BR-52) per visit record, stored in
 * {@code prescriptions} and {@code prescription_details} with the limits of BR-107 and I1-D45. Sending it to the
 * national prescription system (UC-90) is a separate step; a sent prescription is not edited here (BR-47).
 */
@Service
@RequiredArgsConstructor
public class PrescriptionServiceImpl implements PrescriptionService {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String AUDIT_ENTITY = "prescriptions";
    private static final String CODE_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper JSON = new ObjectMapper();

    // BR-107 / I1-D45 and the column sizes of V55.
    static final int MAX_LINES = 50; // ASSUMPTION: no documented limit; guards against runaway requests.
    static final BigDecimal MAX_QUANTITY = BigDecimal.valueOf(9_999);
    static final int MAX_TIMES_PER_DAY = 24;
    static final int MAX_DAYS = 90;
    static final int MAX_DAYS_WITHOUT_LONG_TERM_DISEASE = 30;
    static final int DOSE_MAX = 50;
    static final int USAGE_MAX = 255;
    static final int NOTES_MAX = 2000;
    static final int SEARCH_MIN_LENGTH = 2;
    static final int SEARCH_LIMIT = 20;
    /** TT 26/2025 Điều 6 khoản 4: a child under 72 months has the age in months, weight and accompanying person. */
    static final int CHILD_MONTHS = 72;

    private final ConcludingVisitGuard guard;
    private final ConclusionRepository conclusionRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final MedicineCatalogRepository medicineRepository;
    private final MedicalHistoryRepository historyRepository;
    private final LongTermPrescriptionDiseaseCodeRepository longTermCodeRepository;
    private final ClinicFacilityRepository clinicFacilityRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final VitalSignRepository vitalSignRepository;
    private final FollowUpAppointmentRepository followUpRepository;
    private final AuditLogService auditLogService;
    private final StaffSigningCertificateRepository signingCertificateRepository;
    private final SigningRequestRepository signingRequestRepository;
    private final PrescriptionSubmissionRepository submissionRepository;
    private final RecordSignatureRepository recordSignatureRepository;
    private final RemoteSigningGateway signingGateway;
    private final MedicalRecordRepository medicalRecordRepository;
    private final DigitalSignatureVerifier signatureVerifier;
    private final UuidV7Generator ids;
    private final Clock clock;

    @Override
    @Transactional
    public PrescriptionResponse get(StaffPrincipal principal, UUID visitId) {
        Context context = guard.require(principal, visitId);
        Prescription prescription = prescriptionRepository.findByMedicalRecordId(context.record().getId())
                .orElse(null);
        if (prescription != null) {
            auditLogService.recordBusinessChange(
                    principal.accountId(), AuditLogAction.VIEW_RECORD, AUDIT_ENTITY, prescription.getId(),
                    context.visit().getSovaovien(), Map.of("visitId", visitId), clock.instant());
        }
        Conclusion conclusion = conclusionRepository.findByMedicalRecordId(context.record().getId()).orElse(null);
        return toResponse(context, conclusion, prescription);
    }

    @Override
    @Transactional
    public PrescriptionResponse save(StaffPrincipal principal, UUID visitId, SavePrescriptionRequest request) {
        Context context = guard.require(principal, visitId);
        // UC-59 precondition: the conclusion of the visit has been saved.
        Conclusion conclusion = conclusionRepository.findByMedicalRecordId(context.record().getId())
                .orElseThrow(() -> new AppException(ErrorCode.PRESCRIPTION_CONCLUSION_REQUIRED));
        ClinicFacility facility = requireLinkCodes(context);
        Prescription prescription = prescriptionRepository.findByMedicalRecordId(context.record().getId())
                .orElse(null);
        if (prescription != null) {
            if (prescription.isSent()) {
                throw new AppException(ErrorCode.PRESCRIPTION_ALREADY_SENT);
            }
            // EF-09: a save of an existing prescription must name the version the Doctor loaded.
            if (request.version() == null || !request.version().equals(prescription.getVersion())) {
                throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
            }
        }

        String notes = optionalText(request.notes(), "notes", NOTES_MAX);
        String advice = optionalText(request.advice(), "advice", NOTES_MAX);
        List<SavePrescriptionRequest.Line> lines = request.lines() == null ? List.of() : request.lines();
        if (lines.isEmpty()) {
            throw invalid("lines", null, "REQUIRED"); // EF-01, MSG02: at least one medicine line
        }
        if (lines.size() > MAX_LINES) {
            throw invalid("lines", null, "TOO_MANY");
        }
        Map<UUID, MedicineCatalog> medicines = prescribableMedicines(lines);
        boolean longTermAllowed = longTermAllowed(conclusion);
        List<ValidLine> valid = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            valid.add(validLine(lines.get(index), index, medicines, longTermAllowed));
        }

        Instant now = clock.instant();
        if (prescription != null) {
            cancelPendingSignature(prescription, now);
        }
        Map<String, Object> oldValues = prescription == null ? Map.of() : auditValues(prescription);
        if (prescription == null) {
            prescription = new Prescription(
                    ids.next(), now, "DT-" + context.visit().getSovaovien(), newPrescriptionCode(facility),
                    context.record().getId());
        }
        prescription.record(principal.accountId(), conclusion.getChandoan(),
                (short) (context.visit().isInsuranceCovered() ? 1 : 2), notes, advice, now);
        // Remove the old lines first: their line codes are unique and are reused by the new lines.
        prescription.replaceDetails(List.of());
        prescription = prescriptionRepository.saveAndFlush(prescription);
        Prescription owner = prescription;
        LocalDate today = LocalDate.ofInstant(now, CLINIC_ZONE);
        List<PrescriptionDetail> details = new ArrayList<>();
        for (int index = 0; index < valid.size(); index++) {
            ValidLine line = valid.get(index);
            details.add(new PrescriptionDetail(
                    ids.next(), now, owner, "%s-%02d".formatted(owner.getSophieu(), index + 1), line.medicine(),
                    line.quantity(), line.dosePerTime(), line.timesPerDay(), line.days(), line.usage(), line.note(),
                    today));
        }
        prescription.replaceDetails(details);
        Prescription saved = prescriptionRepository.saveAndFlush(prescription);
        Map<String, Object> newValues = auditValues(saved);
        if (oldValues.isEmpty()) {
            auditLogService.recordEntityChange(
                    principal.accountId(), saved.getId(), AuditLogAction.CREATE, AUDIT_ENTITY, oldValues, newValues, now);
        } else {
            recordUpdate(principal, saved, oldValues, newValues, now);
        }
        return toResponse(context, conclusion, saved);
    }

    /** BR-59-14: an UPDATE row lists only the fields whose value changed; nothing is written when none did. */
    private void recordUpdate(
            StaffPrincipal principal, Prescription saved, Map<String, Object> before, Map<String, Object> after,
            Instant now) {
        Map<String, Object> oldChanged = new LinkedHashMap<>();
        Map<String, Object> newChanged = new LinkedHashMap<>();
        after.forEach((field, value) -> {
            Object old = before.get(field);
            if (!Objects.equals(old, value)) {
                oldChanged.put(field, old);
                newChanged.put(field, value);
            }
        });
        if (!newChanged.isEmpty()) {
            auditLogService.recordEntityChange(
                    principal.accountId(), saved.getId(), AuditLogAction.UPDATE, AUDIT_ENTITY, oldChanged, newChanged,
                    now);
        }
    }

    /**
     * BR-59-03, BR-110: Active ordinary medicines (every catalog medicine has a registration number, V55) whose name,
     * active ingredient, strength, internal code or registration number contains the keyword, ignoring case and
     * Vietnamese accents; at most {@link #SEARCH_LIMIT} ordered by name.
     */
    @Override
    @Transactional(readOnly = true)
    public List<PrescribableMedicineResponse> searchMedicines(String keyword) {
        String query = keyword == null ? "" : String.join(" ", keyword.strip().split("\\s+"));
        if (query.length() < SEARCH_MIN_LENGTH) {
            return List.of();
        }
        String pattern = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Specification<MedicineCatalog> prescribable = (root, ignored, cb) -> {
            Expression<String> term = folded(cb, cb.literal(pattern));
            return cb.and(
                    cb.equal(root.get("status"), ActivationStatus.ACTIVE),
                    cb.equal(root.get("nhomKiemSoat"), MedicineControlGroup.THUONG),
                    cb.or(
                            cb.like(folded(cb, root.get("tenthuocvattu")), term, '\\'),
                            cb.like(folded(cb, root.get("hoatchat")), term, '\\'),
                            cb.like(folded(cb, root.get("hamluong")), term, '\\'),
                            cb.like(folded(cb, root.get("mathuocvattuBv")), term, '\\'),
                            cb.like(folded(cb, root.get("soDangKy")), term, '\\')));
        };
        return medicineRepository
                .findAll(prescribable, PageRequest.of(0, SEARCH_LIMIT, Sort.by("tenthuocvattu")))
                .stream()
                .map(medicine -> new PrescribableMedicineResponse(
                        medicine.getId(), medicine.getMathuocvattuBv(), medicine.getSoDangKy(),
                        medicine.getTenthuocvattu(), medicine.getHoatchat(), medicine.getHamluong(),
                        medicine.getDuongdung(), medicine.getDonvitinh()))
                .toList();
    }

    /** Lower case without Vietnamese accents (unaccent extension, V11), as the Admin catalog search (I1-D46). */
    private static Expression<String> folded(CriteriaBuilder cb, Expression<String> value) {
        return cb.lower(cb.function("unaccent", String.class, value));
    }

    /**
     * EF-07 (BR-48, BR-49, BR-65): the Doctor's prescriber link code and the facility's code and prescription link
     * code are needed before a prescription is saved.
     */
    public ClinicFacility requireLinkCodes(Context context) {
        if (normalize(context.doctor().getMaLienthongBacsi()) == null) {
            throw new AppException(ErrorCode.PRESCRIBER_LINK_CODE_REQUIRED);
        }
        return clinicFacilityRepository.findCurrent()
                .filter(PrescriptionServiceImpl::hasFacilityCodes)
                .orElseThrow(() -> new AppException(ErrorCode.FACILITY_LINK_CODE_REQUIRED));
    }

    private static boolean hasFacilityCodes(ClinicFacility facility) {
        return normalize(facility.getMaCskcb()) != null && normalize(facility.getMaLienthongDonthuoc()) != null;
    }

    /** BR-33, BR-52: every medicine is an active ordinary medicine of the catalog, and appears once. */
    private Map<UUID, MedicineCatalog> prescribableMedicines(List<SavePrescriptionRequest.Line> lines) {
        Set<UUID> requested = new HashSet<>();
        for (int index = 0; index < lines.size(); index++) {
            UUID id = lines.get(index).medicineId();
            if (id == null) {
                throw invalid("medicine", index, "REQUIRED");
            }
            if (!requested.add(id)) {
                throw invalid("medicine", index, "DUPLICATE");
            }
        }
        Map<UUID, MedicineCatalog> found = medicineRepository.findAllById(requested).stream()
                .collect(Collectors.toMap(MedicineCatalog::getId, Function.identity()));
        for (int index = 0; index < lines.size(); index++) {
            MedicineCatalog medicine = found.get(lines.get(index).medicineId());
            // EF-03: MSG40 for a medicine that is gone or Inactive, MSG106 for one moved out of the ordinary group.
            String reason = medicine == null || medicine.getStatus() != ActivationStatus.ACTIVE ? "INACTIVE"
                    : medicine.getNhomKiemSoat() != MedicineControlGroup.THUONG ? "NOT_ORDINARY" : null;
            if (reason != null) {
                Map<String, String> details = new LinkedHashMap<>();
                details.put("line", String.valueOf(index));
                details.put("reason", reason);
                if (medicine != null) details.put("medicineName", medicine.getTenthuocvattu());
                throw new AppException(ErrorCode.PRESCRIPTION_MEDICINE_UNAVAILABLE, details);
            }
        }
        return found;
    }

    private static ValidLine validLine(
            SavePrescriptionRequest.Line line, int index, Map<UUID, MedicineCatalog> medicines,
            boolean longTermAllowed) {
        BigDecimal quantity = line.quantity();
        if (quantity == null) throw invalid("quantity", index, "REQUIRED");
        // BR-59-04: a whole number of the dispensing unit from 1 to 9,999 (TT 26/2025 Điều 6 khoản 7a); a split dose
        // such as "½ viên" belongs to the dose per time.
        if (quantity.signum() <= 0 || quantity.compareTo(MAX_QUANTITY) > 0 || quantity.stripTrailingZeros().scale() > 0) {
            throw invalid("quantity", index, "RANGE");
        }
        String dose = requiredText(line.dosePerTime(), "dosePerTime", index, DOSE_MAX);
        Short times = line.timesPerDay();
        if (times == null) throw invalid("timesPerDay", index, "REQUIRED");
        if (times < 1 || times > MAX_TIMES_PER_DAY) throw invalid("timesPerDay", index, "RANGE");
        Short days = line.days();
        if (days == null) throw invalid("days", index, "REQUIRED");
        if (days < 1 || days > MAX_DAYS) throw invalid("days", index, "RANGE");
        if (days > MAX_DAYS_WITHOUT_LONG_TERM_DISEASE && !longTermAllowed) {
            throw new AppException(ErrorCode.PRESCRIPTION_LONG_TERM_NOT_ALLOWED, Map.of("line", String.valueOf(index)));
        }
        String usage = requiredText(line.usage(), "usage", index, USAGE_MAX);
        return new ValidLine(medicines.get(line.medicineId()), quantity.setScale(0), dose, times, days, usage,
                normalize(line.note()));
    }

    /** TT 26/2025: more than 30 days needs a main or secondary diagnosis of the long-term list. */
    public boolean longTermAllowed(Conclusion conclusion) {
        if (conclusion == null) {
            return false;
        }
        List<String> codes = new ArrayList<>();
        codes.add(conclusion.getMaicd());
        conclusion.getSecondaryDiagnoses().forEach(item -> codes.add(item.getMaicd()));
        return longTermCodeRepository.existsByMaicdIn(codes);
    }

    /** Recorded drug allergies, shown in red on the form (BR-23); the prescription is not checked against them. */
    private List<String> allergies(Context context) {
        return historyRepository.findAllByPatientIdOrderByCreatedAtAsc(context.patient().getId()).stream()
                .filter(item -> item.getHistoryType() == MedicalHistoryType.DRUG_ALLERGY)
                // UC-39 AF-01: the "no known drug allergy" statement is not an allergy.
                .filter(item -> !item.isNoKnownDrugAllergy())
                .map(MedicalHistory::getThongtinNoidung)
                .map(PrescriptionServiceImpl::normalize)
                .filter(Objects::nonNull)
                .toList();
    }

    /** BR-52, V55: 5-digit facility code, 7 random characters 0-9 or a-z, "-c"; never changed afterwards. */
    private String newPrescriptionCode(ClinicFacility facility) {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder code = new StringBuilder(facility.getMaCskcb().strip());
            for (int i = 0; i < 7; i++) {
                code.append(CODE_CHARACTERS.charAt(RANDOM.nextInt(CODE_CHARACTERS.length())));
            }
            String candidate = code.append("-c").toString();
            if (!prescriptionRepository.existsByMadonthuoc(candidate)) {
                return candidate;
            }
        }
        throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
    }

    /** The prescription form after a change made by UC-90 (no VIEW_RECORD audit row). */
    public PrescriptionResponse describe(Context context, Prescription prescription) {
        Conclusion conclusion = conclusionRepository.findByMedicalRecordId(context.record().getId()).orElse(null);
        return toResponse(context, conclusion, prescription);
    }

    private PrescriptionResponse toResponse(Context context, Conclusion conclusion, Prescription prescription) {
        List<Icd10OptionResponse> diagnoses = new ArrayList<>();
        if (conclusion != null) {
            diagnoses.add(new Icd10OptionResponse(conclusion.getMaicd(), conclusion.getTenicd()));
            conclusion.getSecondaryDiagnoses().stream()
                    .sorted(Comparator.comparingInt(ConclusionSecondaryDiagnosis::getThuTu))
                    .forEach(item -> diagnoses.add(new Icd10OptionResponse(item.getMaicd(), item.getTenicd())));
        }
        List<PrescriptionResponse.Line> lines = prescription == null ? List.of() : prescription.getDetails().stream()
                .sorted(Comparator.comparing(PrescriptionDetail::getMachidinh))
                .map(detail -> new PrescriptionResponse.Line(
                        detail.getMedicineId(), detail.getMathuocvattuBv(), detail.getTenthuocvattu(),
                        detail.getHoatchat(), detail.getHamluong(), detail.getDuongdung(), detail.getDonvitinh(),
                        detail.getSoluongthuocvattu(), detail.getLieuMoiLan(), detail.getSoLanNgay(),
                        detail.getSoNgayDung(), detail.getCachdung(), detail.getYlenhthuocvattuGhichu()))
                .toList();
        String doctorName = prescription == null || prescription.getDoctorAccountId() == null ? null
                : staffAccountRepository.findById(prescription.getDoctorAccountId())
                        .map(StaffAccount::getFullName).orElse(null);
        // UC-59 step 2: the scheduled follow-up appointment of the visit, copied onto the prescription when sent.
        LocalDate followUpDate = followUpRepository.findByMedicalRecordId(context.record().getId())
                .filter(appointment -> appointment.getStatus() == FollowUpAppointmentStatus.SCHEDULED)
                .map(FollowUpAppointment::getFollowUpDate)
                .orElse(null);
        boolean facilityLinkCodeMissing = clinicFacilityRepository.findCurrent()
                .filter(PrescriptionServiceImpl::hasFacilityCodes).isEmpty();
        return new PrescriptionResponse(
                context.visit().getId(),
                prescription == null ? null : prescription.getId(),
                prescription == null ? null : prescription.getVersion(),
                prescription == null ? null : prescription.getMadonthuoc(),
                prescription == null ? null : prescription.getSophieu(),
                conclusion != null,
                conclusion == null ? null : conclusion.getChandoan(),
                diagnoses,
                allergies(context),
                longTermAllowed(conclusion),
                lines,
                prescription == null ? null : prescription.getLuuY(),
                prescription == null ? null : prescription.getLoiDan(),
                prescription != null && prescription.isSent(),
                doctorName,
                prescription == null ? null : prescription.getPrescribedAt(),
                normalize(context.doctor().getMaLienthongBacsi()) == null,
                facilityLinkCodeMissing,
                followUpDate,
                childData(context, prescription),
                prescription == null ? null : prescription.getLienthongStatus().name(),
                signingCertificateRepository.findByStaffAccountIdAndStatus(
                        context.doctor().getId(), SigningCertificateStatus.ACTIVE).isPresent(),
                prescription == null ? null : lastSubmission(prescription));
    }

    @Override
    @Transactional
    public void delete(StaffPrincipal principal, UUID prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        Context context = guard.require(principal, visitId(prescription));
        // P03: never sent. A send always leaves a submission and a signature row, which are kept as evidence.
        if (!prescription.isDeletable()
                || submissionRepository.existsByPrescriptionId(prescription.getId())
                || recordSignatureRepository.existsByDocumentTypeAndDocumentId(
                        RecordSignatureDocumentType.DON_THUOC, prescription.getId())) {
            throw new AppException(ErrorCode.PRESCRIPTION_ALREADY_SENT);
        }
        Instant now = clock.instant();
        cancelPendingSignature(prescription, now);
        // BR-59-14: the removed values, so the prescription could be restored from the audit log.
        Map<String, Object> removed = new LinkedHashMap<>();
        removed.put("prescriptionCode", prescription.getMadonthuoc());
        removed.put("visitNumber", context.visit().getSovaovien());
        removed.putAll(auditValues(prescription));
        Map<String, Object> after = new LinkedHashMap<>();
        removed.keySet().forEach(field -> after.put(field, null));
        prescriptionRepository.delete(prescription);
        prescriptionRepository.flush();
        auditLogService.recordEntityChange(
                principal.accountId(), prescription.getId(), AuditLogAction.DELETE, AUDIT_ENTITY, removed, after, now);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PrescriptionResponse.Submission> submissions(StaffPrincipal principal, UUID prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        guard.require(principal, visitId(prescription));
        return submissionViews(prescription, submissionRepository
                .findAllByPrescriptionIdOrderByAttemptNoDesc(prescription.getId()));
    }

    /** The visit of the prescription's medical record (UC-59 AF-03, UC-91, UC-60). */
    UUID visitId(Prescription prescription) {
        return medicalRecordRepository.findById(prescription.getMedicalRecordId())
                .map(MedicalRecord::getVisitId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
    }

    /** UC-91: the latest send, with the signature sent with it. */
    PrescriptionResponse.Submission lastSubmission(Prescription prescription) {
        return submissionRepository.findFirstByPrescriptionIdOrderByAttemptNoDesc(prescription.getId())
                .map(submission -> submissionViews(prescription, List.of(submission)).getFirst())
                .orElse(null);
    }

    /**
     * UC-91 step 2: each send with the signature made for it. The signature row of a send is written just before its
     * submission (UC-90 step 5), so a send carries the latest signature created no later than it was submitted.
     */
    private List<PrescriptionResponse.Submission> submissionViews(
            Prescription prescription, List<PrescriptionSubmission> submissions) {
        if (submissions.isEmpty()) {
            return List.of();
        }
        List<RecordSignature> signatures = recordSignatureRepository.findAllByDocumentTypeAndDocumentIdOrderByCreatedAtAsc(
                RecordSignatureDocumentType.DON_THUOC, prescription.getId());
        Map<UUID, String> senders = staffAccountRepository.findAllById(submissions.stream()
                        .map(PrescriptionSubmission::getSubmittedByAccountId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(StaffAccount::getId, StaffAccount::getFullName));
        return submissions.stream().map(submission -> {
            RecordSignature signature = null;
            for (RecordSignature candidate : signatures) {
                if (!candidate.getCreatedAt().isAfter(submission.getSubmittedAt())) {
                    signature = candidate;
                }
            }
            // BR-59-16: the status is the stored signature verified again, not the result kept at signing time.
            String failure = signature == null ? null : signatureVerifier.reverify(
                    signature.getSignatureValue(), signature.getSignedPayload(), signature.getCertificateSerial(),
                    signature.getCertificateIssuer(), signature.getVerificationEvidence());
            return new PrescriptionResponse.Submission(
                    submission.getAttemptNo(), submission.getResult().name(), errors(submission.getErrors()),
                    submission.getSubmittedAt(), submission.getRespondedAt(),
                    signature == null ? null : signature.getSignedAt(),
                    signature == null ? null : signature.getSignerName(),
                    signature == null ? null : signature.getCertificateSerial(),
                    signature == null ? null : signature.getCertificateIssuer(),
                    senders.get(submission.getSubmittedByAccountId()),
                    signature == null ? null : failure == null,
                    failure);
        }).toList();
    }

    private List<String> errors(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return List.of(JSON.readValue(json, String[].class));
        } catch (JsonProcessingException exception) {
            return List.of(json);
        }
    }

    /**
     * K06: a save creates a new version, so a pending signing request of the old version is cancelled. Withdrawing
     * it at the CA is best effort; the local state is authoritative.
     */
    private void cancelPendingSignature(Prescription prescription, Instant now) {
        signingRequestRepository.findByDocumentTypeAndDocumentIdAndStatus(
                        RecordSignatureDocumentType.DON_THUOC, prescription.getId(), SigningRequestStatus.PENDING)
                .ifPresent(pending -> {
                    try {
                        signingGateway.cancel(pending.getProviderTransactionId());
                    } catch (AppException exception) {
                        // The CA lets an unanswered request expire.
                    }
                    pending.markCancelled(now);
                    signingRequestRepository.save(pending);
                });
    }

    /**
     * BR-59-07: for a patient under 72 months on the prescribing date (today until the first save), the age in
     * months, the latest weight of this visit and the relative as the accompanying person; null otherwise. Missing
     * values stay null; sending (UC-90) requires them.
     */
    public PrescriptionResponse.Child childData(Context context, Prescription prescription) {
        LocalDate birthDate = context.patient().getNgaysinh();
        LocalDate prescribedOn = LocalDate.ofInstant(
                prescription == null ? clock.instant() : prescription.getPrescribedAt(), CLINIC_ZONE);
        if (birthDate == null) {
            return null;
        }
        long months = Period.between(birthDate, prescribedOn).toTotalMonths();
        if (months >= CHILD_MONTHS) {
            return null;
        }
        BigDecimal weight = vitalSignRepository
                .findFirstByMedicalRecordIdAndCannangIsNotNullOrderByThoidiemDesc(context.record().getId())
                .map(VitalSign::getCannang)
                .orElse(null);
        return new PrescriptionResponse.Child(
                (int) Math.max(months, 0), weight, normalize(context.patient().getHotennguoithan()));
    }

    private static Map<String, Object> auditValues(Prescription prescription) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("notes", prescription.getLuuY());
        values.put("advice", prescription.getLoiDan());
        values.put("lines", prescription.getDetails().stream()
                .sorted(Comparator.comparing(PrescriptionDetail::getMachidinh))
                .map(detail -> "%s x%s; %s, %d lần/ngày, %d ngày; %s".formatted(
                        // The stored NUMERIC(10,2) reads back as 21.00: compare the value, not its scale.
                        detail.getTenthuocvattu(), detail.getSoluongthuocvattu().stripTrailingZeros().toPlainString(),
                        detail.getLieuMoiLan(), detail.getSoLanNgay(), detail.getSoNgayDung(), detail.getCachdung()))
                .toList());
        return values;
    }

    private static String requiredText(String value, String field, int line, int max) {
        String normalized = normalize(value);
        if (normalized == null) throw invalid(field, line, "REQUIRED");
        if (normalized.length() > max) throw invalid(field, line, "TOO_LONG");
        return normalized;
    }

    private static String optionalText(String value, String field, int max) {
        String normalized = normalize(value);
        if (normalized != null && normalized.length() > max) throw invalid(field, null, "TOO_LONG");
        return normalized;
    }

    /** MSG02 / MSG05 / MSG08 for one field; {@code line} is the 0-based medicine line, if any. */
    private static AppException invalid(String field, Integer line, String reason) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("field", field);
        if (line != null) details.put("line", String.valueOf(line));
        details.put("reason", reason);
        return new AppException(ErrorCode.PRESCRIPTION_INVALID, details);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private record ValidLine(
            MedicineCatalog medicine, BigDecimal quantity, String dosePerTime, short timesPerDay, short days,
            String usage, String note) {
    }
}
