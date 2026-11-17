package clinic.backend.dto.response;

/**
 * UC-42 one page of the examination rooms with their queue counts, plus how many of all rooms can receive patients
 * (a Doctor is on duty), which drives the "no room can receive patients" message (MSG102).
 */
public record RoomQueueStatusPageResponse(
        PageResponse<RoomQueueStatusResponse> rooms,
        long receivableRooms) {
}
