package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import clinic.backend.dto.response.RoomResponse;
import clinic.backend.entity.Department;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.DepartmentType;
import clinic.backend.enums.RoomType;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-28T01:00:00Z");

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private ExaminationRoomDuty examinationRoomDuty;

    private final UUID departmentId = UUID.randomUUID();

    private RoomServiceImpl service() {
        return new RoomServiceImpl(
                roomRepository, departmentRepository,
                examinationRoomDuty, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void listsOnlyRoomsWithADoctorOnDutyAndShowsThatDoctor() {
        Room room = new Room(
                UUID.randomUUID(), NOW, departmentId, null,
                "PK-01", "Phòng khám", "Tầng 1",
                RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        Room roomWithoutDoctor = new Room(
                UUID.randomUUID(), NOW, departmentId, null,
                "PK-02", "Phòng khám 2", "Tầng 1",
                RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        when(roomRepository.findByTypeAndStatusOrderByRoomCodeAsc(
                RoomType.EXAMINATION, ActivationStatus.ACTIVE))
                .thenReturn(List.of(room, roomWithoutDoctor));
        when(departmentRepository.findAllById(Set.of(departmentId))).thenReturn(List.of(department()));
        StaffAccount onDuty = mock(StaffAccount.class);
        when(onDuty.getFullName()).thenReturn("Lê Thị Bình");
        when(examinationRoomDuty.onDutyDoctor(room.getId(), NOW)).thenReturn(Optional.of(onDuty));
        when(examinationRoomDuty.onDutyDoctor(roomWithoutDoctor.getId(), NOW)).thenReturn(Optional.empty());

        List<RoomResponse> response = service().getActiveExaminationRooms();

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo("PK-01");
            assertThat(item.type()).isEqualTo(RoomType.EXAMINATION);
            assertThat(item.status()).isEqualTo(ActivationStatus.ACTIVE);
            assertThat(item.departmentName()).isEqualTo("Khoa Nội");
            assertThat(item.doctorName()).isEqualTo("Lê Thị Bình");
        });
    }

    private Department department() {
        return new Department(
                departmentId, "K-NOI", "Khoa Nội", DepartmentType.CLINICAL,
                null, true, NOW);
    }
}
