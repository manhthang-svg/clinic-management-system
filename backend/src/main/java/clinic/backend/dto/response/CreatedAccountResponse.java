package clinic.backend.dto.response;

import java.util.UUID;

public record CreatedAccountResponse(
        UUID accountId,
        String username,
        String temporaryPassword,
        boolean passwordPreviouslyReturned,
        boolean roomAssignmentRequired) {
}
