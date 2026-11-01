package clinic.backend.service;

import clinic.backend.dto.request.UpdateClinicFacilityRequest;
import clinic.backend.dto.response.ClinicFacilityResponse;
import clinic.backend.security.user.StaffPrincipal;

/** UC-77 Update Hospital Information. */
public interface ClinicFacilityService {

    ClinicFacilityResponse getFacility();

    /** Declares the facility on first use, otherwise updates it. */
    ClinicFacilityResponse saveFacility(StaffPrincipal principal, UpdateClinicFacilityRequest request);
}
