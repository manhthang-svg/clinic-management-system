package clinic.backend.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.Gender;
import clinic.backend.enums.Role;

public record AccountDetailResponse(
        UUID id,
        String username,
        Role role,
        AccountStatus status,
        String roomName,
        String departmentName,
        String fullName,
        String academicTitle,
        String practiceLicenseNo,
        String maLienthongBacsi,
        /** UC-93: when the Doctor was added to the facility on the national prescription system; null if not. */
        Instant lienthongAddedAt,
        String citizenId,
        LocalDate dateOfBirth,
        Gender gender,
        String phoneNumber,
        String email,
        String address,
        boolean passwordChangeRequired,
        int failedLoginAttempts,
        Instant loginBlockedUntil,
        Instant createdAt,
        UUID createdByAccountId,
        Instant lastLoginAt,
        Instant passwordChangedAt,
        String lockReason,
        Instant lockedAt,
        UUID lockedByAccountId,
        Long version,
        AllowedAccountActionsResponse allowedActions) {
}
