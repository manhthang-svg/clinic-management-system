package clinic.backend.service;

import java.time.Duration;
import java.util.Objects;

/**
 * Session lifetimes decided in docs/security.md.
 *
 * @param idleTimeout       a refresh token not used within this time expires
 * @param absoluteLifetime  maximum session length from login, not extended by rotation
 * @param retentionAfterEnd how long ended sessions are kept before cleanup
 */
public record SessionPolicy(Duration idleTimeout, Duration absoluteLifetime, Duration retentionAfterEnd) {

    public SessionPolicy {
        Objects.requireNonNull(idleTimeout, "idleTimeout");
        Objects.requireNonNull(absoluteLifetime, "absoluteLifetime");
        Objects.requireNonNull(retentionAfterEnd, "retentionAfterEnd");
    }
}
