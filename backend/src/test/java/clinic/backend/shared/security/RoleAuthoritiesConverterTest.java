package clinic.backend.shared.security;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class RoleAuthoritiesConverterTest {

    private final RoleAuthoritiesConverter converter = new RoleAuthoritiesConverter();

    @Test
    void grantsTheRoleAuthority() {
        Jwt jwt = jwt().claim(AccessTokenClaims.ROLE, "DOCTOR").build();

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_DOCTOR");
    }

    @Test
    void grantsOnlyThePasswordChangeMarkerWhileAPasswordChangeIsRequired() {
        Jwt jwt = jwt()
                .claim(AccessTokenClaims.ROLE, "ADMIN")
                .claim(AccessTokenClaims.PASSWORD_CHANGE_REQUIRED, true)
                .build();

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly(AccessTokenClaims.PASSWORD_CHANGE_REQUIRED_AUTHORITY);
    }

    @Test
    void grantsNothingWithoutARole() {
        assertThat(converter.convert(jwt().claim("other", "value").build())).isEmpty();
    }

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("0192e3a0-0000-7000-8000-000000000001")
                .issuedAt(Instant.parse("2026-10-14T08:00:00Z"))
                .expiresAt(Instant.parse("2026-10-14T08:15:00Z"));
    }
}
