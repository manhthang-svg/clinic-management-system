package clinic.backend.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.CreateRoomRequest;
import clinic.backend.dto.response.RoomResponse;
import clinic.backend.entity.Department;
import clinic.backend.entity.Room;
import clinic.backend.entity.ServiceGroup;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.DepartmentType;
import clinic.backend.enums.RoomType;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.ServiceGroupRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-28T01:00:00Z");

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private ServiceGroupRepository serviceGroupRepository;

    @Mock
    private StaffRoomAssignmentRepository staffRoomAssignmentRepository;

    @Mock
    private UuidV7Generator ids;

    private final UUID departmentId = UUID.randomUUID();

    private RoomServiceImpl service() {
        return new RoomServiceImpl(
                roomRepository, departmentRepository, serviceGroupRepository,
                staffRoomAssignmentRepository, ids);
    }

    @Test
    void createsExaminationRoomWithNormalizedValues() {
        UUID roomId = UUID.randomUUID();
        when(departmentRepository.findById(departmentId))
                .thenReturn(Optional.of(department()));
        when(roomRepository.existsByRoomCode("PK-01")).thenReturn(false);
        when(ids.next()).thenReturn(roomId);
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoomResponse response = service().createRoom(new CreateRoomRequest(
                departmentId, null, " PK-01 ", " Phòng khám ", " Tầng 1 ", RoomType.EXAMINATION));

        assertThat(response.id()).isEqualTo(roomId);
        assertThat(response.code()).isEqualTo("PK-01");
        assertThat(response.name()).isEqualTo("Phòng khám");
        assertThat(response.location()).isEqualTo("Tầng 1");
        assertThat(response.status()).isEqualTo(ActivationStatus.ACTIVE);
        verify(roomRepository).save(any(Room.class));
    }

    @Test
    void rejectsParaclinicalRoomWithoutServiceGroup() {
        assertThatThrownBy(() -> service().createRoom(new CreateRoomRequest(
                departmentId, null, "XN-01", "Xét nghiệm", "Tầng 2", RoomType.PARACLINICAL)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("phải có nhóm dịch vụ");

        verifyNoRoomCreation();
    }

    @Test
    void listsOnlyActiveExaminationRooms() {
        Room room = new Room(
                UUID.randomUUID(), NOW, departmentId, null,
                "PK-01", "Phòng khám", "Tầng 1",
                RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        when(roomRepository.findByTypeAndStatusOrderByRoomCodeAsc(
                RoomType.EXAMINATION, ActivationStatus.ACTIVE))
                .thenReturn(List.of(room));
        when(staffRoomAssignmentRepository.hasActiveDoctor(room.getId())).thenReturn(true);

        List<RoomResponse> response = service().getActiveExaminationRooms();

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo("PK-01");
            assertThat(item.type()).isEqualTo(RoomType.EXAMINATION);
            assertThat(item.status()).isEqualTo(ActivationStatus.ACTIVE);
        });
    }

    private Department department() {
        return new Department(
                departmentId, "K-NOI", "Khoa Nội", DepartmentType.CLINICAL,
                null, true, NOW);
    }

    private void verifyNoRoomCreation() {
        verify(roomRepository, never()).save(any(Room.class));
    }
}
