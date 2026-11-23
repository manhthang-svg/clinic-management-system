package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import clinic.backend.dto.request.RejectParaclinicalOrderRequest;
import clinic.backend.dto.request.ReturnParaclinicalOrderToWaitingRequest;
import clinic.backend.dto.response.TechnicianPendingItemResponse;
import clinic.backend.dto.response.TechnicianPendingResultResponse;
import clinic.backend.dto.response.TechnicianPendingStatsResponse;
import clinic.backend.dto.response.TechnicianQueueItemResponse;
import clinic.backend.dto.response.TechnicianQueueResponse;
import clinic.backend.dto.response.TechnicianQueueStatsResponse;
import clinic.backend.dto.response.TechnicianRoomResponse;
import clinic.backend.dto.response.TechnicianSiblingOrderResponse;
import clinic.backend.entity.ClinicalExamination;
import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.ImagingResult;
import clinic.backend.entity.LabResult;
import clinic.backend.entity.MedicalHistory;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.DutyShift;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalHistoryType;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.Role;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicalExaminationRepository;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.ImagingResultRepository;
import clinic.backend.repository.LabResultRepository;
import clinic.backend.repository.MedicalHistoryRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.RoomQueueCounterRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.DutyShiftConfigService;
import clinic.backend.service.TechnicianQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TechnicianQueueServiceImpl implements TechnicianQueueService {

    // bo loc danh sach KTV (UC-66): trang thai chi nhan cac gia tri FE dung, tu khoa toi da 100 ky tu
    static final Set<String> QUEUE_STATUSES = Set.of("ALL", "WAITING", "SKIPPED", "IN_PROGRESS", "CANCELLED");
    static final Set<String> PENDING_RESULT_STATUSES = Set.of("ALL", "PENDING", "DRAFT");
    static final int MAX_SEARCH_LENGTH = 100;

    static void validateFilters(String search, String status, Set<String> allowedStatuses) {
        Map<String, String> errors = new java.util.LinkedHashMap<>();
        if (search != null && search.strip().length() > MAX_SEARCH_LENGTH) {
            errors.put("search", "TOO_LONG");
        }
        if (status != null && !status.isBlank() && !allowedStatuses.contains(status.strip().toUpperCase(Locale.ROOT))) {
            errors.put("status", "INVALID_FORMAT");
        }
        if (!errors.isEmpty()) {
            throw new AppException(ErrorCode.REQUEST_PARAMETER_INVALID, errors);
        }
    }


    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm").withZone(CLINIC_ZONE);
    private static final String AUDIT_ENTITY = "paraclinical_order_details";

    private final RoomRepository roomRepository;
    private final RoomQueueCounterRepository roomQueueCounterRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final StaffRoomAssignmentRepository staffRoomAssignmentRepository;
    private final ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final VisitRepository visitRepository;
    private final PatientRepository patientRepository;
    private final ClinicalExaminationRepository clinicalExaminationRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final DutyScheduleRepository dutyScheduleRepository;
    private final DutyShiftConfigService dutyShiftConfigService;
    private final ImagingResultRepository imagingResultRepository;
    private final LabResultRepository labResultRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    // Lấy danh sách phòng cận lâm sàng kèm thông tin KTV trực ca và quyền thao tác (canManage)
    @Override
    @Transactional(readOnly = true)
    public List<TechnicianRoomResponse> getTechnicianRooms(UUID currentUserId) {
        List<Room> rooms = roomRepository.findByTypeAndStatusOrderByRoomCodeAsc(
                RoomType.PARACLINICAL, ActivationStatus.ACTIVE);

        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, CLINIC_ZONE);
        LocalTime localTime = LocalTime.ofInstant(now, CLINIC_ZONE);
        Optional<DutyShift> currentShift = dutyShiftConfigService != null
                ? dutyShiftConfigService.shiftAt(today, localTime)
                : Optional.empty();

        Map<UUID, String> roomTechMap = new HashMap<>();

        // Kiểm tra xem hệ thống có thiết lập lịch trực cho ngày hôm nay không (DutySchedule - BR-55)
        List<DutySchedule> todaySchedules = dutyScheduleRepository
                .findByWorkDateBetweenOrderByWorkDateAscShiftAsc(today, today);

        Set<UUID> roomsWithScheduleToday = todaySchedules.stream()
                .map(DutySchedule::getRoomId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 1. Với các phòng có lịch trực: lấy đúng KTV đang trực trong ca hiện tại
        if (currentShift.isPresent() && !todaySchedules.isEmpty()) {
            Set<UUID> onDutyStaffIds = todaySchedules.stream()
                    .filter(s -> s.getShift() == currentShift.get())
                    .map(DutySchedule::getStaffAccountId)
                    .collect(Collectors.toSet());
            Map<UUID, StaffAccount> onDutyStaffMap = staffAccountRepository.findAllById(onDutyStaffIds).stream()
                    .collect(Collectors.toMap(StaffAccount::getId, Function.identity()));

            for (DutySchedule schedule : todaySchedules) {
                if (schedule.getShift() == currentShift.get() && schedule.getRoomId() != null) {
                    StaffAccount staff = onDutyStaffMap.get(schedule.getStaffAccountId());
                    if (staff != null && staff.getStatus() == AccountStatus.ACTIVE) {
                        roomTechMap.put(schedule.getRoomId(), staffDisplayName(staff.getAcademicTitle(), staff.getFullName()));
                    }
                }
            }
        }

        // 2. Với các phòng chưa cấu hình lịch trực hôm nay: fallback phân công phòng cố định nếu trong ca làm việc
        boolean hasShiftOrNoConfig = currentShift.isPresent() || dutyShiftConfigService == null;
        List<StaffRoomAssignment> activeAssignments = null;
        boolean hasRoomsWithoutSchedule = rooms.stream().anyMatch(r -> !roomsWithScheduleToday.contains(r.getId()));
        if (hasRoomsWithoutSchedule && hasShiftOrNoConfig) {
            activeAssignments = staffRoomAssignmentRepository.findByRemovedAtIsNull();
            Set<UUID> assignedStaffIds = activeAssignments.stream()
                    .map(StaffRoomAssignment::getStaffAccountId)
                    .collect(Collectors.toSet());
            Map<UUID, StaffAccount> assignedStaffMap = staffAccountRepository.findAllById(assignedStaffIds).stream()
                    .collect(Collectors.toMap(StaffAccount::getId, Function.identity()));

            for (StaffRoomAssignment assignment : activeAssignments) {
                if (!roomsWithScheduleToday.contains(assignment.getRoomId())) {
                    StaffAccount staff = assignedStaffMap.get(assignment.getStaffAccountId());
                    if (staff != null && staff.getRole() == Role.TECHNICIAN) {
                        roomTechMap.putIfAbsent(assignment.getRoomId(), staffDisplayName(staff.getAcademicTitle(), staff.getFullName()));
                    }
                }
            }
        }

        boolean isAdmin = false;
        StaffAccount currentStaff = null;
        if (currentUserId != null) {
            currentStaff = staffAccountRepository.findById(currentUserId).orElse(null);
            if (currentStaff != null && currentStaff.getRole() == Role.ADMIN) {
                isAdmin = true;
            }
        }

        final boolean userIsAdmin = isAdmin;
        final Set<UUID> manageableRoomIds = new HashSet<>();

        // Tối ưu hóa: tính toán trước quyền quản lý phòng trong bộ nhớ, loại bỏ N+1 truy vấn DB lặp lại
        if (!userIsAdmin && currentStaff != null && currentStaff.getRole() == Role.TECHNICIAN) {
            // Phòng có lịch trực: KTV phải trực đúng ca hiện tại
            if (currentShift.isPresent()) {
                for (DutySchedule s : todaySchedules) {
                    if (s.getShift() == currentShift.get() && currentUserId.equals(s.getStaffAccountId()) && s.getRoomId() != null) {
                        manageableRoomIds.add(s.getRoomId());
                    }
                }
            }
            // Phòng chưa có lịch trực: xét phân công phòng cố định nếu trong ca làm việc
            if (hasShiftOrNoConfig) {
                UUID userAssignedRoomId = null;
                if (activeAssignments != null) {
                    for (StaffRoomAssignment a : activeAssignments) {
                        if (currentUserId.equals(a.getStaffAccountId())) {
                            userAssignedRoomId = a.getRoomId();
                            break;
                        }
                    }
                } else {
                    userAssignedRoomId = staffRoomAssignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(currentUserId)
                            .map(StaffRoomAssignment::getRoomId)
                            .orElse(null);
                }
                if (userAssignedRoomId != null && !roomsWithScheduleToday.contains(userAssignedRoomId)) {
                    manageableRoomIds.add(userAssignedRoomId);
                }
            }
        }

        return rooms.stream()
                .map(room -> {
                    boolean canManage = userIsAdmin || manageableRoomIds.contains(room.getId());
                    return new TechnicianRoomResponse(
                            room.getId(),
                            room.getRoomCode(),
                            room.getRoomName(),
                            room.getLocation(),
                            roomTechMap.getOrDefault(room.getId(), "Chưa có"),
                            canManage
                    );
                })
                .toList();
    }

    // Tải danh sách hàng đợi theo phòng và tính toán 4 thẻ số liệu KPI (chờ, ưu tiên, bỏ qua)
    @Override
    @Transactional(readOnly = true)
    public TechnicianQueueResponse getQueue(UUID roomId, String search, String status, String doctorRoom, UUID currentUserId) {
        validateFilters(search, status, QUEUE_STATUSES);
        UUID targetRoomId = roomId;
        if (targetRoomId == null) {
            List<Room> rooms = roomRepository.findByTypeAndStatusOrderByRoomCodeAsc(
                    RoomType.PARACLINICAL, ActivationStatus.ACTIVE);
            if (rooms.isEmpty()) {
                return new TechnicianQueueResponse(
                        new TechnicianQueueStatsResponse(0, 0, 0, 0),
                        Collections.emptyList(),
                        false
                );
            }
            targetRoomId = rooms.getFirst().getId();
        }

        boolean canManage = canManageRoom(targetRoomId, currentUserId);
        LocalDate today = LocalDate.now(clock.withZone(CLINIC_ZONE));
        Instant startOfDay = today.atStartOfDay(CLINIC_ZONE).toInstant();

        // lay chi dinh da thanh toan
        List<ParaclinicalOrderDetail> details = paraclinicalOrderDetailRepository.findQueueDetails(targetRoomId, startOfDay);

        if (details.isEmpty()) {
            return new TechnicianQueueResponse(
                    new TechnicianQueueStatsResponse(0, 0, 0, 0),
                    Collections.emptyList(),
                    canManage
            );
        }

        Set<UUID> orderIds = details.stream().map(d -> d.getOrder().getId()).collect(Collectors.toSet());
        Set<UUID> serviceIds = details.stream().map(ParaclinicalOrderDetail::getServiceId).collect(Collectors.toSet());
        Set<UUID> recordIds = details.stream().map(d -> d.getOrder().getMedicalRecordId()).collect(Collectors.toSet());

        Map<UUID, MedicalService> serviceMap = medicalServiceRepository.findAllById(serviceIds).stream()
                .collect(Collectors.toMap(MedicalService::getId, Function.identity()));

        Map<UUID, MedicalRecord> recordMap = medicalRecordRepository.findAllById(recordIds).stream()
                .collect(Collectors.toMap(MedicalRecord::getId, Function.identity()));

        Set<UUID> visitIds = recordMap.values().stream().map(MedicalRecord::getVisitId).collect(Collectors.toSet());
        Map<UUID, Visit> visitMap = visitRepository.findAllById(visitIds).stream()
                .collect(Collectors.toMap(Visit::getId, Function.identity()));

        Set<UUID> patientIds = visitMap.values().stream().map(Visit::getPatientId).collect(Collectors.toSet());
        Map<UUID, Patient> patientMap = patientRepository.findAllById(patientIds).stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity()));

        Set<UUID> doctorAccountIds = details.stream().map(d -> d.getOrder().getDoctorAccountId()).collect(Collectors.toSet());
        Map<UUID, StaffAccount> doctorMap = staffAccountRepository.findAllById(doctorAccountIds).stream()
                .collect(Collectors.toMap(StaffAccount::getId, Function.identity()));

        Map<UUID, List<ParaclinicalOrderDetail>> siblingOrdersMap = orderIds.isEmpty() ? Collections.emptyMap() :
                paraclinicalOrderDetailRepository.findByOrderIdIn(orderIds).stream()
                        .collect(Collectors.groupingBy(d -> d.getOrder().getId()));

        Set<UUID> neededRoomIds = new HashSet<>();
        if (targetRoomId != null) {
            neededRoomIds.add(targetRoomId);
        }
        // gom dich vu cung dot chi dinh vao phong lien quan
        siblingOrdersMap.values().forEach(list -> {
            for (ParaclinicalOrderDetail sib : list) {
                if (sib.getRoomId() != null) {
                    neededRoomIds.add(sib.getRoomId());
                }
            }
        });

        // Tối ưu hóa: chỉ fetch những phòng liên quan thay vì findAll toàn bộ phòng trong viện
        Map<UUID, Room> allRoomsMap = neededRoomIds.isEmpty() ? Collections.emptyMap() :
                roomRepository.findAllById(neededRoomIds).stream()
                        .collect(Collectors.toMap(Room::getId, Function.identity()));

        // Tối ưu hóa: Batch fetch tất cả thông tin lâm sàng, tiền sử và y lệnh liên quan tránh lỗi N+1 truy vấn
        Map<UUID, ClinicalExamination> examMap = recordIds.isEmpty() ? Collections.emptyMap() :
                clinicalExaminationRepository.findByMedicalRecordIdIn(recordIds).stream()
                        .collect(Collectors.toMap(ClinicalExamination::getMedicalRecordId, Function.identity(), (a, b) -> a));

        Map<UUID, List<MedicalHistory>> historiesMap = patientIds.isEmpty() ? Collections.emptyMap() :
                medicalHistoryRepository.findByPatientIdInOrderByCreatedAtAsc(patientIds).stream()
                        .collect(Collectors.groupingBy(MedicalHistory::getPatientId));

        List<TechnicianQueueItemResponse> allItems = new ArrayList<>();
        for (ParaclinicalOrderDetail detail : details) {
            allItems.add(mapToItemResponse(
                    detail,
                    serviceMap.get(detail.getServiceId()),
                    recordMap.get(detail.getOrder().getMedicalRecordId()),
                    visitMap,
                    patientMap,
                    doctorMap.get(detail.getOrder().getDoctorAccountId()),
                    allRoomsMap,
                    examMap,
                    historiesMap,
                    siblingOrdersMap,
                    targetRoomId,
                    today
            ));
        }

        // Calculate stats on all room orders today
        long total = allItems.size();
        long waiting = allItems.stream().filter(i -> "WAITING".equals(i.status())).count();
        long priority = allItems.stream().filter(i -> "PRIORITY".equals(i.priority()) && !"CANCELLED".equals(i.status())).count();
        long skipped = allItems.stream().filter(TechnicianQueueItemResponse::isSkipped).count();
        TechnicianQueueStatsResponse statsResponse = new TechnicianQueueStatsResponse(total, waiting, priority, skipped);

        // Apply filters
        List<TechnicianQueueItemResponse> filtered = allItems.stream()
                .filter(item -> {
                    if (search != null && !search.isBlank()) {
                        String q = search.trim().toLowerCase(Locale.ROOT);
                        boolean matchName = item.patientName() != null && item.patientName().toLowerCase(Locale.ROOT).contains(q);
                        boolean matchId = item.patientId() != null && item.patientId().toLowerCase(Locale.ROOT).contains(q);
                        boolean matchCode = item.orderCode() != null && item.orderCode().toLowerCase(Locale.ROOT).contains(q);
                        boolean matchCccd = item.cccd() != null && item.cccd().toLowerCase(Locale.ROOT).contains(q);
                        boolean matchIns = item.insuranceNumber() != null && item.insuranceNumber().toLowerCase(Locale.ROOT).contains(q);
                        if (!matchName && !matchId && !matchCode && !matchCccd && !matchIns) {
                            return false;
                        }
                    }

                    if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
                        if ("WAITING".equalsIgnoreCase(status) && (!"WAITING".equals(item.status()) || item.isSkipped())) {
                            return false;
                        }
                        if ("SKIPPED".equalsIgnoreCase(status) && !item.isSkipped() && !"SKIPPED".equals(item.status())) {
                            return false;
                        }
                        if ("IN_PROGRESS".equalsIgnoreCase(status) && !"IN_PROGRESS".equals(item.status())) {
                            return false;
                        }
                        if ("CANCELLED".equalsIgnoreCase(status) && !"CANCELLED".equals(item.status())) {
                            return false;
                        }
                    }

                    if (doctorRoom != null && !doctorRoom.isBlank() && !"ALL".equalsIgnoreCase(doctorRoom)) {
                        if (!Objects.equals(item.doctorRoom(), doctorRoom)) {
                            return false;
                        }
                    }

                    return true;
                })
                .sorted((a, b) -> {
                    // 1. Ưu tiên trước
                    if ("PRIORITY".equals(a.priority()) && !"PRIORITY".equals(b.priority())) return -1;
                    if (!"PRIORITY".equals(a.priority()) && "PRIORITY".equals(b.priority())) return 1;

                    // 2. Đã có mặt (Check-in / có STT) trước người chưa có mặt
                    boolean aIn = a.isCheckedIn() || (a.queueNumber() != null && a.queueNumber() > 0);
                    boolean bIn = b.isCheckedIn() || (b.queueNumber() != null && b.queueNumber() > 0);
                    if (aIn && !bIn) return -1;
                    if (!aIn && bIn) return 1;

                    // 3. Đã check-in: sắp xếp theo số thứ tự tăng dần (thấp lên trước, cao ở sau: #01, #02, #03...)
                    if (aIn && bIn) {
                        int qA = a.queueNumber() != null ? a.queueNumber() : Integer.MAX_VALUE;
                        int qB = b.queueNumber() != null ? b.queueNumber() : Integer.MAX_VALUE;
                        if (qA != qB) return Integer.compare(qA, qB);
                    }

                    // 4. Chưa check-in hoặc bằng nhau: sắp xếp theo thời gian chỉ định
                    String tA = a.orderTime() != null ? a.orderTime() : "";
                    String tB = b.orderTime() != null ? b.orderTime() : "";
                    int cmpTime = tA.compareTo(tB);
                    if (cmpTime != 0) return cmpTime;

                    return a.id().compareTo(b.id());
                })
                .toList();

        return new TechnicianQueueResponse(statsResponse, filtered, canManage);
    }

    // Tải danh sách các ca đã thực hiện xong đang chờ nhập kết luận hoặc đang lưu nháp
    @Override
    @Transactional(readOnly = true)
    public TechnicianPendingResultResponse getPendingResults(
            UUID roomId, String search, String status, String doctorRoom, UUID currentUserId) {
        validateFilters(search, status, PENDING_RESULT_STATUSES);
        UUID targetRoomId = roomId;
        if (targetRoomId == null) {
            List<Room> rooms = roomRepository.findByTypeAndStatusOrderByRoomCodeAsc(
                    RoomType.PARACLINICAL, ActivationStatus.ACTIVE);
            if (rooms.isEmpty()) {
                return new TechnicianPendingResultResponse(
                        new TechnicianPendingStatsResponse(0, 0, 0, 0),
                        Collections.emptyList(),
                        false
                );
            }
            targetRoomId = rooms.getFirst().getId();
        }

        boolean canManage = canManageRoom(targetRoomId, currentUserId);
        LocalDate today = LocalDate.now(clock.withZone(CLINIC_ZONE));
        Instant now = clock.instant();

        List<ParaclinicalOrderDetail> details = paraclinicalOrderDetailRepository.findPendingResultsByRoom(targetRoomId);

        if (details.isEmpty()) {
            return new TechnicianPendingResultResponse(
                    new TechnicianPendingStatsResponse(0, 0, 0, 0),
                    Collections.emptyList(),
                    canManage
            );
        }

        List<UUID> detailIds = details.stream().map(ParaclinicalOrderDetail::getId).toList();
        Set<UUID> serviceIds = details.stream().map(ParaclinicalOrderDetail::getServiceId).collect(Collectors.toSet());
        Set<UUID> recordIds = details.stream().map(d -> d.getOrder().getMedicalRecordId()).collect(Collectors.toSet());

        Map<UUID, MedicalService> serviceMap = medicalServiceRepository.findAllById(serviceIds).stream()
                .collect(Collectors.toMap(MedicalService::getId, Function.identity()));

        Map<UUID, MedicalRecord> recordMap = medicalRecordRepository.findAllById(recordIds).stream()
                .collect(Collectors.toMap(MedicalRecord::getId, Function.identity()));

        Set<UUID> visitIds = recordMap.values().stream().map(MedicalRecord::getVisitId).collect(Collectors.toSet());
        Map<UUID, Visit> visitMap = visitRepository.findAllById(visitIds).stream()
                .collect(Collectors.toMap(Visit::getId, Function.identity()));

        Set<UUID> patientIds = visitMap.values().stream().map(Visit::getPatientId).collect(Collectors.toSet());
        Map<UUID, Patient> patientMap = patientRepository.findAllById(patientIds).stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity()));

        Set<UUID> doctorAccountIds = details.stream().map(d -> d.getOrder().getDoctorAccountId()).collect(Collectors.toSet());
        Map<UUID, StaffAccount> doctorMap = staffAccountRepository.findAllById(doctorAccountIds).stream()
                .collect(Collectors.toMap(StaffAccount::getId, Function.identity()));

        // Tra cứu bản nháp kết quả
        Set<UUID> draftDetailIds = new java.util.HashSet<>();
        List<ImagingResult> imagingResults = imagingResultRepository.findByOrderDetailIdIn(detailIds);
        for (ImagingResult ir : imagingResults) {
            if (ir.getSubmittedAt() == null) {
                draftDetailIds.add(ir.getOrderDetailId());
            }
        }
        List<LabResult> labResults = labResultRepository.findByOrderDetailIdIn(detailIds);
        for (LabResult lr : labResults) {
            if (lr.getSubmittedAt() == null) {
                draftDetailIds.add(lr.getOrderDetailId());
            }
        }

        List<TechnicianPendingItemResponse> allItems = new ArrayList<>();
        for (ParaclinicalOrderDetail detail : details) {
            ParaclinicalOrder order = detail.getOrder();
            MedicalRecord record = recordMap.get(order.getMedicalRecordId());
            Visit visit = record != null ? visitMap.get(record.getVisitId()) : null;
            Patient patient = visit != null ? patientMap.get(visit.getPatientId()) : null;
            MedicalService service = serviceMap.get(detail.getServiceId());
            StaffAccount doctor = doctorMap.get(order.getDoctorAccountId());

            boolean isPriority = visit != null && visit.isPriority();
            String priority = isPriority ? "PRIORITY" : "NORMAL";
            String priorityLabel = isPriority ? "Ưu tiên" : "Thường";

            Integer birthYear = null;
            Integer age = 0;
            String gender = "Khác";
            if (patient != null) {
                if (patient.getNgaysinh() != null) {
                    birthYear = patient.getNgaysinh().getYear();
                    age = Period.between(patient.getNgaysinh(), today).getYears();
                }
                gender = switch (patient.getGioitinh()) {
                    case 1 -> "Nam";
                    case 2 -> "Nữ";
                    default -> "Khác";
                };
            }

            String insuranceType = "Viện phí";
            if ((visit != null && visit.getDoituongbnLoai() == VisitDoituongbnLoai.BHYT) || order.getDoituongbn() == 1) {
                insuranceType = "BHYT";
            }

            String doctorName = doctor != null ? staffDisplayName(doctor.getAcademicTitle(), doctor.getFullName()) : "—";
            Instant completedAt = detail.getPerformedAt() != null ? detail.getPerformedAt() : detail.getNgaychidinh();
            String completedAtTime = completedAt != null ? TIME_FORMATTER.format(completedAt) : "";
            long completedMinutesAgo = completedAt != null ? java.time.Duration.between(completedAt, now).toMinutes() : 0L;

            boolean isDraft = draftDetailIds.contains(detail.getId());
            String itemStatus = isDraft ? "DRAFT" : "PENDING";

            allItems.add(new TechnicianPendingItemResponse(
                    detail.getId(),
                    detail.getQueueNumber(),
                    detail.getMachidinh(),
                    detail.getRoomId(),
                    priority,
                    priorityLabel,
                    patient != null ? patient.getMabenhnhan() : "—",
                    patient != null ? patient.getHoten() : "—",
                    birthYear,
                    age,
                    gender,
                    insuranceType,
                    visit != null ? visit.getMabhyt() : null,
                    patient != null ? patient.getCccdSo() : null,
                    patient != null ? patient.getSodienthoai() : null,
                    detail.getTenchidinh(),
                    service != null ? service.getCode() : null,
                    order.getChandoansobo() != null ? order.getChandoansobo() : "",
                    doctorName,
                    order.getNoichidinh(),
                    completedAtTime,
                    completedMinutesAgo,
                    itemStatus
            ));
        }

        // Stats calculation
        long total = allItems.size();
        long pending = allItems.stream().filter(i -> "PENDING".equals(i.status())).count();
        long draft = allItems.stream().filter(i -> "DRAFT".equals(i.status())).count();
        long overdue = allItems.stream().filter(i -> i.completedMinutesAgo() >= 30).count();
        TechnicianPendingStatsResponse statsResponse = new TechnicianPendingStatsResponse(total, pending, draft, overdue);

        // Apply filters
        List<TechnicianPendingItemResponse> filtered = allItems.stream()
                .filter(item -> {
                    if (search != null && !search.isBlank()) {
                        String q = search.trim().toLowerCase(Locale.ROOT);
                        boolean matchName = item.patientName() != null && item.patientName().toLowerCase(Locale.ROOT).contains(q);
                        boolean matchId = item.patientId() != null && item.patientId().toLowerCase(Locale.ROOT).contains(q);
                        boolean matchCode = item.orderCode() != null && item.orderCode().toLowerCase(Locale.ROOT).contains(q);
                        boolean matchCccd = item.cccd() != null && item.cccd().toLowerCase(Locale.ROOT).contains(q);
                        boolean matchIns = item.insuranceNumber() != null && item.insuranceNumber().toLowerCase(Locale.ROOT).contains(q);
                        boolean matchService = item.serviceName() != null && item.serviceName().toLowerCase(Locale.ROOT).contains(q);
                        if (!matchName && !matchId && !matchCode && !matchCccd && !matchIns && !matchService) {
                            return false;
                        }
                    }

                    if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
                        if (!status.equalsIgnoreCase(item.status())) {
                            return false;
                        }
                    }

                    if (doctorRoom != null && !doctorRoom.isBlank() && !"ALL".equalsIgnoreCase(doctorRoom)) {
                        if (!Objects.equals(item.doctorRoom(), doctorRoom)) {
                            return false;
                        }
                    }

                    return true;
                })
                .sorted((a, b) -> {
                    // Priority first
                    if ("PRIORITY".equals(a.priority()) && !"PRIORITY".equals(b.priority())) return -1;
                    if (!"PRIORITY".equals(a.priority()) && "PRIORITY".equals(b.priority())) return 1;

                    // Then by queueNumber
                    int qA = a.queueNumber() != null ? a.queueNumber() : Integer.MAX_VALUE;
                    int qB = b.queueNumber() != null ? b.queueNumber() : Integer.MAX_VALUE;
                    return Integer.compare(qA, qB);
                })
                .toList();

        return new TechnicianPendingResultResponse(statsResponse, filtered, canManage);
    }

    // Lấy thông tin chi tiết một y lệnh cận lâm sàng kèm hồ sơ bệnh án và tiền sử bệnh
    @Override
    @Transactional(readOnly = true)
    public TechnicianQueueItemResponse getQueueDetail(UUID detailId) {
        ParaclinicalOrderDetail detail = paraclinicalOrderDetailRepository.findById(detailId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));

        LocalDate today = LocalDate.now(clock.withZone(CLINIC_ZONE));
        MedicalService service = medicalServiceRepository.findById(detail.getServiceId()).orElse(null);
        MedicalRecord record = medicalRecordRepository.findById(detail.getOrder().getMedicalRecordId()).orElse(null);
        Map<UUID, Visit> visitMap = record != null
                ? visitRepository.findAllById(List.of(record.getVisitId())).stream().collect(Collectors.toMap(Visit::getId, Function.identity()))
                : Collections.emptyMap();
        Map<UUID, Patient> patientMap = new HashMap<>();
        if (!visitMap.isEmpty()) {
            Visit v = visitMap.values().iterator().next();
            patientRepository.findById(v.getPatientId()).ifPresent(p -> patientMap.put(p.getId(), p));
        }
        StaffAccount doctor = staffAccountRepository.findById(detail.getOrder().getDoctorAccountId()).orElse(null);
        Map<UUID, Room> roomMap = roomRepository.findAll().stream().collect(Collectors.toMap(Room::getId, Function.identity()));

        Map<UUID, ClinicalExamination> examMap = record != null
                ? clinicalExaminationRepository.findByMedicalRecordId(record.getId())
                        .map(e -> Map.of(record.getId(), e))
                        .orElse(Collections.emptyMap())
                : Collections.emptyMap();

        Map<UUID, List<MedicalHistory>> historiesMap = new HashMap<>();
        for (Patient p : patientMap.values()) {
            historiesMap.put(p.getId(), medicalHistoryRepository.findByPatientIdOrderByCreatedAtAsc(p.getId()));
        }

        Map<UUID, List<ParaclinicalOrderDetail>> siblingOrdersMap = Map.of(
                detail.getOrder().getId(),
                paraclinicalOrderDetailRepository.findByOrderId(detail.getOrder().getId())
        );

        return mapToItemResponse(
                detail, service, record, visitMap, patientMap, doctor, roomMap,
                examMap, historiesMap, siblingOrdersMap,
                detail.getRoomId(), today
        );
    }

    // Xác nhận bệnh nhân có mặt tại phòng và cấp phát số thứ tự (STT) hàng đợi tự động
    @Override
    @Transactional
    public TechnicianQueueItemResponse checkInOrder(UUID detailId, UUID technicianAccountId) {
        Instant now = clock.instant();
        ParaclinicalOrderDetail detail = paraclinicalOrderDetailRepository.findById(detailId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));

        verifyRoomPermission(detail.getRoomId(), technicianAccountId);

        if (detail.isCheckedIn()) {
            throw new AppException(ErrorCode.ORDER_ALREADY_CHECKED_IN);
        }

        if (detail.getStatus() == ParaclinicalOrderDetailStatus.PENDING_PAYMENT) {
            throw new AppException(ErrorCode.PARACLINICAL_ORDER_NOT_PAID);
        }

        if (detail.getStatus() != ParaclinicalOrderDetailStatus.WAITING) {
            throw new AppException(ErrorCode.PARACLINICAL_SERVICE_NOT_IN_PROGRESS);
        }

        LocalDate today = LocalDate.now(clock.withZone(CLINIC_ZONE));
        Instant startOfDay = today.atStartOfDay(CLINIC_ZONE).toInstant();
        Instant endOfDay = today.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();

        int nextQueueNumber = roomQueueCounterRepository.takeNextParaclinical(
                detail.getRoomId(), today, startOfDay, endOfDay);

        detail.checkIn(nextQueueNumber, technicianAccountId, now);
        paraclinicalOrderDetailRepository.save(detail);

        // Check in all sibling details of the same order in the same room that are WAITING
        List<ParaclinicalOrderDetail> checkInSiblings = paraclinicalOrderDetailRepository
                .findByOrderId(detail.getOrder().getId());
        for (ParaclinicalOrderDetail sib : checkInSiblings) {
            if (!sib.getId().equals(detail.getId())
                    && Objects.equals(sib.getRoomId(), detail.getRoomId())
                    && sib.getStatus() == ParaclinicalOrderDetailStatus.WAITING
                    && !sib.isCheckedIn()) {
                sib.checkIn(nextQueueNumber, technicianAccountId, now);
                paraclinicalOrderDetailRepository.save(sib);

                Map<String, Object> sibAudit = new LinkedHashMap<>();
                sibAudit.put("action", "CHECK_IN");
                sibAudit.put("queueNumber", nextQueueNumber);
                sibAudit.put("checkedInAt", now.toString());
                auditLogService.recordBusinessChange(
                        technicianAccountId, AuditLogAction.UPDATE, AUDIT_ENTITY, sib.getId(),
                        sib.getMachidinh(), sibAudit, now);
            }
        }

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("action", "CHECK_IN");
        audit.put("queueNumber", nextQueueNumber);
        audit.put("checkedInAt", now.toString());
        auditLogService.recordBusinessChange(
                technicianAccountId, AuditLogAction.UPDATE, AUDIT_ENTITY, detail.getId(),
                detail.getMachidinh(), audit, now);

        return getQueueDetail(detailId);
    }

    // Tiếp nhận bệnh nhân vào phòng thực hiện kỹ thuật và chuyển trạng thái sang IN_PROGRESS
    @Override
    @Transactional
    public TechnicianQueueItemResponse acceptOrder(UUID detailId, UUID technicianAccountId) {
        Instant now = clock.instant();
        ParaclinicalOrderDetail detail = paraclinicalOrderDetailRepository.findById(detailId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));

        verifyRoomPermission(detail.getRoomId(), technicianAccountId);

        if (!detail.isCheckedIn()) {
            throw new AppException(ErrorCode.ORDER_NOT_CHECKED_IN);
        }

        detail.accept(technicianAccountId, now);
        paraclinicalOrderDetailRepository.save(detail);

        // Accept all sibling details of the same order in the same room that are WAITING & checked in
        List<ParaclinicalOrderDetail> acceptSiblings = paraclinicalOrderDetailRepository
                .findByOrderId(detail.getOrder().getId());
        for (ParaclinicalOrderDetail sib : acceptSiblings) {
            if (!sib.getId().equals(detail.getId())
                    && Objects.equals(sib.getRoomId(), detail.getRoomId())
                    && sib.getStatus() == ParaclinicalOrderDetailStatus.WAITING
                    && sib.isCheckedIn()) {
                sib.accept(technicianAccountId, now);
                paraclinicalOrderDetailRepository.save(sib);

                Map<String, Object> sibAudit = new LinkedHashMap<>();
                sibAudit.put("action", "ACCEPT");
                sibAudit.put("status", sib.getStatus().name());
                auditLogService.recordBusinessChange(
                        technicianAccountId, AuditLogAction.UPDATE, AUDIT_ENTITY, sib.getId(),
                        sib.getMachidinh(), sibAudit, now);
            }
        }

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("action", "ACCEPT");
        audit.put("status", detail.getStatus().name());
        auditLogService.recordBusinessChange(
                technicianAccountId, AuditLogAction.UPDATE, AUDIT_ENTITY, detail.getId(),
                detail.getMachidinh(), audit, now);

        return getQueueDetail(detailId);
    }

    // Đánh dấu tạm bỏ qua bệnh nhân vắng mặt khi gọi tên và chuyển vào tab Đã bỏ qua
    @Override
    @Transactional
    public TechnicianQueueItemResponse skipOrder(UUID detailId, UUID technicianAccountId) {
        Instant now = clock.instant();
        ParaclinicalOrderDetail detail = paraclinicalOrderDetailRepository.findById(detailId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));

        verifyRoomPermission(detail.getRoomId(), technicianAccountId);

        detail.skip(now);
        paraclinicalOrderDetailRepository.save(detail);

        List<ParaclinicalOrderDetail> skipSiblings = paraclinicalOrderDetailRepository
                .findByOrderId(detail.getOrder().getId());
        for (ParaclinicalOrderDetail sib : skipSiblings) {
            if (!sib.getId().equals(detail.getId())
                    && Objects.equals(sib.getRoomId(), detail.getRoomId())
                    && sib.getStatus() == ParaclinicalOrderDetailStatus.WAITING) {
                sib.skip(now);
                paraclinicalOrderDetailRepository.save(sib);
            }
        }

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("action", "SKIP");
        audit.put("isSkipped", true);
        auditLogService.recordBusinessChange(
                technicianAccountId, AuditLogAction.UPDATE, AUDIT_ENTITY, detail.getId(),
                detail.getMachidinh(), audit, now);

        return getQueueDetail(detailId);
    }

    // Tiếp nhận lại bệnh nhân vắng mặt quay lại phòng và khôi phục vào hàng đợi
    @Override
    @Transactional
    public TechnicianQueueItemResponse unskipOrder(UUID detailId, UUID technicianAccountId) {
        Instant now = clock.instant();
        ParaclinicalOrderDetail detail = paraclinicalOrderDetailRepository.findById(detailId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));

        verifyRoomPermission(detail.getRoomId(), technicianAccountId);

        detail.unskip(now);
        paraclinicalOrderDetailRepository.save(detail);

        List<ParaclinicalOrderDetail> unskipSiblings = paraclinicalOrderDetailRepository
                .findByOrderId(detail.getOrder().getId());
        for (ParaclinicalOrderDetail sib : unskipSiblings) {
            if (!sib.getId().equals(detail.getId())
                    && Objects.equals(sib.getRoomId(), detail.getRoomId())
                    && sib.isSkipped()) {
                sib.unskip(now);
                paraclinicalOrderDetailRepository.save(sib);
            }
        }

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("action", "UNSKIP");
        audit.put("isSkipped", false);
        auditLogService.recordBusinessChange(
                technicianAccountId, AuditLogAction.UPDATE, AUDIT_ENTITY, detail.getId(),
                detail.getMachidinh(), audit, now);

        return getQueueDetail(detailId);
    }

    // UC-66: KTV xac nhan da thuc hien xong, dich vu chuyen sang cho nhap ket qua
    @Override
    @Transactional
    public TechnicianQueueItemResponse completeOrder(UUID detailId, UUID technicianAccountId) {
        Instant now = clock.instant();
        ParaclinicalOrderDetail detail = paraclinicalOrderDetailRepository.findById(detailId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));

        // KTV phai truc phong nay va dang lam dich vu nay
        verifyRoomPermission(detail.getRoomId(), technicianAccountId);
        requireInProgressBy(detail, technicianAccountId);

        // dich vu -> cho nhap ket qua
        detail.completePerformance(technicianAccountId, now);
        paraclinicalOrderDetailRepository.save(detail);

        // cac dich vu khac cung phieu, cung phong, KTV nay dang lam thi xong cung luc
        List<ParaclinicalOrderDetail> completeSiblings = paraclinicalOrderDetailRepository
                .findByOrderId(detail.getOrder().getId());
        for (ParaclinicalOrderDetail sib : completeSiblings) {
            if (!sib.getId().equals(detail.getId())
                    && Objects.equals(sib.getRoomId(), detail.getRoomId())
                    && sib.getStatus() == ParaclinicalOrderDetailStatus.IN_PROGRESS
                    && Objects.equals(sib.getPerformerAccountId(), technicianAccountId)) {
                sib.completePerformance(technicianAccountId, now);
                paraclinicalOrderDetailRepository.save(sib);

                Map<String, Object> sibAudit = new LinkedHashMap<>();
                sibAudit.put("action", "COMPLETE_PERFORMANCE");
                sibAudit.put("status", sib.getStatus().name());
                auditLogService.recordBusinessChange(
                        technicianAccountId, AuditLogAction.UPDATE, AUDIT_ENTITY, sib.getId(),
                        sib.getMachidinh(), sibAudit, now);
            }
        }

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("action", "COMPLETE_PERFORMANCE");
        audit.put("status", detail.getStatus().name());
        auditLogService.recordBusinessChange(
                technicianAccountId, AuditLogAction.UPDATE, AUDIT_ENTITY, detail.getId(),
                detail.getMachidinh(), audit, now);

        return getQueueDetail(detailId);
    }

    // UC-66: tra dich vu dang lam ve hang cho (giu so thu tu) kem ly do
    @Override
    @Transactional
    public TechnicianQueueItemResponse returnToWaiting(
            UUID detailId, UUID technicianAccountId, ReturnParaclinicalOrderToWaitingRequest request) {
        Instant now = clock.instant();
        ParaclinicalOrderDetail detail = paraclinicalOrderDetailRepository.findById(detailId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));

        verifyRoomPermission(detail.getRoomId(), technicianAccountId);
        requireInProgressBy(detail, technicianAccountId);

        // ve lai hang cho dung vi tri cu, ly do ghi vao audit
        detail.returnToWaiting(technicianAccountId, now);
        paraclinicalOrderDetailRepository.save(detail);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("action", "RETURN_TO_WAITING");
        audit.put("status", detail.getStatus().name());
        audit.put("reason", request.reason().strip());
        auditLogService.recordBusinessChange(
                technicianAccountId, AuditLogAction.UPDATE, AUDIT_ENTITY, detail.getId(),
                detail.getMachidinh(), audit, now);

        return getQueueDetail(detailId);
    }

    // dich vu phai dang thuc hien va do chinh KTV nay tiep nhan
    private static void requireInProgressBy(ParaclinicalOrderDetail detail, UUID technicianAccountId) {
        if (detail.getStatus() != ParaclinicalOrderDetailStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.PARACLINICAL_SERVICE_NOT_IN_PROGRESS);
        }
        if (!detail.isPerformedBy(technicianAccountId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    // Từ chối thực hiện y lệnh do chống chỉ định y khoa hoặc bệnh nhân không hợp tác
    @Override
    @Transactional
    public TechnicianQueueItemResponse rejectOrder(UUID detailId, UUID technicianAccountId, RejectParaclinicalOrderRequest request) {
        Instant now = clock.instant();
        ParaclinicalOrderDetail detail = paraclinicalOrderDetailRepository.findById(detailId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));

        verifyRoomPermission(detail.getRoomId(), technicianAccountId);

        detail.cancel(request.reason(), technicianAccountId, now);
        paraclinicalOrderDetailRepository.save(detail);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("action", "REJECT");
        audit.put("status", detail.getStatus().name());
        audit.put("cancelReason", request.reason());
        auditLogService.recordBusinessChange(
                technicianAccountId, AuditLogAction.CANCEL, AUDIT_ENTITY, detail.getId(),
                detail.getMachidinh(), audit, now);

        return getQueueDetail(detailId);
    }

    // Kiểm tra quyền phụ trách phòng máy của KTV, ném lỗi 403 nếu không có thẩm quyền
    private void verifyRoomPermission(UUID roomId, UUID technicianAccountId) {
        if (!canManageRoom(roomId, technicianAccountId)) {
            throw new AppException(ErrorCode.TECHNICIAN_NOT_ASSIGNED_TO_ROOM,
                    "Bạn không được phân công phụ trách phòng máy này",
                    Map.of("roomId", "Bạn không được phân công phụ trách phòng máy này"));
        }
    }

    // Xác định KTV có quyền quản lý phòng theo ca trực (DutySchedule) hoặc phân công cố định
    private boolean canManageRoom(UUID roomId, UUID currentUserId) {
        if (currentUserId == null || roomId == null) {
            return false;
        }
        StaffAccount staff = staffAccountRepository.findById(currentUserId).orElse(null);
        if (staff == null) {
            return false;
        }
        if (staff.getRole() == Role.ADMIN) {
            return true;
        }
        if (staff.getRole() != Role.TECHNICIAN) {
            return false;
        }

        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, CLINIC_ZONE);
        LocalTime localTime = LocalTime.ofInstant(now, CLINIC_ZONE);
        Optional<DutyShift> currentShift = dutyShiftConfigService != null
                ? dutyShiftConfigService.shiftAt(today, localTime)
                : Optional.empty();

        // 1. Kiểm tra lịch trực trong ngày hôm nay của phòng này (DutySchedule - BR-55)
        List<DutySchedule> roomSchedulesToday = dutyScheduleRepository
                .findByRoomIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(roomId, today, today);

        if (!roomSchedulesToday.isEmpty()) {
            // Khi phòng đã có lịch trực được cấu hình trong ngày:
            // KTV phải đang trong khung giờ ca trực hợp lệ và được phân công đúng ca trực đó
            if (currentShift.isEmpty()) {
                return false; // Ngoài khung giờ ca trực (ví dụ nửa đêm, sáng sớm ngoài giờ làm việc)
            }
            return roomSchedulesToday.stream()
                    .anyMatch(schedule -> schedule.getShift() == currentShift.get() && currentUserId.equals(schedule.getStaffAccountId()));
        }

        // 2. Nếu phòng hôm nay chưa có lịch trực nào được cấu hình:
        // Xét theo phân công phòng cố định (StaffRoomAssignment) trong khung giờ làm việc
        boolean hasAssignment = staffRoomAssignmentRepository
                .findByStaffAccountIdAndRemovedAtIsNull(currentUserId)
                .map(assignment -> roomId.equals(assignment.getRoomId()))
                .orElse(false);

        if (hasAssignment) {
            return currentShift.isPresent() || dutyShiftConfigService == null;
        }

        return false;
    }

    // Ánh xạ thực thể chỉ định và các bảng liên quan trong RAM thành DTO hoàn chỉnh cho Frontend
    private TechnicianQueueItemResponse mapToItemResponse(
            ParaclinicalOrderDetail detail,
            MedicalService service,
            MedicalRecord record,
            Map<UUID, Visit> visitMap,
            Map<UUID, Patient> patientMap,
            StaffAccount doctor,
            Map<UUID, Room> roomMap,
            Map<UUID, ClinicalExamination> examMap,
            Map<UUID, List<MedicalHistory>> historiesMap,
            Map<UUID, List<ParaclinicalOrderDetail>> siblingOrdersMap,
            UUID currentRoomId,
            LocalDate today
    ) {
        ParaclinicalOrder order = detail.getOrder();
        Visit visit = record != null ? visitMap.get(record.getVisitId()) : null;
        Patient patient = visit != null ? patientMap.get(visit.getPatientId()) : null;

        boolean isPriority = visit != null && visit.isPriority();
        String priority = isPriority ? "PRIORITY" : "NORMAL";
        String priorityLabel = isPriority ? "Ưu tiên" : "Thường";

        Integer age = 0;
        String gender = "Khác";
        if (patient != null) {
            if (patient.getNgaysinh() != null) {
                age = Period.between(patient.getNgaysinh(), today).getYears();
            }
            gender = switch (patient.getGioitinh()) {
                case 1 -> "Nam";
                case 2 -> "Nữ";
                default -> "Khác";
            };
        }

        String insuranceType = "Viện phí";
        if ((visit != null && visit.getDoituongbnLoai() == VisitDoituongbnLoai.BHYT) || order.getDoituongbn() == 1) {
            insuranceType = "BHYT";
        }

        String doctorName = doctor != null ? staffDisplayName(doctor.getAcademicTitle(), doctor.getFullName()) : "—";
        String orderTime = detail.getNgaychidinh() != null ? TIME_FORMATTER.format(detail.getNgaychidinh()) : "";

        String displayStatus = detail.getStatus().name();
        if (detail.isSkipped() && detail.getStatus() == ParaclinicalOrderDetailStatus.WAITING) {
            displayStatus = "SKIPPED";
        }

        // Clinical examination data (Tra cứu O(1) từ examMap trong bộ nhớ)
        String chiefComplaint = visit != null ? visit.getLydovaovien() : "";
        String clinicalDiagnosis = order.getChandoansobo() != null ? order.getChandoansobo() : "";
        String icd10 = "";
        String physicalExam = "";

        if (record != null) {
            ClinicalExamination exam = examMap != null ? examMap.get(record.getId()) : null;
            if (exam != null) {
                if (exam.getKhambenhChandoanvaovienmaicd() != null && !exam.getKhambenhChandoanvaovienmaicd().isBlank()) {
                    icd10 = exam.getKhambenhChandoanvaovienmaicd();
                }
                StringBuilder peBuilder = new StringBuilder();
                if (exam.getKhambenhToanthan() != null && !exam.getKhambenhToanthan().isBlank()) {
                    peBuilder.append(exam.getKhambenhToanthan().strip());
                }
                if (exam.getKhambenhBophan() != null && !exam.getKhambenhBophan().isBlank()) {
                    if (!peBuilder.isEmpty()) peBuilder.append(" ");
                    peBuilder.append(exam.getKhambenhBophan().strip());
                }
                physicalExam = peBuilder.toString();
            }
        }

        // Medical history & allergies (Tra cứu O(1) từ historiesMap trong bộ nhớ)
        String medicalHistory = "Chưa ghi nhận bệnh lý đặc biệt";
        String allergies = "Không ghi nhận tiền sử dị ứng";
        boolean hasAllergyAlert = false;

        if (patient != null) {
            List<MedicalHistory> histories = historiesMap != null ? historiesMap.getOrDefault(patient.getId(), Collections.emptyList()) : Collections.emptyList();
            if (!histories.isEmpty()) {
                List<String> personalHist = histories.stream()
                        .filter(h -> h.getHistoryType() == MedicalHistoryType.PERSONAL)
                        .map(MedicalHistory::getThongtinNoidung)
                        .filter(s -> s != null && !s.isBlank())
                        .toList();
                if (!personalHist.isEmpty()) {
                    medicalHistory = String.join(", ", personalHist);
                }

                List<MedicalHistory> allergyList = histories.stream()
                        .filter(h -> h.getHistoryType() == MedicalHistoryType.DRUG_ALLERGY)
                        .toList();
                List<String> allergyItems = allergyList.stream()
                        .filter(h -> !h.isNoKnownDrugAllergy())
                        .map(h -> {
                            String drug = h.getThongtinNoidung();
                            String note = h.getThongtinGhichu();
                            return (note != null && !note.isBlank()) ? drug + " (" + note + ")" : drug;
                        })
                        .filter(s -> s != null && !s.isBlank())
                        .toList();

                if (!allergyItems.isEmpty()) {
                    allergies = "Dị ứng: " + String.join(", ", allergyItems);
                    hasAllergyAlert = true;
                }
            }
        }

        // Sibling orders in same order (Tra cứu O(1) từ siblingOrdersMap trong bộ nhớ)
        List<TechnicianSiblingOrderResponse> allOrdersToday = new ArrayList<>();
        List<ParaclinicalOrderDetail> siblings = siblingOrdersMap != null
                ? siblingOrdersMap.getOrDefault(order.getId(), Collections.emptyList())
                : Collections.emptyList();
        for (ParaclinicalOrderDetail sib : siblings) {
            Room r = sib.getRoomId() != null ? roomMap.get(sib.getRoomId()) : null;
            String rName = r != null ? "%s (%s)".formatted(r.getRoomName(), r.getRoomCode()) : "Chưa phân phòng";
            allOrdersToday.add(new TechnicianSiblingOrderResponse(
                    sib.getId(),
                    sib.getTenchidinh(),
                    rName,
                    sib.getRoomId() != null && sib.getRoomId().equals(currentRoomId),
                    sib.getStatus().name()
            ));
        }

        return new TechnicianQueueItemResponse(
                detail.getId(),
                order.getId(),
                detail.getQueueNumber(),
                detail.getMachidinh(),
                detail.getRoomId(),
                priority,
                priorityLabel,
                patient != null ? patient.getMabenhnhan() : "—",
                patient != null ? patient.getHoten() : "—",
                age,
                gender,
                insuranceType,
                visit != null ? visit.getMabhyt() : null,
                patient != null ? patient.getCccdSo() : null,
                patient != null ? patient.getSodienthoai() : null,
                patient != null ? patient.getDiachi() : null,
                detail.getTenchidinh(),
                service != null ? service.getCode() : null,
                doctorName,
                order.getNoichidinh(),
                orderTime,
                displayStatus,
                detail.isSkipped(),
                chiefComplaint,
                clinicalDiagnosis,
                icd10,
                physicalExam,
                medicalHistory,
                allergies,
                hasAllergyAlert,
                detail.getCancelReason(),
                allOrdersToday,
                detail.isCheckedIn(),
                detail.getCheckedInAt() != null ? TIME_FORMATTER.format(detail.getCheckedInAt()) : null,
                detail.getSkippedAt() != null ? TIME_FORMATTER.format(detail.getSkippedAt()) : null,
                detail.getSkippedAt()
        );
    }

    // Ghép học hàm/học vị với họ tên nhân viên để hiển thị chuẩn định dạng
    private static String staffDisplayName(String academicTitle, String fullName) {
        return academicTitle == null || academicTitle.isBlank()
                ? fullName
                : academicTitle.strip() + " " + fullName;
    }
}

