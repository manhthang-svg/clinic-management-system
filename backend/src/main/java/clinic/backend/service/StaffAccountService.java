package clinic.backend.service;

import java.util.Optional;

import clinic.backend.dto.request.ChangePasswordRequest;
import clinic.backend.dto.request.UpdateProfileRequest;
import clinic.backend.dto.response.ProfileResponse;
import clinic.backend.entity.StaffAccount;
import clinic.backend.security.user.StaffPrincipal;

public interface StaffAccountService {

    /**
     * UC-03 Change Password (Report 3). Every other session of the account is
     * revoked; the caller's session stays valid.
     */
    void changePassword(StaffPrincipal principal, ChangePasswordRequest request);

    /** UC-4: returns the authenticated staff member's own profile. */
    ProfileResponse getOwnProfile(StaffPrincipal principal);

    /** UC-4: replaces the authenticated staff member's editable contact information. */
    ProfileResponse updateOwnProfile(StaffPrincipal principal, UpdateProfileRequest request);

    /** Shell-only recovery for the bootstrap Admin. Never exposed through a controller. */
    AdminRecoveryResult recoverAdmin();

    /**
     * Creates the first Admin of a new environment from configuration.
     *
     * @return the created account, or empty when an Admin already exists
     * @throws IllegalStateException when the configured values are unusable
     */
    Optional<StaffAccount> createFirstAdminIfMissing(String username, String password, String fullName);

}
