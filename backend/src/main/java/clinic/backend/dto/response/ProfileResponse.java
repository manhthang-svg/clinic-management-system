package clinic.backend.dto.response;

import java.time.Instant;
import java.time.LocalDate;

import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.Gender;
import clinic.backend.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;

/** The caller's own staff profile. Password and other technical account fields are intentionally absent. */
public record ProfileResponse(
        @Schema(example = "bs.tranvanan")
        String username,
        Role role,
        @Schema(description = "Assigned room name for a Doctor/Technician; otherwise null.",
                example = "Phòng khám Nội tổng quát")
        String roomName,
        @Schema(description = "Department of the assigned room; otherwise null.", example = "Khoa Nội")
        String departmentName,
        AccountStatus status,
        Instant createdAt,
        Instant lastLoginAt,
        String fullName,
        String academicTitle,
        LocalDate dateOfBirth,
        Gender gender,
        String address,
        @Schema(example = "0901234567")
        String phoneNumber,
        @Schema(example = "bs.tranvanan@clinic.example")
        String email) {
}
