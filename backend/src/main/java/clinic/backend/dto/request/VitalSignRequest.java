package clinic.backend.dto.request;

import java.math.BigDecimal;
import java.time.Instant;

public record VitalSignRequest(
        Short pulse,
        BigDecimal temperature,
        Short respiratoryRate,
        Short systolicBloodPressure,
        Short diastolicBloodPressure,
        BigDecimal weight,
        BigDecimal height,
        Short spo2,
        Instant measuredAt) {
}
