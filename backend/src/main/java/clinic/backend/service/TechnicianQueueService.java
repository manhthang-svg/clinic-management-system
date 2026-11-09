package clinic.backend.service;

import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.RejectParaclinicalOrderRequest;
import clinic.backend.dto.response.TechnicianQueueItemResponse;
import clinic.backend.dto.response.TechnicianQueueResponse;
import clinic.backend.dto.response.TechnicianRoomResponse;

public interface TechnicianQueueService {

    List<TechnicianRoomResponse> getTechnicianRooms(UUID currentUserId);

    default List<TechnicianRoomResponse> getTechnicianRooms() {
        return getTechnicianRooms(null);
    }

    TechnicianQueueResponse getQueue(UUID roomId, String search, String status, String doctorRoom, UUID currentUserId);

    default TechnicianQueueResponse getQueue(UUID roomId, String search, String status, String doctorRoom) {
        return getQueue(roomId, search, status, doctorRoom, null);
    }

    TechnicianQueueItemResponse getQueueDetail(UUID detailId);

    TechnicianQueueItemResponse acceptOrder(UUID detailId, UUID technicianAccountId);

    TechnicianQueueItemResponse skipOrder(UUID detailId, UUID technicianAccountId);

    TechnicianQueueItemResponse unskipOrder(UUID detailId, UUID technicianAccountId);

    TechnicianQueueItemResponse completeOrder(UUID detailId, UUID technicianAccountId);

    TechnicianQueueItemResponse rejectOrder(UUID detailId, UUID technicianAccountId, RejectParaclinicalOrderRequest request);
}

