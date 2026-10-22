package clinic.backend.config;

import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import clinic.backend.config.properties.ClinicSecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * JWT signing and verification. Kept apart from the web-only {@link SecurityConfig} because the token
 * services are also created when the application runs without a web server (Admin recovery).
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    private static final int MINIMUM_SIGNING_KEY_BYTES = 32;

    @Bean
    SecretKey jwtSigningKey(ClinicSecurityProperties properties) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(properties.jwt().signingKey());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("clinic.security.jwt.signing-key must be base64-encoded");
        }
        if (keyBytes.length < MINIMUM_SIGNING_KEY_BYTES) {
            throw new IllegalStateException("clinic.security.jwt.signing-key must contain at least 256 bits");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, ClinicSecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        String audience = properties.jwt().audience();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(properties.jwt().clockSkew()),
                new JwtIssuerValidator(properties.jwt().issuer()),
                new JwtClaimValidator<List<String>>(
                        JwtClaimNames.AUD,
                        tokenAudience -> tokenAudience != null && tokenAudience.contains(audience)));
        decoder.setJwtValidator(validator);
        return decoder;
    }
}
