package clinic.backend.dto.response;

public record TechnicianQueueStatsResponse(
        long total,
        long waiting,
        long priority,
        long skipped
) {}

