package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import clinic.backend.dto.request.SavePrescriptionRequest;
import clinic.backend.dto.response.Icd10OptionResponse;
import clinic.backend.dto.response.PrescribableMedicineResponse;
import clinic.backend.dto.response.PrescriptionResponse;
import clinic.backend.entity.Conclusion;
import clinic.backend.entity.ConclusionSecondaryDiagnosis;
import clinic.backend.entity.MedicalHistory;
import clinic.backend.entity.MedicineCatalog;
import clinic.backend.entity.Prescription;
import clinic.backend.entity.PrescriptionDetail;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalHistoryType;
import clinic.backend.enums.MedicineControlGroup;
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
import clinic.backend.service.PrescriptionService;
import clinic.backend.service.impl.ConcludingVisitGuard.Context;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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

    // BR-107 / I1-D45 and the column sizes of V55.
    static final int MAX_LINES = 50; // ASSUMPTION: no documented limit; guards against runaway requests.
    static final BigDecimal MAX_QUANTITY = BigDecimal.valueOf(9_999);
    static final int MAX_TIMES_PER_DAY = 24;
    static final int MAX_DAYS = 90;
    static final int MAX_DAYS_WITHOUT_LONG_TERM_DISEASE = 30;
    static final int DOSE_MAX = 50;
    static final int USAGE_MAX = 255;
    static final int NOTES_MAX = 2000;

    private final ConcludingVisitGuard guard;
    private final ConclusionRepository conclusionRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final MedicineCatalogRepository medicineRepository;
    private final MedicalHistoryRepository historyRepository;
    private final LongTermPrescriptionDiseaseCodeRepository longTermCodeRepository;
    private final ClinicFacilityRepository clinicFacilityRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final AuditLogService auditLogService;
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
        Prescription prescription = prescriptionRepository.findByMedicalRecordId(context.record().getId())
                .orElse(null);
        if (prescription != null) {
            if (prescription.isSent()) {
                throw new AppException(ErrorCode.PRESCRIPTION_ALREADY_SENT);
            }
            if (request.version() != null && !request.version().equals(prescription.getVersion())) {
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

        // AF-01 (MSG26): a medicine that matches a recorded drug allergy needs the Doctor's confirmation.
        List<String> matches = allergyMatches(context, valid);
        if (!matches.isEmpty() && !request.confirmAllergy()) {
            throw new AppException(ErrorCode.PRESCRIPTION_ALLERGY_WARNING,
                    Map.of("allergies", String.join(", ", matches)));
        }

        Instant now = clock.instant();
        Map<String, Object> oldValues = prescription == null ? Map.of() : auditValues(prescription);
        if (prescription == null) {
            prescription = new Prescription(
                    ids.next(), now, "DT-" + context.visit().getSovaovien(), newPrescriptionCode(),
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
        if (!matches.isEmpty()) {
            newValues.put("allergyConfirmed", String.join(", ", matches));
        }
        auditLogService.recordEntityChange(
                principal.accountId(), saved.getId(), oldValues.isEmpty() ? AuditLogAction.CREATE : AuditLogAction.UPDATE,
                AUDIT_ENTITY, oldValues, newValues, now);
        return toResponse(context, conclusion, saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PrescribableMedicineResponse> searchMedicines(String keyword, int limit) {
        String query = keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT);
        if (query.length() < 2) {
            return List.of();
        }
        return medicineRepository
                .searchPrescribable("%" + query + "%", PageRequest.of(0, Math.min(Math.max(limit, 1), 30)))
                .stream()
                .map(medicine -> new PrescribableMedicineResponse(
                        medicine.getId(), medicine.getMathuocvattuBv(), medicine.getTenthuocvattu(),
                        medicine.getHoatchat(), medicine.getHamluong(), medicine.getDuongdung(),
                        medicine.getDonvitinh()))
                .toList();
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
            if (medicine == null || medicine.getStatus() != ActivationStatus.ACTIVE
                    || medicine.getNhomKiemSoat() != MedicineControlGroup.THUONG) {
                Map<String, String> details = new LinkedHashMap<>();
                details.put("line", String.valueOf(index));
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
        if (quantity.signum() <= 0 || quantity.compareTo(MAX_QUANTITY) > 0 || quantity.stripTrailingZeros().scale() > 0) {
            throw invalid("quantity", index, "RANGE"); // whole number 1-9,999
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
        return new ValidLine(medicines.get(line.medicineId()), quantity.stripTrailingZeros(), dose, times, days, usage,
                normalize(line.note()));
    }

    /** TT 26/2025: more than 30 days needs a main or secondary diagnosis of the long-term list. */
    private boolean longTermAllowed(Conclusion conclusion) {
        if (conclusion == null) {
            return false;
        }
        List<String> codes = new ArrayList<>();
        codes.add(conclusion.getMaicd());
        conclusion.getSecondaryDiagnoses().forEach(item -> codes.add(item.getMaicd()));
        return longTermCodeRepository.existsByMaicdIn(codes);
    }

    /** Recorded drug allergies whose text matches a prescribed medicine's name or active ingredient. */
    private List<String> allergyMatches(Context context, List<ValidLine> lines) {
        List<String> allergies = allergies(context);
        List<String> matches = new ArrayList<>();
        for (String allergy : allergies) {
            String term = allergy.toLowerCase(Locale.ROOT);
            if (term.length() < 3) continue;
            for (ValidLine line : lines) {
                String name = line.medicine().getTenthuocvattu().toLowerCase(Locale.ROOT);
                String ingredient = line.medicine().getHoatchat().toLowerCase(Locale.ROOT);
                if (name.contains(term) || ingredient.contains(term) || term.contains(ingredient)) {
                    matches.add(allergy + " (" + line.medicine().getTenthuocvattu() + ")");
                }
            }
        }
        return matches;
    }

    private List<String> allergies(Context context) {
        return historyRepository.findAllByPatientIdOrderByCreatedAtAsc(context.patient().getId()).stream()
                .filter(item -> item.getHistoryType() == MedicalHistoryType.DRUG_ALLERGY)
                .map(MedicalHistory::getThongtinNoidung)
                .map(PrescriptionServiceImpl::normalize)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** BR-52, V55: 5-digit facility code, 7 random characters 0-9 or a-z, "-c"; never changed afterwards. */
    private String newPrescriptionCode() {
        String facility = clinicFacilityRepository.findCurrent()
                .map(value -> value.getMaCskcb())
                .orElseThrow(() -> new AppException(ErrorCode.CLINIC_FACILITY_NOT_CONFIGURED));
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder code = new StringBuilder(facility);
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
                prescription == null ? null : prescription.getPrescribedAt());
    }

    private static Map<String, Object> auditValues(Prescription prescription) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("notes", prescription.getLuuY());
        values.put("advice", prescription.getLoiDan());
        values.put("lines", prescription.getDetails().stream()
                .sorted(Comparator.comparing(PrescriptionDetail::getMachidinh))
                .map(detail -> "%s x%s; %s, %d lần/ngày, %d ngày; %s".formatted(
                        detail.getTenthuocvattu(), detail.getSoluongthuocvattu().toPlainString(),
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
