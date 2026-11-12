package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.Role;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.DutyShiftConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Who may work on the conclusion steps of a visit (UC-56 conclusion, UC-59 prescription): an active Doctor in the
 * assigned examination room and shift (BR-02, BR-05, BR-19, BR-55), on their own visit, which is still in
 * examination with nothing waiting for paraclinical services or back for the conclusion (BR-30, BR-41), with an
 * open medical record (BR-32).
 */
@Component
@RequiredArgsConstructor
public class ConcludingVisitGuard {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<VisitStatus> CONCLUDABLE =
            Set.of(VisitStatus.IN_EXAMINATION, VisitStatus.WAITING_CONCLUSION);

    private final VisitRepository visitRepository;
    private final PatientRepository patientRepository;
    private final RoomRepository roomRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final StaffRoomAssignmentRepository assignmentRepository;
    private final DutyScheduleRepository dutyScheduleRepository;
    private final DutyShiftConfigService dutyShiftConfigService;
    private final MedicalRecordRepository medicalRecordRepository;
    private final Clock clock;

    public Context require(StaffPrincipal principal, UUID visitId) {
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

    public record Context(Visit visit, Patient patient, MedicalRecord record, StaffAccount doctor) {
    }
}
