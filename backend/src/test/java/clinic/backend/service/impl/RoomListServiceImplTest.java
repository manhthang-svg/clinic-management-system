package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import clinic.backend.dto.response.DepartmentRoomItemResponse;
import clinic.backend.entity.Department;
import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.Room;
import clinic.backend.entity.ServiceGroup;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.DepartmentType;
import clinic.backend.enums.DutyShift;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.enums.Role;
import clinic.backend.enums.RoomType;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.ServiceGroupRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** UC-16 View Room List: the room rows of the Department &amp; Room screen. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoomListServiceImplTest {

    // 10:00 in Vietnam on 25/11/2026.
    private static final Instant NOW = Instant.parse("2026-11-25T03:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Mock DepartmentRepository departmentRepository;
    @Mock RoomRepository roomRepository;
    @Mock ServiceGroupRepository serviceGroupRepository;
    @Mock StaffRoomAssignmentRepository staffRoomAssignmentRepository;
    @Mock StaffAccountRepository staffAccountRepository;
    @Mock MedicalServiceRepository medicalServiceRepository;
    @Mock ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;
    @Mock DutyScheduleRepository dutyScheduleRepository;
    @Mock VisitRepository visitRepository;
    @Mock UuidV7Generator uuidV7Generator;

    private DepartmentServiceImpl service;
    private final UUID deptId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DepartmentServiceImpl(departmentRepository, roomRepository, serviceGroupRepository,
                staffRoomAssignmentRepository, staffAccountRepository, medicalServiceRepository,
                paraclinicalOrderDetailRepository, dutyScheduleRepository, visitRepository,
                Clock.fixed(NOW, ZoneOffset.UTC), uuidV7Generator);
        when(departmentRepository.findAll()).thenReturn(List.of(
                new Department(deptId, "K-NOI", "Khoa Nội", DepartmentType.CLINICAL, null, true, NOW)));
        ServiceGroup group = mock(ServiceGroup.class);
        when(group.getId()).thenReturn(groupId);
        when(group.getServiceGroupName()).thenReturn("Xét nghiệm huyết học");
        when(serviceGroupRepository.findAll()).thenReturn(List.of(group));
        when(staffRoomAssignmentRepository.findByRemovedAtIsNull()).thenReturn(List.of());
        when(dutyScheduleRepository.findByWorkDateBetweenOrderByWorkDateAscShiftAsc(TODAY, TODAY))
                .thenReturn(List.of());
        when(dutyScheduleRepository.findRoomIdsWithDutyScheduleAfter(TODAY)).thenReturn(Set.of());
        when(visitRepository.findRoomIdsWithActiveVisitsToday(any(), any())).thenReturn(Set.of());
        when(paraclinicalOrderDetailRepository.findRoomIdsWithStatusIn(any())).thenReturn(Set.of());
        List<MedicalService> services = List.of(
                service(MedicalServiceType.EXAMINATION, null, "KB01", "Khám bệnh"),
                service(MedicalServiceType.LAB, groupId, "XN01", "Tổng phân tích tế bào máu"));
        when(medicalServiceRepository.findAllByStatus(ActivationStatus.ACTIVE)).thenReturn(services);
    }

    @Test
    void listsDepartmentsFirstThenRoomsSortedByCode() {
        Room p102 = room("P102", RoomType.EXAMINATION, null, ActivationStatus.ACTIVE);
        Room p101 = room("P101", RoomType.EXAMINATION, null, ActivationStatus.INACTIVE);
        when(roomRepository.findAll()).thenReturn(List.of(p102, p101));

        List<DepartmentRoomItemResponse> items = service.getAllItems();

        assertThat(items).extracting(DepartmentRoomItemResponse::getCode).containsExactly("K-NOI", "P101", "P102");
        assertThat(items.get(0).getUnitType()).isEqualTo("DEPARTMENT");
        assertThat(items.get(0).getRoomCount()).isEqualTo(2);
        assertThat(items.get(1).getStatus()).isEqualTo("INACTIVE");
        assertThat(items.get(1).getDepartmentName()).isEqualTo("Khoa Nội");
    }

    @Test
    void roomWithoutStaffShowsNotAssigned() {
        when(roomRepository.findAll()).thenReturn(List.of(
                room("P101", RoomType.EXAMINATION, null, ActivationStatus.ACTIVE)));

        DepartmentRoomItemResponse room = roomItem(service.getAllItems());

        assertThat(room.getHeadPerson()).isEqualTo("Chưa phân công");
        assertThat(room.getHasDoctorOnDutyToday()).isFalse();
        assertThat(room.getDoctorOnDutyToday()).isNull();
    }

    @Test
    void roomOfAnUnknownDepartmentIsShownAsNotClassified() {
        Room orphan = new Room(UUID.randomUUID(), NOW, UUID.randomUUID(), null, "P900", "Phòng tạm", "Tầng 9",
                RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        when(roomRepository.findAll()).thenReturn(List.of(orphan));

        assertThat(roomItem(service.getAllItems()).getDepartmentName()).isEqualTo("Chưa phân khoa");
    }

    @Test
    void examinationRoomOffersTheExaminationServices() {
        when(roomRepository.findAll()).thenReturn(List.of(
                room("P101", RoomType.EXAMINATION, null, ActivationStatus.ACTIVE)));

        assertThat(roomItem(service.getAllItems()).getAssignedServices())
                .singleElement().satisfies(s -> assertThat(s.code()).isEqualTo("KB01"));
    }

    @Test
    void paraclinicalRoomOffersTheServicesOfItsGroup() {
        when(roomRepository.findAll()).thenReturn(List.of(
                room("XN01", RoomType.PARACLINICAL, groupId, ActivationStatus.ACTIVE)));

        DepartmentRoomItemResponse room = roomItem(service.getAllItems());

        assertThat(room.getServiceGroupName()).isEqualTo("Xét nghiệm huyết học");
        assertThat(room.getAssignedServices()).singleElement()
                .satisfies(s -> assertThat(s.code()).isEqualTo("XN01"));
    }

    @Test
    void paraclinicalRoomWithoutGroupOffersNoService() {
        when(roomRepository.findAll()).thenReturn(List.of(
                room("XN02", RoomType.PARACLINICAL, null, ActivationStatus.ACTIVE)));

        assertThat(roomItem(service.getAllItems()).getAssignedServices()).isEmpty();
    }

    @Test
    void showsTheDoctorOnDutyTodayWithAcademicTitleOnce() {
        Room room = room("P101", RoomType.EXAMINATION, null, ActivationStatus.ACTIVE);
        when(roomRepository.findAll()).thenReturn(List.of(room));
        UUID doctorId = UUID.randomUUID();
        StaffAccount doctor = StaffAccount.builder().id(doctorId).fullName("Nguyễn Văn A").academicTitle(" BS. ")
                .role(Role.DOCTOR).build();
        when(staffAccountRepository.findAllById(any())).thenReturn(List.of(doctor));
        when(dutyScheduleRepository.findByWorkDateBetweenOrderByWorkDateAscShiftAsc(TODAY, TODAY)).thenReturn(List.of(
                new DutySchedule(UUID.randomUUID(), NOW, TODAY, DutyShift.MORNING, doctorId, room.getId(), null, null),
                new DutySchedule(UUID.randomUUID(), NOW, TODAY, DutyShift.AFTERNOON, doctorId, room.getId(), null, null)));

        DepartmentRoomItemResponse item = roomItem(service.getAllItems());

        assertThat(item.getHasDoctorOnDutyToday()).isTrue();
        assertThat(item.getDoctorOnDutyToday()).isEqualTo("BS. Nguyễn Văn A");
    }

    @Test
    void ignoresDutyOfStaffWhoIsNotDoctorOrTechnician() {
        Room room = room("P101", RoomType.EXAMINATION, null, ActivationStatus.ACTIVE);
        when(roomRepository.findAll()).thenReturn(List.of(room));
        UUID cashierId = UUID.randomUUID();
        when(staffAccountRepository.findAllById(any())).thenReturn(List.of(
                StaffAccount.builder().id(cashierId).fullName("Thu Ngân").role(Role.CASHIER).build()));
        when(dutyScheduleRepository.findByWorkDateBetweenOrderByWorkDateAscShiftAsc(TODAY, TODAY)).thenReturn(List.of(
                new DutySchedule(UUID.randomUUID(), NOW, TODAY, DutyShift.MORNING, cashierId, room.getId(), null, null)));

        assertThat(roomItem(service.getAllItems()).getHasDoctorOnDutyToday()).isFalse();
    }

    @Test
    void flagsFutureShiftsAndActiveVisitsOfAnExaminationRoom() {
        Room room = room("P101", RoomType.EXAMINATION, null, ActivationStatus.ACTIVE);
        when(roomRepository.findAll()).thenReturn(List.of(room));
        when(dutyScheduleRepository.findRoomIdsWithDutyScheduleAfter(TODAY)).thenReturn(Set.of(room.getId()));
        when(visitRepository.findRoomIdsWithActiveVisitsToday(any(), any())).thenReturn(Set.of(room.getId()));

        DepartmentRoomItemResponse item = roomItem(service.getAllItems());

        assertThat(item.getHasFutureDutySchedules()).isTrue();
        assertThat(item.getHasActiveVisitsToday()).isTrue();
    }

    @Test
    void paraclinicalRoomBusyMeansPendingOrdersNotVisits() {
        Room room = room("XN01", RoomType.PARACLINICAL, groupId, ActivationStatus.ACTIVE);
        when(roomRepository.findAll()).thenReturn(List.of(room));
        when(visitRepository.findRoomIdsWithActiveVisitsToday(any(), any())).thenReturn(Set.of(room.getId()));

        assertThat(roomItem(service.getAllItems()).getHasActiveVisitsToday()).isFalse();

        when(paraclinicalOrderDetailRepository.findRoomIdsWithStatusIn(any())).thenReturn(Set.of(room.getId()));

        assertThat(roomItem(service.getAllItems()).getHasActiveVisitsToday()).isTrue();
    }

    private static DepartmentRoomItemResponse roomItem(List<DepartmentRoomItemResponse> items) {
        return items.stream().filter(i -> "ROOM".equals(i.getUnitType())).findFirst().orElseThrow();
    }

    private Room room(String code, RoomType type, UUID serviceGroupId, ActivationStatus status) {
        return new Room(UUID.randomUUID(), NOW, deptId, serviceGroupId, code, "Phòng " + code, "Tầng 1", type, status);
    }

    private static MedicalService service(MedicalServiceType type, UUID groupId, String code, String name) {
        MedicalService service = mock(MedicalService.class);
        when(service.getId()).thenReturn(UUID.randomUUID());
        when(service.getType()).thenReturn(type);
        when(service.getServiceGroupId()).thenReturn(groupId);
        when(service.getCode()).thenReturn(code);
        when(service.getTenchidinh()).thenReturn(name);
        when(service.getClinicPrice()).thenReturn(BigDecimal.valueOf(100_000));
        when(service.getStatus()).thenReturn(ActivationStatus.ACTIVE);
        return service;
    }
}
