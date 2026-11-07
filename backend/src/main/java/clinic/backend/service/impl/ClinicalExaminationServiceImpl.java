package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import clinic.backend.dto.request.MedicalHistoryItemRequest;
import clinic.backend.dto.request.SaveClinicalExaminationRequest;
import clinic.backend.dto.request.VitalSignRequest;
import clinic.backend.dto.response.ClinicalExaminationResponse;
import clinic.backend.dto.response.Icd10OptionResponse;
import clinic.backend.dto.response.MedicalHistoryItemResponse;
import clinic.backend.dto.response.VitalSignResponse;
import clinic.backend.entity.ClinicalExamination;
import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.Icd10;
import clinic.backend.entity.MedicalHistory;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.entity.Visit;
import clinic.backend.entity.VitalSign;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ClinicalExaminationNextStep;
import clinic.backend.enums.ClinicalExaminationSaveMode;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.Role;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.mapper.ClinicalExaminationMapper;
import clinic.backend.repository.ClinicalExaminationRepository;
import clinic.backend.repository.DutyScheduleRepository;
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
import clinic.backend.service.ClinicalExaminationService;
import clinic.backend.service.DutyShiftConfigService;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicalExaminationServiceImpl implements ClinicalExaminationService {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String EXAMINATION_AUDIT_ENTITY = "clinical_examinations";
    private static final String HISTORY_AUDIT_ENTITY = "medical_histories";

    private final VisitRepository visitRepository;
    private final PatientRepository patientRepository;
    private final RoomRepository roomRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final StaffRoomAssignmentRepository assignmentRepository;
    private final DutyScheduleRepository dutyScheduleRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final ClinicalExaminationRepository examinationRepository;
    private final VitalSignRepository vitalSignRepository;
    private final MedicalHistoryRepository historyRepository;
    private final Icd10Repository icd10Repository;
    private final Icd10SearchCache icd10SearchCache;
    private final AuditLogService auditLogService;
    private final ClinicalExaminationMapper mapper;
    private final UuidV7Generator ids;
    private final DutyShiftConfigService dutyShiftConfigService;
    private final Clock clock;

    @Override
    @Transactional
    public ClinicalExaminationResponse open(StaffPrincipal principal, UUID visitId) {
        log.debug("Opening clinical examination [visitId={}, accountId={}]", visitId, principal.accountId());
        AccessContext context = requireViewableVisit(principal, visitId);
        Instant now = clock.instant();
        MedicalRecord record = medicalRecordRepository.findByVisitId(visitId)
                .orElseGet(() -> medicalRecordRepository.save(new MedicalRecord(
                        ids.next(), now, visitId, "BA-" + context.visit().getSovaovien(), "NGOAITRU",
                        MedicalRecordStatus.OPEN, now, principal.accountId())));
        requireOpen(record);
        ClinicalExamination examination = examinationRepository.findByMedicalRecordId(record.getId())
                .orElseGet(() -> examinationRepository.saveAndFlush(new ClinicalExamination(
                        ids.next(), now, record.getId(), "KB-" + context.visit().getSovaovien(),
                        principal.accountId())));
        auditLogService.recordBusinessChange(
                principal.accountId(), AuditLogAction.VIEW_RECORD, EXAMINATION_AUDIT_ENTITY,
                examination.getId(), context.visit().getSovaovien(), Map.of("visitId", visitId), now);
        return toResponse(context, record, examination);
    }

    @Override
    @Transactional
    public ClinicalExaminationResponse save(
            StaffPrincipal principal, UUID visitId, SaveClinicalExaminationRequest request) {
        AccessContext context = requireEditableVisit(principal, visitId);
        MedicalRecord record = medicalRecordRepository.findByVisitId(visitId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        requireOpen(record);
        ClinicalExamination examination = examinationRepository.findByMedicalRecordId(record.getId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        if (request.version() != null && !request.version().equals(examination.getVersion())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        validateRequest(request);
        Instant now = clock.instant();
        Icd10 icd10 = resolveIcd10(
                request.preliminaryDiagnosisIcd10Code(), context.patient(), LocalDate.ofInstant(now, CLINIC_ZONE));
        Map<String, Object> oldValues = auditValues(context.visit(), examination);

        context.visit().updateReasonForVisit(normalize(request.reasonForVisit()), now);
        examination.updateClinicalData(
                principal.accountId(), normalize(request.presentIllness()), normalize(request.generalFinding()),
                normalize(request.organFinding()),
                normalize(request.preliminaryDiagnosis()), icd10 == null ? null : icd10.getMaicd(),
                icd10 == null ? null : icd10.getTenicd(), normalize(request.treatmentCarePerformed()),
                now);
        replaceInitialVitalSign(record, principal.accountId(), request.vitalSigns(), now);

        visitRepository.save(context.visit());
        ClinicalExamination saved = examinationRepository.saveAndFlush(examination);
        auditLogService.recordEntityChange(
                principal.accountId(), saved.getId(), AuditLogAction.UPDATE, EXAMINATION_AUDIT_ENTITY,
                oldValues, auditValues(context.visit(), saved), now);
        return toResponse(context, record, saved);
    }

    @Override
    public List<Icd10OptionResponse> searchActiveIcd10(String keyword, int limit) {
        String query = keyword == null ? "" : keyword.strip();
        if (query.length() < 2) return List.of();
        // Served from memory (Icd10SearchCache): no database round trip per keystroke.
        LocalDate today = LocalDate.ofInstant(clock.instant(), CLINIC_ZONE);
        return icd10SearchCache.search(query, today, Math.min(Math.max(limit, 1), 30)).stream()
                .map(entry -> new Icd10OptionResponse(entry.code(), entry.name()))
                .toList();
    }

    @Override
    @Transactional
    public List<MedicalHistoryItemResponse> addMedicalHistory(
            StaffPrincipal principal, UUID visitId, MedicalHistoryItemRequest request) {
        AccessContext context = requireEditableVisit(principal, visitId);
        MedicalRecord record = requireOpenRecord(visitId);
        Instant now = clock.instant();
        MedicalHistory history = historyRepository.saveAndFlush(new MedicalHistory(
                ids.next(), now, context.patient().getId(), record.getId(), request.type(),
                request.content().strip(), normalize(request.notes()), principal.accountId()));
        auditLogService.recordEntityChange(
                principal.accountId(), history.getId(), AuditLogAction.CREATE, HISTORY_AUDIT_ENTITY,
                Map.of(), historyAuditValues(history), now);
        return historyOf(context.patient());
    }

    @Override
    @Transactional
    public List<MedicalHistoryItemResponse> updateMedicalHistory(
            StaffPrincipal principal, UUID visitId, UUID historyId, MedicalHistoryItemRequest request) {
        AccessContext context = requireEditableVisit(principal, visitId);
        MedicalRecord record = requireOpenRecord(visitId);
        MedicalHistory history = historyRepository.findById(historyId)
                .filter(item -> item.getPatientId().equals(context.patient().getId()))
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        if (request.version() != null && !request.version().equals(history.getVersion())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        Instant now = clock.instant();
        Map<String, Object> oldValues = historyAuditValues(history);
        history.update(request.type(), request.content().strip(), normalize(request.notes()), record.getId(),
                principal.accountId(), now);
        MedicalHistory saved = historyRepository.saveAndFlush(history);
        auditLogService.recordEntityChange(
                principal.accountId(), saved.getId(), AuditLogAction.UPDATE, HISTORY_AUDIT_ENTITY,
                oldValues, historyAuditValues(saved), now);
        return historyOf(context.patient());
    }

    private MedicalRecord requireOpenRecord(UUID visitId) {
        MedicalRecord record = medicalRecordRepository.findByVisitId(visitId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        requireOpen(record);
        return record;
    }

    private List<MedicalHistoryItemResponse> historyOf(Patient patient) {
        return historyRepository.findAllByPatientIdOrderByCreatedAtAsc(patient.getId()).stream()
                .map(mapper::toHistoryResponse).toList();
    }

    private static Map<String, Object> historyAuditValues(MedicalHistory history) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("type", history.getHistoryType());
        values.put("content", history.getThongtinNoidung());
        values.put("notes", history.getThongtinGhichu());
        return values;
    }

    /** Editing is allowed only while the patient is in the examination room. */
    private AccessContext requireEditableVisit(StaffPrincipal principal, UUID visitId) {
        return requireVisitInStatus(principal, visitId, Set.of(VisitStatus.IN_EXAMINATION));
    }

    /** While the patient is away for paraclinical services the Doctor may still read the examination. */
    private AccessContext requireViewableVisit(StaffPrincipal principal, UUID visitId) {
        return requireVisitInStatus(
                principal, visitId, Set.of(VisitStatus.IN_EXAMINATION, VisitStatus.WAITING_PARACLINICAL));
    }

    private AccessContext requireVisitInStatus(
            StaffPrincipal principal, UUID visitId, Set<VisitStatus> allowedStatuses) {
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
        if (!isOnDuty(doctor.getId(), room.getId(), today, time)) {
            throw new AppException(ErrorCode.DOCTOR_OFF_DUTY);
        }
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new AppException(ErrorCode.VISIT_NOT_FOUND));
        if (!room.getId().equals(visit.getRoomId()) || !doctor.getId().equals(visit.getDoctorAccountId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        if (visit.isSkipped() || !allowedStatuses.contains(visit.getStatus())) {
            throw new AppException(ErrorCode.VISIT_NOT_IN_EXAMINATION);
        }
        Patient patient = patientRepository.findById(visit.getPatientId())
                .orElseThrow(() -> new AppException(ErrorCode.PATIENT_NOT_FOUND));
        return new AccessContext(visit, patient, room, doctor);
    }

    private boolean isOnDuty(UUID doctorId, UUID roomId, LocalDate date, LocalTime time) {
        return dutyShiftConfigService.shiftAt(date, time)
                .flatMap(shift -> dutyScheduleRepository.findByWorkDateAndShiftAndStaffAccountId(date, shift, doctorId))
                .map(DutySchedule::getRoomId).filter(roomId::equals).isPresent();
    }

    private static void requireOpen(MedicalRecord record) {
        if (record.getStatus() != MedicalRecordStatus.OPEN) {
            throw new AppException(ErrorCode.MEDICAL_RECORD_CLOSED);
        }
    }

    private void validateRequest(SaveClinicalExaminationRequest request) {
        if (request.reasonForVisit() == null || request.reasonForVisit().strip().length() < 2) {
            throw new AppException(ErrorCode.CLINICAL_EXAMINATION_INVALID);
        }
        validateVitalSigns(request.vitalSigns());
        if (request.mode() == ClinicalExaminationSaveMode.CONTINUE) {
            String diagnosis = normalize(request.preliminaryDiagnosis());
            if (request.nextStep() == null || diagnosis == null || diagnosis.length() < 2) {
                throw new AppException(ErrorCode.PRELIMINARY_DIAGNOSIS_REQUIRED);
            }
        }
    }

    private static void validateVitalSigns(VitalSignRequest value) {
        if (value == null || !hasVitalValue(value)) throw new AppException(ErrorCode.VITAL_SIGNS_REQUIRED);
        if (!between(value.pulse(), 20, 250)
                || !between(value.temperature(), new BigDecimal("30.0"), new BigDecimal("45.0"))
                || !between(value.respiratoryRate(), 5, 80)
                || !between(value.systolicBloodPressure(), 50, 300)
                || !between(value.diastolicBloodPressure(), 20, 200)
                || !between(value.weight(), new BigDecimal("0.5"), new BigDecimal("300"))
                || !between(value.height(), new BigDecimal("30"), new BigDecimal("250"))
                || !between(value.spo2(), 50, 100)
                || (value.systolicBloodPressure() != null && value.diastolicBloodPressure() != null
                    && value.diastolicBloodPressure() >= value.systolicBloodPressure())) {
            throw new AppException(ErrorCode.VITAL_SIGNS_INVALID);
        }
    }

    /**
     * The code must be usable on the visit day, be the most detailed level (TT 06/2026 column 26) and fit the
     * patient's sex (columns 28, 29). Main-diagnosis rules (columns 24, 25, 27) belong to the conclusion (UC-50).
     */
    private Icd10 resolveIcd10(String code, Patient patient, LocalDate day) {
        String normalized = normalize(code);
        if (normalized == null) return null;
        Icd10 icd10 = icd10Repository.findById(normalized.toUpperCase(Locale.ROOT))
                .filter(item -> item.isUsableOn(day))
                .orElseThrow(() -> new AppException(ErrorCode.ICD10_INVALID));
        if (icd10.isCoMaChiTietHon()) {
            throw new AppException(ErrorCode.ICD10_NOT_DETAILED);
        }
        if (!icd10.appliesToSex(patient.getGioitinh())) {
            throw new AppException(ErrorCode.ICD10_SEX_MISMATCH);
        }
        return icd10;
    }

    private void replaceInitialVitalSign(
            MedicalRecord record, UUID doctorId, VitalSignRequest value, Instant now) {
        vitalSignRepository.findByMedicalRecordIdAndIsInitialTrue(record.getId())
                .ifPresent(vitalSignRepository::delete);
        vitalSignRepository.flush();
        vitalSignRepository.save(new VitalSign(
                ids.next(), now, record.getId(), true, value.pulse(), value.temperature(),
                value.respiratoryRate(), value.systolicBloodPressure(), value.diastolicBloodPressure(),
                value.weight(), value.height(), value.spo2(), value.measuredAt() == null ? now : value.measuredAt(),
                doctorId));
    }

    private ClinicalExaminationResponse toResponse(
            AccessContext context, MedicalRecord record, ClinicalExamination examination) {
        VitalSign vital = vitalSignRepository.findByMedicalRecordIdAndIsInitialTrue(record.getId()).orElse(null);
        String measurer = vital == null ? null : staffAccountRepository.findById(vital.getMeasuredByAccountId())
                .map(StaffAccount::getFullName).orElse(null);
        VitalSignResponse vitalResponse = mapper.toVitalSignResponse(vital, measurer);
        Icd10 icd10 = examination.getKhambenhChandoanvaovienmaicd() == null ? null
                : icd10Repository.findById(examination.getKhambenhChandoanvaovienmaicd()).orElse(null);
        Patient patient = context.patient();
        Visit visit = context.visit();
        Room room = context.room();
        return new ClinicalExaminationResponse(
                visit.getId(), record.getId(), examination.getId(), examination.getVersion(), visit.getSovaovien(),
                visit.getQueueNumber(), visit.getStatus(), visit.getThoigianvaovien(), visit.getDenkhambenhluc(),
                visit.getDoituongbnLoai(), visit.getMabhyt(), visit.getInsuranceCoverageRate(), patient.getId(), patient.getMabenhnhan(),
                patient.getHoten(), patient.getNgaysinh(), patientGender(patient), patient.getNghenghiep(),
                patient.getDantoc(), patient.getDiachi(), room.getId(), room.getRoomCode(), room.getRoomName(),
                context.doctor().getId(), context.doctor().getFullName(), visit.getLydovaovien(),
                examination.getQuatrinhbenhly(), examination.getKhambenhToanthan(), vitalResponse,
                examination.getKhambenhBophan(),
                examination.getChandoansobo(), mapper.toIcd10Option(icd10), examination.getTreatmentCarePerformed(),
                historyOf(patient),
                List.of(ClinicalExaminationNextStep.PARACLINICAL, ClinicalExaminationNextStep.CONCLUSION),
                examination.getUpdatedAt());
    }

    private static Map<String, Object> auditValues(Visit visit, ClinicalExamination examination) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("reasonForVisit", visit.getLydovaovien());
        values.put("presentIllness", examination.getQuatrinhbenhly());
        values.put("generalFinding", examination.getKhambenhToanthan());
        values.put("organFinding", examination.getKhambenhBophan());
        values.put("preliminaryDiagnosis", examination.getChandoansobo());
        values.put("preliminaryDiagnosisIcd10Code", examination.getKhambenhChandoanvaovienmaicd());
        values.put("treatmentCarePerformed", examination.getTreatmentCarePerformed());
        return values;
    }

    private static boolean hasVitalValue(VitalSignRequest value) {
        return value.pulse() != null || value.temperature() != null || value.respiratoryRate() != null
                || value.systolicBloodPressure() != null || value.diastolicBloodPressure() != null
                || value.weight() != null || value.height() != null || value.spo2() != null;
    }

    private static boolean between(Short value, int minimum, int maximum) {
        return value == null || value >= minimum && value <= maximum;
    }

    private static boolean between(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        return value == null || value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String patientGender(Patient patient) {
        return switch (patient.getGioitinh()) {
            case 1 -> "Nam";
            case 2 -> "Nữ";
            default -> "Không xác định";
        };
    }

    private record AccessContext(Visit visit, Patient patient, Room room, StaffAccount doctor) {
    }
}
