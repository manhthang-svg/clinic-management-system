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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import clinic.backend.dto.request.DutyScheduleSaveRequest;
import clinic.backend.dto.request.WeeklyBatchScheduleRequest;
import clinic.backend.dto.response.DutyScheduleItemResponse;
import clinic.backend.dto.response.PersonalDutyScheduleResponse;
import clinic.backend.dto.response.DutyShiftConfigResponse;
import clinic.backend.dto.response.WeeklyScheduleResponse;
import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.enums.DutyShift;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.Role;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.VisitStatus;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.DutyScheduleService;
import clinic.backend.service.DutyShiftConfigService;
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
    private final DutyShiftConfigService dutyShiftConfigService;
    private final Clock clock;
    private final UuidV7Generator uuidV7Generator;
    private final VisitRepository visitRepository;
    private final ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;

    public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final LocalTime MORNING_SHIFT_END = LocalTime.of(11, 30);
    public static final LocalTime AFTERNOON_SHIFT_END = LocalTime.of(17, 30);

    /**
     * Kiểm tra xem ca trực (workDate, shift) đã kết thúc hoặc thuộc thời gian quá khứ hay chưa theo giờ Việt Nam.
     * 
     * Quy tắc:
     * - Nếu ngày trực nhỏ hơn ngày hôm nay theo giờ VN: Đã qua (bị khóa).
     * - Nếu ngày trực lớn hơn ngày hôm nay: Thuộc tương lai (hợp lệ).
     * - Nếu ngày trực là hôm nay:
     *   + Ca Sáng: Đã kết thúc nếu hiện tại sau giờ kết thúc ca sáng (hoặc sau 11:59).
     *   + Ca Chiều: Đã kết thúc nếu hiện tại sau giờ kết thúc ca chiều (hoặc sau 23:59).
     *
     * @param workDate Ngày trực cần kiểm tra
     * @param shift    Ca trực (MORNING hoặc AFTERNOON)
     * @return true nếu ca trực đã trôi qua / đã kết thúc
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
            DutyShiftConfigResponse cfg = dutyShiftConfigService.getConfigForDate(workDate);
            LocalTime morningEnd = LocalTime.parse(cfg.getMorningEndTime());
            LocalTime afternoonEnd = LocalTime.parse(cfg.getAfternoonEndTime());

            if (shift == DutyShift.MORNING && (currentTimeVN.isAfter(morningEnd) || currentTimeVN.isAfter(LocalTime.of(11, 59)))) {
                return true;
            }
            if (shift == DutyShift.AFTERNOON && (currentTimeVN.isAfter(afternoonEnd) || currentTimeVN.isAfter(LocalTime.of(23, 59)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * BR-113: Kiểm tra phòng khám hoặc nhân viên có lượt khám / dịch vụ đang chờ hoặc dở dang hay không
     * trước khi xóa hoặc thay đổi nhân sự ca trực trong ngày hiện tại.
     */
    private void checkWorkInProgress(DutySchedule schedule, String actionDescription) {
        if (schedule == null || schedule.getWorkDate() == null) {
            return;
        }
        ZonedDateTime nowVN = clock.instant().atZone(VIETNAM_ZONE);
        LocalDate todayVN = nowVN.toLocalDate();
        if (!schedule.getWorkDate().isEqual(todayVN)) {
            return;
        }

        // 1. Kiểm tra nhân viên có lượt khám hoặc dịch vụ dở dang không
        if (schedule.getStaffAccountId() != null) {
            long visitsInProgress = visitRepository.countByDoctorAccountIdAndStatus(
                    schedule.getStaffAccountId(), VisitStatus.IN_EXAMINATION);
            long ordersInProgress = paraclinicalOrderDetailRepository.countByPerformerAccountIdAndStatus(
                    schedule.getStaffAccountId(), ParaclinicalOrderDetailStatus.IN_PROGRESS);

            if (visitsInProgress > 0 || ordersInProgress > 0) {
                StaffAccount staff = staffAccountRepository.findById(schedule.getStaffAccountId()).orElse(null);
                String staffName = staff != null ? staff.getFullName() : "Nhân viên";
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        staffName + " đang có lượt khám hoặc dịch vụ đang thực hiện dở dang. Vui lòng hoàn tất trước khi " + actionDescription + ".");
            }
        }

        // 2. Kiểm tra phòng khám có bệnh nhân đang chờ hoặc đang khám trong ca trực này không
        if (schedule.getRoomId() == null) {
            return;
        }
        Room room = roomRepository.findById(schedule.getRoomId()).orElse(null);
        if (room == null) {
            return;
        }
        String roomName = room.getRoomName() != null ? room.getRoomName() : "phòng khám";

        LocalTime currentTimeVN = nowVN.toLocalTime();
        Optional<DutyShift> currentShiftOpt = dutyShiftConfigService.shiftAt(todayVN, currentTimeVN);
        Optional<DutyShift> regShiftOpt = dutyShiftConfigService.registrationShift(todayVN, currentTimeVN);

        boolean isCurrentOrRegShift = (currentShiftOpt.isPresent() && currentShiftOpt.get() == schedule.getShift())
                || (regShiftOpt.isPresent() && regShiftOpt.get() == schedule.getShift());

        if (isCurrentOrRegShift) {
            if (room.getType() == RoomType.EXAMINATION) {
                List<VisitStatus> activeStatuses = List.of(
                        VisitStatus.WAITING,
                        VisitStatus.IN_EXAMINATION,
                        VisitStatus.WAITING_PARACLINICAL,
                        VisitStatus.WAITING_CONCLUSION
                );
                List<Visit> activeVisits = visitRepository.findAllByRoomIdAndQueueDateAndStatusIn(
                        schedule.getRoomId(), todayVN, activeStatuses
                );
                if (!activeVisits.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Phòng " + roomName + " đang có " + activeVisits.size()
                                    + " bệnh nhân đang chờ hoặc đang khám trong ca trực này. Không thể " + actionDescription + ".");
                }
            } else if (room.getType() == RoomType.PARACLINICAL) {
                Instant startOfDay = todayVN.atStartOfDay(VIETNAM_ZONE).toInstant();
                List<ParaclinicalOrderDetail> queueDetails = paraclinicalOrderDetailRepository.findQueueDetails(
                        schedule.getRoomId(), startOfDay
                );
                long activeOrders = queueDetails.stream()
                        .filter(d -> d.getStatus() == ParaclinicalOrderDetailStatus.WAITING
                                || d.getStatus() == ParaclinicalOrderDetailStatus.IN_PROGRESS)
                        .count();
                if (activeOrders > 0) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Phòng " + roomName + " đang có " + activeOrders
                                    + " dịch vụ cận lâm sàng đang chờ hoặc đang thực hiện trong ca trực này. Không thể " + actionDescription + ".");
                }
            }
        }
    }

    /**
     * Lấy toàn bộ ma trận phân công lịch trực của tất cả các phòng khám trong 1 tuần (từ Thứ 2 đến Chủ Nhật).
     *
     * @param startDate Ngày bắt đầu tuần (mặc định lấy Thứ 2 của tuần hiện tại nếu null)
     * @param endDate   Ngày kết thúc tuần (mặc định bằng startDate + 6 ngày nếu null)
     * @return Đối tượng WeeklyScheduleResponse gồm cấu hình giờ ca trực và danh sách các ca trực trong tuần
     */
    @Override
    @Transactional(readOnly = true)
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

    /**
     * Lấy lịch trực theo tuần nhưng chỉ lọc riêng cho một phòng khám/phòng kỹ thuật cụ thể (UC-19).
     * Dùng để xác định danh sách bác sĩ/KTV phụ trách phòng đó trong tuần.
     *
     * @param roomId    ID định danh của phòng
     * @param startDate Ngày bắt đầu tuần
     * @param endDate   Ngày kết thúc tuần
     * @return Lịch trực tuần của riêng phòng được chỉ định
     */
    @Override
    @Transactional(readOnly = true)
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

    /**
     * Phân công mới (Tạo mới) hoặc Cập nhật 1 ca trực đơn lẻ cho nhân viên tại 1 phòng khám.
     *
     * Quy trình xử lý & kiểm tra ràng buộc (Business Rules):
     * 1. Chặn thao tác trên ca trực đã kết thúc hoặc trong quá khứ theo giờ Việt Nam.
     * 2. Kiểm tra tài khoản nhân viên và phòng khám tồn tại trong hệ thống.
     * 3. Ràng buộc vai trò: Bác sĩ (DOCTOR) chỉ trực phòng lâm sàng (EXAMINATION);
     *    Kỹ thuật viên (TECHNICIAN) chỉ trực phòng cận lâm sàng (PARACLINICAL).
     * 4. Nếu là CẬP NHẬT (id != null):
     *    - BR-113: Nếu thay đổi Bác sĩ/KTV hoặc đổi phòng trong ngày hôm nay, kiểm tra xem
     *      nhân viên/phòng đó có lượt khám hoặc dịch vụ dở dang hay không. Nếu có -> từ chối đổi.
     *    - Kiểm tra nhân viên không bị trùng ca trực khác trong cùng buổi (trừ ca hiện tại).
     *    - Kiểm tra phòng khám không bị phân 2 người cùng 1 ca (trừ ca hiện tại).
     * 5. Nếu là TẠO MỚI (id == null):
     *    - Kiểm tra nhân viên chưa có lịch trực nào khác trong ca này.
     *    - Kiểm tra phòng khám chưa có ai trực trong ca này.
     *    - Khởi tạo Entity với UUIDv7 và lưu xuống Database.
     *
     * @param request       Dữ liệu yêu cầu phân công ca trực
     * @param currentUserId ID của Admin đang thực hiện thao tác
     * @return Bản ghi ca trực chi tiết sau khi đã lưu thành công
     */
    @Override
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

            // BR-113: Nếu thay đổi nhân viên hoặc thay đổi phòng trong ngày hôm nay, kiểm tra xem có bệnh nhân hoặc công việc dở dang không
            boolean isStaffChanged = !schedule.getStaffAccountId().equals(request.getStaffAccountId());
            boolean isRoomChanged = !schedule.getRoomId().equals(request.getRoomId());
            if (isStaffChanged || isRoomChanged) {
                checkWorkInProgress(schedule, "thay đổi nhân sự hoặc phòng trực");
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

            schedule.update(request.getStaffAccountId(), request.getRoomId(), request.getNotes(), currentUserId, now);
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

    /**
     * Lưu hàng loạt lịch trực cho một tuần (thường dùng cho tính năng "Sao chép tuần trước").
     *
     * Quy trình xử lý:
     * 1. Kiểm tra tuần đích không được nằm trọn trong quá khứ.
     * 2. Xác định mốc xóa an toàn `deleteFrom`: Chỉ xóa các ngày từ hôm nay trở đi,
     *    tuyệt đối không xóa các ca trực thuộc ngày đã qua trong quá khứ.
     * 3. BR-113: Kiểm tra các ca trực ngày hôm nay xem có phòng nào đang có bệnh nhân chờ/khám không.
     *    Nếu có -> từ chối sao chép để tránh ghi đè làm mất phiên trực đang làm việc.
     * 4. Xóa các ca trực cũ từ mốc `deleteFrom` đến `endDate`.
     * 5. Lọc và chuẩn hóa danh sách ca mới: Bỏ qua ca quá khứ, kiểm tra vai trò nhân viên - loại phòng,
     *    kiểm tra không trùng phòng và không trùng người trong cùng buổi.
     * 6. Lưu danh sách ca trực mới hàng loạt (`saveAll`) và trả về ma trận tuần mới.
     *
     * @param request       Yêu cầu lưu hàng loạt kèm danh sách các ca trực
     * @param currentUserId ID của Admin thực hiện
     * @return Toàn bộ lịch trực tuần sau khi đã sao chép/cập nhật
     */
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

        // BR-113: Kiểm tra xem các ca trực bị xóa đè trong ngày hôm nay có ca nào đang có bệnh nhân dở dang không
        for (DutySchedule s : existing) {
            if (s.getWorkDate().isEqual(todayVN)) {
                checkWorkInProgress(s, "sao chép lịch trực đè lên ngày hôm nay");
            }
        }

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

    /**
     * Xóa / Hủy bỏ phân công một ca trực theo ID (Admin thực hiện).
     *
     * Quy tắc an toàn:
     * 1. Không cho phép xóa ca trực đã kết thúc hoặc trong quá khứ.
     * 2. BR-113: Từ chối xóa nếu phòng khám đang có bệnh nhân chờ khám hoặc nhân viên đang có lượt khám dở dang.
     *
     * @param id ID của bản ghi ca trực cần xóa
     */
    @Override
    public void deleteDuty(UUID id) {
        DutySchedule schedule = dutyScheduleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch trực cần xoá"));
        if (isPastShift(schedule.getWorkDate(), schedule.getShift())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể xoá ca trực đã kết thúc hoặc trong quá khứ");
        }
        // BR-113: Kiểm tra phòng khám hoặc nhân viên có bệnh nhân/công việc dở dang không
        checkWorkInProgress(schedule, "xoá ca trực");
        dutyScheduleRepository.delete(schedule);
    }

    /**
     * Chuyển đổi danh sách thực thể DutySchedule sang đối tượng phản hồi tuần WeeklyScheduleResponse.
     * Tải trước toàn bộ phòng và tài khoản vào Map để tối ưu hiệu năng (tránh N+1 query).
     *
     * @param startDate Ngày bắt đầu tuần
     * @param endDate   Ngày kết thúc tuần
     * @param schedules Danh sách entity các ca trực truy vấn từ DB
     * @return Đối tượng response đóng gói đầy đủ dữ liệu cho giao diện ma trận tuần
     */
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
                .shiftConfig(dutyShiftConfigService.getConfigForDate(startDate))
                .items(items)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PersonalDutyScheduleResponse getPersonalSchedule(UUID accountId, LocalDate startDate, LocalDate endDate) {
        List<DutySchedule> schedules = dutyScheduleRepository
                .findByStaffAccountIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(accountId, startDate, endDate);
        Set<UUID> roomIds = schedules.stream().map(DutySchedule::getRoomId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Room> rooms = roomRepository.findAllById(roomIds).stream()
                .collect(Collectors.toMap(Room::getId, room -> room));
        return new PersonalDutyScheduleResponse(schedules.stream().map(schedule -> {
            Room room = rooms.get(schedule.getRoomId());
            var config = dutyShiftConfigService.getConfigForDate(schedule.getWorkDate());
            boolean morning = schedule.getShift() == DutyShift.MORNING;
            return new PersonalDutyScheduleResponse.Shift(
                    schedule.getId(), schedule.getStaffAccountId(), schedule.getWorkDate(), schedule.getShift(),
                    schedule.getRoomId(), room == null ? null : room.getRoomCode(),
                    room == null ? null : room.getRoomName(),
                    morning ? config.getMorningStartTime() : config.getAfternoonStartTime(),
                    morning ? config.getMorningEndTime() : config.getAfternoonEndTime());
        }).toList());
    }

    /**
     * Ánh xạ một bản ghi thực thể DutySchedule sang DTO DutyScheduleItemResponse hiển thị trên giao diện.
     * Ghép học hàm/học vị vào họ tên bác sĩ/KTV, đính kèm tên phòng, mã phòng và người phân công.
     *
     * @param schedule Bản ghi ca trực từ database
     * @param staffMap Bản đồ tra cứu nhanh tài khoản nhân viên theo ID
     * @param roomMap  Bản đồ tra cứu nhanh phòng theo ID
     * @return DTO chứa đầy đủ thông tin ca trực
     */
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
