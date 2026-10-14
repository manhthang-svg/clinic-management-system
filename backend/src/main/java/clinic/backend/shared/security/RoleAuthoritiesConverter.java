package clinic.backend.shared.security;

import java.util.Collection;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Maps the access-token claims to authorities.
 *
 * <p>While a password change is required the token grants no role authority, so only endpoints
 * that require nothing more than authentication (change password, own profile, logout) accept it.
 * Every other endpoint must require a role or permission.
 */
public class RoleAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        if (Boolean.TRUE.equals(jwt.getClaimAsBoolean(AccessTokenClaims.PASSWORD_CHANGE_REQUIRED))) {
            return List.of(new SimpleGrantedAuthority(AccessTokenClaims.PASSWORD_CHANGE_REQUIRED_AUTHORITY));
        }

        String role = jwt.getClaimAsString(AccessTokenClaims.ROLE);
        if (role == null || role.isBlank()) {
            return List.of();
        }
        return List.of(new SimpleGrantedAuthority(AccessTokenClaims.ROLE_AUTHORITY_PREFIX + role));
    }
}
