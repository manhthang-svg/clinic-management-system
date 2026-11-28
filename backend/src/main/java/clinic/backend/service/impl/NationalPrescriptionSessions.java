package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import clinic.backend.service.NationalPrescriptionSystem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The clinic side of the national sign-ins (BR-59-17, QĐ 808/QĐ-BYT III, VI): keeps the facility's token and each
 * Doctor's token in this backend's memory for 7 days from when it was received, so a password is asked for about
 * once a week. Tokens are never written to the database, the logs or the browser (docs/security.md); a restart
 * forgets them and the next call asks for the password again. QĐ 808 states the 7 days but returns no expiry, so the
 * expiry is counted from receipt (ASSUMPTION), less a margin so a token is not used in its last minutes.
 */
@Component
@RequiredArgsConstructor
public class NationalPrescriptionSessions {

    static final Duration TOKEN_LIFETIME = Duration.ofDays(7);
    static final Duration RENEWAL_MARGIN = Duration.ofMinutes(5);

    private final NationalPrescriptionSystem nationalSystem;
    private final Clock clock;
    private final Map<String, Session> facilitySessions = new ConcurrentHashMap<>();
    private final Map<String, Session> prescriberSessions = new ConcurrentHashMap<>();

    /** Whether the Doctor has a token that is still valid, so sending asks for no password. */
    public boolean hasPrescriberSession(String facilityLinkCode, String prescriberLinkCode) {
        return valid(prescriberSessions, prescriberKey(facilityLinkCode, prescriberLinkCode)) != null;
    }

    /**
     * III: the facility's token, signing in with {@code password} when none is kept. Clears {@code password}.
     *
     * @throws AppException NATIONAL_PRESCRIPTION_SIGN_IN_REQUIRED when a sign-in is needed and no password is given,
     *                      NATIONAL_PRESCRIPTION_SIGN_IN_FAILED when the national system refuses it
     */
    public String facilityToken(String facilityLinkCode, char[] password) {
        try {
            String kept = valid(facilitySessions, facilityLinkCode);
            if (kept != null) {
                return kept;
            }
            NationalPrescriptionSystem.SignIn signIn = nationalSystem.signInFacility(facilityLinkCode, required(password));
            return keep(facilitySessions, facilityLinkCode, signIn);
        } finally {
            clear(password);
        }
    }

    /**
     * VI: the Doctor's token, signing in with {@code password} when none is kept. Clears {@code password}.
     *
     * @throws AppException as {@link #facilityToken}
     */
    public String prescriberToken(String facilityLinkCode, String prescriberLinkCode, char[] password) {
        String key = prescriberKey(facilityLinkCode, prescriberLinkCode);
        try {
            String kept = valid(prescriberSessions, key);
            if (kept != null) {
                return kept;
            }
            NationalPrescriptionSystem.SignIn signIn = nationalSystem.signInPrescriber(
                    prescriberLinkCode, facilityLinkCode, required(password));
            return keep(prescriberSessions, key, signIn);
        } finally {
            clear(password);
        }
    }

    /** The national system refused the facility's token: the next call signs in again. */
    public void forgetFacility(String facilityLinkCode) {
        facilitySessions.remove(facilityLinkCode);
    }

    /** The national system refused the Doctor's token, or the Doctor was removed from the facility. */
    public void forgetPrescriber(String facilityLinkCode, String prescriberLinkCode) {
        prescriberSessions.remove(prescriberKey(facilityLinkCode, prescriberLinkCode));
    }

    private String valid(Map<String, Session> sessions, String key) {
        Session session = sessions.get(key);
        if (session == null) {
            return null;
        }
        if (!clock.instant().isBefore(session.expiresAt())) {
            sessions.remove(key, session);
            return null;
        }
        return session.token();
    }

    private String keep(Map<String, Session> sessions, String key, NationalPrescriptionSystem.SignIn signIn) {
        if (!signIn.succeeded()) {
            throw new AppException(ErrorCode.NATIONAL_PRESCRIPTION_SIGN_IN_FAILED,
                    Map.of("errors", String.join("\n", signIn.errors())));
        }
        Instant expiresAt = clock.instant().plus(TOKEN_LIFETIME).minus(RENEWAL_MARGIN);
        sessions.put(key, new Session(signIn.token(), expiresAt));
        return signIn.token();
    }

    private static char[] required(char[] password) {
        if (password != null) {
            for (char character : password) {
                if (!Character.isWhitespace(character)) {
                    return password;
                }
            }
        }
        throw new AppException(ErrorCode.NATIONAL_PRESCRIPTION_SIGN_IN_REQUIRED);
    }

    private static void clear(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }

    private static String prescriberKey(String facilityLinkCode, String prescriberLinkCode) {
        return facilityLinkCode + "|" + prescriberLinkCode;
    }

    private record Session(String token, Instant expiresAt) {

        @Override
        public String toString() {
            return "Session[expiresAt=" + expiresAt + "]"; // never the token
        }
    }
}
