package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.UpdateMedicalHistoryRequest;
import clinic.backend.dto.response.MedicalHistoryResponse;
import clinic.backend.entity.MedicalHistory;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalHistoryType;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.MedicalHistoryRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.MedicalHistoryService;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-39 Update Medical History and Allergies. History and allergies belong to the patient profile and are
 * changed only by the Doctor examining the patient in their own room while the record is open (BR-19, BR-23).
 */
@Service
@RequiredArgsConstructor
public class MedicalHistoryServiceImpl implements MedicalHistoryService {

    static final int MAX_HISTORY_LENGTH = 2_000;
    static final String NOT_IN_EXAMINATION = "NOT_IN_EXAMINATION";
    static final String MEDICAL_RECORD_CLOSED = "MEDICAL_RECORD_CLOSED";
    private static final String PATIENT_AUDIT_ENTITY = "patients";

    private final VisitRepository visitRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final StaffRoomAssignmentRepository staffRoomAssignmentRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final AuditLogService auditLogService;
    private final UuidV7Generator ids;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public MedicalHistoryResponse getForVisit(UUID doctorAccountId, UUID visitId) {
        Visit visit = requireVisit(visitId);
        Optional<MedicalRecord> record = medicalRecordRepository.findByVisitId(visitId);
        return toResponse(visit, readOnlyReason(doctorAccountId, visit, record),
                medicalHistoryRepository.findByPatientIdOrderByCreatedAtAsc(visit.getPatientId()));
    }

    @Override
    @Transactional
    public MedicalHistoryResponse updateForVisit(
            UUID doctorAccountId, UUID visitId, UpdateMedicalHistoryRequest request) {
        // lay luot kham
        Visit visit = requireVisit(visitId);

        // lay benh an
        Optional<MedicalRecord> record = medicalRecordRepository.findByVisitId(visitId);
        // EF-02: only while examining the patient in the Doctor's room, and the record is not closed.

        // hoi ly do
        String reason = readOnlyReason(doctorAccountId, visit, record);
        //ượt khám không ở trạng thái Đang khám / Chờ kết luận, hoặc không phải phòng của bác sĩ này
        if (NOT_IN_EXAMINATION.equals(reason)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        //đang khám đúng phòng, nhưng bệnh án đã khóa
        if (MEDICAL_RECORD_CLOSED.equals(reason)) {
            throw new AppException(ErrorCode.MEDICAL_RECORD_CLOSED);
        }
        // kiem tra du lieu co hop le ko
        validate(request);

        // tiền sử và dị ứng HIỆN ĐANG LƯU
        List<MedicalHistory> current = medicalHistoryRepository.findByPatientIdOrderByCreatedAtAsc(
                visit.getPatientId());
        // Another Doctor saved in between: the Doctor reloads instead of overwriting (MSG75).
        //Chống ghi đè
        if (!sameInstant(lastUpdatedAt(current), request.lastUpdatedAt())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }

        // dung danh sach moi
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        UUID recordId = record.map(MedicalRecord::getId).orElse(null);
        List<MedicalHistory> replacement = new ArrayList<>();
        request.history().forEach(item -> replacement.add(MedicalHistory.personal(
                ids.next(), now, visit.getPatientId(), recordId, item.category(), item.content().strip(),
                doctorAccountId)));
        if (request.noKnownDrugAllergy()) {
            replacement.add(MedicalHistory.noKnownDrugAllergy(
                    ids.next(), now, visit.getPatientId(), recordId, doctorAccountId));
        } else {
            request.allergies().forEach(item -> replacement.add(MedicalHistory.drugAllergy(
                    ids.next(), now, visit.getPatientId(), recordId, item.drug().strip(),
                    blankToNull(item.reaction()), item.severity(), doctorAccountId)));
        }

        medicalHistoryRepository.deleteAllInBatch(current);
        medicalHistoryRepository.saveAll(replacement);

        // Step 5: audit trail with old and new values (IV.2.3).
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("visitId", visit.getId());  // sửa trong lượt khám nao
        audit.put("old", summary(current)); // danh sách TRƯỚC khi sửa
        audit.put("new", summary(replacement)); // danh sách SAU khi sửa
        auditLogService.recordBusinessChange(
                doctorAccountId, AuditLogAction.UPDATE, PATIENT_AUDIT_ENTITY, visit.getPatientId(),
                visit.getSovaovien(), audit, now);

        return toResponse(visit, null, replacement);
    }

    private Visit requireVisit(UUID visitId) {
        return visitRepository.findById(visitId)
                .orElseThrow(() -> new AppException(ErrorCode.VISIT_NOT_FOUND));
    }

    private String readOnlyReason(UUID doctorAccountId, Visit visit, Optional<MedicalRecord> record) {
        boolean examining = visit.getStatus() == VisitStatus.IN_EXAMINATION
                || visit.getStatus() == VisitStatus.WAITING_CONCLUSION;
        boolean ownRoom = staffRoomAssignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(doctorAccountId)
                .map(StaffRoomAssignment::getRoomId)
                .filter(roomId -> roomId.equals(visit.getRoomId()))
                .isPresent();
        if (!examining || !ownRoom) {
            return NOT_IN_EXAMINATION;
        }
        if (record.isPresent() && record.get().getStatus() != MedicalRecordStatus.OPEN) {
            return MEDICAL_RECORD_CLOSED;
        }
        return null;
    }

    private static void validate(UpdateMedicalHistoryRequest request) {
        int historyLength = request.history().stream()
                .mapToInt(item -> item.content().strip().length())
                .sum();
        if (historyLength > MAX_HISTORY_LENGTH) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    Map.of("history", "Tiền sử không được vượt quá 2.000 ký tự"));
        }
        if (request.noKnownDrugAllergy() && !request.allergies().isEmpty()) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    Map.of("allergies", "Đã chọn không có tiền sử dị ứng thuốc nên không thể nhập dị ứng"));
        }
    }

    private MedicalHistoryResponse toResponse(Visit visit, String readOnlyReason, List<MedicalHistory> items) {
        List<MedicalHistoryResponse.HistoryEntry> history = items.stream()
                .filter(item -> item.getHistoryType() == MedicalHistoryType.PERSONAL)
                .map(item -> new MedicalHistoryResponse.HistoryEntry(item.getCategory(), item.getThongtinNoidung()))
                .toList();
        List<MedicalHistoryResponse.AllergyEntry> allergies = items.stream()
                .filter(item -> item.getHistoryType() == MedicalHistoryType.DRUG_ALLERGY)
                .filter(item -> !item.isNoKnownDrugAllergy())
                .map(item -> new MedicalHistoryResponse.AllergyEntry(
                        item.getThongtinNoidung(), item.getThongtinGhichu(), item.getSeverity()))
                .toList();
        boolean noKnownAllergy = items.stream().anyMatch(MedicalHistory::isNoKnownDrugAllergy);
        Optional<MedicalHistory> latest = items.stream().max(Comparator.comparing(MedicalHistory::getUpdatedAt));
        String updatedByName = latest
                .flatMap(item -> staffAccountRepository.findById(item.getUpdatedByAccountId()))
                .map(account -> account.getAcademicTitle() == null || account.getAcademicTitle().isBlank()
                        ? account.getFullName()
                        : account.getAcademicTitle().strip() + " " + account.getFullName())
                .orElse(null);
        return new MedicalHistoryResponse(
                visit.getId(), visit.getPatientId(), readOnlyReason == null, readOnlyReason, history, allergies,
                noKnownAllergy, latest.map(MedicalHistory::getUpdatedAt).orElse(null), updatedByName);
    }

    private static Instant lastUpdatedAt(List<MedicalHistory> items) {
        return items.stream().map(MedicalHistory::getUpdatedAt).max(Comparator.naturalOrder()).orElse(null);
    }

    private static boolean sameInstant(Instant left, Instant right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.truncatedTo(ChronoUnit.MILLIS).equals(right.truncatedTo(ChronoUnit.MILLIS));
    }

    private static List<String> summary(List<MedicalHistory> items) {
        return items.stream()
                .map(item -> String.join(" | ", item.getHistoryType().name(),
                        Objects.toString(item.getCategory() != null ? item.getCategory() : item.getSeverity(), ""),
                        item.getThongtinNoidung(),
                        Objects.toString(item.getThongtinGhichu(), "")))
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
