package clinic.backend.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AccountStatus;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.service.DutyShiftConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Which rooms are staffed now: an active staff member is scheduled in the room for the current duty shift
 * (configured shift hours). Used to suggest a staffed room when ordering paraclinical services; rooms without staff
 * stay selectable.
 */
@Component
@RequiredArgsConstructor
public class ParaclinicalRoomDuty {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final DutyScheduleRepository dutyScheduleRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final DutyShiftConfigService dutyShiftConfigService;

    /** Rooms with an active staff member on duty now, in two queries whatever the number of rooms. */
    public Set<UUID> staffedRooms(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, CLINIC_ZONE);
        List<DutySchedule> schedules = dutyShiftConfigService.shiftAt(today, LocalTime.ofInstant(now, CLINIC_ZONE))
                .map(shift -> dutyScheduleRepository.findByWorkDateAndShift(today, shift))
                .orElse(List.of());
        if (schedules.isEmpty()) {
            return Set.of();
        }
        Set<UUID> activeStaff = staffAccountRepository
                .findAllById(schedules.stream().map(DutySchedule::getStaffAccountId).collect(Collectors.toSet()))
                .stream()
                .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
                .map(StaffAccount::getId)
                .collect(Collectors.toSet());
        return schedules.stream()
                .filter(schedule -> activeStaff.contains(schedule.getStaffAccountId()))
                .map(DutySchedule::getRoomId)
                .collect(Collectors.toSet());
    }
}
