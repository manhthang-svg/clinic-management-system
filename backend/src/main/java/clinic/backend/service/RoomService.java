package clinic.backend.service;

import clinic.backend.dto.response.RoomResponse;
import java.util.List;

public interface RoomService {

    List<RoomResponse> getActiveExaminationRooms();
}
