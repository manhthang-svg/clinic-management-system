package clinic.backend.service;

import java.util.UUID;

import clinic.backend.dto.request.NationalPrescriberRequest;
import clinic.backend.dto.response.AccountDetailResponse;
import clinic.backend.security.user.StaffPrincipal;

/** UC-93: the facility adds or removes a Doctor on the national prescription system (QĐ 808 III, IV, V). */
public interface NationalPrescriberService {

    /** QĐ 808 IV; signs the facility in first when no token is kept. */
    AccountDetailResponse addToFacility(StaffPrincipal principal, UUID accountId, NationalPrescriberRequest request);

    /** QĐ 808 V; signs the facility in first when no token is kept. */
    AccountDetailResponse removeFromFacility(StaffPrincipal principal, UUID accountId, NationalPrescriberRequest request);
}
