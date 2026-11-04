package clinic.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import clinic.backend.enums.LabReferenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "lab_reference_ranges")
public class LabReferenceRange extends VersionedEntity {

    @Column(name = "parameter_id", nullable = false, updatable = false)
    private UUID parameterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 30)
    private LabReferenceType referenceType;

    @Column(name = "lower_bound") private BigDecimal lowerBound;
    @Column(name = "upper_bound") private BigDecimal upperBound;
    @Column(name = "lower_inclusive", nullable = false) private boolean lowerInclusive;
    @Column(name = "upper_inclusive", nullable = false) private boolean upperInclusive;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "normal_values", columnDefinition = "text[]")
    private String[] normalValues;

    @Column(name = "reference_text", length = 255) private String referenceText;
    @Column(name = "sex") private Short sex;
    @Column(name = "age_min_days") private Integer ageMinDays;
    @Column(name = "age_max_days") private Integer ageMaxDays;
    @Column(name = "effective_from", nullable = false) private LocalDate effectiveFrom;
    @Column(name = "effective_to") private LocalDate effectiveTo;
    @Column(name = "source", nullable = false, length = 255) private String source;
    @Column(name = "is_active", nullable = false) private boolean active;

    public static LabReferenceRange create(
            UUID id, Instant now, UUID parameterId, LabReferenceType type,
            BigDecimal lowerBound, BigDecimal upperBound, boolean lowerInclusive,
            boolean upperInclusive, List<String> normalValues, String referenceText,
            Short sex, Integer ageMinDays, Integer ageMaxDays, LocalDate effectiveFrom,
            LocalDate effectiveTo, String source, boolean active) {
        LabReferenceRange range = new LabReferenceRange(id, now, parameterId);
        range.change(type, lowerBound, upperBound, lowerInclusive, upperInclusive, normalValues,
                referenceText, sex, ageMinDays, ageMaxDays, effectiveFrom, effectiveTo, source, active, now);
        return range;
    }

    private LabReferenceRange(UUID id, Instant now, UUID parameterId) {
        super(id, now);
        this.parameterId = Objects.requireNonNull(parameterId, "parameterId");
    }

    public void update(
            LabReferenceType type, BigDecimal lowerBound, BigDecimal upperBound,
            boolean lowerInclusive, boolean upperInclusive, List<String> normalValues,
            String referenceText, Short sex, Integer ageMinDays, Integer ageMaxDays,
            LocalDate effectiveFrom, LocalDate effectiveTo, String source, boolean active, Instant now) {
        change(type, lowerBound, upperBound, lowerInclusive, upperInclusive, normalValues,
                referenceText, sex, ageMinDays, ageMaxDays, effectiveFrom, effectiveTo, source, active, now);
    }

    private void change(
            LabReferenceType type, BigDecimal lowerBound, BigDecimal upperBound,
            boolean lowerInclusive, boolean upperInclusive, List<String> normalValues,
            String referenceText, Short sex, Integer ageMinDays, Integer ageMaxDays,
            LocalDate effectiveFrom, LocalDate effectiveTo, String source, boolean active, Instant now) {
        validate(type, lowerBound, upperBound, normalValues, referenceText,
                ageMinDays, ageMaxDays, effectiveFrom, effectiveTo);
        this.referenceType = type;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.lowerInclusive = lowerInclusive;
        this.upperInclusive = upperInclusive;
        this.normalValues = normalValues == null ? null : normalValues.toArray(String[]::new);
        this.referenceText = normalize(referenceText);
        this.sex = sex;
        this.ageMinDays = ageMinDays;
        this.ageMaxDays = ageMaxDays;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.source = source.strip();
        this.active = active;
        touch(now);
    }

    private static void validate(
            LabReferenceType type, BigDecimal lower, BigDecimal upper, List<String> values,
            String text, Integer minAge, Integer maxAge, LocalDate from, LocalDate to) {
        Objects.requireNonNull(type, "referenceType");
        Objects.requireNonNull(from, "effectiveFrom");
        if (to != null && to.isBefore(from)) throw new IllegalArgumentException("effectiveTo before effectiveFrom");
        if (minAge != null && maxAge != null && minAge > maxAge) throw new IllegalArgumentException("invalid age range");
        boolean hasValues = values != null && !values.isEmpty();
        boolean hasText = text != null && !text.isBlank();
        switch (type) {
            case RANGE -> {
                if (lower == null || upper == null || lower.compareTo(upper) >= 0 || hasValues) {
                    throw new IllegalArgumentException("invalid numeric range");
                }
            }
            case UPPER_LIMIT -> {
                if (lower != null || upper == null || hasValues) throw new IllegalArgumentException("invalid upper limit");
            }
            case LOWER_LIMIT -> {
                if (lower == null || upper != null || hasValues) throw new IllegalArgumentException("invalid lower limit");
            }
            case QUALITATIVE -> {
                if (lower != null || upper != null || !hasValues) {
                    throw new IllegalArgumentException("normalValues required");
                }
            }
            case TEXT -> {
                if (lower != null || upper != null || hasValues || !hasText) {
                    throw new IllegalArgumentException("referenceText required");
                }
            }
        }
    }

    public List<String> normalValueList() {
        return normalValues == null ? List.of() : List.copyOf(Arrays.asList(normalValues));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
