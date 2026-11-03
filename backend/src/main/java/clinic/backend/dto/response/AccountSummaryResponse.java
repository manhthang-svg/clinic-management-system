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
        UUID roomId,
        String roomCode,
        String roomName,
        String departmentName,
        Instant lastLoginAt,
        boolean passwordChangeRequired,
        Long version) {

    public AccountSummaryResponse(
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
        this(id, username, fullName, phoneNumber, email, role, status, null, null, roomName, departmentName, lastLoginAt, passwordChangeRequired, version);
    }
}
