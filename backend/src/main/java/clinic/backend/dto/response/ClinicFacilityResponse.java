package clinic.backend.dto.response;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.FacilityOrganizationForm;

/**
 * UC-77 facility information. {@code configured} is false, and the facility fields null, until the
 * Admin declares the facility. {@code maCskcbLocked} is true once a visit exists (BR-77-02).
 */
public record ClinicFacilityResponse(
        boolean configured,
        UUID id,
        String maCskcb,
        String tenCskcb,
        String diachi,
        String sodienthoai,
        String soGiayPhepHoatDong,
        FacilityOrganizationForm hinhThucToChuc,
        boolean maCskcbLocked,
        Instant updatedAt,
        Long version) {
}
