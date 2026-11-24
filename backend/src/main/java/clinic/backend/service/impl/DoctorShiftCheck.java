package clinic.backend.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.entity.Visit;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.service.DutyShiftConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * BR-19, BR-55: the staff member is scheduled in the current shift (by the configured shift hours) for the room they
 * are assigned to now. Outside the shift they can only view lists (MSG48).
 */
@Component
@RequiredArgsConstructor
public class DoctorShiftCheck {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final StaffRoomAssignmentRepository assignmentRepository;
    private final DutyScheduleRepository dutyScheduleRepository;
    private final DutyShiftConfigService dutyShiftConfigService;

    /**
     * BR-19, BR-20: after the shift ends, the Doctor may still finish a visit of today that is assigned to them (they
     * called the patient in), but cannot call new patients or create new paraclinical orders.
     */
    public static boolean isOwnVisitOfToday(Visit visit, UUID doctorAccountId, LocalDate today) {
        return doctorAccountId.equals(visit.getDoctorAccountId()) && today.equals(visit.getQueueDate());
    }

    public boolean isOnDuty(UUID staffAccountId, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, CLINIC_ZONE);
        LocalTime time = LocalTime.ofInstant(now, CLINIC_ZONE);
        UUID assignedRoomId = assignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(staffAccountId)
                .map(StaffRoomAssignment::getRoomId)
                .orElse(null);
        if (assignedRoomId == null) {
            return false;
        }
        return dutyShiftConfigService.shiftAt(today, time)
                .flatMap(shift -> dutyScheduleRepository.findByWorkDateAndShiftAndStaffAccountId(
                        today, shift, staffAccountId))
                .map(DutySchedule::getRoomId)
                .filter(assignedRoomId::equals)
                .isPresent();
    }
}
