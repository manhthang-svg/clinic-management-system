package clinic.backend.dto.response;

import java.util.List;

public record TechnicianQueueResponse(
        TechnicianQueueStatsResponse stats,
        List<TechnicianQueueItemResponse> items,
        boolean canManage
) {
    public TechnicianQueueResponse(TechnicianQueueStatsResponse stats, List<TechnicianQueueItemResponse> items) {
        this(stats, items, false);
    }
}

