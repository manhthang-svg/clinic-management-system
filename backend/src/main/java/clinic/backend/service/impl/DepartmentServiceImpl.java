package clinic.backend.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import clinic.backend.dto.request.DepartmentRequest;
import clinic.backend.dto.request.RoomRequest;
import clinic.backend.dto.response.DepartmentRoomItemResponse;
import clinic.backend.dto.response.DepartmentStatsResponse;
import clinic.backend.dto.response.RoomServiceItemResponse;
import clinic.backend.entity.Department;
import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.Room;
import clinic.backend.entity.ServiceGroup;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.DepartmentType;
import clinic.backend.enums.Role;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.RoomType;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.ServiceGroupRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.DepartmentService;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DepartmentServiceImpl implements DepartmentService {

    /** Order line statuses that keep their chosen paraclinical room active (BR-50-03). */
    private static final List<ParaclinicalOrderDetailStatus> ROOM_PENDING_WORK = List.of(
            ParaclinicalOrderDetailStatus.PENDING_PAYMENT,
            ParaclinicalOrderDetailStatus.WAITING,
            ParaclinicalOrderDetailStatus.IN_PROGRESS);

    private final DepartmentRepository departmentRepository;
    private final RoomRepository roomRepository;
    private final ServiceGroupRepository serviceGroupRepository;
    private final StaffRoomAssignmentRepository staffRoomAssignmentRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;
    private final DutyScheduleRepository dutyScheduleRepository;
    private final VisitRepository visitRepository;
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final Clock clock;
    private final UuidV7Generator uuidV7Generator;

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentRoomItemResponse> getAllItems() {
        List<Department> departments = departmentRepository.findAll();
        List<Room> rooms = roomRepository.findAll();
        List<ServiceGroup> serviceGroups = serviceGroupRepository.findAll();
        Map<UUID, String> serviceGroupMap = serviceGroups.stream()
                .collect(Collectors.toMap(ServiceGroup::getId, ServiceGroup::getServiceGroupName, (s1, s2) -> s1));
        List<StaffRoomAssignment> activeAssignments = staffRoomAssignmentRepository.findByRemovedAtIsNull();
        Set<UUID> staffIds = activeAssignments.stream()
                .map(StaffRoomAssignment::getStaffAccountId)
                .collect(Collectors.toSet());
        Map<UUID, StaffAccount> staffMap = staffIds.isEmpty()
                ? Map.of()
                : staffAccountRepository.findAllById(staffIds).stream()
                        .collect(Collectors.toMap(StaffAccount::getId, s -> s, (s1, s2) -> s1));

        Map<UUID, List<String>> roomStaffNamesMap = new HashMap<>();
        for (StaffRoomAssignment assignment : activeAssignments) {
            StaffAccount staff = staffMap.get(assignment.getStaffAccountId());
            if (staff != null) {
                String title = staff.getAcademicTitle() != null ? staff.getAcademicTitle() + " " : "";
                roomStaffNamesMap.computeIfAbsent(assignment.getRoomId(), k -> new ArrayList<>())
                        .add(title + staff.getFullName());
            }
        }

        Map<UUID, String> roomStaffMap = new HashMap<>();
        roomStaffNamesMap.forEach((roomId, names) -> roomStaffMap.put(roomId, String.join(", ", names)));

        LocalDate today = LocalDate.ofInstant(clock.instant(), CLINIC_ZONE);
        List<DutySchedule> todayDutySchedules = dutyScheduleRepository
                .findByWorkDateBetweenOrderByWorkDateAscShiftAsc(today, today);
        Set<UUID> dutyStaffIds = todayDutySchedules.stream()
                .map(DutySchedule::getStaffAccountId)
                .collect(Collectors.toSet());
        Map<UUID, StaffAccount> dutyStaffMap = dutyStaffIds.isEmpty()
                ? Map.of()
                : staffAccountRepository.findAllById(dutyStaffIds).stream()
                        .collect(Collectors.toMap(StaffAccount::getId, s -> s, (s1, s2) -> s1));

        Map<UUID, List<String>> roomDutyDoctorNames = new HashMap<>();
        for (DutySchedule ds : todayDutySchedules) {
            if (ds.getRoomId() == null) continue;
            StaffAccount staff = dutyStaffMap.get(ds.getStaffAccountId());
            if (staff != null && (staff.getRole() == Role.DOCTOR || staff.getRole() == Role.TECHNICIAN)) {
                String title = staff.getAcademicTitle() != null && !staff.getAcademicTitle().isBlank()
                        ? staff.getAcademicTitle().strip() + " "
                        : "";
                roomDutyDoctorNames.computeIfAbsent(ds.getRoomId(), k -> new ArrayList<>())
                        .add(title + staff.getFullName());
            }
        }
        Map<UUID, String> roomDutyDoctorMap = new HashMap<>();
        roomDutyDoctorNames.forEach((roomId, names) ->
                roomDutyDoctorMap.put(roomId, String.join(", ", names.stream().distinct().toList())));

        Map<UUID, Department> departmentMap = departments.stream()
                .collect(Collectors.toMap(Department::getId, d -> d, (d1, d2) -> d1));

        Map<UUID, Long> roomCountByDept = rooms.stream()
                .collect(Collectors.groupingBy(Room::getDepartmentId, Collectors.counting()));

        List<MedicalService> activeServices = medicalServiceRepository.findAllByStatus(ActivationStatus.ACTIVE);

        Map<UUID, List<RoomServiceItemResponse>> servicesByGroup = activeServices.stream()
                .filter(s -> s.getServiceGroupId() != null)
                .map(s -> new RoomServiceItemResponse(
                        s.getId(),
                        s.getCode(),
                        s.getTenchidinh(),
                        s.getType().name(),
                        s.getServiceGroupId(),
                        serviceGroupMap.get(s.getServiceGroupId()),
                        s.getDepartmentId(),
                        s.getClinicPrice(),
                        s.getInsurancePrice(),
                        s.getStatus().name()
                ))
                .collect(Collectors.groupingBy(RoomServiceItemResponse::serviceGroupId));

        List<RoomServiceItemResponse> examServices = activeServices.stream()
                .filter(s -> s.getType() == MedicalServiceType.EXAMINATION)
                .map(s -> new RoomServiceItemResponse(
                        s.getId(),
                        s.getCode(),
                        s.getTenchidinh(),
                        s.getType().name(),
                        null,
                        null,
                        null,
                        s.getClinicPrice(),
                        s.getInsurancePrice(),
                        s.getStatus().name()
                ))
                .toList();

        List<DepartmentRoomItemResponse> items = new ArrayList<>();

        // Add Departments
        for (Department dept : departments) {
            items.add(DepartmentRoomItemResponse.builder()
                    .id(dept.getId())
                    .unitType("DEPARTMENT")
                    .code(dept.getDepartmentCode())
                    .name(dept.getDepartmentName())
                    .departmentId(dept.getId())
                    .departmentName(dept.getDepartmentName())
                    .type(dept.getType() != null ? dept.getType().name() : null)
                    .location(null)
                    .headPerson(null)
                    .status(dept.isActive() ? "ACTIVE" : "INACTIVE")
                    .description(dept.getDescription())
                    .roomCount(roomCountByDept.getOrDefault(dept.getId(), 0L).intValue())
                    .createdAt(dept.getCreatedAt())
                    .updatedAt(dept.getUpdatedAt())
                    .version(dept.getVersion())
                    .build());
        }

        // Add Rooms
        for (Room room : rooms) {
            Department parentDept = departmentMap.get(room.getDepartmentId());
            String deptName = parentDept != null ? parentDept.getDepartmentName() : "Chưa phân khoa";
            String staffName = roomStaffMap.get(room.getId());
            if (staffName == null) {
                staffName = "Chưa phân công";
            }
            String serviceGroupName = room.getServiceGroupId() != null
                    ? serviceGroupMap.get(room.getServiceGroupId())
                    : null;

            List<RoomServiceItemResponse> roomServices;
            if (room.getType() == RoomType.EXAMINATION) {
                roomServices = examServices;
            } else if (room.getServiceGroupId() != null) {
                roomServices = servicesByGroup.getOrDefault(room.getServiceGroupId(), List.of());
            } else {
                roomServices = List.of();
            }

            items.add(DepartmentRoomItemResponse.builder()
                    .id(room.getId())
                    .unitType("ROOM")
                    .code(room.getRoomCode())
                    .name(room.getRoomName())
                    .departmentId(room.getDepartmentId())
                    .departmentName(deptName)
                    .serviceGroupId(room.getServiceGroupId())
                    .serviceGroupName(serviceGroupName)
                    .type(room.getType() != null ? room.getType().name() : null)
                    .location(room.getLocation())
                    .headPerson(staffName)
                    .status(room.getStatus() != null ? room.getStatus().name() : "ACTIVE")
                    .assignedServices(roomServices)
                    .hasDoctorOnDutyToday(roomDutyDoctorMap.containsKey(room.getId()))
                    .doctorOnDutyToday(roomDutyDoctorMap.get(room.getId()))
                    .createdAt(room.getCreatedAt())
                    .updatedAt(room.getUpdatedAt())
                    .version(room.getVersion())
                    .build());
        }

        // Sort items: Departments first, then rooms
        items.sort(Comparator.comparing(DepartmentRoomItemResponse::getUnitType)
                .thenComparing(DepartmentRoomItemResponse::getCode));

        return items;
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentStatsResponse getStats() {
        List<Department> departments = departmentRepository.findAll();
        long totalDepartments = departments.size();
        long clinicalDepartments = departments.stream().filter(d -> d.getType() == DepartmentType.CLINICAL).count();
        long paraclinicalDepartments = departments.stream().filter(d -> d.getType() == DepartmentType.PARACLINICAL).count();

        List<Room> rooms = roomRepository.findAll();
        long totalRooms = rooms.size();
        long clinicalRooms = rooms.stream().filter(r -> r.getType() == RoomType.EXAMINATION).count();
        long paraclinicalRooms = rooms.stream().filter(r -> r.getType() == RoomType.PARACLINICAL).count();

        long activeRooms = rooms.stream().filter(r -> r.getStatus() == ActivationStatus.ACTIVE).count();
        long maintenanceRooms = totalRooms - activeRooms;

        double activeRate = totalRooms > 0
                ? Math.round(((double) activeRooms / totalRooms) * 1000.0) / 10.0
                : 0.0;

        String maintenanceNote = maintenanceRooms > 0
                ? maintenanceRooms + " phòng đang tạm ngưng hoạt động"
                : "Tất cả phòng hoạt động bình thường";

        return DepartmentStatsResponse.builder()
                .totalDepartments(totalDepartments)
                .clinicalDepartments(clinicalDepartments)
                .paraclinicalDepartments(paraclinicalDepartments)
                .totalRooms(totalRooms)
                .clinicalRooms(clinicalRooms)
                .paraclinicalRooms(paraclinicalRooms)
                .activeRooms(activeRooms)
                .maintenanceRooms(maintenanceRooms)
                .activeRate(activeRate)
                .maintenanceNote(maintenanceNote)
                .activeCount(activeRooms)
                .maintenanceCount(maintenanceRooms)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentRoomItemResponse> getDepartmentOptions() {
        return departmentRepository.findAll().stream()
                .filter(Department::isActive)
                .map(d -> DepartmentRoomItemResponse.builder()
                        .id(d.getId())
                        .unitType("DEPARTMENT")
                        .code(d.getDepartmentCode())
                        .name(d.getDepartmentName())
                        .type(d.getType() != null ? d.getType().name() : null)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public DepartmentRoomItemResponse createDepartment(DepartmentRequest request) {
        if (request.getDepartmentCode() == null || request.getDepartmentCode().isBlank()) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, "Vui lòng nhập mã chuyên khoa",
                    Map.of("departmentCode", "Vui lòng nhập mã chuyên khoa"));
        }
        String code = request.getDepartmentCode().trim().toUpperCase();
        if (code.length() < 2 || code.length() > 50 || !code.matches("^[A-Z0-9-]+$")) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    "Mã khoa phải từ 2 đến 50 ký tự, gồm chữ in hoa, chữ số hoặc gạch ngang (-)",
                    Map.of("departmentCode", "Mã khoa phải từ 2 đến 50 ký tự, gồm chữ in hoa, chữ số hoặc gạch ngang (-)"));
        }

        if (request.getDepartmentName() == null || request.getDepartmentName().isBlank()) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, "Vui lòng nhập tên chuyên khoa",
                    Map.of("departmentName", "Vui lòng nhập tên chuyên khoa"));
        }
        String name = request.getDepartmentName().trim();
        if (name.length() < 2 || name.length() > 100) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, "Tên khoa phải từ 2 đến 100 ký tự",
                    Map.of("departmentName", "Tên khoa phải từ 2 đến 100 ký tự"));
        }

        if (departmentRepository.existsByDepartmentCode(code)) {
            throw new AppException(ErrorCode.DEPARTMENT_CODE_ALREADY_EXISTS, "Mã khoa đã tồn tại: " + code,
                    Map.of("departmentCode", "Mã khoa đã tồn tại: " + code));
        }
        if (departmentRepository.existsByDepartmentNameIgnoreCase(name)) {
            throw new AppException(ErrorCode.DEPARTMENT_NAME_ALREADY_EXISTS, "Tên khoa đã tồn tại: " + name,
                    Map.of("departmentName", "Tên khoa đã tồn tại: " + name));
        }

        if (request.getDescription() != null && request.getDescription().trim().length() > 500) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, "Mô tả chuyên khoa tối đa 500 ký tự",
                    Map.of("description", "Mô tả chuyên khoa tối đa 500 ký tự"));
        }

        Instant now = clock.instant();
        UUID id = uuidV7Generator.next();
        boolean active = request.getActive() == null || request.getActive();

        Department department = new Department(
                id,
                code,
                name,
                request.getType(),
                request.getDescription(),
                active,
                now
        );

        Department saved = departmentRepository.save(department);



        return DepartmentRoomItemResponse.builder()
                .id(saved.getId())
                .unitType("DEPARTMENT")
                .code(saved.getDepartmentCode())
                .name(saved.getDepartmentName())
                .departmentId(saved.getId())
                .departmentName(saved.getDepartmentName())
                .type(saved.getType().name())
                .location(null)
                .headPerson(null)
                .status(saved.isActive() ? "ACTIVE" : "INACTIVE")
                .description(saved.getDescription())
                .roomCount(0)
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .version(saved.getVersion())
                .build();
    }

    @Override
    public DepartmentRoomItemResponse updateDepartment(UUID id, DepartmentRequest request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.DEPARTMENT_NOT_FOUND,
                        "Không tìm thấy khoa với mã ID: " + id,
                        Map.of("departmentId", "Không tìm thấy khoa với mã ID: " + id)));

        if (request.getVersion() != null && !Objects.equals(department.getVersion(), request.getVersion())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }

        if (request.getDepartmentName() == null || request.getDepartmentName().isBlank()) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, "Vui lòng nhập tên chuyên khoa",
                    Map.of("departmentName", "Vui lòng nhập tên chuyên khoa"));
        }
        String name = request.getDepartmentName().trim();
        if (name.length() < 2 || name.length() > 100) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, "Tên khoa phải từ 2 đến 100 ký tự",
                    Map.of("departmentName", "Tên khoa phải từ 2 đến 100 ký tự"));
        }
        if (departmentRepository.existsByDepartmentNameIgnoreCase(name)
                && !name.equalsIgnoreCase(department.getDepartmentName())) {
            throw new AppException(ErrorCode.DEPARTMENT_NAME_ALREADY_EXISTS, "Tên khoa đã tồn tại: " + name,
                    Map.of("departmentName", "Tên khoa đã tồn tại: " + name));
        }

        if (request.getDescription() != null && request.getDescription().trim().length() > 500) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, "Mô tả chuyên khoa tối đa 500 ký tự",
                    Map.of("description", "Mô tả chuyên khoa tối đa 500 ký tự"));
        }

        Instant now = clock.instant();
        boolean active = request.getActive() == null ? department.isActive() : request.getActive();

        // RỦI RO 4: Kiểm tra và ngưng hoạt động toàn bộ phòng con khi khóa chuyên khoa
        if (department.isActive() && !active) {
            validateDepartmentDeactivation(department);
            deactivateRoomsOfDepartment(department.getId(), now);
        }

        department.update(
                name,
                department.getType(),
                request.getDescription(),
                active,
                now
        );

        Department saved = departmentRepository.save(department);
        int roomCount = roomRepository.findByDepartmentId(id).size();



        return DepartmentRoomItemResponse.builder()
                .id(saved.getId())
                .unitType("DEPARTMENT")
                .code(saved.getDepartmentCode())
                .name(saved.getDepartmentName())
                .departmentId(saved.getId())
                .departmentName(saved.getDepartmentName())
                .type(saved.getType().name())
                .location(null)
                .headPerson(null)
                .status(saved.isActive() ? "ACTIVE" : "INACTIVE")
                .description(saved.getDescription())
                .roomCount(roomCount)
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .version(saved.getVersion())
                .build();
    }

    @Override
    public DepartmentRoomItemResponse toggleDepartmentStatus(UUID id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.DEPARTMENT_NOT_FOUND,
                        "Không tìm thấy khoa với mã ID: " + id,
                        Map.of("departmentId", "Không tìm thấy khoa với mã ID: " + id)));

        Instant now = clock.instant();
        boolean nextActive = !department.isActive();

        // RỦI RO 4: Kiểm tra và ngưng hoạt động toàn bộ phòng con khi khóa chuyên khoa
        if (department.isActive() && !nextActive) {
            validateDepartmentDeactivation(department);
            deactivateRoomsOfDepartment(department.getId(), now);
        }

        department.setActive(nextActive, now);
        Department saved = departmentRepository.save(department);
        int roomCount = roomRepository.findByDepartmentId(id).size();



        return DepartmentRoomItemResponse.builder()
                .id(saved.getId())
                .unitType("DEPARTMENT")
                .code(saved.getDepartmentCode())
                .name(saved.getDepartmentName())
                .departmentId(saved.getId())
                .departmentName(saved.getDepartmentName())
                .type(saved.getType().name())
                .location(null)
                .headPerson(null)
                .status(saved.isActive() ? "ACTIVE" : "INACTIVE")
                .description(saved.getDescription())
                .roomCount(roomCount)
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .version(saved.getVersion())
                .build();
    }

    @Override
    public DepartmentRoomItemResponse createRoom(RoomRequest request) {
        if (request.getRoomCode() == null || request.getRoomCode().isBlank()) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, "Vui lòng nhập mã phòng khám",
                    Map.of("roomCode", "Vui lòng nhập mã phòng khám"));
        }
        String roomCode = request.getRoomCode().trim().toUpperCase();
        if (roomCode.length() < 2 || roomCode.length() > 20 || !roomCode.matches("^[A-Z0-9-]+$")) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    "Mã phòng phải từ 2 đến 20 ký tự, gồm chữ in hoa, chữ số hoặc gạch ngang (-)",
                    Map.of("roomCode", "Mã phòng phải từ 2 đến 20 ký tự, gồm chữ in hoa, chữ số hoặc gạch ngang (-)"));
        }

        if (request.getRoomName() == null || request.getRoomName().isBlank()) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, "Vui lòng nhập tên phòng khám",
                    Map.of("roomName", "Vui lòng nhập tên phòng khám"));
        }
        String roomName = request.getRoomName().trim();
        if (roomName.length() < 2 || roomName.length() > 100) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, "Tên phòng phải từ 2 đến 100 ký tự",
                    Map.of("roomName", "Tên phòng phải từ 2 đến 100 ký tự"));
        }

        if (request.getLocation() == null || request.getLocation().isBlank()) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, "Vui lòng nhập vị trí buồng",
                    Map.of("location", "Vui lòng nhập vị trí buồng"));
        }
        String location = request.getLocation().trim();
        if (location.length() < 2 || location.length() > 255) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, "Vị trí buồng phải từ 2 đến 255 ký tự",
                    Map.of("location", "Vị trí buồng phải từ 2 đến 255 ký tự"));
        }

        if (roomRepository.existsByRoomCode(roomCode)) {
            throw new AppException(ErrorCode.ROOM_CODE_ALREADY_EXISTS, "Mã phòng đã tồn tại: " + roomCode,
                    Map.of("roomCode", "Mã phòng đã tồn tại: " + roomCode));
        }
        if (roomRepository.existsByRoomNameIgnoreCase(roomName)) {
            throw new AppException(ErrorCode.ROOM_NAME_ALREADY_EXISTS, "Tên phòng đã tồn tại: " + roomName,
                    Map.of("roomName", "Tên phòng đã tồn tại: " + roomName));
        }

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new AppException(ErrorCode.DEPARTMENT_NOT_FOUND,
                        "Khoa trực thuộc không tồn tại: " + request.getDepartmentId(),
                        Map.of("departmentId", "Khoa trực thuộc không tồn tại: " + request.getDepartmentId())));

        if (!department.isActive()) {
            throw new AppException(ErrorCode.DEPARTMENT_INACTIVE,
                    "Không thể tạo phòng trong chuyên khoa đang tạm ngưng hoạt động",
                    Map.of("departmentId", "Không thể tạo phòng trong chuyên khoa đang tạm ngưng hoạt động"));
        }

        final UUID serviceGroupId;
        if (request.getType() == RoomType.PARACLINICAL) {
            UUID requestedGroupId = request.getServiceGroupId();
            if (requestedGroupId == null) {
                throw new AppException(ErrorCode.PARACLINICAL_ROOM_REQUIRES_SERVICE_GROUP,
                        "Phòng khám cận lâm sàng phải chọn nhóm dịch vụ kỹ thuật",
                        Map.of("serviceGroupId", "Phòng khám cận lâm sàng phải chọn nhóm dịch vụ kỹ thuật"));
            }
            ServiceGroup group = serviceGroupRepository.findById(requestedGroupId)
                    .orElseThrow(() -> new AppException(ErrorCode.SERVICE_GROUP_NOT_FOUND,
                            "Nhóm dịch vụ kỹ thuật không tồn tại: " + requestedGroupId,
                            Map.of("serviceGroupId", "Nhóm dịch vụ kỹ thuật không tồn tại: " + requestedGroupId)));
            if (group.getDepartmentId() != null && !group.getDepartmentId().equals(department.getId())) {
                throw new AppException(ErrorCode.SERVICE_GROUP_DEPARTMENT_MISMATCH,
                        "Nhóm dịch vụ kỹ thuật không thuộc chuyên khoa đã chọn",
                        Map.of("serviceGroupId", "Nhóm dịch vụ kỹ thuật không thuộc chuyên khoa đã chọn"));
            }
            serviceGroupId = requestedGroupId;
        } else {
            if (request.getServiceGroupId() != null) {
                throw new AppException(ErrorCode.EXAMINATION_ROOM_CANNOT_HAVE_SERVICE_GROUP,
                        "Phòng khám lâm sàng không được liên kết với nhóm dịch vụ kỹ thuật",
                        Map.of("serviceGroupId", "Phòng khám lâm sàng không được liên kết với nhóm dịch vụ kỹ thuật"));
            }
            serviceGroupId = null;
        }

        Instant now = clock.instant();
        UUID id = uuidV7Generator.next();
        ActivationStatus status = request.getStatus() != null ? request.getStatus() : ActivationStatus.ACTIVE;

        String dutyDoctor = null;
        Room room = new Room(
                id,
                now,
                request.getDepartmentId(),
                serviceGroupId,
                roomCode,
                roomName,
                location,
                request.getType(),
                status
        );

        Room saved = roomRepository.save(room);

        String serviceGroupName = saved.getServiceGroupId() != null
                ? serviceGroupRepository.findById(saved.getServiceGroupId())
                        .map(ServiceGroup::getServiceGroupName)
                        .orElse(null)
                : null;




        return DepartmentRoomItemResponse.builder()
                .id(saved.getId())
                .unitType("ROOM")
                .code(saved.getRoomCode())
                .name(saved.getRoomName())
                .departmentId(saved.getDepartmentId())
                .departmentName(department.getDepartmentName())
                .serviceGroupId(saved.getServiceGroupId())
                .serviceGroupName(serviceGroupName)
                .type(saved.getType().name())
                .location(saved.getLocation())
                .headPerson(resolveStaffNamesForRoom(saved.getId()))
                .status(saved.getStatus().name())
                .assignedServices(resolveSingleRoomServices(saved, serviceGroupName))
                .hasDoctorOnDutyToday(dutyDoctor != null)
                .doctorOnDutyToday(dutyDoctor)
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .version(saved.getVersion())
                .build();
    }

    @Override
    public DepartmentRoomItemResponse updateRoom(UUID id, RoomRequest request) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND,
                        "Không tìm thấy phòng với mã ID: " + id,
                        Map.of("roomId", "Không tìm thấy phòng với mã ID: " + id)));

        if (request.getVersion() != null && !Objects.equals(room.getVersion(), request.getVersion())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }

        if (request.getDepartmentId() != null && !request.getDepartmentId().equals(room.getDepartmentId())) {
            throw new AppException(ErrorCode.ROOM_IMMUTABLE_FIELD_CHANGE,
                    "Không thể thay đổi chuyên khoa trực thuộc của phòng khám",
                    Map.of("departmentId", "Không thể thay đổi chuyên khoa trực thuộc của phòng khám"));
        }

        if (request.getType() != null && request.getType() != room.getType()) {
            throw new AppException(ErrorCode.ROOM_IMMUTABLE_FIELD_CHANGE,
                    "Không thể thay đổi loại phòng của phòng khám",
                    Map.of("type", "Không thể thay đổi loại phòng của phòng khám"));
        }

        Department department = departmentRepository.findById(room.getDepartmentId())
                .orElseThrow(() -> new AppException(ErrorCode.DEPARTMENT_NOT_FOUND,
                        "Khoa trực thuộc không tồn tại: " + room.getDepartmentId(),
                        Map.of("departmentId", "Khoa trực thuộc không tồn tại: " + room.getDepartmentId())));

        if (request.getRoomName() == null || request.getRoomName().isBlank()) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, "Vui lòng nhập tên phòng khám",
                    Map.of("roomName", "Vui lòng nhập tên phòng khám"));
        }
        String roomName = request.getRoomName().trim();
        if (roomName.length() < 2 || roomName.length() > 100) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, "Tên phòng phải từ 2 đến 100 ký tự",
                    Map.of("roomName", "Tên phòng phải từ 2 đến 100 ký tự"));
        }
        if (roomRepository.existsByRoomNameIgnoreCase(roomName)
                && !roomName.equalsIgnoreCase(room.getRoomName())) {
            throw new AppException(ErrorCode.ROOM_NAME_ALREADY_EXISTS, "Tên phòng đã tồn tại: " + roomName,
                    Map.of("roomName", "Tên phòng đã tồn tại: " + roomName));
        }

        if (request.getLocation() == null || request.getLocation().isBlank()) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, "Vui lòng nhập vị trí buồng",
                    Map.of("location", "Vui lòng nhập vị trí buồng"));
        }
        String location = request.getLocation().trim();
        if (location.length() < 2 || location.length() > 255) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, "Vị trí buồng phải từ 2 đến 255 ký tự",
                    Map.of("location", "Vị trí buồng phải từ 2 đến 255 ký tự"));
        }

        final UUID serviceGroupId;
        if (request.getType() == RoomType.PARACLINICAL) {
            UUID requestedGroupId = request.getServiceGroupId();
            if (requestedGroupId == null) {
                throw new AppException(ErrorCode.PARACLINICAL_ROOM_REQUIRES_SERVICE_GROUP,
                        "Phòng khám cận lâm sàng phải chọn nhóm dịch vụ kỹ thuật",
                        Map.of("serviceGroupId", "Phòng khám cận lâm sàng phải chọn nhóm dịch vụ kỹ thuật"));
            }
            ServiceGroup group = serviceGroupRepository.findById(requestedGroupId)
                    .orElseThrow(() -> new AppException(ErrorCode.SERVICE_GROUP_NOT_FOUND,
                            "Nhóm dịch vụ kỹ thuật không tồn tại: " + requestedGroupId,
                            Map.of("serviceGroupId", "Nhóm dịch vụ kỹ thuật không tồn tại: " + requestedGroupId)));
            if (group.getDepartmentId() != null && !group.getDepartmentId().equals(department.getId())) {
                throw new AppException(ErrorCode.SERVICE_GROUP_DEPARTMENT_MISMATCH,
                        "Nhóm dịch vụ kỹ thuật không thuộc chuyên khoa đã chọn",
                        Map.of("serviceGroupId", "Nhóm dịch vụ kỹ thuật không thuộc chuyên khoa đã chọn"));
            }
            serviceGroupId = requestedGroupId;
        } else {
            if (request.getServiceGroupId() != null) {
                throw new AppException(ErrorCode.EXAMINATION_ROOM_CANNOT_HAVE_SERVICE_GROUP,
                        "Phòng khám lâm sàng không được liên kết với nhóm dịch vụ kỹ thuật",
                        Map.of("serviceGroupId", "Phòng khám lâm sàng không được liên kết với nhóm dịch vụ kỹ thuật"));
            }
            serviceGroupId = null;
        }

        Instant now = clock.instant();
        ActivationStatus status = request.getStatus() != null ? request.getStatus() : room.getStatus();
        if (room.getStatus() == ActivationStatus.ACTIVE && status == ActivationStatus.INACTIVE) {
            validateRoomDeactivation(room);
        }

        room.update(
                room.getDepartmentId(),
                serviceGroupId,
                roomName,
                location,
                room.getType(),
                status,
                now
        );

        Room saved = roomRepository.save(room);

        String serviceGroupName = saved.getServiceGroupId() != null
                ? serviceGroupRepository.findById(saved.getServiceGroupId())
                        .map(ServiceGroup::getServiceGroupName)
                        .orElse(null)
                : null;



        String staffName = resolveStaffNamesForRoom(saved.getId());
        String dutyDoctor = resolveDutyDoctorForRoomToday(saved.getId());





        return DepartmentRoomItemResponse.builder()
                .id(saved.getId())
                .unitType("ROOM")
                .code(saved.getRoomCode())
                .name(saved.getRoomName())
                .departmentId(saved.getDepartmentId())
                .departmentName(department.getDepartmentName())
                .serviceGroupId(saved.getServiceGroupId())
                .serviceGroupName(serviceGroupName)
                .type(saved.getType().name())
                .location(saved.getLocation())
                .headPerson(staffName)
                .status(saved.getStatus().name())
                .assignedServices(resolveSingleRoomServices(saved, serviceGroupName))
                .hasDoctorOnDutyToday(dutyDoctor != null)
                .doctorOnDutyToday(dutyDoctor)
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .version(saved.getVersion())
                .build();
    }

    @Override
    public DepartmentRoomItemResponse toggleRoomStatus(UUID id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND, Map.of("roomId", "Không tìm thấy phòng với mã ID: " + id)));

        Department department = departmentRepository.findById(room.getDepartmentId())
                .orElse(null);

        Instant now = clock.instant();
        if (room.getStatus() == ActivationStatus.ACTIVE) {
            validateRoomDeactivation(room);
        }

        ActivationStatus nextStatus = room.getStatus() == ActivationStatus.ACTIVE
                ? ActivationStatus.INACTIVE
                : ActivationStatus.ACTIVE;

        room.setStatus(nextStatus, now);
        Room saved = roomRepository.save(room);

        String serviceGroupName = saved.getServiceGroupId() != null
                ? serviceGroupRepository.findById(saved.getServiceGroupId())
                        .map(ServiceGroup::getServiceGroupName)
                        .orElse(null)
                : null;



        String staffName = resolveStaffNamesForRoom(saved.getId());
        String dutyDoctor = resolveDutyDoctorForRoomToday(saved.getId());





        return DepartmentRoomItemResponse.builder()
                .id(saved.getId())
                .unitType("ROOM")
                .code(saved.getRoomCode())
                .name(saved.getRoomName())
                .departmentId(saved.getDepartmentId())
                .departmentName(department != null ? department.getDepartmentName() : "—")
                .serviceGroupId(saved.getServiceGroupId())
                .serviceGroupName(serviceGroupName)
                .type(saved.getType().name())
                .location(saved.getLocation())
                .headPerson(staffName)
                .status(saved.getStatus().name())
                .assignedServices(resolveSingleRoomServices(saved, serviceGroupName))
                .hasDoctorOnDutyToday(dutyDoctor != null)
                .doctorOnDutyToday(dutyDoctor)
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .version(saved.getVersion())
                .build();
    }

    private String resolveStaffNamesForRoom(UUID roomId) {
        List<StaffRoomAssignment> assignments = staffRoomAssignmentRepository.findAllByRoomIdAndRemovedAtIsNull(roomId);
        if (assignments.isEmpty()) {
            return "Chưa phân công";
        }
        Set<UUID> staffIds = assignments.stream()
                .map(StaffRoomAssignment::getStaffAccountId)
                .collect(Collectors.toSet());
        Map<UUID, StaffAccount> staffMap = staffIds.isEmpty()
                ? Map.of()
                : staffAccountRepository.findAllById(staffIds).stream()
                        .collect(Collectors.toMap(StaffAccount::getId, s -> s, (s1, s2) -> s1));

        List<String> names = assignments.stream()
                .map(a -> staffMap.get(a.getStaffAccountId()))
                .filter(Objects::nonNull)
                .map(s -> (s.getAcademicTitle() != null ? s.getAcademicTitle() + " " : "") + s.getFullName())
                .toList();
        if (names.isEmpty()) {
            return "Chưa phân công";
        }
        return String.join(", ", names);
    }

    private List<RoomServiceItemResponse> resolveSingleRoomServices(Room room, String serviceGroupName) {
        if (room.getType() == RoomType.EXAMINATION) {
            return medicalServiceRepository.findAllByTypeAndStatus(MedicalServiceType.EXAMINATION, ActivationStatus.ACTIVE).stream()
                    .map(s -> new RoomServiceItemResponse(
                            s.getId(),
                            s.getCode(),
                            s.getTenchidinh(),
                            s.getType().name(),
                            null,
                            null,
                            null,
                            s.getClinicPrice(),
                            s.getInsurancePrice(),
                            s.getStatus().name()
                    ))
                    .toList();
        } else if (room.getServiceGroupId() != null) {
            return medicalServiceRepository.findAllByServiceGroupIdAndStatus(room.getServiceGroupId(), ActivationStatus.ACTIVE).stream()
                    .map(s -> new RoomServiceItemResponse(
                            s.getId(),
                            s.getCode(),
                            s.getTenchidinh(),
                            s.getType().name(),
                            s.getServiceGroupId(),
                            serviceGroupName,
                            s.getDepartmentId(),
                            s.getClinicPrice(),
                            s.getInsurancePrice(),
                            s.getStatus().name()
                    ))
                    .toList();
        }
        return List.of();
    }

    /**
     * Kiểm tra tính hợp lệ trước khi tạm ngưng phòng khám (BR-07, BR-50-03, EF-03 / MSG55, EF-04 / MSG79).
     * 1. Phòng đang có nhân sự (Bác sĩ / KTV) trực trong ngày hôm nay.
     * 2. Rủi ro 2: Phòng vẫn còn ca trực được xếp lịch trong tương lai (sau ngày hôm nay).
     * 3. Rủi ro 1: Phòng lâm sàng đang có bệnh nhân chờ khám hoặc đang khám dở;
     *    hoặc phòng cận lâm sàng có chỉ định đang chờ thanh toán / chờ thực hiện / đang xử lý.
     */
    private void validateRoomDeactivation(Room room) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), CLINIC_ZONE);

        // 1. Kiểm tra nhân sự trực hôm nay (Doctor/Technician on duty today)
        checkNoDoctorOnDutyToday(room.getId());

        // 2. RỦI RO 2 (EF-04 / MSG79): Kiểm tra ca trực tương lai (các ngày tới)
        if (dutyScheduleRepository.existsByRoomIdAndWorkDateAfter(room.getId(), today)) {
            throw new AppException(ErrorCode.ROOM_HAS_FUTURE_SHIFTS,
                    "Không thể tạm ngưng phòng \"" + room.getRoomName()
                            + "\": phòng vẫn còn nhân viên được xếp ca trực trong các ngày tới. Vui lòng điều chỉnh lịch trực trước.",
                    Map.of("roomId", room.getId().toString(), "roomName", room.getRoomName()));
        }

        // 3. RỦI RO 1 (EF-03 / MSG55, BR-07, BR-50-03): Kiểm tra bệnh nhân hoặc công việc dở dang
        if (room.getType() == RoomType.EXAMINATION) {
            if (visitRepository.hasActiveVisitsToday(room.getId(), today, VisitRepository.FINISHED_STATUSES)) {
                throw new AppException(ErrorCode.ROOM_HAS_PENDING_WORK,
                        "Không thể tạm ngưng phòng \"" + room.getRoomName()
                                + "\": phòng vẫn còn bệnh nhân đang chờ hoặc đang khám dở trong ngày hôm nay.",
                        Map.of("roomId", room.getId().toString(), "roomName", room.getRoomName()));
            }
        } else if (room.getType() == RoomType.PARACLINICAL) {
            if (paraclinicalOrderDetailRepository.existsByRoomIdAndStatusIn(room.getId(), ROOM_PENDING_WORK)) {
                throw new AppException(ErrorCode.ROOM_HAS_PENDING_WORK,
                        "Không thể tạm ngưng phòng \"" + room.getRoomName()
                                + "\": phòng vẫn còn dịch vụ cận lâm sàng đang chờ thanh toán, chờ thực hiện hoặc đang xử lý.",
                        Map.of("roomId", room.getId().toString(), "roomName", room.getRoomName()));
            }
        }

        // 4. RỦI RO 3 (EF-06 / MSG94): Chặn tạm ngưng phòng khám lâm sàng duy nhất còn lại
        if (room.getType() == RoomType.EXAMINATION) {
            long activeExamRooms = roomRepository.countByTypeAndStatus(RoomType.EXAMINATION, ActivationStatus.ACTIVE);
            if (activeExamRooms <= 1) {
                throw new AppException(ErrorCode.LAST_ACTIVE_EXAMINATION_ROOM,
                        "Không thể tạm ngưng phòng \"" + room.getRoomName()
                                + "\": đây là phòng khám duy nhất còn hoạt động nên không thể ngừng hoạt động.",
                        Map.of("roomId", room.getId().toString(), "roomName", room.getRoomName()));
            }
        }
    }

    /**
     * RỦI RO 4: Kiểm tra điều kiện ngưng hoạt động chuyên khoa.
     * Chuyên khoa chỉ được phép tạm ngưng khi TẤT CẢ các phòng trực thuộc đang hoạt động
     * đều thỏa mãn điều kiện tạm ngưng phòng (không có BS/KTV trực hôm nay, không còn ca trực tương lai,
     * không có bệnh nhân chờ/khám dở hoặc chỉ định CLS chưa xong).
     */
    private void validateDepartmentDeactivation(Department department) {
        List<Room> rooms = roomRepository.findByDepartmentId(department.getId());
        List<Room> activeRooms = rooms.stream()
                .filter(r -> r.getStatus() == ActivationStatus.ACTIVE)
                .toList();

        // RỦI RO 3: Chặn nếu việc tạm ngưng chuyên khoa sẽ tắt toàn bộ phòng khám lâm sàng còn lại trong cơ sở
        long totalActiveExamRooms = roomRepository.countByTypeAndStatus(RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        long activeExamRoomsInDept = activeRooms.stream()
                .filter(r -> r.getType() == RoomType.EXAMINATION)
                .count();
        if (activeExamRoomsInDept > 0 && totalActiveExamRooms <= activeExamRoomsInDept) {
            throw new AppException(ErrorCode.LAST_ACTIVE_EXAMINATION_ROOM,
                    "Không thể tạm ngưng chuyên khoa \"" + department.getDepartmentName()
                            + "\": thao tác này sẽ ngừng hoạt động toàn bộ phòng khám lâm sàng còn lại trong cơ sở.",
                    Map.of("departmentId", department.getId().toString(), "departmentName", department.getDepartmentName()));
        }

        for (Room room : activeRooms) {
            try {
                validateRoomDeactivation(room);
            } catch (AppException ex) {
                throw new AppException(
                        ex.getErrorCode(),
                        "Không thể tạm ngưng chuyên khoa \"" + department.getDepartmentName() + "\": " + ex.getMessage(),
                        ex.getDetails()
                );
            }
        }
    }

    /**
     * RỦI RO 4: Cascade chuyển tất cả các phòng đang hoạt động thuộc chuyên khoa sang INACTIVE.
     */
    private void deactivateRoomsOfDepartment(UUID departmentId, Instant now) {
        List<Room> rooms = roomRepository.findByDepartmentId(departmentId);
        for (Room room : rooms) {
            if (room.getStatus() == ActivationStatus.ACTIVE) {
                room.setStatus(ActivationStatus.INACTIVE, now);
                roomRepository.save(room);
            }
        }
    }

    private void checkNoDoctorOnDutyToday(UUID roomId) {
        String dutyStaff = resolveDutyDoctorForRoomToday(roomId);
        if (dutyStaff != null) {
            throw new AppException(ErrorCode.ROOM_HAS_DOCTOR_ON_DUTY,
                    "Không thể tạm ngưng phòng đang có nhân sự (bác sĩ/kỹ thuật viên) trực trong ngày: " + dutyStaff,
                    Map.of("roomId", "Không thể tạm ngưng phòng đang có nhân sự (bác sĩ/kỹ thuật viên) trực trong ngày"));
        }
    }



    private String resolveDutyDoctorForRoomToday(UUID roomId) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), CLINIC_ZONE);
        List<DutySchedule> todaySchedules = dutyScheduleRepository
                .findByRoomIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(roomId, today, today);
        if (todaySchedules.isEmpty()) {
            return null;
        }
        Set<UUID> staffIds = todaySchedules.stream()
                .map(DutySchedule::getStaffAccountId)
                .collect(Collectors.toSet());
        List<String> names = staffAccountRepository.findAllById(staffIds).stream()
                .filter(staff -> staff.getRole() == Role.DOCTOR || staff.getRole() == Role.TECHNICIAN)
                .map(staff -> (staff.getAcademicTitle() != null && !staff.getAcademicTitle().isBlank()
                        ? staff.getAcademicTitle().strip() + " "
                        : "") + staff.getFullName())
                .distinct()
                .toList();
        return names.isEmpty() ? null : String.join(", ", names);
    }
}
