package clinic.backend.dto.response;

import java.util.List;

public record TechnicianPendingResultResponse(
        TechnicianPendingStatsResponse stats,
        List<TechnicianPendingItemResponse> items,
        boolean canManage
) {}

