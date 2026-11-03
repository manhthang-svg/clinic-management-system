package clinic.backend.service.impl;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import clinic.backend.dto.request.DutyScheduleSaveRequest;
import clinic.backend.dto.request.WeeklyBatchScheduleRequest;
import clinic.backend.dto.response.DutyScheduleItemResponse;
import clinic.backend.dto.response.WeeklyScheduleResponse;
import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.DutyShift;
import clinic.backend.enums.Role;
import clinic.backend.enums.RoomType;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.service.DutyScheduleService;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class DutyScheduleServiceImpl implements DutyScheduleService {

    private final DutyScheduleRepository dutyScheduleRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final RoomRepository roomRepository;
    private final Clock clock;
    private final UuidV7Generator uuidV7Generator;

    public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final LocalTime MORNING_SHIFT_END = LocalTime.of(11, 30);
    public static final LocalTime AFTERNOON_SHIFT_END = LocalTime.of(17, 30);

    /**
     * Kiểm tra xem ca trực (workDate, shift) đã kết thúc hoặc thuộc thời gian quá khứ hay chưa theo giờ Việt Nam
     */
    private boolean isPastShift(LocalDate workDate, DutyShift shift) {
        if (workDate == null) return false;
        ZonedDateTime nowVN = clock.instant().atZone(VIETNAM_ZONE);
        LocalDate todayVN = nowVN.toLocalDate();
        LocalTime currentTimeVN = nowVN.toLocalTime();

        if (workDate.isBefore(todayVN)) {
            return true;
        }
        if (workDate.isEqual(todayVN) && shift != null) {
            if (shift == DutyShift.MORNING && currentTimeVN.isAfter(MORNING_SHIFT_END)) {
                return true;
            }
            if (shift == DutyShift.AFTERNOON && currentTimeVN.isAfter(AFTERNOON_SHIFT_END)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    // lay lich truc trong 1 tuan
    public WeeklyScheduleResponse getWeeklySchedule(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            startDate = LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        if (endDate == null) {
            endDate = startDate.plusDays(6);
        }

        List<DutySchedule> schedules = dutyScheduleRepository.findByWorkDateBetweenOrderByWorkDateAscShiftAsc(startDate, endDate);
        return mapToWeeklyResponse(startDate, endDate, schedules);
    }

    @Override
    @Transactional(readOnly = true)
    // Lấy lịch trực theo tuần nhưng chỉ lọc riêng cho một phòng cụ thể
    public WeeklyScheduleResponse getWeeklyScheduleByRoom(UUID roomId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            startDate = LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        if (endDate == null) {
            endDate = startDate.plusDays(6);
        }

        List<DutySchedule> schedules = dutyScheduleRepository.findByRoomIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(
                roomId, startDate, endDate
        );
        return mapToWeeklyResponse(startDate, endDate, schedules);
    }

    @Override
    // Tạo mới hoặc cập nhật 1 ca trực đơn lẻ.
    public DutyScheduleItemResponse saveDuty(DutyScheduleSaveRequest request, UUID currentUserId) {
        if (isPastShift(request.getWorkDate(), request.getShift())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể phân công hoặc chỉnh sửa ca trực đã kết thúc hoặc trong quá khứ");
        }

        StaffAccount staff = staffAccountRepository.findById(request.getStaffAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên"));

        if (request.getRoomId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn phòng làm việc");
        }

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phòng làm việc"));

        // Validate: Bác sĩ chỉ chọn phòng lâm sàng, Kỹ thuật viên chỉ chọn phòng cận lâm sàng
        if (staff.getRole() == Role.DOCTOR) {
            if (room.getType() != RoomType.EXAMINATION) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bác sĩ chỉ có thể trực tại phòng khám lâm sàng");
            }
        } else if (staff.getRole() == Role.TECHNICIAN) {
            if (room.getType() != RoomType.PARACLINICAL) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kỹ thuật viên chỉ có thể trực tại phòng cận lâm sàng");
            }
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ có Bác sĩ và Kỹ thuật viên mới được phân công ca trực");
        }

        Instant now = clock.instant();
        DutySchedule schedule;

        if (request.getId() != null) {
            schedule = dutyScheduleRepository.findById(request.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch trực"));

            if (isPastShift(schedule.getWorkDate(), schedule.getShift())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể chỉnh sửa ca trực đã kết thúc hoặc trong quá khứ");
            }

            // Kiểm tra nhân viên không trùng ca trong ngày
            boolean existsDuplicateStaff = dutyScheduleRepository.existsByWorkDateAndShiftAndStaffAccountIdAndIdNot(
                    request.getWorkDate(), request.getShift(), request.getStaffAccountId(), request.getId()
            );
            if (existsDuplicateStaff) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhân viên đã được phân công ca trực này trong ngày");
            }

            // Kiểm tra mỗi phòng mỗi ca chỉ có 1 người trực
            boolean existsDuplicateRoom = dutyScheduleRepository.existsByRoomIdAndWorkDateAndShiftAndIdNot(
                    request.getRoomId(), request.getWorkDate(), request.getShift(), request.getId()
            );
            if (existsDuplicateRoom) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phòng này đã có người trực trong ca làm việc này");
            }

            schedule.update(request.getRoomId(), request.getNotes(), currentUserId, now);
        } else {
            // Kiểm tra nhân viên không trùng ca trong ngày
            dutyScheduleRepository.findByWorkDateAndShiftAndStaffAccountId(
                    request.getWorkDate(), request.getShift(), request.getStaffAccountId()
            ).ifPresent(existing -> {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhân viên đã được phân công ca trực này trong ngày");
            });

            // Kiểm tra mỗi phòng mỗi ca chỉ có 1 người trực
            dutyScheduleRepository.findByRoomIdAndWorkDateAndShift(
                    request.getRoomId(), request.getWorkDate(), request.getShift()
            ).ifPresent(existing -> {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phòng này đã có người trực trong ca làm việc này");
            });

            schedule = new DutySchedule(
                    uuidV7Generator.next(),
                    now,
                    request.getWorkDate(),
                    request.getShift(),
                    request.getStaffAccountId(),
                    request.getRoomId(),
                    request.getNotes(),
                    currentUserId
            );
        }

        DutySchedule saved = dutyScheduleRepository.save(schedule);

        Map<UUID, StaffAccount> staffMap = staffAccountRepository.findAll().stream()
                .collect(Collectors.toMap(StaffAccount::getId, s -> s, (s1, s2) -> s1));
        Map<UUID, Room> roomMap = roomRepository.findAll().stream()
                .collect(Collectors.toMap(Room::getId, r -> r, (r1, r2) -> r1));

        return toResponse(saved, staffMap, roomMap);
    }

    @Override
    public WeeklyScheduleResponse batchSaveWeeklySchedule(WeeklyBatchScheduleRequest request, UUID currentUserId) {
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();

        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày bắt đầu không thể sau ngày kết thúc");
        }

        ZonedDateTime nowVN = clock.instant().atZone(VIETNAM_ZONE);
        LocalDate todayVN = nowVN.toLocalDate();
        if (endDate.isBefore(todayVN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể sao chép lịch trực cho tuần đã qua trong quá khứ");
        }

        LocalDate deleteFrom = startDate.isBefore(todayVN) ? todayVN : startDate;
        List<DutySchedule> existing = dutyScheduleRepository.findByWorkDateBetweenOrderByWorkDateAscShiftAsc(deleteFrom, endDate);
        dutyScheduleRepository.deleteAll(existing);
        dutyScheduleRepository.flush();

        Instant now = clock.instant();
        List<DutySchedule> newSchedules = new ArrayList<>();

        Map<UUID, StaffAccount> staffMap = staffAccountRepository.findAll().stream()
                .collect(Collectors.toMap(StaffAccount::getId, s -> s, (s1, s2) -> s1));
        Map<UUID, Room> roomMap = roomRepository.findAll().stream()
                .collect(Collectors.toMap(Room::getId, r -> r, (r1, r2) -> r1));

        Set<String> roomShiftKeys = new HashSet<>();
        Set<String> staffShiftKeys = new HashSet<>();

        if (request.getItems() != null) {
            for (DutyScheduleSaveRequest item : request.getItems()) {
                if (isPastShift(item.getWorkDate(), item.getShift())) {
                    continue;
                }
                if (item.getStaffAccountId() == null || item.getRoomId() == null) {
                    continue;
                }

                StaffAccount staff = staffMap.get(item.getStaffAccountId());
                Room room = roomMap.get(item.getRoomId());
                if (staff == null || room == null) {
                    continue;
                }

                // Kiểm tra vai trò nhân viên và loại phòng
                if (staff.getRole() == Role.DOCTOR && room.getType() != RoomType.EXAMINATION) {
                    continue;
                }
                if (staff.getRole() == Role.TECHNICIAN && room.getType() != RoomType.PARACLINICAL) {
                    continue;
                }
                if (staff.getRole() != Role.DOCTOR && staff.getRole() != Role.TECHNICIAN) {
                    continue;
                }

                // Kiểm tra 1 phòng 1 ca chỉ 1 người & 1 người 1 ca chỉ trực 1 lần
                String roomKey = item.getWorkDate() + "_" + item.getShift() + "_" + item.getRoomId();
                String staffKey = item.getWorkDate() + "_" + item.getShift() + "_" + item.getStaffAccountId();
                if (!roomShiftKeys.add(roomKey) || !staffShiftKeys.add(staffKey)) {
                    continue;
                }

                DutySchedule entity = new DutySchedule(
                        uuidV7Generator.next(),
                        now,
                        item.getWorkDate(),
                        item.getShift(),
                        item.getStaffAccountId(),
                        item.getRoomId(),
                        item.getNotes(),
                        currentUserId
                );
                newSchedules.add(entity);
            }
        }

        dutyScheduleRepository.saveAll(newSchedules);

        return getWeeklySchedule(startDate, endDate);
    }

    @Override
    public void deleteDuty(UUID id) {
        DutySchedule schedule = dutyScheduleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch trực cần xoá"));
        if (isPastShift(schedule.getWorkDate(), schedule.getShift())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể xoá ca trực đã kết thúc hoặc trong quá khứ");
        }
        dutyScheduleRepository.delete(schedule);
    }

    private WeeklyScheduleResponse mapToWeeklyResponse(LocalDate startDate, LocalDate endDate, List<DutySchedule> schedules) {
        Map<UUID, StaffAccount> staffMap = staffAccountRepository.findAll().stream()
                .collect(Collectors.toMap(StaffAccount::getId, s -> s, (s1, s2) -> s1));

        Map<UUID, Room> roomMap = roomRepository.findAll().stream()
                .collect(Collectors.toMap(Room::getId, r -> r, (r1, r2) -> r1));

        List<DutyScheduleItemResponse> items = schedules.stream()
                .map(s -> toResponse(s, staffMap, roomMap))
                .toList();

        return WeeklyScheduleResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .items(items)
                .build();
    }

    private DutyScheduleItemResponse toResponse(
            DutySchedule schedule,
            Map<UUID, StaffAccount> staffMap,
            Map<UUID, Room> roomMap
    ) {
        StaffAccount staff = staffMap.get(schedule.getStaffAccountId());
        StaffAccount assignedBy = schedule.getAssignedByAccountId() != null ? staffMap.get(schedule.getAssignedByAccountId()) : null;
        Room room = schedule.getRoomId() != null ? roomMap.get(schedule.getRoomId()) : null;

        String staffDisplayName = staff != null
                ? ((staff.getAcademicTitle() != null ? staff.getAcademicTitle() + " " : "") + staff.getFullName())
                : "Không xác định";

        return DutyScheduleItemResponse.builder()
                .id(schedule.getId())
                .workDate(schedule.getWorkDate())
                .shift(schedule.getShift())
                .staffAccountId(schedule.getStaffAccountId())
                .staffName(staffDisplayName)
                .staffUsername(staff != null ? staff.getUsername() : null)
                .staffRole(staff != null ? staff.getRole().name() : null)
                .roomId(schedule.getRoomId())
                .roomName(room != null ? room.getRoomName() : null)
                .roomCode(room != null ? room.getRoomCode() : null)
                .notes(schedule.getNotes())
                .assignedByAccountId(schedule.getAssignedByAccountId())
                .assignedByName(assignedBy != null ? assignedBy.getFullName() : null)
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .build();
    }
}
