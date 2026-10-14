package clinic.backend.account.infrastructure.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import clinic.backend.account.application.port.AccessTokenIssuer;
import clinic.backend.account.domain.StaffAccount;
import clinic.backend.shared.configuration.ClinicSecurityProperties;
import clinic.backend.shared.security.AccessTokenClaims;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Issues HS256 access tokens with the claims listed in docs/security.md.
 */
@Component
class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final ClinicSecurityProperties.Jwt settings;

    JwtAccessTokenIssuer(JwtEncoder encoder, ClinicSecurityProperties properties) {
        this.encoder = encoder;
        this.settings = properties.jwt();
    }

    @Override
    public IssuedAccessToken issue(StaffAccount account, UUID sessionId, Instant now) {
        Instant expiresAt = now.plus(settings.accessTokenTtl());

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(account.id().toString())
                .issuer(settings.issuer())
                .audience(List.of(settings.audience()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(AccessTokenClaims.ROLE, account.role().name())
                .claim(AccessTokenClaims.SESSION_ID, sessionId.toString());
        if (account.passwordChangeRequired()) {
            claims.claim(AccessTokenClaims.PASSWORD_CHANGE_REQUIRED, true);
        }

        String value = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims.build())).getTokenValue();
        return new IssuedAccessToken(value, expiresAt);
    }
}
