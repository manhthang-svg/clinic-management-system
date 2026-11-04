package clinic.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import clinic.backend.enums.LabReferenceType;

public record LabReferenceRangeResponse(
        UUID id,
        UUID parameterId,
        LabReferenceType referenceType,
        BigDecimal lowerBound,
        BigDecimal upperBound,
        boolean lowerInclusive,
        boolean upperInclusive,
        List<String> normalValues,
        String referenceText,
        Short sex,
        Integer ageMinDays,
        Integer ageMaxDays,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String source,
        boolean active,
        Long version,
        String displayText) {
}
