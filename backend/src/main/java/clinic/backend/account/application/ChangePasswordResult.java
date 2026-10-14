package clinic.backend.account.application;

import clinic.backend.account.domain.PasswordPolicy;

/**
 * Outcome of UC-3. Rejections are results, not exceptions, so the failed-attempt counter commits.
 */
public sealed interface ChangePasswordResult {

    record Changed() implements ChangePasswordResult {
    }

    /**
     * The current password was wrong, or password checks are temporarily blocked (AQ-04).
     */
    record CurrentPasswordIncorrect() implements ChangePasswordResult {
    }

    record PolicyViolated(Violation violation) implements ChangePasswordResult {
    }

    /**
     * The account no longer exists or is locked; the caller must authenticate again.
     */
    record AccountUnavailable() implements ChangePasswordResult {
    }

    enum Violation {
        TOO_SHORT,
        TOO_LONG,
        SAME_AS_CURRENT;

        static Violation from(PasswordPolicy.Violation violation) {
            return switch (violation) {
                case TOO_SHORT -> TOO_SHORT;
                case TOO_LONG -> TOO_LONG;
            };
        }
    }
}
