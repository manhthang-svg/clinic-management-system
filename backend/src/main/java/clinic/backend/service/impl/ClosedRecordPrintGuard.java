package clinic.backend.service.impl;

import java.util.UUID;

import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.Patient;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.Role;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.impl.ConcludingVisitGuard.Context;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Who may print the conclusion (UC-57), the prescription (UC-60) and the follow-up slip (UC-62): the active Doctor of
 * the visit, once its medical record is closed (PO 23/11/2026). The room and shift of {@link ConcludingVisitGuard} do
 * not apply, so the Doctor can print again later from the medical record (UC-52).
 */
@Component
@RequiredArgsConstructor
public class ClosedRecordPrintGuard {

    private final VisitRepository visitRepository;
    private final PatientRepository patientRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final MedicalRecordRepository medicalRecordRepository;

    public Context require(StaffPrincipal principal, UUID visitId) {
        StaffAccount doctor = staffAccountRepository.findById(principal.accountId())
                .filter(account -> account.getRole() == Role.DOCTOR)
                .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new AppException(ErrorCode.VISIT_NOT_FOUND));
        if (!doctor.getId().equals(visit.getDoctorAccountId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        MedicalRecord record = medicalRecordRepository.findByVisitId(visitId)
                .orElseThrow(() -> new AppException(ErrorCode.MEDICAL_RECORD_NOT_FOUND));
        // MSG61: the conclusion is confirmed, and the record closed, before anything is printed.
        if (record.getStatus() == MedicalRecordStatus.OPEN) {
            throw new AppException(ErrorCode.PRINT_RECORD_NOT_CLOSED);
        }
        Patient patient = patientRepository.findById(visit.getPatientId())
                .orElseThrow(() -> new AppException(ErrorCode.PATIENT_NOT_FOUND));
        return new Context(visit, patient, record, doctor);
    }
}
