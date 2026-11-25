package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import clinic.backend.dto.response.ConclusionSlipResponse;
import clinic.backend.dto.response.Icd10OptionResponse;
import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.Conclusion;
import clinic.backend.entity.ConclusionSecondaryDiagnosis;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.ConclusionRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.ConclusionPrintService;
import clinic.backend.service.impl.ConcludingVisitGuard.Context;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-57 Print Conclusion (docs/uc-57-print-conclusion.md): the conclusion slip of a closed record, printed by the
 * Doctor of the visit right after closing or later from the record (UC-52). The first print is the original; every
 * later one carries "Bản sao". The conclusion has no {@code printed_at}, so the earlier {@code PRINT} audit rows tell
 * whether it was printed before.
 */
@Service
@RequiredArgsConstructor
public class ConclusionPrintServiceImpl implements ConclusionPrintService {

    static final String AUDIT_ENTITY = "conclusions";

    private final ClosedRecordPrintGuard guard;
    private final ConclusionRepository conclusionRepository;
    private final ClinicFacilityRepository clinicFacilityRepository;
    private final RoomRepository roomRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public ConclusionSlipResponse printPreview(StaffPrincipal principal, UUID visitId) {
        Context context = guard.require(principal, visitId);
        Conclusion conclusion = conclusion(context);
        return slip(context, conclusion, printedBefore(conclusion), null, null);
    }

    @Override
    @Transactional
    public ConclusionSlipResponse preparePrint(StaffPrincipal principal, UUID visitId) {
        Context context = guard.require(principal, visitId);
        Conclusion conclusion = conclusion(context);
        boolean reprint = printedBefore(conclusion);
        Instant now = clock.instant();
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("visitCode", context.visit().getSovaovien());
        audit.put("reprint", reprint);
        auditLogService.recordBusinessChange(
                principal.accountId(), AuditLogAction.PRINT, AUDIT_ENTITY, conclusion.getId(),
                context.visit().getSovaovien(), audit, now);
        return slip(context, conclusion, reprint, now, displayName(context.doctor()));
    }

    /** A closed record always has its conclusion (BR-58-01); MSG61 otherwise. */
    private Conclusion conclusion(Context context) {
        return conclusionRepository.findByMedicalRecordId(context.record().getId())
                .orElseThrow(() -> new AppException(ErrorCode.PRINT_RECORD_NOT_CLOSED));
    }

    private boolean printedBefore(Conclusion conclusion) {
        return auditLogService.hasAction(AuditLogAction.PRINT, AUDIT_ENTITY, conclusion.getId());
    }

    private ConclusionSlipResponse slip(
            Context context, Conclusion conclusion, boolean copy, Instant printedAt, String printedBy) {
        ClinicFacility facility = clinicFacilityRepository.findCurrent()
                .orElseThrow(() -> new AppException(ErrorCode.CLINIC_FACILITY_NOT_CONFIGURED));
        Visit visit = context.visit();
        Patient patient = context.patient();
        return new ConclusionSlipResponse(
                visit.getId(),
                visit.getSovaovien(),
                copy,
                printedAt,
                printedBy,
                new ConclusionSlipResponse.Facility(
                        facility.getTenCskcb(), facility.getDiachi(), facility.getSodienthoai()),
                new ConclusionSlipResponse.PatientInfo(
                        patient.getMabenhnhan(), patient.getHoten(), patient.getNgaysinh(),
                        gender(patient.getGioitinh()), text(PrescriptionPayloadBuilder.address(patient))),
                FollowUpRules.visitDate(visit.getThoigianvaovien()),
                roomRepository.findById(visit.getRoomId()).map(Room::getRoomName).orElse(null),
                new Icd10OptionResponse(conclusion.getMaicd(), conclusion.getTenicd()),
                secondaryDiagnoses(conclusion),
                text(conclusion.getChandoan()),
                text(conclusion.getHuongdieutri()),
                conclusion.getKetquadieutri(),
                text(conclusion.getLoidanbacsi()),
                conclusion.getConcludedAt(),
                staffAccountRepository.findById(conclusion.getDoctorAccountId())
                        .map(ConclusionPrintServiceImpl::displayName)
                        .orElse(null));
    }

    private static List<Icd10OptionResponse> secondaryDiagnoses(Conclusion conclusion) {
        return conclusion.getSecondaryDiagnoses().stream()
                .sorted(Comparator.comparingInt(ConclusionSecondaryDiagnosis::getThuTu))
                .map(item -> new Icd10OptionResponse(item.getMaicd(), item.getTenicd()))
                .toList();
    }

    private static String gender(short code) {
        return switch (code) {
            case 1 -> "Nam";
            case 2 -> "Nữ";
            default -> null;
        };
    }

    private static String displayName(StaffAccount account) {
        String title = text(account.getAcademicTitle());
        return title == null ? account.getFullName() : title + " " + account.getFullName();
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
