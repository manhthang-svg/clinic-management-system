package clinic.backend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VitalSignResponse(
        Short pulse,
        BigDecimal temperature,
        Short respiratoryRate,
        Short systolicBloodPressure,
        Short diastolicBloodPressure,
        BigDecimal weight,
        BigDecimal height,
        Short spo2,
        BigDecimal bmi,
        Instant measuredAt,
        UUID measuredByAccountId,
        String measuredByName) {
}
