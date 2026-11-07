package clinic.backend.dto.response;

import java.util.UUID;

/** A room that can receive a transferred patient now (UC-48, BR-96), with the Doctor on duty there. */
public record TransferRoomOptionResponse(
        UUID roomId,
        String roomCode,
        String roomName,
        String location,
        String departmentName,
        String doctorName) {
}
