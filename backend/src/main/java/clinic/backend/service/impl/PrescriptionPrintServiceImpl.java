package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import clinic.backend.dto.response.Icd10OptionResponse;
import clinic.backend.dto.response.PrescriptionPrintResponse;
import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Prescription;
import clinic.backend.entity.PrescriptionDetail;
import clinic.backend.entity.PrescriptionDiagnosis;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.PrescriptionRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.PrescriptionPrintService;
import clinic.backend.service.impl.ConcludingVisitGuard.Context;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-60 Print Prescription: the form of TT 26/2025 Phụ lục I (BR-59-13) for an accepted prescription, printed by the
 * Doctor of the visit once the record is closed (PO 23/11/2026), right after closing or later from the record (UC-52).
 * The first print is the original; every later one carries "Bản sao".
 */
@Service
@RequiredArgsConstructor
public class PrescriptionPrintServiceImpl implements PrescriptionPrintService {

    private static final String AUDIT_ENTITY = "prescriptions";

    private final ClosedRecordPrintGuard guard;
    private final PrescriptionServiceImpl prescriptions;
    private final PrescriptionRepository prescriptionRepository;
    private final ClinicFacilityRepository clinicFacilityRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PrescriptionPrintResponse printPreview(StaffPrincipal principal, UUID prescriptionId) {
        Prescription prescription = printablePrescription(prescriptionId);
        Context context = guard.require(principal, prescriptions.visitId(prescription));
        requireAccepted(prescription);
        return form(context, prescription, prescription.getPrintedAt() != null, null, null);
    }

    @Override
    @Transactional
    public PrescriptionPrintResponse preparePrint(StaffPrincipal principal, UUID prescriptionId) {
        Prescription prescription = printablePrescription(prescriptionId);
        Context context = guard.require(principal, prescriptions.visitId(prescription));
        requireAccepted(prescription);
        Instant now = clock.instant();
        boolean reprint = prescription.getPrintedAt() != null;
        if (!reprint) {
            prescription.markPrinted(now);
            prescriptionRepository.save(prescription);
        }
        // BR-59-14: every print is audited; the first one also sets printed_at.
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("prescriptionCode", prescription.getMadonthuoc());
        audit.put("reprint", reprint);
        auditLogService.recordBusinessChange(
                principal.accountId(), AuditLogAction.PRINT, AUDIT_ENTITY, prescription.getId(),
                context.visit().getSovaovien(), audit, now);
        return form(context, prescription, reprint, now, displayName(context.doctor()));
    }

    private Prescription printablePrescription(UUID prescriptionId) {
        return prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
    }

    /** UC-60 AF-01 (MSG135): only an accepted prescription is printed. */
    private static void requireAccepted(Prescription prescription) {
        if (!prescription.isPrintable()) {
            throw new AppException(ErrorCode.PRESCRIPTION_NOT_PRINTABLE,
                    Map.of("status", prescription.getLienthongStatus().name()));
        }
    }

    /**
     * BR-59-13: the data sent to the national system (diagnoses and follow-up date copied at send time, BR-59-08) and
     * the signature of the accepted send, verified again (BR-59-16). An accepted prescription is never sent again, so
     * its last send is the accepted one.
     */
    private PrescriptionPrintResponse form(
            Context context, Prescription prescription, boolean copy, Instant printedAt, String printedBy) {
        return new PrescriptionPrintResponse(
                prescription.getId(),
                prescription.getMadonthuoc(),
                copy,
                printedAt,
                printedBy,
                facility(),
                patient(context.patient(), context.visit()),
                prescriptions.childData(context, prescription),
                diagnoses(prescription),
                lines(prescription),
                prescription.getLuuY(),
                prescription.getLoiDan(),
                prescription.getNgayTaiKham(),
                prescription.getPrescribedAt(),
                staffAccountRepository.findById(prescription.getDoctorAccountId())
                        .map(PrescriptionPrintServiceImpl::displayName)
                        .orElse(null),
                prescriptions.lastSubmission(prescription));
    }

    private PrescriptionPrintResponse.Facility facility() {
        ClinicFacility facility = clinicFacilityRepository.findCurrent()
                .orElseThrow(() -> new AppException(ErrorCode.CLINIC_FACILITY_NOT_CONFIGURED));
        return new PrescriptionPrintResponse.Facility(
                facility.getTenCskcb(), facility.getDiachi(), facility.getSodienthoai());
    }

    /** BR-59-07 and TT 26 Phụ lục I: the administrative data, read from the patient and visit. */
    private static PrescriptionPrintResponse.PatientInfo patient(Patient patient, Visit visit) {
        // Chú thích 8: the patient's phone, or the accompanying person's.
        String phone = text(patient.getSodienthoai());
        return new PrescriptionPrintResponse.PatientInfo(
                patient.getHoten(),
                patient.getNgaysinh(),
                gender(patient.getGioitinh()),
                text(PrescriptionPayloadBuilder.address(patient)),
                phone != null ? phone : text(patient.getSodienthoainguoithan()),
                text(patient.getCccdSo()),
                text(patient.getHochieuSo()),
                // The insurance number as sent (BR-59-11 ma_so_bao_hiem_y_te).
                visit.isInsuranceCovered() ? text(visit.getInsuranceSocialNumber()) : null);
    }

    /** The form has boxes for Nam and Nữ only; any other value leaves both empty. */
    private static String gender(short code) {
        return switch (code) {
            case 1 -> "Nam";
            case 2 -> "Nữ";
            default -> null;
        };
    }

    private static List<Icd10OptionResponse> diagnoses(Prescription prescription) {
        return prescription.getDiagnoses().stream()
                .sorted(Comparator.comparingInt(PrescriptionDiagnosis::getThuTu))
                .map(diagnosis -> new Icd10OptionResponse(diagnosis.getMaicd(), diagnosis.getTenicd()))
                .toList();
    }

    private static List<PrescriptionPrintResponse.Line> lines(Prescription prescription) {
        return prescription.getDetails().stream()
                .sorted(Comparator.comparing(PrescriptionDetail::getMachidinh))
                .map(detail -> new PrescriptionPrintResponse.Line(
                        detail.getHoatchat(), detail.getTenthuocvattu(), detail.getHamluong(), detail.getDuongdung(),
                        detail.getDonvitinh(), detail.getSoluongthuocvattu().stripTrailingZeros(),
                        detail.getLieuMoiLan(), detail.getSoLanNgay(), detail.getSoNgayDung(), detail.getCachdung(),
                        detail.getYlenhthuocvattuGhichu()))
                .toList();
    }

    /** BR-59-13: the Doctor's academic title and name. */
    private static String displayName(StaffAccount account) {
        String title = text(account.getAcademicTitle());
        return title == null ? account.getFullName() : title + " " + account.getFullName();
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
