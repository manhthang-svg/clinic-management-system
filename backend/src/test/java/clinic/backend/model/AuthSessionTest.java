package clinic.backend.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.SessionRevocationReason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthSessionTest {

    private static final Instant LOGIN = Instant.parse("2026-10-14T07:00:00Z");

    private final AuthSession session =
            AuthSession.start(UUID.randomUUID(), UUID.randomUUID(), LOGIN, Duration.ofHours(12));

    @Test
    void refreshTokensExpireAfterTheIdleTimeout() {
        assertThat(session.refreshTokenExpiry(LOGIN, Duration.ofHours(2))).isEqualTo(LOGIN.plus(Duration.ofHours(2)));
    }

    @Test
    void refreshTokensNeverOutliveTheSession() {
        Instant lateRefresh = LOGIN.plus(Duration.ofHours(11));

        assertThat(session.refreshTokenExpiry(lateRefresh, Duration.ofHours(2)))
                .isEqualTo(LOGIN.plus(Duration.ofHours(12)));
    }

    @Test
    void sessionIsUnusableAfterItsAbsoluteExpiryOrRevocation() {
        assertThat(session.isUsable(LOGIN.plus(Duration.ofHours(12)).minusMillis(1))).isTrue();
        assertThat(session.isUsable(LOGIN.plus(Duration.ofHours(12)))).isFalse();

        AuthSession revoked = new AuthSession(
                session.id(), session.accountId(), LOGIN, session.absoluteExpiresAt(),
                LOGIN.plusSeconds(60), SessionRevocationReason.LOGOUT);
        assertThat(revoked.isUsable(LOGIN.plusSeconds(120))).isFalse();
    }
}
