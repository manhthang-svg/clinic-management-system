package clinic.backend.dto.response;

public record WorkInProgressResponse(
        long visitsInProgress,
        long ordersInProgress) {
}
