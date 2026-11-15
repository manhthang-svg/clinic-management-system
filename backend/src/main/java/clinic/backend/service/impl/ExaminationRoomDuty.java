package clinic.backend.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.Role;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.service.DutyShiftConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Who examines in a room at registration time (BR-55, BR-96): the active doctor scheduled for that room in the
 * shift the registration falls into.
 */
@Component
@RequiredArgsConstructor
public class ExaminationRoomDuty {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final DutyScheduleRepository dutyScheduleRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final DutyShiftConfigService dutyShiftConfigService;

    /** The active doctor on duty in the room for a registration made at {@code now}, if any. */
    public Optional<StaffAccount> onDutyDoctor(UUID roomId, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, CLINIC_ZONE);
        return dutyShiftConfigService.registrationShift(today, LocalTime.ofInstant(now, CLINIC_ZONE))
                .flatMap(shift -> dutyScheduleRepository.findByRoomIdAndWorkDateAndShift(roomId, today, shift))
                .map(DutySchedule::getStaffAccountId)
                .flatMap(staffAccountRepository::findById)
                .filter(account -> account.getRole() == Role.DOCTOR && account.getStatus() == AccountStatus.ACTIVE);
    }

    /**
     * The active doctor on duty in each examination room for the shift {@code now} falls into, keyed by room
     * (UC-42). Same shift rule as {@link #onDutyDoctor}, in two queries for all rooms.
     */
    public Map<UUID, StaffAccount> onDutyDoctorsByRoom(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, CLINIC_ZONE);
        List<DutySchedule> schedules = dutyShiftConfigService
                .registrationShift(today, LocalTime.ofInstant(now, CLINIC_ZONE))
                .map(shift -> dutyScheduleRepository.findByWorkDateAndShift(today, shift))
                .orElse(List.of());
        Map<UUID, StaffAccount> doctors = staffAccountRepository.findAllById(schedules.stream()
                        .map(DutySchedule::getStaffAccountId)
                        .collect(Collectors.toSet()))
                .stream()
                .filter(account -> account.getRole() == Role.DOCTOR && account.getStatus() == AccountStatus.ACTIVE)
                .collect(Collectors.toMap(StaffAccount::getId, account -> account));
        Map<UUID, StaffAccount> byRoom = new HashMap<>();
        schedules.stream()
                .filter(schedule -> schedule.getRoomId() != null && doctors.containsKey(schedule.getStaffAccountId()))
                .forEach(schedule -> byRoom.putIfAbsent(schedule.getRoomId(), doctors.get(schedule.getStaffAccountId())));
        return byRoom;
    }

    /** Doctor name as shown to staff, with the academic title when there is one. */
    public static String displayName(StaffAccount doctor) {
        String title = doctor.getAcademicTitle();
        return title == null || title.isBlank() ? doctor.getFullName() : title.strip() + " " + doctor.getFullName();
    }
}
