package clinic.backend.dto.response;

import java.util.List;

public record TechnicianQueueResponse(
        TechnicianQueueStatsResponse stats,
        List<TechnicianQueueItemResponse> items,
        boolean canManage,
        boolean hasOwnWork
) {
    public TechnicianQueueResponse(TechnicianQueueStatsResponse stats, List<TechnicianQueueItemResponse> items) {
        this(stats, items, false);
    }

    public TechnicianQueueResponse(TechnicianQueueStatsResponse stats, List<TechnicianQueueItemResponse> items,
                                   boolean canManage) {
        this(stats, items, canManage, false);
    }
}

