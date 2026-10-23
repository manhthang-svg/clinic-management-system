package clinic.backend.dto.response;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.Role;

public record AccountSummaryResponse(
        UUID id,
        String username,
        String fullName,
        String phoneNumber,
        String email,
        Role role,
        AccountStatus status,
        String roomName,
        String departmentName,
        Instant lastLoginAt,
        boolean passwordChangeRequired,
        Long version) {
}
