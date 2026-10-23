package clinic.backend.dto.response;

public record AllowedAccountActionsResponse(
        boolean update,
        boolean assignRole,
        boolean lock,
        boolean unlock,
        boolean resetPassword,
        boolean assignRoom) {
}
