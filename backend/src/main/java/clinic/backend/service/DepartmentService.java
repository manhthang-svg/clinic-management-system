package clinic.backend.service;

import java.util.List;
import java.util.UUID;
import clinic.backend.dto.request.DepartmentRequest;
import clinic.backend.dto.request.RoomRequest;
import clinic.backend.dto.response.DepartmentRoomItemResponse;
import clinic.backend.dto.response.DepartmentStatsResponse;

public interface DepartmentService {

    List<DepartmentRoomItemResponse> getAllItems();

    DepartmentStatsResponse getStats();

    List<DepartmentRoomItemResponse> getDepartmentOptions();

    DepartmentRoomItemResponse createDepartment(DepartmentRequest request);

    DepartmentRoomItemResponse updateDepartment(UUID id, DepartmentRequest request);

    DepartmentRoomItemResponse toggleDepartmentStatus(UUID id);

    DepartmentRoomItemResponse createRoom(RoomRequest request);

    DepartmentRoomItemResponse updateRoom(UUID id, RoomRequest request);

    DepartmentRoomItemResponse toggleRoomStatus(UUID id);
}
