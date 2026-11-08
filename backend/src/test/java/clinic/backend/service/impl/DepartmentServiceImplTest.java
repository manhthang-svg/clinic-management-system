package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.DepartmentRequest;
import clinic.backend.dto.request.RoomRequest;
import clinic.backend.dto.response.DepartmentRoomItemResponse;
import clinic.backend.dto.response.DepartmentStatsResponse;
import clinic.backend.entity.Department;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.DepartmentType;
import clinic.backend.enums.RoomType;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.ServiceGroupRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceImplTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ServiceGroupRepository serviceGroupRepository;

    @Mock
    private StaffRoomAssignmentRepository staffRoomAssignmentRepository;

    @Mock
    private StaffAccountRepository staffAccountRepository;

    @Mock
    private clinic.backend.repository.MedicalServiceRepository medicalServiceRepository;

    @Mock
    private UuidV7Generator uuidV7Generator;

    private Clock clock;
    private DepartmentServiceImpl departmentService;
    private final Instant now = Instant.parse("2026-10-23T10:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(now, ZoneOffset.UTC);
        departmentService = new DepartmentServiceImpl(
                departmentRepository,
                roomRepository,
                serviceGroupRepository,
                staffRoomAssignmentRepository,
                staffAccountRepository,
                medicalServiceRepository,
                clock,
                uuidV7Generator
        );
    }

    @Test
    @DisplayName("Tạo khoa mới thành công khi mã khoa và tên khoa chưa tồn tại")
    void createDepartment_Success() {
        UUID generatedId = UUID.randomUUID();
        when(uuidV7Generator.next()).thenReturn(generatedId);
        when(departmentRepository.existsByDepartmentCode("K-NOI")).thenReturn(false);
        when(departmentRepository.existsByDepartmentNameIgnoreCase("Khoa Nội")).thenReturn(false);

        Department savedDept = new Department(
                generatedId,
                "K-NOI",
                "Khoa Nội",
                DepartmentType.CLINICAL,
                "Khám nội",
                true,
                now
        );
        when(departmentRepository.save(any(Department.class))).thenReturn(savedDept);

        DepartmentRequest request = DepartmentRequest.builder()
                .departmentCode("K-NOI")
                .departmentName("Khoa Nội")
                .type(DepartmentType.CLINICAL)
                .description("Khám nội")
                .active(true)
                .build();

        DepartmentRoomItemResponse response = departmentService.createDepartment(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(generatedId);
        assertThat(response.getCode()).isEqualTo("K-NOI");
        assertThat(response.getName()).isEqualTo("Khoa Nội");
        assertThat(response.getUnitType()).isEqualTo("DEPARTMENT");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Ném ngoại lệ khi tạo phòng mà mã phòng đã tồn tại")
    void createRoom_DuplicateCode_ThrowsException() {
        when(roomRepository.existsByRoomCode("PK-NOI-01")).thenReturn(true);

        RoomRequest request = RoomRequest.builder()
                .roomCode("PK-NOI-01")
                .roomName("Phòng khám Nội 1")
                .departmentId(UUID.randomUUID())
                .location("Tầng 1")
                .type(RoomType.EXAMINATION)
                .build();

        assertThatThrownBy(() -> departmentService.createRoom(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mã phòng đã tồn tại");
    }

    @Test
    @DisplayName("Tạo phòng mới thành công với entity Room refactored")
    void createRoom_Success() {
        UUID deptId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        Department dept = new Department(deptId, "K-NOI", "Khoa Nội", DepartmentType.CLINICAL, "Mô tả", true, now);
        when(roomRepository.existsByRoomCode("PK-NOI-01")).thenReturn(false);
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));
        when(uuidV7Generator.next()).thenReturn(roomId);

        Room savedRoom = new Room(
                roomId,
                now,
                deptId,
                null,
                "PK-NOI-01",
                "Phòng khám Nội 1",
                "Tòa A - P.101",
                RoomType.EXAMINATION,
                ActivationStatus.ACTIVE
        );
        when(roomRepository.save(any(Room.class))).thenReturn(savedRoom);

        RoomRequest request = RoomRequest.builder()
                .roomCode("PK-NOI-01")
                .roomName("Phòng khám Nội 1")
                .departmentId(deptId)
                .location("Tòa A - P.101")
                .type(RoomType.EXAMINATION)
                .status(ActivationStatus.ACTIVE)
                .build();

        DepartmentRoomItemResponse response = departmentService.createRoom(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(roomId);
        assertThat(response.getCode()).isEqualTo("PK-NOI-01");
        assertThat(response.getName()).isEqualTo("Phòng khám Nội 1");
        assertThat(response.getDepartmentName()).isEqualTo("Khoa Nội");
        assertThat(response.getUnitType()).isEqualTo("ROOM");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Tạo phòng cận lâm sàng thành công kèm nhóm dịch vụ kỹ thuật")
    void createRoom_Paraclinical_WithServiceGroup_Success() {
        UUID deptId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        Department dept = new Department(deptId, "K-CDHA", "Khoa Chẩn Đoán Hình Ảnh", DepartmentType.PARACLINICAL, "Mô tả", true, now);
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));
        when(uuidV7Generator.next()).thenReturn(roomId);

        clinic.backend.entity.ServiceGroup group = org.mockito.Mockito.mock(clinic.backend.entity.ServiceGroup.class);
        when(group.getServiceGroupName()).thenReturn("Chẩn đoán hình ảnh (X-Quang, CT)");
        when(serviceGroupRepository.findById(groupId)).thenReturn(Optional.of(group));

        Room savedRoom = new Room(
                roomId,
                now,
                deptId,
                groupId,
                "PK-XQ-01",
                "Phòng Chụp X-Quang 1",
                "Tòa B - P.102",
                RoomType.PARACLINICAL,
                ActivationStatus.ACTIVE
        );
        when(roomRepository.save(any(Room.class))).thenReturn(savedRoom);

        RoomRequest request = RoomRequest.builder()
                .roomCode("PK-XQ-01")
                .roomName("Phòng Chụp X-Quang 1")
                .departmentId(deptId)
                .serviceGroupId(groupId)
                .location("Tòa B - P.102")
                .type(RoomType.PARACLINICAL)
                .status(ActivationStatus.ACTIVE)
                .build();

        DepartmentRoomItemResponse response = departmentService.createRoom(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(roomId);
        assertThat(response.getCode()).isEqualTo("PK-XQ-01");
        assertThat(response.getServiceGroupId()).isEqualTo(groupId);
        assertThat(response.getServiceGroupName()).isEqualTo("Chẩn đoán hình ảnh (X-Quang, CT)");
        assertThat(response.getType()).isEqualTo("PARACLINICAL");
    }

    @Test
    @DisplayName("Ném ngoại lệ khi nhóm dịch vụ kỹ thuật không thuộc chuyên khoa")
    void createRoom_Paraclinical_ServiceGroupMismatchDepartment_ThrowsException() {
        UUID deptId = UUID.randomUUID();
        UUID otherDeptId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();

        Department dept = new Department(deptId, "K-XN", "Khoa Xét Nghiệm", DepartmentType.PARACLINICAL, "Mô tả", true, now);
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));

        clinic.backend.entity.ServiceGroup group = org.mockito.Mockito.mock(clinic.backend.entity.ServiceGroup.class);
        when(group.getDepartmentId()).thenReturn(otherDeptId);
        when(serviceGroupRepository.findById(groupId)).thenReturn(Optional.of(group));

        RoomRequest request = RoomRequest.builder()
                .roomCode("PK-SA-01")
                .roomName("Phòng Siêu Âm 1")
                .departmentId(deptId)
                .serviceGroupId(groupId)
                .location("Tòa A - P.101")
                .type(RoomType.PARACLINICAL)
                .status(ActivationStatus.ACTIVE)
                .build();

        assertThatThrownBy(() -> departmentService.createRoom(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nhóm dịch vụ kỹ thuật không thuộc chuyên khoa đã chọn");
    }

    @Test
    @DisplayName("Ném ngoại lệ khi phòng khám lâm sàng gửi kèm nhóm dịch vụ kỹ thuật")
    void createRoom_Examination_WithServiceGroup_ThrowsException() {
        UUID deptId = UUID.randomUUID();
        Department dept = new Department(deptId, "K-NOI", "Khoa Nội", DepartmentType.CLINICAL, "Mô tả", true, now);
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));

        RoomRequest request = RoomRequest.builder()
                .roomCode("PK-NOI-01")
                .roomName("Phòng khám Nội 1")
                .departmentId(deptId)
                .serviceGroupId(UUID.randomUUID())
                .location("Tòa A - P.101")
                .type(RoomType.EXAMINATION)
                .status(ActivationStatus.ACTIVE)
                .build();

        assertThatThrownBy(() -> departmentService.createRoom(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Phòng khám lâm sàng không được liên kết với nhóm dịch vụ kỹ thuật");
    }

    @Test
    @DisplayName("Tính toán stats phòng và khoa chuẩn xác")
    void getStats_CalculatesCorrectly() {
        UUID deptId1 = UUID.randomUUID();
        UUID deptId2 = UUID.randomUUID();
        Department d1 = new Department(deptId1, "K-NOI", "Khoa Nội", DepartmentType.CLINICAL, null, true, now);
        Department d2 = new Department(deptId2, "K-XN", "Khoa Xét nghiệm", DepartmentType.PARACLINICAL, null, true, now);
        when(departmentRepository.findAll()).thenReturn(List.of(d1, d2));

        Room r1 = new Room(UUID.randomUUID(), now, deptId1, null, "R1", "Room 1", "L1", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        Room r2 = new Room(UUID.randomUUID(), now, deptId1, null, "R2", "Room 2", "L2", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        Room r3 = new Room(UUID.randomUUID(), now, deptId2, null, "R3", "Room 3", "L3", RoomType.PARACLINICAL, ActivationStatus.ACTIVE);
        Room r4 = new Room(UUID.randomUUID(), now, deptId2, null, "R4", "Room 4", "L4", RoomType.PARACLINICAL, ActivationStatus.INACTIVE);

        when(roomRepository.findAll()).thenReturn(List.of(r1, r2, r3, r4));

        DepartmentStatsResponse stats = departmentService.getStats();

        assertThat(stats.getTotalDepartments()).isEqualTo(2L);
        assertThat(stats.getClinicalDepartments()).isEqualTo(1L);
        assertThat(stats.getParaclinicalDepartments()).isEqualTo(1L);
        assertThat(stats.getTotalRooms()).isEqualTo(4L);
        assertThat(stats.getClinicalRooms()).isEqualTo(2L);
        assertThat(stats.getParaclinicalRooms()).isEqualTo(2L);
        assertThat(stats.getActiveRooms()).isEqualTo(3L);
        assertThat(stats.getMaintenanceRooms()).isEqualTo(1L);
        assertThat(stats.getActiveRate()).isEqualTo(75.0);
    }

    @Test
    @DisplayName("Chuyển đổi trạng thái phòng giữa ACTIVE và INACTIVE")
    void toggleRoomStatus_TogglesActiveState() {
        UUID roomId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Room room = new Room(roomId, now, deptId, null, "R1", "Room 1", "L1", RoomType.EXAMINATION, ActivationStatus.ACTIVE);

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DepartmentRoomItemResponse response = departmentService.toggleRoomStatus(roomId);

        assertThat(response.getStatus()).isEqualTo("INACTIVE");
        assertThat(room.getStatus()).isEqualTo(ActivationStatus.INACTIVE);
    }

    @Test
    @DisplayName("Ném ngoại lệ khi mã khoa vi phạm BR-67 (dưới 2 ký tự hoặc chứa ký tự đặc biệt)")
    void createDepartment_InvalidCode_ThrowsException() {
        DepartmentRequest shortCode = DepartmentRequest.builder()
                .departmentCode("A")
                .departmentName("Khoa Nội")
                .type(DepartmentType.CLINICAL)
                .build();

        assertThatThrownBy(() -> departmentService.createDepartment(shortCode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mã khoa phải từ 2 đến 50 ký tự");

        DepartmentRequest specialCharCode = DepartmentRequest.builder()
                .departmentCode("KHOA_NOI@")
                .departmentName("Khoa Nội")
                .type(DepartmentType.CLINICAL)
                .build();

        assertThatThrownBy(() -> departmentService.createDepartment(specialCharCode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mã khoa phải từ 2 đến 50 ký tự, gồm chữ in hoa, chữ số hoặc gạch ngang (-)");
    }

    @Test
    @DisplayName("Ném ngoại lệ khi tên khoa vi phạm BR-68 (dưới 2 hoặc trên 100 ký tự)")
    void createDepartment_InvalidName_ThrowsException() {
        DepartmentRequest shortName = DepartmentRequest.builder()
                .departmentCode("K-NOI")
                .departmentName("A")
                .type(DepartmentType.CLINICAL)
                .build();

        assertThatThrownBy(() -> departmentService.createDepartment(shortName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tên khoa phải từ 2 đến 100 ký tự");

        DepartmentRequest longName = DepartmentRequest.builder()
                .departmentCode("K-NOI")
                .departmentName("A".repeat(101))
                .type(DepartmentType.CLINICAL)
                .build();

        assertThatThrownBy(() -> departmentService.createDepartment(longName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tên khoa phải từ 2 đến 100 ký tự");
    }

    @Test
    @DisplayName("Ném ngoại lệ khi mô tả khoa vi phạm BR-70 (vượt quá 500 ký tự)")
    void createDepartment_DescriptionTooLong_ThrowsException() {
        DepartmentRequest longDesc = DepartmentRequest.builder()
                .departmentCode("K-NOI")
                .departmentName("Khoa Nội")
                .type(DepartmentType.CLINICAL)
                .description("X".repeat(501))
                .build();

        assertThatThrownBy(() -> departmentService.createDepartment(longDesc))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mô tả chuyên khoa tối đa 500 ký tự");
    }

    @Test
    @DisplayName("Ném ngoại lệ khi mã phòng vi phạm BR-71 (độ dài ngoài [2,20] hoặc regex không hợp lệ)")
    void createRoom_InvalidCode_ThrowsException() {
        RoomRequest shortCode = RoomRequest.builder()
                .roomCode("R")
                .roomName("Phòng 1")
                .departmentId(UUID.randomUUID())
                .location("Tầng 1")
                .type(RoomType.EXAMINATION)
                .build();

        assertThatThrownBy(() -> departmentService.createRoom(shortCode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mã phòng phải từ 2 đến 20 ký tự");

        RoomRequest longCode = RoomRequest.builder()
                .roomCode("A".repeat(21))
                .roomName("Phòng 1")
                .departmentId(UUID.randomUUID())
                .location("Tầng 1")
                .type(RoomType.EXAMINATION)
                .build();

        assertThatThrownBy(() -> departmentService.createRoom(longCode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mã phòng phải từ 2 đến 20 ký tự");

        RoomRequest specialCharCode = RoomRequest.builder()
                .roomCode("PK#01")
                .roomName("Phòng 1")
                .departmentId(UUID.randomUUID())
                .location("Tầng 1")
                .type(RoomType.EXAMINATION)
                .build();

        assertThatThrownBy(() -> departmentService.createRoom(specialCharCode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mã phòng phải từ 2 đến 20 ký tự, gồm chữ in hoa, chữ số hoặc gạch ngang (-)");
    }

    @Test
    @DisplayName("Ném ngoại lệ khi tên phòng vi phạm BR-72 (độ dài ngoài [2,100])")
    void createRoom_InvalidName_ThrowsException() {
        RoomRequest shortName = RoomRequest.builder()
                .roomCode("PK-01")
                .roomName("P")
                .departmentId(UUID.randomUUID())
                .location("Tầng 1")
                .type(RoomType.EXAMINATION)
                .build();

        assertThatThrownBy(() -> departmentService.createRoom(shortName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tên phòng phải từ 2 đến 100 ký tự");
    }

    @Test
    @DisplayName("Ném ngoại lệ khi vị trí buồng vi phạm BR-73 (độ dài ngoài [2,255])")
    void createRoom_InvalidLocation_ThrowsException() {
        RoomRequest shortLocation = RoomRequest.builder()
                .roomCode("PK-01")
                .roomName("Phòng khám 1")
                .departmentId(UUID.randomUUID())
                .location("X")
                .type(RoomType.EXAMINATION)
                .build();

        assertThatThrownBy(() -> departmentService.createRoom(shortLocation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vị trí buồng phải từ 2 đến 255 ký tự");
    }

    @Test
    @DisplayName("Ném ngoại lệ khi tạo phòng thuộc chuyên khoa đang tạm ngưng hoạt động")
    void createRoom_InactiveDepartment_ThrowsException() {
        UUID deptId = UUID.randomUUID();
        Department inactiveDept = new Department(deptId, "K-NOI", "Khoa Nội", DepartmentType.CLINICAL, "Mô tả", false, now);

        when(roomRepository.existsByRoomCode("PK-NOI-01")).thenReturn(false);
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(inactiveDept));

        RoomRequest request = RoomRequest.builder()
                .roomCode("PK-NOI-01")
                .roomName("Phòng khám Nội 1")
                .departmentId(deptId)
                .location("Tầng 2 - Nhà A")
                .type(RoomType.EXAMINATION)
                .build();

        assertThatThrownBy(() -> departmentService.createRoom(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không thể tạo phòng trong chuyên khoa đang tạm ngưng hoạt động");
    }

    @Test
    @DisplayName("updateRoom: Ném ngoại lệ khi cố tình thay đổi chuyên khoa trực thuộc của phòng")
    void updateRoom_ChangeDepartment_ThrowsException() {
        UUID roomId = UUID.randomUUID();
        UUID originDeptId = UUID.randomUUID();
        UUID differentDeptId = UUID.randomUUID();

        Room existingRoom = new Room(
                roomId,
                now,
                originDeptId,
                null,
                "PK-NOI-01",
                "Phòng khám Nội 1",
                "Tầng 1",
                RoomType.EXAMINATION,
                ActivationStatus.ACTIVE
        );

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(existingRoom));

        RoomRequest request = RoomRequest.builder()
                .departmentId(differentDeptId)
                .roomName("Phòng khám Nội 1 mới")
                .location("Tầng 2")
                .type(RoomType.EXAMINATION)
                .status(ActivationStatus.ACTIVE)
                .build();

        assertThatThrownBy(() -> departmentService.updateRoom(roomId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không thể thay đổi chuyên khoa trực thuộc của phòng khám");
    }

    @Test
    @DisplayName("updateRoom: Ném ngoại lệ khi cố tình thay đổi loại phòng của phòng đã tạo")
    void updateRoom_ChangeType_ThrowsException() {
        UUID roomId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        Room existingRoom = new Room(
                roomId,
                now,
                deptId,
                null,
                "PK-NOI-01",
                "Phòng khám Nội 1",
                "Tầng 1",
                RoomType.EXAMINATION,
                ActivationStatus.ACTIVE
        );

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(existingRoom));

        RoomRequest request = RoomRequest.builder()
                .departmentId(deptId)
                .roomName("Phòng khám Nội 1")
                .location("Tầng 1")
                .type(RoomType.PARACLINICAL)
                .status(ActivationStatus.ACTIVE)
                .build();

        assertThatThrownBy(() -> departmentService.updateRoom(roomId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không thể thay đổi loại phòng của phòng khám");
    }

    @Test
    @DisplayName("updateRoom: Cập nhật thành công tên phòng, vị trí buồng và trạng thái")
    void updateRoom_Success() {
        UUID roomId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        Room existingRoom = new Room(
                roomId,
                now,
                deptId,
                null,
                "PK-NOI-01",
                "Phòng khám Nội 1",
                "Tầng 1",
                RoomType.EXAMINATION,
                ActivationStatus.ACTIVE
        );

        Department dept = new Department(deptId, "K-NOI", "Khoa Nội", DepartmentType.CLINICAL, "Mô tả", true, now);

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(existingRoom));
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(staffRoomAssignmentRepository.findAllByRoomIdAndRemovedAtIsNull(roomId)).thenReturn(List.of());

        RoomRequest request = RoomRequest.builder()
                .departmentId(deptId)
                .roomName("Phòng khám Nội tổng hợp 1")
                .location("Tầng 2 - P.201")
                .type(RoomType.EXAMINATION)
                .status(ActivationStatus.INACTIVE)
                .build();

        var response = departmentService.updateRoom(roomId, request);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Phòng khám Nội tổng hợp 1");
        assertThat(response.getLocation()).isEqualTo("Tầng 2 - P.201");
        assertThat(response.getStatus()).isEqualTo("INACTIVE");
        assertThat(response.getDepartmentId()).isEqualTo(deptId);
        assertThat(response.getType()).isEqualTo("EXAMINATION");
    }

    @Test
    @DisplayName("getAllItems & updateRoom: Phòng có nhiều nhân sự thì nối tất cả tên bằng dấu phẩy không bị ghi đè")
    void roomWithMultipleStaff_DoesNotOverwriteAndJoinsNames() {
        UUID roomId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID staffId1 = UUID.randomUUID();
        UUID staffId2 = UUID.randomUUID();

        Room room = new Room(
                roomId,
                now,
                deptId,
                null,
                "PK-NOI-01",
                "Phòng khám Nội 1",
                "Tầng 1",
                RoomType.EXAMINATION,
                ActivationStatus.ACTIVE
        );

        Department dept = new Department(deptId, "K-NOI", "Khoa Nội", DepartmentType.CLINICAL, "Mô tả", true, now);

        StaffAccount doctor = StaffAccount.builder()
                .id(staffId1)
                .fullName("Nguyễn Văn A")
                .academicTitle("BS.")
                .build();

        StaffAccount technician = StaffAccount.builder()
                .id(staffId2)
                .fullName("Trần Thị B")
                .academicTitle("KTV.")
                .build();

        StaffRoomAssignment assign1 = new StaffRoomAssignment(
                UUID.randomUUID(), staffId1, roomId, now, null, null, null, 0L
        );
        StaffRoomAssignment assign2 = new StaffRoomAssignment(
                UUID.randomUUID(), staffId2, roomId, now, null, null, null, 0L
        );

        // Test getAllItems()
        when(departmentRepository.findAll()).thenReturn(List.of(dept));
        when(roomRepository.findAll()).thenReturn(List.of(room));
        when(serviceGroupRepository.findAll()).thenReturn(List.of());
        when(medicalServiceRepository.findAll()).thenReturn(List.of());
        when(staffAccountRepository.findAll()).thenReturn(List.of(doctor, technician));
        when(staffRoomAssignmentRepository.findByRemovedAtIsNull()).thenReturn(List.of(assign1, assign2));

        var allItems = departmentService.getAllItems();
        var roomItem = allItems.stream().filter(i -> "ROOM".equals(i.getUnitType())).findFirst().orElseThrow();
        assertThat(roomItem.getHeadPerson()).contains("BS. Nguyễn Văn A");
        assertThat(roomItem.getHeadPerson()).contains("KTV. Trần Thị B");
        assertThat(roomItem.getHeadPerson()).isEqualTo("BS. Nguyễn Văn A, KTV. Trần Thị B");

        // Test updateRoom() with multiple staff (không bị crash NonUniqueResultException)
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(staffRoomAssignmentRepository.findAllByRoomIdAndRemovedAtIsNull(roomId)).thenReturn(List.of(assign1, assign2));
        when(staffAccountRepository.findById(staffId1)).thenReturn(Optional.of(doctor));
        when(staffAccountRepository.findById(staffId2)).thenReturn(Optional.of(technician));

        RoomRequest updateReq = RoomRequest.builder()
                .departmentId(deptId)
                .roomName("Phòng khám Nội 1 đổi tên")
                .location("Tầng 2")
                .type(RoomType.EXAMINATION)
                .status(ActivationStatus.ACTIVE)
                .build();

        var updateRes = departmentService.updateRoom(roomId, updateReq);
        assertThat(updateRes.getHeadPerson()).isEqualTo("BS. Nguyễn Văn A, KTV. Trần Thị B");
    }
}
