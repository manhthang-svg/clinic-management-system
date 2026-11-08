package clinic.backend.service.impl;

import clinic.backend.dto.response.RoomResponse;
import clinic.backend.entity.Department;
import clinic.backend.entity.Room;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.RoomType;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;
    private final DepartmentRepository departmentRepository;
    private final ExaminationRoomDuty examinationRoomDuty;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> getActiveExaminationRooms() {
        List<Room> rooms = roomRepository.findByTypeAndStatusOrderByRoomCodeAsc(
                RoomType.EXAMINATION,
                ActivationStatus.ACTIVE
        );
        Map<UUID, String> departmentNames = departmentRepository.findAllById(rooms.stream()
                        .map(Room::getDepartmentId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Department::getId, Department::getDepartmentName));

        // BR-96: only rooms with a doctor on duty in the current registration shift can take patients,
        // and the room shows that doctor only.
        Instant now = clock.instant();
        return rooms.stream()
                .flatMap(room -> examinationRoomDuty.onDutyDoctor(room.getId(), now).stream()
                        .map(doctor -> toResponse(
                                room,
                                departmentNames.get(room.getDepartmentId()),
                                ExaminationRoomDuty.displayName(doctor))))
                .toList();
    }

    private RoomResponse toResponse(Room room, String departmentName, String doctorName) {
        return new RoomResponse(
                room.getId(),
                room.getDepartmentId(),
                departmentName,
                room.getServiceGroupId(),
                room.getRoomCode(),
                room.getRoomName(),
                room.getLocation(),
                room.getType(),
                room.getStatus(),
                doctorName,
                room.getCreatedAt(),
                room.getUpdatedAt()
        );
    }
}
