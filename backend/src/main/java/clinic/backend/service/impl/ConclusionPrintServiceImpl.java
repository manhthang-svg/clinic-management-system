package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
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
import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.ConclusionRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.ConclusionPrintService;
import clinic.backend.service.impl.ConcludingVisitGuard.Context;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-57 Print Conclusion (docs/uc-57-print-conclusion.md): the conclusion slip of a closed record, printed by the
 * Doctor of the visit right after closing or later from the record (UC-52). Printing changes nothing and writes no
 * audit row; every print is the same slip, without a "Bản sao" mark (PO 26/11/2026).
 */
@Service
@RequiredArgsConstructor
public class ConclusionPrintServiceImpl implements ConclusionPrintService {

    private final ClosedRecordPrintGuard guard;
    private final ConclusionRepository conclusionRepository;
    private final ClinicFacilityRepository clinicFacilityRepository;
    private final RoomRepository roomRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public ConclusionSlipResponse printPreview(StaffPrincipal principal, UUID visitId) {
        Context context = guard.require(principal, visitId);
        return slip(context, conclusion(context), null, null);
    }

    /** Step 3: the slip with the print time and the Doctor who prints it; nothing is recorded. */
    @Override
    @Transactional(readOnly = true)
    public ConclusionSlipResponse preparePrint(StaffPrincipal principal, UUID visitId) {
        Context context = guard.require(principal, visitId);
        return slip(context, conclusion(context), clock.instant(), displayName(context.doctor()));
    }

    /** A closed record always has its conclusion (BR-58-01); MSG61 otherwise. */
    private Conclusion conclusion(Context context) {
        return conclusionRepository.findByMedicalRecordId(context.record().getId())
                .orElseThrow(() -> new AppException(ErrorCode.PRINT_RECORD_NOT_CLOSED));
    }

    private ConclusionSlipResponse slip(
            Context context, Conclusion conclusion, Instant printedAt, String printedBy) {
        ClinicFacility facility = clinicFacilityRepository.findCurrent()
                .orElseThrow(() -> new AppException(ErrorCode.CLINIC_FACILITY_NOT_CONFIGURED));
        Visit visit = context.visit();
        Patient patient = context.patient();
        return new ConclusionSlipResponse(
                visit.getId(),
                visit.getSovaovien(),
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
