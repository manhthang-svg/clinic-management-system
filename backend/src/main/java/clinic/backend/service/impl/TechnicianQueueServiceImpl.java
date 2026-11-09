package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import clinic.backend.dto.request.RejectParaclinicalOrderRequest;
import clinic.backend.dto.request.ReturnParaclinicalOrderToWaitingRequest;
import clinic.backend.dto.response.TechnicianQueueItemResponse;
import clinic.backend.dto.response.TechnicianQueueResponse;
import clinic.backend.dto.response.TechnicianQueueStatsResponse;
import clinic.backend.dto.response.TechnicianRoomResponse;
import clinic.backend.dto.response.TechnicianSiblingOrderResponse;
import clinic.backend.entity.ClinicalExamination;
import clinic.backend.entity.DutySchedule;
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
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalHistoryType;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.Role;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicalExaminationRepository;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.MedicalHistoryRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.TechnicianQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TechnicianQueueServiceImpl implements TechnicianQueueService {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm").withZone(CLINIC_ZONE);
    private static final String AUDIT_ENTITY = "paraclinical_order_details";

    private final RoomRepository roomRepository;
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
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<TechnicianRoomResponse> getTechnicianRooms(UUID currentUserId) {
        List<Room> rooms = roomRepository.findByTypeAndStatusOrderByRoomCodeAsc(
                RoomType.PARACLINICAL, ActivationStatus.ACTIVE);

        List<StaffRoomAssignment> activeAssignments = staffRoomAssignmentRepository.findByRemovedAtIsNull();
        Set<UUID> assignedStaffIds = activeAssignments.stream()
                .map(StaffRoomAssignment::getStaffAccountId)
                .collect(Collectors.toSet());
        Map<UUID, StaffAccount> assignedStaffMap = staffAccountRepository.findAllById(assignedStaffIds).stream()
                .collect(Collectors.toMap(StaffAccount::getId, Function.identity()));

        Map<UUID, String> roomTechMap = new HashMap<>();
        for (StaffRoomAssignment assignment : activeAssignments) {
            StaffAccount staff = assignedStaffMap.get(assignment.getStaffAccountId());
            if (staff != null && staff.getRole() == Role.TECHNICIAN) {
                roomTechMap.putIfAbsent(assignment.getRoomId(), staffDisplayName(staff.getAcademicTitle(), staff.getFullName()));
            }
        }

        boolean isAdmin = false;
        UUID userAssignedRoomId = null;
        Set<UUID> todayDutyRoomIds = Collections.emptySet();

        if (currentUserId != null) {
            StaffAccount currentStaff = staffAccountRepository.findById(currentUserId).orElse(null);
            if (currentStaff != null) {
                if (currentStaff.getRole() == Role.ADMIN) {
                    isAdmin = true;
                } else if (currentStaff.getRole() == Role.TECHNICIAN) {
                    userAssignedRoomId = staffRoomAssignmentRepository
                            .findByStaffAccountIdAndRemovedAtIsNull(currentUserId)
                            .map(StaffRoomAssignment::getRoomId)
                            .orElse(null);

                    LocalDate today = LocalDate.now(clock.withZone(CLINIC_ZONE));
                    todayDutyRoomIds = dutyScheduleRepository
                            .findByStaffAccountIdAndWorkDate(currentUserId, today)
                            .stream()
                            .map(DutySchedule::getRoomId)
                            .collect(Collectors.toSet());
                }
            }
        }

        final boolean userIsAdmin = isAdmin;
        final UUID finalUserAssignedRoomId = userAssignedRoomId;
        final Set<UUID> finalTodayDutyRoomIds = todayDutyRoomIds;

        return rooms.stream()
                .map(room -> {
                    boolean canManage = userIsAdmin ||
                            (room.getId() != null && (room.getId().equals(finalUserAssignedRoomId) || finalTodayDutyRoomIds.contains(room.getId())));
                    return new TechnicianRoomResponse(
                            room.getId(),
                            room.getRoomCode(),
                            room.getRoomName(),
                            room.getLocation(),
                            roomTechMap.getOrDefault(room.getId(), "Chưa phân công"),
                            canManage
                    );
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TechnicianQueueResponse getQueue(UUID roomId, String search, String status, String doctorRoom, UUID currentUserId) {
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

        Map<UUID, Room> allRoomsMap = roomRepository.findAll().stream()
                .collect(Collectors.toMap(Room::getId, Function.identity()));

        // Tối ưu hóa: Batch fetch tất cả thông tin lâm sàng, tiền sử và y lệnh liên quan tránh lỗi N+1 truy vấn
        Map<UUID, ClinicalExamination> examMap = recordIds.isEmpty() ? Collections.emptyMap() :
                clinicalExaminationRepository.findByMedicalRecordIdIn(recordIds).stream()
                        .collect(Collectors.toMap(ClinicalExamination::getMedicalRecordId, Function.identity(), (a, b) -> a));

        Map<UUID, List<MedicalHistory>> historiesMap = patientIds.isEmpty() ? Collections.emptyMap() :
                medicalHistoryRepository.findByPatientIdInOrderByCreatedAtAsc(patientIds).stream()
                        .collect(Collectors.groupingBy(MedicalHistory::getPatientId));

        Map<UUID, List<ParaclinicalOrderDetail>> siblingOrdersMap = orderIds.isEmpty() ? Collections.emptyMap() :
                paraclinicalOrderDetailRepository.findByOrderIdIn(orderIds).stream()
                        .collect(Collectors.groupingBy(d -> d.getOrder().getId()));

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
                    // Priority first
                    if ("PRIORITY".equals(a.priority()) && !"PRIORITY".equals(b.priority())) return -1;
                    if (!"PRIORITY".equals(a.priority()) && "PRIORITY".equals(b.priority())) return 1;

                    // Then by queueNumber
                    int qA = a.queueNumber() != null ? a.queueNumber() : Integer.MAX_VALUE;
                    int qB = b.queueNumber() != null ? b.queueNumber() : Integer.MAX_VALUE;
                    return Integer.compare(qA, qB);
                })
                .toList();

        return new TechnicianQueueResponse(statsResponse, filtered, canManage);
    }

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

    @Override
    @Transactional
    public TechnicianQueueItemResponse acceptOrder(UUID detailId, UUID technicianAccountId) {
        Instant now = clock.instant();
        ParaclinicalOrderDetail detail = paraclinicalOrderDetailRepository.findById(detailId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));

        verifyRoomPermission(detail.getRoomId(), technicianAccountId);

        detail.accept(technicianAccountId, now);
        paraclinicalOrderDetailRepository.save(detail);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("action", "ACCEPT");
        audit.put("status", detail.getStatus().name());
        auditLogService.recordBusinessChange(
                technicianAccountId, AuditLogAction.UPDATE, AUDIT_ENTITY, detail.getId(),
                detail.getMachidinh(), audit, now);

        return getQueueDetail(detailId);
    }

    @Override
    @Transactional
    public TechnicianQueueItemResponse skipOrder(UUID detailId, UUID technicianAccountId) {
        Instant now = clock.instant();
        ParaclinicalOrderDetail detail = paraclinicalOrderDetailRepository.findById(detailId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));

        verifyRoomPermission(detail.getRoomId(), technicianAccountId);

        detail.skip(now);
        paraclinicalOrderDetailRepository.save(detail);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("action", "SKIP");
        audit.put("isSkipped", true);
        auditLogService.recordBusinessChange(
                technicianAccountId, AuditLogAction.UPDATE, AUDIT_ENTITY, detail.getId(),
                detail.getMachidinh(), audit, now);

        return getQueueDetail(detailId);
    }

    @Override
    @Transactional
    public TechnicianQueueItemResponse unskipOrder(UUID detailId, UUID technicianAccountId) {
        Instant now = clock.instant();
        ParaclinicalOrderDetail detail = paraclinicalOrderDetailRepository.findById(detailId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));

        verifyRoomPermission(detail.getRoomId(), technicianAccountId);

        detail.unskip(now);
        paraclinicalOrderDetailRepository.save(detail);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("action", "UNSKIP");
        audit.put("isSkipped", false);
        auditLogService.recordBusinessChange(
                technicianAccountId, AuditLogAction.UPDATE, AUDIT_ENTITY, detail.getId(),
                detail.getMachidinh(), audit, now);

        return getQueueDetail(detailId);
    }

    @Override
    @Transactional
    public TechnicianQueueItemResponse completeOrder(UUID detailId, UUID technicianAccountId) {
        Instant now = clock.instant();
        ParaclinicalOrderDetail detail = paraclinicalOrderDetailRepository.findById(detailId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));

        verifyRoomPermission(detail.getRoomId(), technicianAccountId);
        requireInProgressBy(detail, technicianAccountId);

        // UC-66 step 3: Performed, waiting for its result (UC-64).
        detail.completePerformance(technicianAccountId, now);
        paraclinicalOrderDetailRepository.save(detail);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("action", "COMPLETE_PERFORMANCE");
        audit.put("status", detail.getStatus().name());
        auditLogService.recordBusinessChange(
                technicianAccountId, AuditLogAction.UPDATE, AUDIT_ENTITY, detail.getId(),
                detail.getMachidinh(), audit, now);

        return getQueueDetail(detailId);
    }

    @Override
    @Transactional
    public TechnicianQueueItemResponse returnToWaiting(
            UUID detailId, UUID technicianAccountId, ReturnParaclinicalOrderToWaitingRequest request) {
        Instant now = clock.instant();
        ParaclinicalOrderDetail detail = paraclinicalOrderDetailRepository.findById(detailId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));

        verifyRoomPermission(detail.getRoomId(), technicianAccountId);
        requireInProgressBy(detail, technicianAccountId);

        // UC-66 AF-02: back to Waiting at its original place; the reason is recorded in the audit log.
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

    /** UC-66 EF-01: only the technician who accepted the service, while it is In Progress (MSG13). */
    private static void requireInProgressBy(ParaclinicalOrderDetail detail, UUID technicianAccountId) {
        if (detail.getStatus() != ParaclinicalOrderDetailStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.PARACLINICAL_SERVICE_NOT_IN_PROGRESS);
        }
        if (!detail.isPerformedBy(technicianAccountId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

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

    private void verifyRoomPermission(UUID roomId, UUID technicianAccountId) {
        if (!canManageRoom(roomId, technicianAccountId)) {
            throw new AppException(ErrorCode.TECHNICIAN_NOT_ASSIGNED_TO_ROOM,
                    "Bạn không được phân công phụ trách phòng máy này",
                    Map.of("roomId", "Bạn không được phân công phụ trách phòng máy này"));
        }
    }

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

        // 1. Phân công cố định (StaffRoomAssignment)
        boolean hasAssignment = staffRoomAssignmentRepository
                .findByStaffAccountIdAndRemovedAtIsNull(currentUserId)
                .map(assignment -> roomId.equals(assignment.getRoomId()))
                .orElse(false);
        if (hasAssignment) {
            return true;
        }

        // 2. Lịch trực trong ngày hôm nay (DutySchedule)
        LocalDate today = LocalDate.now(clock.withZone(CLINIC_ZONE));
        return dutyScheduleRepository
                .findByRoomIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(roomId, today, today)
                .stream()
                .anyMatch(schedule -> currentUserId.equals(schedule.getStaffAccountId()));
    }

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
                allOrdersToday
        );
    }

    private static String staffDisplayName(String academicTitle, String fullName) {
        return academicTitle == null || academicTitle.isBlank()
                ? fullName
                : academicTitle.strip() + " " + fullName;
    }
}

