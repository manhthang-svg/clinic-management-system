package clinic.backend.service;

import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.RoomQueueStatusPageResponse;
import clinic.backend.dto.response.VisitResponse;

/**
 * UC-42 for the Receptionist, cut into pages on the server so the browser only receives the rows on screen. Page
 * numbers start at 0; the size defaults to 20.
 */
public interface RoomQueueListService {

    /**
     * One page of the examination rooms with their queue counts; with {@code roomId} only that room (empty when it
     * is not active), filtered on the server.
     */
    RoomQueueStatusPageResponse getRoomQueueStatusPage(LocalDate date, UUID roomId, Integer page, Integer size);

    /** One page of the waiting list of one room, in queue order. */
    PageResponse<VisitResponse> getRoomQueuePage(UUID roomId, LocalDate date, Integer page, Integer size);
}
