package clinic.backend.dto.response;

import clinic.backend.enums.Role;

public record RoleChangeCheckResponse(
        Role currentRole,
        Role newRole,
        long visitsInProgress,
        long ordersInProgress,
        boolean roomAssignmentWillBeRemoved,
        boolean roomAssignmentRequired) {
}
