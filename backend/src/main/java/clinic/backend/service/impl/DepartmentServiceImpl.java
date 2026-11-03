package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import clinic.backend.dto.request.DepartmentRequest;
import clinic.backend.dto.request.RoomRequest;
import clinic.backend.dto.response.DepartmentRoomItemResponse;
import clinic.backend.dto.response.DepartmentStatsResponse;
import clinic.backend.entity.Department;
import clinic.backend.entity.Room;
import clinic.backend.entity.ServiceGroup;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.RoomType;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.ServiceGroupRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.service.DepartmentService;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final RoomRepository roomRepository;
    private final ServiceGroupRepository serviceGroupRepository;
    private final StaffRoomAssignmentRepository staffRoomAssignmentRepository;
    private final StaffAccountRepository staffAccountRepository;
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
        Map<UUID, StaffAccount> staffMap = staffAccountRepository.findAll().stream()
                .collect(Collectors.toMap(StaffAccount::getId, s -> s, (s1, s2) -> s1));

        Map<UUID, String> roomStaffMap = new HashMap<>();
        for (StaffRoomAssignment assignment : activeAssignments) {
            StaffAccount staff = staffMap.get(assignment.getStaffAccountId());
            if (staff != null) {
                String title = staff.getAcademicTitle() != null ? staff.getAcademicTitle() + " " : "";
                roomStaffMap.put(assignment.getRoomId(), title + staff.getFullName());
            }
        }

        Map<UUID, Department> departmentMap = departments.stream()
                .collect(Collectors.toMap(Department::getId, d -> d, (d1, d2) -> d1));

        Map<UUID, Long> roomCountByDept = rooms.stream()
                .collect(Collectors.groupingBy(Room::getDepartmentId, Collectors.counting()));

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
                    .location("Khu trung tâm")
                    .headPerson(null)
                    .status(dept.isActive() ? "ACTIVE" : "INACTIVE")
                    .description(dept.getDescription())
                    .roomCount(roomCountByDept.getOrDefault(dept.getId(), 0L).intValue())
                    .createdAt(dept.getCreatedAt())
                    .updatedAt(dept.getUpdatedAt())
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
                    .createdAt(room.getCreatedAt())
                    .updatedAt(room.getUpdatedAt())
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
        long totalDepartments = departmentRepository.count();
        long totalRooms = roomRepository.count();

        List<Room> rooms = roomRepository.findAll();
        long activeRooms = rooms.stream().filter(r -> r.getStatus() == ActivationStatus.ACTIVE).count();
        long maintenanceRooms = totalRooms - activeRooms;

        double activeRate = totalRooms > 0
                ? Math.round(((double) activeRooms / totalRooms) * 1000.0) / 10.0
                : 0.0;

        String maintenanceNote = maintenanceRooms > 0
                ? maintenanceRooms + " phòng đang kiểm tra thiết bị"
                : "Tất cả phòng hoạt động bình thường";

        return DepartmentStatsResponse.builder()
                .totalDepartments(totalDepartments)
                .totalRooms(totalRooms)
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
            throw new IllegalArgumentException("Vui lòng nhập mã chuyên khoa");
        }
        String code = request.getDepartmentCode().trim().toUpperCase();
        if (code.length() < 2 || code.length() > 50 || !code.matches("^[A-Z0-9-]+$")) {
            throw new IllegalArgumentException("Mã khoa phải từ 2 đến 50 ký tự, gồm chữ in hoa, chữ số hoặc gạch ngang (-)");
        }

        if (request.getDepartmentName() == null || request.getDepartmentName().isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập tên chuyên khoa");
        }
        String name = request.getDepartmentName().trim();
        if (name.length() < 2 || name.length() > 100) {
            throw new IllegalArgumentException("Tên khoa phải từ 2 đến 100 ký tự");
        }

        if (departmentRepository.existsByDepartmentCode(code)) {
            throw new IllegalArgumentException("Mã khoa đã tồn tại: " + code);
        }
        if (departmentRepository.existsByDepartmentNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Tên khoa đã tồn tại: " + name);
        }

        if (request.getDescription() != null && request.getDescription().trim().length() > 500) {
            throw new IllegalArgumentException("Mô tả chuyên khoa tối đa 500 ký tự");
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
                .location("Khu trung tâm")
                .headPerson(null)
                .status(saved.isActive() ? "ACTIVE" : "INACTIVE")
                .description(saved.getDescription())
                .roomCount(0)
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    @Override
    public DepartmentRoomItemResponse updateDepartment(UUID id, DepartmentRequest request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khoa với mã ID: " + id));

        if (request.getDepartmentName() == null || request.getDepartmentName().isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập tên chuyên khoa");
        }
        String name = request.getDepartmentName().trim();
        if (name.length() < 2 || name.length() > 100) {
            throw new IllegalArgumentException("Tên khoa phải từ 2 đến 100 ký tự");
        }
        if (departmentRepository.existsByDepartmentNameIgnoreCase(name)
                && !name.equalsIgnoreCase(department.getDepartmentName())) {
            throw new IllegalArgumentException("Tên khoa đã tồn tại: " + name);
        }

        if (request.getDescription() != null && request.getDescription().trim().length() > 500) {
            throw new IllegalArgumentException("Mô tả chuyên khoa tối đa 500 ký tự");
        }

        Instant now = clock.instant();
        boolean active = request.getActive() == null ? department.isActive() : request.getActive();

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
                .location("Khu trung tâm")
                .headPerson(null)
                .status(saved.isActive() ? "ACTIVE" : "INACTIVE")
                .description(saved.getDescription())
                .roomCount(roomCount)
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    @Override
    public DepartmentRoomItemResponse toggleDepartmentStatus(UUID id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khoa với mã ID: " + id));

        Instant now = clock.instant();
        department.setActive(!department.isActive(), now);
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
                .location("Khu trung tâm")
                .headPerson(null)
                .status(saved.isActive() ? "ACTIVE" : "INACTIVE")
                .description(saved.getDescription())
                .roomCount(roomCount)
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    @Override
    public DepartmentRoomItemResponse createRoom(RoomRequest request) {
        if (request.getRoomCode() == null || request.getRoomCode().isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập mã phòng khám");
        }
        String roomCode = request.getRoomCode().trim().toUpperCase();
        if (roomCode.length() < 2 || roomCode.length() > 20 || !roomCode.matches("^[A-Z0-9-]+$")) {
            throw new IllegalArgumentException("Mã phòng phải từ 2 đến 20 ký tự, gồm chữ in hoa, chữ số hoặc gạch ngang (-)");
        }

        if (request.getRoomName() == null || request.getRoomName().isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập tên phòng khám");
        }
        String roomName = request.getRoomName().trim();
        if (roomName.length() < 2 || roomName.length() > 100) {
            throw new IllegalArgumentException("Tên phòng phải từ 2 đến 100 ký tự");
        }

        if (request.getLocation() == null || request.getLocation().isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập vị trí buồng");
        }
        String location = request.getLocation().trim();
        if (location.length() < 2 || location.length() > 255) {
            throw new IllegalArgumentException("Vị trí buồng phải từ 2 đến 255 ký tự");
        }

        if (roomRepository.existsByRoomCode(roomCode)) {
            throw new IllegalArgumentException("Mã phòng đã tồn tại: " + roomCode);
        }

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new IllegalArgumentException("Khoa trực thuộc không tồn tại: " + request.getDepartmentId()));

        final UUID serviceGroupId;
        if (request.getType() == RoomType.PARACLINICAL) {
            UUID requestedGroupId = request.getServiceGroupId();
            if (requestedGroupId == null) {
                throw new IllegalArgumentException("Phòng khám cận lâm sàng phải chọn nhóm dịch vụ kỹ thuật");
            }
            ServiceGroup group = serviceGroupRepository.findById(requestedGroupId)
                    .orElseThrow(() -> new IllegalArgumentException("Nhóm dịch vụ kỹ thuật không tồn tại: " + requestedGroupId));
            if (group.getDepartmentId() != null && !group.getDepartmentId().equals(department.getId())) {
                throw new IllegalArgumentException("Nhóm dịch vụ kỹ thuật không thuộc chuyên khoa đã chọn");
            }
            serviceGroupId = requestedGroupId;
        } else {
            serviceGroupId = null;
        }

        Instant now = clock.instant();
        UUID id = uuidV7Generator.next();
        ActivationStatus status = request.getStatus() != null ? request.getStatus() : ActivationStatus.ACTIVE;

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
                .headPerson(request.getHeadPerson() != null && !request.getHeadPerson().isBlank()
                        ? request.getHeadPerson()
                        : "Chưa phân công")
                .status(saved.getStatus().name())
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    @Override
    public DepartmentRoomItemResponse updateRoom(UUID id, RoomRequest request) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phòng với mã ID: " + id));

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new IllegalArgumentException("Khoa trực thuộc không tồn tại: " + request.getDepartmentId()));

        if (request.getRoomName() == null || request.getRoomName().isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập tên phòng khám");
        }
        String roomName = request.getRoomName().trim();
        if (roomName.length() < 2 || roomName.length() > 100) {
            throw new IllegalArgumentException("Tên phòng phải từ 2 đến 100 ký tự");
        }

        if (request.getLocation() == null || request.getLocation().isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập vị trí buồng");
        }
        String location = request.getLocation().trim();
        if (location.length() < 2 || location.length() > 255) {
            throw new IllegalArgumentException("Vị trí buồng phải từ 2 đến 255 ký tự");
        }

        final UUID serviceGroupId;
        if (request.getType() == RoomType.PARACLINICAL) {
            UUID requestedGroupId = request.getServiceGroupId();
            if (requestedGroupId == null) {
                throw new IllegalArgumentException("Phòng khám cận lâm sàng phải chọn nhóm dịch vụ kỹ thuật");
            }
            ServiceGroup group = serviceGroupRepository.findById(requestedGroupId)
                    .orElseThrow(() -> new IllegalArgumentException("Nhóm dịch vụ kỹ thuật không tồn tại: " + requestedGroupId));
            if (group.getDepartmentId() != null && !group.getDepartmentId().equals(department.getId())) {
                throw new IllegalArgumentException("Nhóm dịch vụ kỹ thuật không thuộc chuyên khoa đã chọn");
            }
            serviceGroupId = requestedGroupId;
        } else {
            serviceGroupId = null;
        }

        Instant now = clock.instant();
        ActivationStatus status = request.getStatus() != null ? request.getStatus() : room.getStatus();

        room.update(
                request.getDepartmentId(),
                serviceGroupId,
                roomName,
                location,
                request.getType(),
                status,
                now
        );

        Room saved = roomRepository.save(room);

        String serviceGroupName = saved.getServiceGroupId() != null
                ? serviceGroupRepository.findById(saved.getServiceGroupId())
                        .map(ServiceGroup::getServiceGroupName)
                        .orElse(null)
                : null;

        String staffName = staffRoomAssignmentRepository.findByRoomIdAndRemovedAtIsNull(saved.getId())
                .flatMap(assignment -> staffAccountRepository.findById(assignment.getStaffAccountId()))
                .map(s -> (s.getAcademicTitle() != null ? s.getAcademicTitle() + " " : "") + s.getFullName())
                .orElse(request.getHeadPerson() != null && !request.getHeadPerson().isBlank() ? request.getHeadPerson() : "Chưa phân công");

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
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    @Override
    public DepartmentRoomItemResponse toggleRoomStatus(UUID id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phòng với mã ID: " + id));

        Department department = departmentRepository.findById(room.getDepartmentId())
                .orElse(null);

        Instant now = clock.instant();
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

        String staffName = staffRoomAssignmentRepository.findByRoomIdAndRemovedAtIsNull(saved.getId())
                .flatMap(assignment -> staffAccountRepository.findById(assignment.getStaffAccountId()))
                .map(s -> (s.getAcademicTitle() != null ? s.getAcademicTitle() + " " : "") + s.getFullName())
                .orElse("Chưa phân công");

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
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }
}
