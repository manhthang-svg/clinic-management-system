package clinic.backend.dto.response;

import java.util.List;

/**
 * Technician home dashboard for one paraclinical room: the room, today's figures, the next patients waiting and
 * the services waiting for a result. {@code room} is null when no active paraclinical room exists.
 */
public record TechnicianDashboardResponse(
        TechnicianRoomResponse room,
        boolean canManage,
        Stats stats,
        List<TechnicianQueueItemResponse> nextQueue,
        List<TechnicianPendingItemResponse> pendingResults
) {

    /**
     * Figures of the room. {@code waiting}, {@code priority} and {@code skipped} count today's queue;
     * {@code pendingResult}, {@code draft} and {@code overdue} count the services performed and waiting for a
     * result; {@code completedToday} counts the services whose result was submitted today.
     */
    public record Stats(
            long waiting,
            long priority,
            long skipped,
            long pendingResult,
            long draft,
            long overdue,
            long completedToday
    ) {
        public static Stats empty() {
            return new Stats(0, 0, 0, 0, 0, 0, 0);
        }
    }
}
