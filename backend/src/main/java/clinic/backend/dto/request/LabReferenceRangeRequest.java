package clinic.backend.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import clinic.backend.enums.LabReferenceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record LabReferenceRangeRequest(
        @NotNull LabReferenceType referenceType,
        BigDecimal lowerBound,
        BigDecimal upperBound,
        Boolean lowerInclusive,
        Boolean upperInclusive,
        List<@NotBlank @Size(max = 100) String> normalValues,
        @Size(max = 255) String referenceText,
        @Min(1) @Max(3) Short sex,
        @PositiveOrZero Integer ageMinDays,
        @PositiveOrZero Integer ageMaxDays,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @NotBlank @Size(max = 255) String source,
        boolean active,
        Long version) {
}
