package clinic.backend.service;

import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.RejectParaclinicalOrderRequest;
import clinic.backend.dto.request.ReturnParaclinicalOrderToWaitingRequest;
import clinic.backend.dto.response.TechnicianPendingResultResponse;
import clinic.backend.dto.response.TechnicianQueueItemResponse;
import clinic.backend.dto.response.TechnicianQueueResponse;
import clinic.backend.dto.response.TechnicianRoomResponse;

public interface TechnicianQueueService {

    List<TechnicianRoomResponse> getTechnicianRooms(UUID currentUserId);

    default List<TechnicianRoomResponse> getTechnicianRooms() {
        return getTechnicianRooms(null);
    }

    TechnicianQueueResponse getQueue(UUID roomId, String search, String status, String doctorRoom, UUID currentUserId);

    TechnicianQueueResponse getDashboardQueue(UUID roomId, UUID currentUserId);

    TechnicianPendingResultResponse getDashboardPendingResults(UUID roomId, UUID currentUserId);

    default TechnicianQueueResponse getQueue(UUID roomId, String search, String status, String doctorRoom) {
        return getQueue(roomId, search, status, doctorRoom, null);
    }

    TechnicianPendingResultResponse getPendingResults(
            UUID roomId, String search, String status, String doctorRoom, UUID currentUserId);

    default TechnicianPendingResultResponse getPendingResults(
            UUID roomId, String search, String status, String doctorRoom) {
        return getPendingResults(roomId, search, status, doctorRoom, null);
    }

    TechnicianQueueItemResponse getQueueDetail(UUID detailId, UUID currentUserId);

    /** Case 1: Confirms the patient's physical arrival at the paraclinical room and assigns daily queue number. */
    TechnicianQueueItemResponse checkInOrder(UUID detailId, UUID technicianAccountId);

    TechnicianQueueItemResponse acceptOrder(UUID detailId, UUID technicianAccountId);

    TechnicianQueueItemResponse skipOrder(UUID detailId, UUID technicianAccountId);

    TechnicianQueueItemResponse unskipOrder(UUID detailId, UUID technicianAccountId);

    /** UC-66: confirms that the service accepted by this technician was performed. */
    TechnicianQueueItemResponse completeOrder(UUID detailId, UUID technicianAccountId);

    /** UC-66 AF-02: sends a service accepted by this technician back to the waiting list with a reason. */
    TechnicianQueueItemResponse returnToWaiting(
            UUID detailId, UUID technicianAccountId, ReturnParaclinicalOrderToWaitingRequest request);

    TechnicianQueueItemResponse rejectOrder(UUID detailId, UUID technicianAccountId, RejectParaclinicalOrderRequest request);
}
