package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import clinic.backend.dto.request.SaveConclusionRequest;
import clinic.backend.dto.response.ConclusionResponse;
import clinic.backend.dto.response.Icd10OptionResponse;
import clinic.backend.entity.ClinicalExamination;
import clinic.backend.entity.ClinicalExaminationDiagnosis;
import clinic.backend.entity.Conclusion;
import clinic.backend.entity.ConclusionSecondaryDiagnosis;
import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.Icd10;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.PrescriptionLienthongStatus;
import clinic.backend.enums.Role;
import clinic.backend.enums.RoomType;
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
import clinic.backend.service.ConclusionService;
import clinic.backend.service.DutyShiftConfigService;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-56 Conclude Examination, stored in {@code conclusions} and {@code conclusion_secondary_diagnoses}.
 *
 * <p>The visit must be in the Doctor's room and either still in examination with nothing waiting for paraclinical
 * services (no order, or every order cancelled) or back from the paraclinical rooms (Waiting for Conclusion, every
 * order has a result: BR-30, BR-41).
 */
@Service
@RequiredArgsConstructor
public class ConclusionServiceImpl implements ConclusionService {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String AUDIT_ENTITY = "conclusions";
    private static final Set<VisitStatus> CONCLUDABLE =
            Set.of(VisitStatus.IN_EXAMINATION, VisitStatus.WAITING_CONCLUSION);
    private static final Set<PrescriptionLienthongStatus> SENT =
            Set.of(PrescriptionLienthongStatus.SENT, PrescriptionLienthongStatus.ACCEPTED);

    // ASSUMPTION (BR-106 not in docs/): at most 12 secondary diagnoses. The texts have no length limit (TEXT).
    static final int SECONDARY_MAX = 12;

    private final VisitRepository visitRepository;
    private final PatientRepository patientRepository;
    private final RoomRepository roomRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final StaffRoomAssignmentRepository assignmentRepository;
    private final DutyScheduleRepository dutyScheduleRepository;
    private final DutyShiftConfigService dutyShiftConfigService;
    private final MedicalRecordRepository medicalRecordRepository;
    private final ClinicalExaminationRepository examinationRepository;
    private final ConclusionRepository conclusionRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final Icd10Repository icd10Repository;
    private final AuditLogService auditLogService;
    private final UuidV7Generator ids;
    private final Clock clock;

    @Override
    @Transactional
    public ConclusionResponse get(StaffPrincipal principal, UUID visitId) {
        Context context = requireConcludableVisit(principal, visitId);
        Conclusion conclusion = conclusionRepository.findByMedicalRecordId(context.record().getId()).orElse(null);
        if (conclusion != null) {
            auditLogService.recordBusinessChange(
                    principal.accountId(), AuditLogAction.VIEW_RECORD, AUDIT_ENTITY, conclusion.getId(),
                    context.visit().getSovaovien(), Map.of("visitId", visitId), clock.instant());
        }
        return toResponse(context, conclusion);
    }

    @Override
    @Transactional
    public ConclusionResponse save(StaffPrincipal principal, UUID visitId, SaveConclusionRequest request) {
        Context context = requireConcludableVisit(principal, visitId);
        Instant now = clock.instant();
        Conclusion conclusion = conclusionRepository.findByMedicalRecordId(context.record().getId())
                .orElse(null);
        if (conclusion != null && request.version() != null && !request.version().equals(conclusion.getVersion())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }

        String diagnosis = text(request.diagnosis(), "diagnosis", true);
        String treatmentPlan = text(request.treatmentPlan(), "treatmentPlan", true);
        String advice = text(request.advice(), "advice", true);
        String summary = text(request.summary(), "summary", false);
        String note = text(request.note(), "note", false);
        Short result = request.treatmentResult();
        if (result == null || result < 1 || result > 4) {
            throw invalid("treatmentResult", "REQUIRED");
        }

        LocalDate day = LocalDate.ofInstant(now, CLINIC_ZONE);
        Icd10 main = mainDiagnosis(request, context.patient(), day);
        List<Icd10> secondary = secondaryDiagnoses(request.secondaryIcd10Codes(), main, context.patient(), day);
        if (conclusion != null && diagnosesLocked(context.record()) && diagnosesChanged(conclusion, main, secondary)) {
            throw new AppException(ErrorCode.CONCLUSION_DIAGNOSES_LOCKED);
        }

        Map<String, Object> oldValues = conclusion == null ? Map.of() : auditValues(conclusion);
        if (conclusion == null) {
            conclusion = new Conclusion(ids.next(), now, context.record().getId());
        }
        conclusion.record(principal.accountId(), diagnosis, main.getMaicd(), main.getTenicd(), treatmentPlan,
                result, advice, summary, note, now);
        // Remove the old codes first so a code kept in the list does not hit the per-conclusion unique constraint.
        conclusion.replaceSecondaryDiagnoses(List.of());
        conclusion = conclusionRepository.saveAndFlush(conclusion);
        Conclusion owner = conclusion;
        conclusion.replaceSecondaryDiagnoses(java.util.stream.IntStream.range(0, secondary.size())
                .mapToObj(index -> new ConclusionSecondaryDiagnosis(
                        ids.next(), now, owner, secondary.get(index).getMaicd(), secondary.get(index).getTenicd(),
                        (short) (index + 1)))
                .toList());
        Conclusion saved = conclusionRepository.saveAndFlush(conclusion);
        auditLogService.recordEntityChange(
                principal.accountId(), saved.getId(), oldValues.isEmpty() ? AuditLogAction.CREATE : AuditLogAction.UPDATE,
                AUDIT_ENTITY, oldValues, auditValues(saved), now);
        return toResponse(context, saved);
    }

    /** BR-31, BR-46, BR-51: one main code, usable today, detailed, fit for the patient and allowed as main. */
    private Icd10 mainDiagnosis(SaveConclusionRequest request, Patient patient, LocalDate day) {
        String code = normalize(request.mainIcd10Code());
        if (code == null) {
            throw new AppException(ErrorCode.CONCLUSION_MAIN_DIAGNOSIS_REQUIRED);
        }
        Icd10 main = resolveIcd10(code, patient, day);
        if (main.isKhongLaBenhChinh() || main.isChiNguyenNhanTuVong()) {
            throw new AppException(ErrorCode.CONCLUSION_MAIN_DIAGNOSIS_NOT_ALLOWED,
                    Map.of("icd10Code", main.getMaicd()));
        }
        if (main.isKhongKhuyenKhichBenhChinh() && !request.confirmDiscouragedMain()) {
            throw new AppException(ErrorCode.CONCLUSION_MAIN_DIAGNOSIS_DISCOURAGED,
                    Map.of("icd10Code", main.getMaicd()));
        }
        return main;
    }

    private List<Icd10> secondaryDiagnoses(List<String> codes, Icd10 main, Patient patient, LocalDate day) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String code : codes) {
            String normalized = normalize(code);
            if (normalized != null) unique.add(normalized.toUpperCase(Locale.ROOT));
        }
        if (unique.contains(main.getMaicd())) {
            // The main diagnosis is listed again as a secondary one (EF-05, MSG64).
            throw new AppException(ErrorCode.CONCLUSION_MAIN_DIAGNOSIS_REQUIRED);
        }
        if (unique.size() > SECONDARY_MAX) {
            throw invalid("secondaryIcd10Codes", "TOO_MANY");
        }
        List<Icd10> result = new ArrayList<>();
        for (String code : unique) {
            result.add(resolveIcd10(code, patient, day));
        }
        return result;
    }

    /** Same checks as the preliminary diagnosis (UC-47): usable today, most detailed level, fits the sex. */
    private Icd10 resolveIcd10(String code, Patient patient, LocalDate day) {
        Icd10 icd10 = icd10Repository.findById(code.toUpperCase(Locale.ROOT))
                .filter(item -> item.isUsableOn(day))
                .orElseThrow(() -> new AppException(ErrorCode.ICD10_INVALID, Map.of("icd10Code", code)));
        if (icd10.isCoMaChiTietHon()) {
            throw new AppException(ErrorCode.ICD10_NOT_DETAILED, Map.of("icd10Code", code));
        }
        if (!icd10.appliesToSex(patient.getGioitinh())) {
            throw new AppException(ErrorCode.ICD10_SEX_MISMATCH, Map.of("icd10Code", code));
        }
        return icd10;
    }

    private boolean diagnosesLocked(MedicalRecord record) {
        return prescriptionRepository.existsByMedicalRecordIdAndLienthongStatusIn(record.getId(), SENT);
    }

    private static boolean diagnosesChanged(Conclusion conclusion, Icd10 main, List<Icd10> secondary) {
        List<String> saved = conclusion.getSecondaryDiagnoses().stream()
                .sorted(Comparator.comparingInt(ConclusionSecondaryDiagnosis::getThuTu))
                .map(ConclusionSecondaryDiagnosis::getMaicd)
                .toList();
        return !Objects.equals(conclusion.getMaicd(), main.getMaicd())
                || !saved.equals(secondary.stream().map(Icd10::getMaicd).toList());
    }

    private Context requireConcludableVisit(StaffPrincipal principal, UUID visitId) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, CLINIC_ZONE);
        LocalTime time = LocalTime.ofInstant(now, CLINIC_ZONE);
        StaffAccount doctor = staffAccountRepository.findById(principal.accountId())
                .filter(account -> account.getRole() == Role.DOCTOR)
                .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));
        StaffRoomAssignment assignment = assignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(doctor.getId())
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_ASSIGNMENT_REQUIRED));
        Room room = roomRepository.findById(assignment.getRoomId())
                .filter(value -> value.getType() == RoomType.EXAMINATION)
                .filter(value -> value.getStatus() == ActivationStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_ASSIGNMENT_REQUIRED));
        boolean onDuty = dutyShiftConfigService.shiftAt(today, time)
                .flatMap(shift -> dutyScheduleRepository.findByWorkDateAndShiftAndStaffAccountId(
                        today, shift, doctor.getId()))
                .map(DutySchedule::getRoomId).filter(room.getId()::equals).isPresent();
        if (!onDuty) {
            throw new AppException(ErrorCode.DOCTOR_OFF_DUTY);
        }
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new AppException(ErrorCode.VISIT_NOT_FOUND));
        if (!room.getId().equals(visit.getRoomId()) || !doctor.getId().equals(visit.getDoctorAccountId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        if (visit.isSkipped() || !CONCLUDABLE.contains(visit.getStatus())) {
            throw new AppException(ErrorCode.VISIT_NOT_IN_EXAMINATION);
        }
        MedicalRecord record = medicalRecordRepository.findByVisitId(visitId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        if (record.getStatus() != MedicalRecordStatus.OPEN) {
            throw new AppException(ErrorCode.MEDICAL_RECORD_CLOSED);
        }
        Patient patient = patientRepository.findById(visit.getPatientId())
                .orElseThrow(() -> new AppException(ErrorCode.PATIENT_NOT_FOUND));
        return new Context(visit, patient, record, doctor);
    }

    private ConclusionResponse toResponse(Context context, Conclusion conclusion) {
        ClinicalExamination examination = examinationRepository.findByMedicalRecordId(context.record().getId())
                .orElse(null);
        List<Icd10OptionResponse> preliminary = examination == null ? List.of() : examination.getDiagnoses().stream()
                .sorted(Comparator.comparingInt(ClinicalExaminationDiagnosis::getThuTu))
                .map(item -> new Icd10OptionResponse(item.getMaicd(), item.getTenicd()))
                .toList();
        boolean locked = diagnosesLocked(context.record());
        if (conclusion == null) {
            return new ConclusionResponse(
                    context.visit().getId(), context.record().getId(), null, null,
                    examination == null ? null : examination.getChandoansobo(), preliminary,
                    null, null, List.of(), null, null, null, null, null, locked, null, null);
        }
        String doctorName = staffAccountRepository.findById(conclusion.getDoctorAccountId())
                .map(StaffAccount::getFullName).orElse(null);
        return new ConclusionResponse(
                context.visit().getId(), context.record().getId(), conclusion.getId(), conclusion.getVersion(),
                examination == null ? null : examination.getChandoansobo(), preliminary,
                conclusion.getChandoan(), new Icd10OptionResponse(conclusion.getMaicd(), conclusion.getTenicd()),
                conclusion.getSecondaryDiagnoses().stream()
                        .sorted(Comparator.comparingInt(ConclusionSecondaryDiagnosis::getThuTu))
                        .map(item -> new Icd10OptionResponse(item.getMaicd(), item.getTenicd()))
                        .toList(),
                conclusion.getHuongdieutri(), conclusion.getKetquadieutri(), conclusion.getLoidanbacsi(),
                conclusion.getTomtat(), conclusion.getGhichu(), locked, doctorName, conclusion.getConcludedAt());
    }

    private static Map<String, Object> auditValues(Conclusion conclusion) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("diagnosis", conclusion.getChandoan());
        values.put("mainIcd10Code", conclusion.getMaicd());
        values.put("secondaryIcd10Codes", conclusion.getSecondaryDiagnoses().stream()
                .sorted(Comparator.comparingInt(ConclusionSecondaryDiagnosis::getThuTu))
                .map(ConclusionSecondaryDiagnosis::getMaicd).toList());
        values.put("treatmentPlan", conclusion.getHuongdieutri());
        values.put("treatmentResult", conclusion.getKetquadieutri());
        values.put("advice", conclusion.getLoidanbacsi());
        values.put("summary", conclusion.getTomtat());
        values.put("note", conclusion.getGhichu());
        return values;
    }

    /** Trimmed text with no length limit (the columns are TEXT); MSG02 when a required one is empty. */
    private static String text(String value, String field, boolean required) {
        String normalized = normalize(value);
        if (normalized == null && required) {
            throw invalid(field, "REQUIRED");
        }
        return normalized;
    }

    private static AppException invalid(String field, String reason) {
        return new AppException(ErrorCode.CONCLUSION_INVALID, Map.of("field", field, "reason", reason));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private record Context(Visit visit, Patient patient, MedicalRecord record, StaffAccount doctor) {
    }
}
