package clinic.backend.dto;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionResponseTest {

    @Test
    void stringRepresentationRedactsTheAccessToken() {
        String accessToken = "header.payload.signature";
        SessionResponse response = new SessionResponse(
                accessToken,
                "Bearer",
                Instant.parse("2026-10-14T07:15:00Z"),
                false,
                new SessionResponse.AccountSummaryResponse(
                        UUID.fromString("0199e783-f640-7000-8000-000000000001"),
                        "doctor.an",
                        "Doctor An",
                        "DOCTOR"));

        assertThat(response.toString())
                .contains("accessToken=redacted")
                .doesNotContain(accessToken);
    }
}
