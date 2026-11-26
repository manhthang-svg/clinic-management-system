package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.DepartmentRequest;
import clinic.backend.dto.request.RoomRequest;
import clinic.backend.dto.response.DepartmentRoomItemResponse;
import clinic.backend.dto.response.DepartmentStatsResponse;
import clinic.backend.entity.Department;
import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.DepartmentType;
import clinic.backend.enums.DutyShift;
import clinic.backend.enums.Role;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.RoomType;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.DutyScheduleRepository;
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
import static org.mockito.Mockito.never;
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
    private clinic.backend.repository.ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;

    @Mock
    private DutyScheduleRepository dutyScheduleRepository;

    @Mock
    private clinic.backend.repository.VisitRepository visitRepository;

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
                paraclinicalOrderDetailRepository,
                dutyScheduleRepository,
                visitRepository,
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
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Mã phòng đã tồn tại");
    }

    @Test
    @DisplayName("Ném ngoại lệ khi tạo phòng mà tên phòng đã tồn tại")
    void createRoom_DuplicateName_ThrowsException() {
        when(roomRepository.existsByRoomCode("PK-NOI-02")).thenReturn(false);
        when(roomRepository.existsByRoomNameIgnoreCase("Phòng khám Nội 1")).thenReturn(true);

        RoomRequest request = RoomRequest.builder()
                .roomCode("PK-NOI-02")
                .roomName("Phòng khám Nội 1")
                .departmentId(UUID.randomUUID())
                .location("Tầng 1")
                .type(RoomType.EXAMINATION)
                .build();

        assertThatThrownBy(() -> departmentService.createRoom(request))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    AppException appException = (AppException) e;
                    assertThat(appException.getErrorCode()).isEqualTo(ErrorCode.ROOM_NAME_ALREADY_EXISTS);
                })
                .hasMessageContaining("Tên phòng đã tồn tại");
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
                .isInstanceOf(AppException.class)
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
                .isInstanceOf(AppException.class)
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
        when(roomRepository.countByTypeAndStatus(RoomType.EXAMINATION, ActivationStatus.ACTIVE)).thenReturn(2L);
        when(roomRepository.saveAndFlush(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DepartmentRoomItemResponse response = departmentService.toggleRoomStatus(roomId);

        assertThat(response.getStatus()).isEqualTo("INACTIVE");
        assertThat(room.getStatus()).isEqualTo(ActivationStatus.INACTIVE);
    }

    @Test
    @DisplayName("BR-50-03: không tắt được phòng CLS còn dịch vụ đã chọn phòng đang chờ thanh toán, chờ hoặc đang làm")
    void toggleRoomStatus_RefusesParaclinicalRoomWithPendingServices() {
        UUID roomId = UUID.randomUUID();
        Room room = new Room(roomId, now, UUID.randomUUID(), UUID.randomUUID(), "SA1", "Phòng siêu âm 1", "Tầng 2",
                RoomType.PARACLINICAL, ActivationStatus.ACTIVE);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(paraclinicalOrderDetailRepository.existsByRoomIdAndStatusIn(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> departmentService.toggleRoomStatus(roomId))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ROOM_HAS_PENDING_WORK));
        assertThat(room.getStatus()).isEqualTo(ActivationStatus.ACTIVE);
    }

    @Test
    @DisplayName("Phòng CLS không còn dịch vụ dở dang thì tắt được")
    void toggleRoomStatus_DeactivatesParaclinicalRoomWithoutPendingServices() {
        UUID roomId = UUID.randomUUID();
        Room room = new Room(roomId, now, UUID.randomUUID(), UUID.randomUUID(), "SA1", "Phòng siêu âm 1", "Tầng 2",
                RoomType.PARACLINICAL, ActivationStatus.ACTIVE);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(paraclinicalOrderDetailRepository.existsByRoomIdAndStatusIn(any(), any())).thenReturn(false);
        when(roomRepository.saveAndFlush(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        departmentService.toggleRoomStatus(roomId);

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
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Mã khoa phải từ 2 đến 50 ký tự");

        DepartmentRequest specialCharCode = DepartmentRequest.builder()
                .departmentCode("KHOA_NOI@")
                .departmentName("Khoa Nội")
                .type(DepartmentType.CLINICAL)
                .build();

        assertThatThrownBy(() -> departmentService.createDepartment(specialCharCode))
                .isInstanceOf(AppException.class)
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
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Tên khoa phải từ 2 đến 100 ký tự");

        DepartmentRequest longName = DepartmentRequest.builder()
                .departmentCode("K-NOI")
                .departmentName("A".repeat(101))
                .type(DepartmentType.CLINICAL)
                .build();

        assertThatThrownBy(() -> departmentService.createDepartment(longName))
                .isInstanceOf(AppException.class)
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
                .isInstanceOf(AppException.class)
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
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Mã phòng phải từ 2 đến 20 ký tự");

        RoomRequest longCode = RoomRequest.builder()
                .roomCode("A".repeat(21))
                .roomName("Phòng 1")
                .departmentId(UUID.randomUUID())
                .location("Tầng 1")
                .type(RoomType.EXAMINATION)
                .build();

        assertThatThrownBy(() -> departmentService.createRoom(longCode))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Mã phòng phải từ 2 đến 20 ký tự");

        RoomRequest specialCharCode = RoomRequest.builder()
                .roomCode("PK#01")
                .roomName("Phòng 1")
                .departmentId(UUID.randomUUID())
                .location("Tầng 1")
                .type(RoomType.EXAMINATION)
                .build();

        assertThatThrownBy(() -> departmentService.createRoom(specialCharCode))
                .isInstanceOf(AppException.class)
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
                .isInstanceOf(AppException.class)
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
                .isInstanceOf(AppException.class)
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
                .isInstanceOf(AppException.class)
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
                .isInstanceOf(AppException.class)
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
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Không thể thay đổi loại phòng của phòng khám");
    }

    @Test
    @DisplayName("updateRoom: Ném ngoại lệ khi cập nhật tên phòng mà tên mới đã tồn tại ở phòng khác")
    void updateRoom_DuplicateName_ThrowsException() {
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
        when(roomRepository.existsByRoomNameIgnoreCase("Phòng khám Ngoại 1")).thenReturn(true);

        RoomRequest request = RoomRequest.builder()
                .departmentId(deptId)
                .roomName("Phòng khám Ngoại 1")
                .location("Tầng 1")
                .type(RoomType.EXAMINATION)
                .status(ActivationStatus.ACTIVE)
                .build();

        assertThatThrownBy(() -> departmentService.updateRoom(roomId, request))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    AppException appException = (AppException) e;
                    assertThat(appException.getErrorCode()).isEqualTo(ErrorCode.ROOM_NAME_ALREADY_EXISTS);
                })
                .hasMessageContaining("Tên phòng đã tồn tại");
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
        when(roomRepository.countByTypeAndStatus(RoomType.EXAMINATION, ActivationStatus.ACTIVE)).thenReturn(2L);
        when(roomRepository.saveAndFlush(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
        when(medicalServiceRepository.findAllByStatus(ActivationStatus.ACTIVE)).thenReturn(List.of());
        when(staffAccountRepository.findAllById(any())).thenReturn(List.of(doctor, technician));
        when(staffRoomAssignmentRepository.findByRemovedAtIsNull()).thenReturn(List.of(assign1, assign2));

        var allItems = departmentService.getAllItems();
        var roomItem = allItems.stream().filter(i -> "ROOM".equals(i.getUnitType())).findFirst().orElseThrow();
        assertThat(roomItem.getHeadPerson()).contains("BS. Nguyễn Văn A");
        assertThat(roomItem.getHeadPerson()).contains("KTV. Trần Thị B");
        assertThat(roomItem.getHeadPerson()).isEqualTo("BS. Nguyễn Văn A, KTV. Trần Thị B");

        // Test updateRoom() with multiple staff (không bị crash NonUniqueResultException)
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));
        when(roomRepository.saveAndFlush(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(staffRoomAssignmentRepository.findAllByRoomIdAndRemovedAtIsNull(roomId)).thenReturn(List.of(assign1, assign2));
        when(staffAccountRepository.findAllById(any())).thenReturn(List.of(doctor, technician));

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

    @Test
    @DisplayName("Ném AppException(CONCURRENT_MODIFICATION) khi version chuyên khoa không khớp")
    void updateDepartment_VersionMismatch_ThrowsException() {
        UUID deptId = UUID.randomUUID();
        Department dept = new Department(deptId, "K-NOI", "Khoa Nội", DepartmentType.CLINICAL, "Mô tả", true, now);
        org.springframework.test.util.ReflectionTestUtils.setField(dept, "version", 2L);
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));

        DepartmentRequest request = DepartmentRequest.builder()
                .departmentName("Khoa Nội đổi tên")
                .type(DepartmentType.CLINICAL)
                .version(1L)
                .build();

        assertThatThrownBy(() -> departmentService.updateDepartment(deptId, request))
                .isInstanceOf(clinic.backend.exceptions.AppException.class)
                .satisfies(e -> assertThat(((clinic.backend.exceptions.AppException) e).getErrorCode())
                        .isEqualTo(clinic.backend.enums.ErrorCode.CONCURRENT_MODIFICATION));
    }

    @Test
    @DisplayName("Ném AppException(CONCURRENT_MODIFICATION) khi version phòng không khớp")
    void updateRoom_VersionMismatch_ThrowsException() {
        UUID roomId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Room room = new Room(roomId, now, deptId, null, "PK-01", "Phòng 1", "Tầng 1", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        org.springframework.test.util.ReflectionTestUtils.setField(room, "version", 2L);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        RoomRequest request = RoomRequest.builder()
                .departmentId(deptId)
                .roomName("Phòng 1 đổi tên")
                .location("Tầng 1")
                .type(RoomType.EXAMINATION)
                .version(1L)
                .build();

        assertThatThrownBy(() -> departmentService.updateRoom(roomId, request))
                .isInstanceOf(clinic.backend.exceptions.AppException.class)
                .satisfies(e -> assertThat(((clinic.backend.exceptions.AppException) e).getErrorCode())
                        .isEqualTo(clinic.backend.enums.ErrorCode.CONCURRENT_MODIFICATION));
    }

    @Test
    @DisplayName("Ném AppException(ROOM_HAS_DOCTOR_ON_DUTY) khi tạm ngưng phòng đang có bác sĩ trực trong ngày qua toggleRoomStatus")
    void toggleRoomStatus_ThrowsException_WhenDoctorOnDutyToday() {
        UUID roomId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Room room = new Room(roomId, now, deptId, null, "PK-01", "Phòng 1", "Tầng 1", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        LocalDate today = LocalDate.ofInstant(now, java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        UUID doctorId = UUID.randomUUID();
        DutySchedule duty = new DutySchedule(UUID.randomUUID(), now, today, DutyShift.MORNING, doctorId, roomId, null, null);
        when(dutyScheduleRepository.findByRoomIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(roomId, today, today))
                .thenReturn(List.of(duty));

        StaffAccount doctor = StaffAccount.builder()
                .id(doctorId)
                .fullName("Bác sĩ A")
                .role(Role.DOCTOR)
                .build();
        when(staffAccountRepository.findAllById(java.util.Set.of(doctorId))).thenReturn(List.of(doctor));

        assertThatThrownBy(() -> departmentService.toggleRoomStatus(roomId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ROOM_HAS_DOCTOR_ON_DUTY));
    }

    @Test
    @DisplayName("Ném AppException(ROOM_HAS_DOCTOR_ON_DUTY) khi tạm ngưng phòng đang có kỹ thuật viên trực trong ngày qua toggleRoomStatus")
    void toggleRoomStatus_ThrowsException_WhenTechnicianOnDutyToday() {
        UUID roomId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Room room = new Room(roomId, now, deptId, null, "CDHA-XQ", "Phòng X-quang", "Tầng 2", RoomType.PARACLINICAL, ActivationStatus.ACTIVE);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        LocalDate today = LocalDate.ofInstant(now, java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        UUID techId = UUID.randomUUID();
        DutySchedule duty = new DutySchedule(UUID.randomUUID(), now, today, DutyShift.MORNING, techId, roomId, null, null);
        when(dutyScheduleRepository.findByRoomIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(roomId, today, today))
                .thenReturn(List.of(duty));

        StaffAccount technician = StaffAccount.builder()
                .id(techId)
                .fullName("KTV Nguyễn Văn B")
                .role(Role.TECHNICIAN)
                .build();
        when(staffAccountRepository.findAllById(java.util.Set.of(techId))).thenReturn(List.of(technician));

        assertThatThrownBy(() -> departmentService.toggleRoomStatus(roomId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ROOM_HAS_DOCTOR_ON_DUTY));
    }

    @Test
    @DisplayName("Ném AppException(ROOM_HAS_DOCTOR_ON_DUTY) khi cập nhật phòng sang INACTIVE và phòng đang có bác sĩ trực trong ngày")
    void updateRoom_ThrowsException_WhenDoctorOnDutyTodayAndStatusInactive() {
        UUID roomId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Department dept = new Department(deptId, "K-NOI", "Khoa Nội", DepartmentType.CLINICAL, "Mô tả", true, now);
        Room room = new Room(roomId, now, deptId, null, "PK-01", "Phòng 1", "Tầng 1", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));

        LocalDate today = LocalDate.ofInstant(now, java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        UUID doctorId = UUID.randomUUID();
        DutySchedule duty = new DutySchedule(UUID.randomUUID(), now, today, DutyShift.MORNING, doctorId, roomId, null, null);
        when(dutyScheduleRepository.findByRoomIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(roomId, today, today))
                .thenReturn(List.of(duty));

        StaffAccount doctor = StaffAccount.builder()
                .id(doctorId)
                .fullName("Bác sĩ A")
                .role(Role.DOCTOR)
                .build();
        when(staffAccountRepository.findAllById(java.util.Set.of(doctorId))).thenReturn(List.of(doctor));

        RoomRequest request = RoomRequest.builder()
                .departmentId(deptId)
                .roomName("Phòng 1")
                .location("Tầng 1")
                .type(RoomType.EXAMINATION)
                .status(ActivationStatus.INACTIVE)
                .build();

        assertThatThrownBy(() -> departmentService.updateRoom(roomId, request))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ROOM_HAS_DOCTOR_ON_DUTY));
    }

    @Test
    @DisplayName("Ném AppException(ROOM_HAS_PENDING_WORK) khi tạm ngưng phòng khám lâm sàng đang có bệnh nhân chờ hoặc khám dở trong ngày")
    void toggleRoomStatus_ThrowsException_WhenExaminationRoomHasActiveVisitsToday() {
        UUID roomId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Room room = new Room(roomId, now, deptId, null, "PK-01", "Phòng khám 1", "Tầng 1", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        LocalDate today = LocalDate.ofInstant(now, java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        when(dutyScheduleRepository.findByRoomIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(roomId, today, today))
                .thenReturn(List.of());
        when(dutyScheduleRepository.existsByRoomIdAndWorkDateAfter(roomId, today)).thenReturn(false);
        when(visitRepository.hasActiveVisitsToday(roomId, today, clinic.backend.repository.VisitRepository.FINISHED_STATUSES))
                .thenReturn(true);

        assertThatThrownBy(() -> departmentService.toggleRoomStatus(roomId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ROOM_HAS_PENDING_WORK));
    }

    @Test
    @DisplayName("Ném AppException(ROOM_HAS_FUTURE_SHIFTS) khi tạm ngưng phòng vẫn còn ca trực trong các ngày tới")
    void toggleRoomStatus_ThrowsException_WhenRoomHasFutureDutySchedules() {
        UUID roomId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Room room = new Room(roomId, now, deptId, null, "PK-01", "Phòng khám 1", "Tầng 1", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        LocalDate today = LocalDate.ofInstant(now, java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        when(dutyScheduleRepository.findByRoomIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(roomId, today, today))
                .thenReturn(List.of());
        when(dutyScheduleRepository.existsByRoomIdAndWorkDateAfter(roomId, today)).thenReturn(true);

        assertThatThrownBy(() -> departmentService.toggleRoomStatus(roomId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ROOM_HAS_FUTURE_SHIFTS));
    }

    @Test
    @DisplayName("Ném AppException(ROOM_HAS_PENDING_WORK) khi cập nhật phòng sang INACTIVE và phòng lâm sàng đang có bệnh nhân chờ/khám dở")
    void updateRoom_ThrowsException_WhenExaminationRoomHasActiveVisitsTodayAndStatusInactive() {
        UUID roomId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Department dept = new Department(deptId, "K-NOI", "Khoa Nội", DepartmentType.CLINICAL, "Mô tả", true, now);
        Room room = new Room(roomId, now, deptId, null, "PK-01", "Phòng 1", "Tầng 1", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));

        LocalDate today = LocalDate.ofInstant(now, java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        when(dutyScheduleRepository.findByRoomIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(roomId, today, today))
                .thenReturn(List.of());
        when(dutyScheduleRepository.existsByRoomIdAndWorkDateAfter(roomId, today)).thenReturn(false);
        when(visitRepository.hasActiveVisitsToday(roomId, today, clinic.backend.repository.VisitRepository.FINISHED_STATUSES))
                .thenReturn(true);

        RoomRequest request = RoomRequest.builder()
                .departmentId(deptId)
                .roomName("Phòng 1")
                .location("Tầng 1")
                .type(RoomType.EXAMINATION)
                .status(ActivationStatus.INACTIVE)
                .build();

        assertThatThrownBy(() -> departmentService.updateRoom(roomId, request))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ROOM_HAS_PENDING_WORK));
    }

    @Test
    @DisplayName("Ném AppException(ROOM_HAS_FUTURE_SHIFTS) khi cập nhật phòng sang INACTIVE và phòng còn ca trực các ngày tới")
    void updateRoom_ThrowsException_WhenRoomHasFutureShiftsAndStatusInactive() {
        UUID roomId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Department dept = new Department(deptId, "K-NOI", "Khoa Nội", DepartmentType.CLINICAL, "Mô tả", true, now);
        Room room = new Room(roomId, now, deptId, null, "PK-01", "Phòng 1", "Tầng 1", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));

        LocalDate today = LocalDate.ofInstant(now, java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        when(dutyScheduleRepository.findByRoomIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(roomId, today, today))
                .thenReturn(List.of());
        when(dutyScheduleRepository.existsByRoomIdAndWorkDateAfter(roomId, today)).thenReturn(true);

        RoomRequest request = RoomRequest.builder()
                .departmentId(deptId)
                .roomName("Phòng 1")
                .location("Tầng 1")
                .type(RoomType.EXAMINATION)
                .status(ActivationStatus.INACTIVE)
                .build();

        assertThatThrownBy(() -> departmentService.updateRoom(roomId, request))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ROOM_HAS_FUTURE_SHIFTS));
    }

    @Test
    @DisplayName("RỦI RO 4: Ném ngoại lệ khi ngưng hoạt động chuyên khoa và có phòng trực thuộc đang có bệnh nhân chờ/khám dở")
    void toggleDepartmentStatus_ThrowsException_WhenActiveRoomHasPendingWork() {
        UUID deptId = UUID.randomUUID();
        Department dept = new Department(deptId, "K-NOI", "Khoa Nội", DepartmentType.CLINICAL, "Mô tả", true, now);
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));

        UUID roomId = UUID.randomUUID();
        Room room = new Room(roomId, now, deptId, null, "PK-01", "Phòng 1", "Tầng 1", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        when(roomRepository.findByDepartmentId(deptId)).thenReturn(List.of(room));
        when(roomRepository.countByTypeAndStatus(RoomType.EXAMINATION, ActivationStatus.ACTIVE)).thenReturn(2L);

        LocalDate today = LocalDate.ofInstant(now, java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        when(dutyScheduleRepository.findByRoomIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(roomId, today, today))
                .thenReturn(List.of());
        when(dutyScheduleRepository.existsByRoomIdAndWorkDateAfter(roomId, today)).thenReturn(false);
        when(visitRepository.hasActiveVisitsToday(roomId, today, clinic.backend.repository.VisitRepository.FINISHED_STATUSES))
                .thenReturn(true);

        assertThatThrownBy(() -> departmentService.toggleDepartmentStatus(deptId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.ROOM_HAS_PENDING_WORK);
                    assertThat(e.getMessage()).contains("Không thể tạm ngưng chuyên khoa \"Khoa Nội\"");
                });
    }

    @Test
    @DisplayName("RỦI RO 4: Ngưng hoạt động chuyên khoa thành công và cascade ngưng hoạt động toàn bộ phòng trực thuộc")
    void toggleDepartmentStatus_Success_DeactivatesDepartmentAndAllActiveRooms() {
        UUID deptId = UUID.randomUUID();
        Department dept = new Department(deptId, "K-NOI", "Khoa Nội", DepartmentType.CLINICAL, "Mô tả", true, now);
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));

        UUID roomId = UUID.randomUUID();
        Room room = new Room(roomId, now, deptId, null, "PK-01", "Phòng 1", "Tầng 1", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        when(roomRepository.findByDepartmentId(deptId)).thenReturn(List.of(room));
        when(roomRepository.countByTypeAndStatus(RoomType.EXAMINATION, ActivationStatus.ACTIVE)).thenReturn(2L);

        LocalDate today = LocalDate.ofInstant(now, java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        when(dutyScheduleRepository.findByRoomIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(roomId, today, today))
                .thenReturn(List.of());
        when(dutyScheduleRepository.existsByRoomIdAndWorkDateAfter(roomId, today)).thenReturn(false);
        when(visitRepository.hasActiveVisitsToday(roomId, today, clinic.backend.repository.VisitRepository.FINISHED_STATUSES))
                .thenReturn(false);

        when(departmentRepository.saveAndFlush(any(Department.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DepartmentRoomItemResponse response = departmentService.toggleDepartmentStatus(deptId);

        assertThat(response.getStatus()).isEqualTo("INACTIVE");
        assertThat(room.getStatus()).isEqualTo(ActivationStatus.INACTIVE);
        verify(roomRepository).save(room);
    }

    @Test
    @DisplayName("RỦI RO 3 (EF-06 / MSG94): Ném ngoại lệ khi tạm ngưng phòng khám lâm sàng duy nhất còn hoạt động")
    void toggleRoomStatus_ThrowsException_WhenLastActiveExaminationRoom() {
        UUID roomId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Room room = new Room(roomId, now, deptId, null, "PK-01", "Phòng 1", "Tầng 1", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        LocalDate today = LocalDate.ofInstant(now, java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        when(roomRepository.countByTypeAndStatus(RoomType.EXAMINATION, ActivationStatus.ACTIVE)).thenReturn(1L);
        when(dutyScheduleRepository.findByRoomIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(roomId, today, today))
                .thenReturn(List.of());
        when(dutyScheduleRepository.existsByRoomIdAndWorkDateAfter(roomId, today)).thenReturn(false);
        when(visitRepository.hasActiveVisitsToday(roomId, today, clinic.backend.repository.VisitRepository.FINISHED_STATUSES))
                .thenReturn(false);

        assertThatThrownBy(() -> departmentService.toggleRoomStatus(roomId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.LAST_ACTIVE_EXAMINATION_ROOM);
                    assertThat(e.getMessage()).contains("đây là phòng khám duy nhất còn hoạt động nên không thể ngừng hoạt động");
                });
    }

    @Test
    @DisplayName("RỦI RO 3: Ném ngoại lệ khi ngưng hoạt động chuyên khoa làm tắt toàn bộ phòng khám lâm sàng còn lại trong cơ sở")
    void toggleDepartmentStatus_ThrowsException_WhenDeactivatingAllRemainingActiveExaminationRooms() {
        UUID deptId = UUID.randomUUID();
        Department dept = new Department(deptId, "K-NOI", "Khoa Nội", DepartmentType.CLINICAL, "Mô tả", true, now);
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));

        UUID roomId = UUID.randomUUID();
        Room room = new Room(roomId, now, deptId, null, "PK-01", "Phòng 1", "Tầng 1", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        when(roomRepository.findByDepartmentId(deptId)).thenReturn(List.of(room));
        when(roomRepository.countByTypeAndStatus(RoomType.EXAMINATION, ActivationStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> departmentService.toggleDepartmentStatus(deptId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.LAST_ACTIVE_EXAMINATION_ROOM);
                    assertThat(e.getMessage()).contains("thao tác này sẽ ngừng hoạt động toàn bộ phòng khám lâm sàng còn lại trong cơ sở");
                });
    }

    private RoomRequest roomRequest(UUID deptId, RoomType type, UUID groupId) {
        return RoomRequest.builder()
                .roomCode("PK-TEST-01")
                .roomName("Phòng kiểm thử 1")
                .departmentId(deptId)
                .serviceGroupId(groupId)
                .location("Tòa A - P.101")
                .type(type)
                .status(ActivationStatus.ACTIVE)
                .build();
    }

    private static ErrorCode codeOf(Throwable e) {
        return ((AppException) e).getErrorCode();
    }

    @Test
    @DisplayName("Ném ROOM_DEPARTMENT_TYPE_MISMATCH khi phòng khám lâm sàng đặt vào Khoa cận lâm sàng")
    void createRoom_ExaminationRoomInParaclinicalDepartment_ThrowsException() {
        UUID deptId = UUID.randomUUID();
        Department dept = new Department(deptId, "K-XN", "Khoa Xét Nghiệm", DepartmentType.PARACLINICAL, "Mô tả", true, now);
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));

        assertThatThrownBy(() -> departmentService.createRoom(roomRequest(deptId, RoomType.EXAMINATION, null)))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ROOM_DEPARTMENT_TYPE_MISMATCH))
                .hasMessageContaining("Phòng khám lâm sàng phải trực thuộc Khoa lâm sàng");
        verify(roomRepository, never()).save(any(Room.class));
    }

    @Test
    @DisplayName("Ném ROOM_DEPARTMENT_TYPE_MISMATCH khi phòng cận lâm sàng đặt vào Khoa lâm sàng")
    void createRoom_ParaclinicalRoomInClinicalDepartment_ThrowsException() {
        UUID deptId = UUID.randomUUID();
        Department dept = new Department(deptId, "K-NOI", "Khoa Nội", DepartmentType.CLINICAL, "Mô tả", true, now);
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));

        assertThatThrownBy(() -> departmentService.createRoom(roomRequest(deptId, RoomType.PARACLINICAL, UUID.randomUUID())))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ROOM_DEPARTMENT_TYPE_MISMATCH))
                .hasMessageContaining("Phòng khám cận lâm sàng phải trực thuộc Khoa cận lâm sàng");
        verify(roomRepository, never()).save(any(Room.class));
    }

    @Test
    @DisplayName("Ném PARACLINICAL_ROOM_REQUIRES_SERVICE_GROUP khi phòng cận lâm sàng không chọn nhóm dịch vụ kỹ thuật")
    void createRoom_ParaclinicalWithoutServiceGroup_ThrowsException() {
        UUID deptId = UUID.randomUUID();
        Department dept = new Department(deptId, "K-XN", "Khoa Xét Nghiệm", DepartmentType.PARACLINICAL, "Mô tả", true, now);
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));

        assertThatThrownBy(() -> departmentService.createRoom(roomRequest(deptId, RoomType.PARACLINICAL, null)))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.PARACLINICAL_ROOM_REQUIRES_SERVICE_GROUP));
    }

    @Test
    @DisplayName("Ném SERVICE_GROUP_NOT_FOUND khi nhóm dịch vụ kỹ thuật không tồn tại")
    void createRoom_UnknownServiceGroup_ThrowsException() {
        UUID deptId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Department dept = new Department(deptId, "K-XN", "Khoa Xét Nghiệm", DepartmentType.PARACLINICAL, "Mô tả", true, now);
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));
        when(serviceGroupRepository.findById(groupId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.createRoom(roomRequest(deptId, RoomType.PARACLINICAL, groupId)))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.SERVICE_GROUP_NOT_FOUND));
    }

    @Test
    @DisplayName("Ném DEPARTMENT_NOT_FOUND khi khoa trực thuộc không tồn tại")
    void createRoom_UnknownDepartment_ThrowsException() {
        UUID deptId = UUID.randomUUID();
        when(departmentRepository.findById(deptId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.createRoom(roomRequest(deptId, RoomType.EXAMINATION, null)))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.DEPARTMENT_NOT_FOUND));
    }

    @Test
    @DisplayName("Ném REQUEST_VALIDATION_FAILED khi mã phòng, tên phòng hoặc vị trí buồng để trống")
    void createRoom_BlankRequiredFields_ThrowsException() {
        UUID deptId = UUID.randomUUID();
        RoomRequest blankCode = roomRequest(deptId, RoomType.EXAMINATION, null);
        blankCode.setRoomCode("   ");
        assertThatThrownBy(() -> departmentService.createRoom(blankCode))
                .hasMessageContaining("Vui lòng nhập mã phòng khám");

        RoomRequest blankName = roomRequest(deptId, RoomType.EXAMINATION, null);
        blankName.setRoomName("   ");
        assertThatThrownBy(() -> departmentService.createRoom(blankName))
                .hasMessageContaining("Vui lòng nhập tên phòng khám");

        RoomRequest blankLocation = roomRequest(deptId, RoomType.EXAMINATION, null);
        blankLocation.setLocation("   ");
        assertThatThrownBy(() -> departmentService.createRoom(blankLocation))
                .hasMessageContaining("Vui lòng nhập vị trí buồng");
    }

    @Test
    @DisplayName("Ném ngoại lệ khi tên phòng trên 100 ký tự hoặc vị trí buồng trên 255 ký tự")
    void createRoom_TooLongNameOrLocation_ThrowsException() {
        UUID deptId = UUID.randomUUID();
        RoomRequest longName = roomRequest(deptId, RoomType.EXAMINATION, null);
        longName.setRoomName("P".repeat(101));
        assertThatThrownBy(() -> departmentService.createRoom(longName))
                .hasMessageContaining("Tên phòng phải từ 2 đến 100 ký tự");

        RoomRequest longLocation = roomRequest(deptId, RoomType.EXAMINATION, null);
        longLocation.setLocation("L".repeat(256));
        assertThatThrownBy(() -> departmentService.createRoom(longLocation))
                .hasMessageContaining("Vị trí buồng phải từ 2 đến 255 ký tự");
    }

    @Test
    @DisplayName("Ném ngoại lệ khi tạo khoa mà mã khoa hoặc tên khoa để trống")
    void createDepartment_BlankCodeOrName_ThrowsException() {
        DepartmentRequest blankCode = DepartmentRequest.builder()
                .departmentCode(" ").departmentName("Khoa Nội").type(DepartmentType.CLINICAL).build();
        assertThatThrownBy(() -> departmentService.createDepartment(blankCode))
                .hasMessageContaining("Vui lòng nhập mã chuyên khoa");

        DepartmentRequest blankName = DepartmentRequest.builder()
                .departmentCode("K-NOI").departmentName(" ").type(DepartmentType.CLINICAL).build();
        assertThatThrownBy(() -> departmentService.createDepartment(blankName))
                .hasMessageContaining("Vui lòng nhập tên chuyên khoa");
    }

    @Test
    @DisplayName("Ném ngoại lệ khi tạo khoa mà mã khoa hoặc tên khoa đã tồn tại")
    void createDepartment_DuplicateCodeOrName_ThrowsException() {
        when(departmentRepository.existsByDepartmentCode("K-NOI")).thenReturn(true);
        DepartmentRequest dupCode = DepartmentRequest.builder()
                .departmentCode("k-noi").departmentName("Khoa Nội").type(DepartmentType.CLINICAL).build();
        assertThatThrownBy(() -> departmentService.createDepartment(dupCode))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.DEPARTMENT_CODE_ALREADY_EXISTS));

        when(departmentRepository.existsByDepartmentCode("K-NGOAI")).thenReturn(false);
        when(departmentRepository.existsByDepartmentNameIgnoreCase("Khoa Ngoại")).thenReturn(true);
        DepartmentRequest dupName = DepartmentRequest.builder()
                .departmentCode("K-NGOAI").departmentName("  Khoa Ngoại ").type(DepartmentType.CLINICAL).build();
        assertThatThrownBy(() -> departmentService.createDepartment(dupName))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.DEPARTMENT_NAME_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("updateDepartment: kiểm tra tên trống, độ dài tên, tên trùng và mô tả quá 500 ký tự")
    void updateDepartment_InvalidFields_ThrowsException() {
        UUID deptId = UUID.randomUUID();
        Department dept = new Department(deptId, "K-NOI", "Khoa Nội", DepartmentType.CLINICAL, "Mô tả", true, now);
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));

        assertThatThrownBy(() -> departmentService.updateDepartment(deptId,
                DepartmentRequest.builder().departmentName(" ").type(DepartmentType.CLINICAL).build()))
                .hasMessageContaining("Vui lòng nhập tên chuyên khoa");
        assertThatThrownBy(() -> departmentService.updateDepartment(deptId,
                DepartmentRequest.builder().departmentName("A".repeat(101)).type(DepartmentType.CLINICAL).build()))
                .hasMessageContaining("Tên khoa phải từ 2 đến 100 ký tự");

        when(departmentRepository.existsByDepartmentNameIgnoreCase("Khoa Ngoại")).thenReturn(true);
        assertThatThrownBy(() -> departmentService.updateDepartment(deptId,
                DepartmentRequest.builder().departmentName("Khoa Ngoại").type(DepartmentType.CLINICAL).build()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.DEPARTMENT_NAME_ALREADY_EXISTS));

        when(departmentRepository.existsByDepartmentNameIgnoreCase("Khoa Nội")).thenReturn(true);
        assertThatThrownBy(() -> departmentService.updateDepartment(deptId,
                DepartmentRequest.builder().departmentName("Khoa Nội").type(DepartmentType.CLINICAL)
                        .description("X".repeat(501)).build()))
                .hasMessageContaining("Mô tả chuyên khoa tối đa 500 ký tự");
    }

    @Test
    @DisplayName("updateRoom: Ném ngoại lệ khi vị trí buồng để trống hoặc phòng CLS bỏ nhóm dịch vụ kỹ thuật")
    void updateRoom_BlankLocationOrMissingServiceGroup_ThrowsException() {
        UUID deptId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Department dept = new Department(deptId, "K-XN", "Khoa Xét Nghiệm", DepartmentType.PARACLINICAL, "Mô tả", true, now);
        Room room = new Room(roomId, now, deptId, UUID.randomUUID(), "XN-01", "Phòng Xét nghiệm 1", "Tầng 1",
                RoomType.PARACLINICAL, ActivationStatus.ACTIVE);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));

        RoomRequest blankLocation = roomRequest(deptId, RoomType.PARACLINICAL, room.getServiceGroupId());
        blankLocation.setRoomName("Phòng Xét nghiệm 1");
        blankLocation.setLocation(" ");
        assertThatThrownBy(() -> departmentService.updateRoom(roomId, blankLocation))
                .hasMessageContaining("Vui lòng nhập vị trí buồng");

        RoomRequest noGroup = roomRequest(deptId, RoomType.PARACLINICAL, null);
        noGroup.setRoomName("Phòng Xét nghiệm 1");
        assertThatThrownBy(() -> departmentService.updateRoom(roomId, noGroup))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.PARACLINICAL_ROOM_REQUIRES_SERVICE_GROUP));
    }
}
