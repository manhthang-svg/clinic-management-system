package clinic.backend.service;

/**
 * Outcome of login and refresh. A rejection is a normal result rather than an exception, so that
 * failed-login counters and audit events are committed with the transaction.
 */
public sealed interface AuthenticationResult {

    record Authenticated(SessionTokens tokens) implements AuthenticationResult {
    }

    /**
     * Deliberately carries no reason: every rejection is reported to the client in the same way.
     */
    record Rejected() implements AuthenticationResult {
    }
}
