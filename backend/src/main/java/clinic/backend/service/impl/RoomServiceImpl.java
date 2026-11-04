package clinic.backend.service.impl;

import clinic.backend.dto.request.CreateRoomRequest;
import clinic.backend.dto.response.RoomResponse;
import clinic.backend.entity.Department;
import clinic.backend.entity.Room;
import clinic.backend.entity.ServiceGroup;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.RoomType;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.ServiceGroupRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.service.RoomService;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
    private final ServiceGroupRepository serviceGroupRepository;
    private final StaffRoomAssignmentRepository staffRoomAssignmentRepository;
    private final UuidV7Generator uuidV7Generator;

    @Override
    public void assignStaffToRoom(Long roomId, Long staffId) {

    }

    @Override
    public void removeStaffFromRoom(Long roomId, Long staffId) {

    }

    @Override
    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request) {
        String roomCode = request.code().trim();


        UUID serviceGroupId = null;
        if(request.type() == RoomType.PARACLINICAL){
            serviceGroupId = request.serviceGroupId();
            if (serviceGroupId == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Phòng cận lâm sàng phải có nhóm dịch vụ"
                );
            }
            ServiceGroup group = serviceGroupRepository.findById(serviceGroupId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Không tìm thấy nhóm dịch vụ"
                    ));
            if (group.getDepartmentId() != null && !group.getDepartmentId().equals(request.departmentId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Nhóm dịch vụ kỹ thuật không thuộc chuyên khoa đã chọn"
                );
            }
        } else if (request.type() == RoomType.EXAMINATION) {
            if (request.serviceGroupId() != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Phòng khám không được liên kết với nhóm dịch vụ"
                );
            }
        }

        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy khoa"
                ));

        if (roomRepository.existsByRoomCode(roomCode)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Mã phòng khám đã tồn tại"
            );
        }


        Room room = new Room(
                uuidV7Generator.next(),
                Instant.now(),
                request.departmentId(),
                serviceGroupId,
                roomCode,
                request.name().trim(),
                request.location().trim(),
                request.type(),
                ActivationStatus.ACTIVE
        );

        Room savedRoom = roomRepository.save(room);

        // A new room has no doctor assigned yet.
        return toResponse(savedRoom, department.getDepartmentName(), null);
    }

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

        // Only rooms with an active assigned doctor can take patients; their names are joined in name order.
        return rooms.stream()
                .map(room -> Map.entry(room, staffRoomAssignmentRepository.findActiveDoctorNames(room.getId())))
                .filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> toResponse(
                        entry.getKey(),
                        departmentNames.get(entry.getKey().getDepartmentId()),
                        String.join(", ", entry.getValue())))
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
