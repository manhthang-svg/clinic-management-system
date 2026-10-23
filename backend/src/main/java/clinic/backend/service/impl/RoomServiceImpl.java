package clinic.backend.service.impl;

import clinic.backend.dto.request.CreateRoomRequest;
import clinic.backend.dto.response.RoomResponse;
import clinic.backend.entity.Room;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.RoomType;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.ServiceGroupRepository;
import clinic.backend.service.RoomService;
import clinic.backend.utils.UuidV7Generator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class RoomServiceImpl implements RoomService {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ServiceGroupRepository serviceGroupRepository;

    private final UuidV7Generator uuidV7Generator;

    public RoomServiceImpl(
            UuidV7Generator uuidV7Generator,
            RoomRepository roomRepository,
            DepartmentRepository departmentRepository,
            ServiceGroupRepository serviceGroupRepository
    ) {
        this.uuidV7Generator = uuidV7Generator;
        this.roomRepository = roomRepository;
        this.departmentRepository = departmentRepository;
        this.serviceGroupRepository = serviceGroupRepository;
    }

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
            serviceGroupRepository.findById(serviceGroupId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Không tìm thấy nhóm dịch vụ"
                    ));
        } else if (request.type() == RoomType.EXAMINATION) {
            if (request.serviceGroupId() != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Phòng khám không được liên kết với nhóm dịch vụ"
                );
            }
        }

        departmentRepository.findById(request.departmentId())
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

        return new RoomResponse(
                savedRoom.getId(),
                savedRoom.getDepartmentId(),
                savedRoom.getServiceGroupId(),
                savedRoom.getRoomCode(),
                savedRoom.getRoomName(),
                savedRoom.getLocation(),
                savedRoom.getType(),
                savedRoom.getStatus(),
                savedRoom.getCreatedAt(),
                savedRoom.getUpdatedAt()
        );
    }
}
