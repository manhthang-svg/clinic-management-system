package clinic.backend.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import clinic.backend.model.PasswordPolicy;
import clinic.backend.model.StaffAccount;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.security.PasswordHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gives the demo accounts of the development seed a usable password (docs/demo-master-data.md).
 *
 * <p>The seed commits no password: it stores {@link #UNSET_PASSWORD_HASH}, which never matches. Only
 * accounts still holding that placeholder are changed, so a password changed later is kept.
 */
@Service
public class DemoAccountPasswordService {

    /** Placeholder written by db/dev-seed; a {bcrypt} id with an invalid hash never matches. */
    public static final String UNSET_PASSWORD_HASH = "{bcrypt}dev-seed-password-not-set";

    private final StaffAccountRepository accounts;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    public DemoAccountPasswordService(StaffAccountRepository accounts, PasswordHasher passwordHasher, Clock clock) {
        this.accounts = accounts;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    /**
     * @return the number of demo accounts that received the password
     * @throws IllegalStateException when the password violates the password policy
     */
    @Transactional
    public int assignPassword(String rawPassword) {
        if (PasswordPolicy.check(rawPassword).isPresent()) {
            throw new IllegalStateException("The demo account password does not satisfy the password policy");
        }
        List<StaffAccount> pending = accounts.findAllByPasswordHash(UNSET_PASSWORD_HASH);
        if (pending.isEmpty()) {
            return 0;
        }
        // One hash for all demo accounts keeps startup fast; this runs only with the dev profile.
        String passwordHash = passwordHasher.hash(rawPassword);
        Instant now = clock.instant();
        for (StaffAccount account : pending) {
            account.changePassword(passwordHash, now);
            accounts.save(account);
        }
        return pending.size();
    }
}
