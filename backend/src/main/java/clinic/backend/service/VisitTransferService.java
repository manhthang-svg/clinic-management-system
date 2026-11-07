package clinic.backend.service;

import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.TransferVisitRequest;
import clinic.backend.dto.response.TransferRoomOptionResponse;
import clinic.backend.dto.response.VisitTransferResponse;
import clinic.backend.security.user.StaffPrincipal;

/** UC-48 Transfer Patient to Another Room. */
public interface VisitTransferService {

    /** Rooms that can receive the visit now: other active examination rooms with a Doctor on duty (BR-96). */
    List<TransferRoomOptionResponse> getTransferRooms(StaffPrincipal principal, UUID visitId);

    /** Moves the called-in visit to another room with that room's next queue number (V11, BR-24). */
    VisitTransferResponse transfer(StaffPrincipal principal, UUID visitId, TransferVisitRequest request);
}
