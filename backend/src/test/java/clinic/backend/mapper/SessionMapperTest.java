package clinic.backend.mapper;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.dto.response.SessionResponse;
import clinic.backend.enums.Role;
import clinic.backend.service.SessionTokens;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class SessionMapperTest {

    private final SessionMapper mapper = Mappers.getMapper(SessionMapper.class);

    @Test
    void mapsTheAccessTokenAndAccountButNeverTheRefreshToken() {
        UUID accountId = UUID.fromString("0199e783-f640-7000-8000-000000000001");
        Instant accessTokenExpiry = Instant.parse("2026-10-14T07:15:00Z");
        SessionTokens tokens = new SessionTokens(
                "header.payload.signature",
                accessTokenExpiry,
                "raw-refresh-token",
                Instant.parse("2026-10-14T19:00:00Z"),
                true,
                new SessionTokens.AccountSummary(accountId, "doctor.an", "Doctor An", Role.DOCTOR));

        SessionResponse response = mapper.toSessionResponse(tokens);

        assertThat(response.accessToken()).isEqualTo("header.payload.signature");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresAt()).isEqualTo(accessTokenExpiry);
        assertThat(response.passwordChangeRequired()).isTrue();
        assertThat(response.account())
                .isEqualTo(new SessionResponse.AccountSummaryResponse(accountId, "doctor.an", "Doctor An", "DOCTOR"));
        assertThat(response.toString()).doesNotContain("raw-refresh-token");
    }
}
