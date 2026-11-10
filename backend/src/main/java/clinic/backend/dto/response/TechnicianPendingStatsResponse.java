package clinic.backend.dto.response;

public record TechnicianPendingStatsResponse(
        long total,
        long pending,
        long draft,
        long overdue
) {}

