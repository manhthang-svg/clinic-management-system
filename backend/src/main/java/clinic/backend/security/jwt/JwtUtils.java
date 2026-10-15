package clinic.backend.security.jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import clinic.backend.config.properties.ClinicSecurityProperties;
import clinic.backend.entity.StaffAccount;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Issues HS256 access tokens with the claims listed in docs/security.md. Incoming tokens are validated by
 * the OAuth2 resource server configured in {@code SecurityConfig}.
 */
@Component
public class JwtUtils {

    private final JwtEncoder encoder;
    private final ClinicSecurityProperties.Jwt settings;

    public JwtUtils(JwtEncoder encoder, ClinicSecurityProperties properties) {
        this.encoder = encoder;
        this.settings = properties.jwt();
    }

    public IssuedAccessToken generateAccessToken(StaffAccount account, UUID sessionId, Instant now) {
        Instant expiresAt = now.plus(settings.accessTokenTtl());

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(account.getId().toString())
                .issuer(settings.issuer())
                .audience(List.of(settings.audience()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(AccessTokenClaims.ROLE, account.getRole().name())
                .claim(AccessTokenClaims.SESSION_ID, sessionId.toString());
        if (account.isPasswordChangeRequired()) {
            claims.claim(AccessTokenClaims.PASSWORD_CHANGE_REQUIRED, true);
        }

        String value = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims.build())).getTokenValue();
        return new IssuedAccessToken(value, expiresAt);
    }

    public record IssuedAccessToken(String value, Instant expiresAt) {

        @Override
        public String toString() {
            return "IssuedAccessToken[value=redacted, expiresAt=" + expiresAt + "]";
        }
    }
}
