package clinic.backend.service.impl;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import clinic.backend.dto.response.ConclusionSlipResponse;
import clinic.backend.dto.response.Icd10OptionResponse;
import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.Conclusion;
import clinic.backend.entity.ConclusionSecondaryDiagnosis;
import clinic.backend.entity.Patient;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.ConclusionRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.ConclusionPrintService;
import clinic.backend.service.impl.ConcludingVisitGuard.Context;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BF-04 "In phiếu kết luận". The same Doctor who may edit the conclusion prints it (ConcludingVisitGuard), so the
 * slip is printed before the record is closed, like the follow-up slip (UC-62). Read-only and not logged, like the
 * other slips the Doctor prints.
 */
@Service
@RequiredArgsConstructor
public class ConclusionPrintServiceImpl implements ConclusionPrintService {

    private final ConcludingVisitGuard guard;
    private final ConclusionRepository conclusionRepository;
    private final ClinicFacilityRepository clinicFacilityRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public ConclusionSlipResponse print(StaffPrincipal principal, UUID visitId) {
        Context context = guard.require(principal, visitId);
        Conclusion conclusion = conclusionRepository.findByMedicalRecordId(context.record().getId())
                .orElseThrow(() -> new AppException(ErrorCode.CLOSE_CONCLUSION_REQUIRED));
        ClinicFacility facility = clinicFacilityRepository.findCurrent()
                .orElseThrow(() -> new AppException(ErrorCode.CLINIC_FACILITY_NOT_CONFIGURED));
        Patient patient = context.patient();
        Visit visit = context.visit();
        boolean insured = visit.getDoituongbnLoai() == VisitDoituongbnLoai.BHYT && text(visit.getMabhyt()) != null;
        List<Icd10OptionResponse> secondary = conclusion.getSecondaryDiagnoses().stream()
                .sorted(Comparator.comparingInt(ConclusionSecondaryDiagnosis::getThuTu))
                .map(item -> new Icd10OptionResponse(item.getMaicd(), item.getTenicd()))
                .toList();
        String doctorName = staffAccountRepository.findById(conclusion.getDoctorAccountId())
                .map(ConclusionPrintServiceImpl::displayName)
                .orElse(null);
        return new ConclusionSlipResponse(
                clock.instant(),
                new ConclusionSlipResponse.Facility(
                        facility.getTenCskcb(), facility.getDiachi(), facility.getSodienthoai()),
                new ConclusionSlipResponse.PatientInfo(
                        patient.getHoten(), patient.getMabenhnhan(), gender(patient.getGioitinh()),
                        patient.getNgaysinh(), text(PrescriptionPayloadBuilder.address(patient))),
                visit.getSovaovien(),
                visit.getDoituongbnLoai(),
                insured ? visit.getMabhyt().strip() : null,
                visit.getThoigianvaovien(),
                conclusion.getChandoan(),
                new Icd10OptionResponse(conclusion.getMaicd(), conclusion.getTenicd()),
                secondary,
                conclusion.getHuongdieutri(),
                conclusion.getKetquadieutri(),
                conclusion.getLoidanbacsi(),
                doctorName,
                conclusion.getConcludedAt());
    }

    private static String gender(short code) {
        return switch (code) {
            case 1 -> "Nam";
            case 2 -> "Nữ";
            default -> "Khác";
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
