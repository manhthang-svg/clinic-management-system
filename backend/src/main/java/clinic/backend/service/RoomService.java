package clinic.backend.service;

import clinic.backend.dto.request.CreateRoomRequest;
import clinic.backend.dto.response.RoomResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;


public interface RoomService {

    void assignStaffToRoom(
            Long roomId,
            Long staffId
    );

    void removeStaffFromRoom(
            Long roomId,
            Long staffId
    );

    RoomResponse createRoom(@Valid CreateRoomRequest request);
}