package clinic.backend.service;

import java.util.Optional;

import clinic.backend.dto.request.ChangePasswordRequest;
import clinic.backend.entity.StaffAccount;
import clinic.backend.security.user.StaffPrincipal;

public interface StaffAccountService {

    /** Placeholder written by db/dev-seed; a {bcrypt} id with an invalid hash never matches. */
    String UNSET_DEMO_PASSWORD_HASH = "{bcrypt}dev-seed-password-not-set";

    /**
     * UC-3 Change Password (docs/use-cases/UC-3-change-password.md). Every other session of the account is
     * revoked; the caller's session stays valid.
     */
    void changePassword(StaffPrincipal principal, ChangePasswordRequest request);

    /**
     * Creates the first Admin of a new environment from configuration (AQ-10).
     *
     * @return the created account, or empty when an Admin already exists
     * @throws IllegalStateException when the configured values are unusable
     */
    Optional<StaffAccount> createFirstAdminIfMissing(String username, String password, String fullName);

    /**
     * Gives the demo accounts of the development seed a usable password (docs/demo-master-data.md). Only
     * accounts still holding {@link #UNSET_DEMO_PASSWORD_HASH} are changed, so a password changed later is kept.
     *
     * @return the number of demo accounts that received the password
     * @throws IllegalStateException when the password violates the password policy
     */
    int assignDemoAccountPassword(String rawPassword);
}
