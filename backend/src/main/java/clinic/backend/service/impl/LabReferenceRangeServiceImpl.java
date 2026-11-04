package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import clinic.backend.dto.request.LabReferenceRangeRequest;
import clinic.backend.dto.response.LabParameterManagementResponse;
import clinic.backend.dto.response.LabReferenceRangeResponse;
import clinic.backend.entity.LabReferenceRange;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.LabParameterRepository;
import clinic.backend.repository.LabReferenceRangeRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.LabReferenceRangeService;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')")
public class LabReferenceRangeServiceImpl implements LabReferenceRangeService {

    private static final String ENTITY_TYPE = "lab_reference_ranges";
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final LabParameterRepository parameterRepository;
    private final LabReferenceRangeRepository rangeRepository;
    private final AuditLogService auditLogService;
    private final UuidV7Generator ids;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<LabReferenceRangeResponse> list(UUID parameterId) {
        requireParameter(parameterId);
        return rangeRepository.findAllByParameterIdOrderByEffectiveFromDesc(parameterId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public LabReferenceRangeResponse create(
            StaffPrincipal principal, UUID parameterId, LabReferenceRangeRequest request) {
        requireParameter(parameterId);
        Instant now = clock.instant();
        LabReferenceRange range;
        try {
            range = LabReferenceRange.create(
                    ids.next(), now, parameterId, request.referenceType(), request.lowerBound(),
                    request.upperBound(), inclusive(request.lowerInclusive()), inclusive(request.upperInclusive()),
                    request.normalValues(), request.referenceText(), request.sex(), request.ageMinDays(),
                    request.ageMaxDays(), request.effectiveFrom(), request.effectiveTo(),
                    request.source(), request.active());
        } catch (IllegalArgumentException exception) {
            throw invalid(exception);
        }
        rangeRepository.saveAndFlush(range);
        auditLogService.recordEntityChange(
                principal.accountId(), range.getId(), AuditLogAction.CREATE, ENTITY_TYPE,
                Map.of(), values(range), now);
        return toResponse(range);
    }

    @Override
    @Transactional
    public LabReferenceRangeResponse update(
            StaffPrincipal principal, UUID parameterId, UUID rangeId, LabReferenceRangeRequest request) {
        requireParameter(parameterId);
        LabReferenceRange range = rangeRepository.findById(rangeId)
                .filter(item -> item.getParameterId().equals(parameterId))
                .orElseThrow(() -> new AppException(ErrorCode.LAB_REFERENCE_RANGE_NOT_FOUND));
        if (request.version() == null || !Objects.equals(range.getVersion(), request.version())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        Map<String, Object> oldValues = values(range);
        Instant now = clock.instant();
        try {
            range.update(
                    request.referenceType(), request.lowerBound(), request.upperBound(),
                    inclusive(request.lowerInclusive()), inclusive(request.upperInclusive()),
                    request.normalValues(), request.referenceText(), request.sex(), request.ageMinDays(),
                    request.ageMaxDays(), request.effectiveFrom(), request.effectiveTo(),
                    request.source(), request.active(), now);
        } catch (IllegalArgumentException exception) {
            throw invalid(exception);
        }
        rangeRepository.saveAndFlush(range);
        auditLogService.recordEntityChange(
                principal.accountId(), range.getId(), AuditLogAction.UPDATE, ENTITY_TYPE,
                oldValues, values(range), now);
        return toResponse(range);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LabParameterManagementResponse> listParameters() {
        Map<UUID, List<LabReferenceRange>> rangesByParameter = rangeRepository.findAllByOrderByEffectiveFromDesc()
                .stream()
                .collect(Collectors.groupingBy(LabReferenceRange::getParameterId));
        return parameterRepository.findAllByOrderByCodeAsc().stream()
                .map(parameter -> new LabParameterManagementResponse(
                        parameter.getId(), parameter.getCode(), parameter.getName(), parameter.getValueType(),
                        parameter.getUnit(), parameter.isActive(),
                        rangesByParameter.getOrDefault(parameter.getId(), List.of()).stream()
                                .map(this::toResponse)
                                .toList()))
                .toList();
    }

    @Override
    @Transactional
    public LabReferenceRangeResponse revise(
            StaffPrincipal principal, UUID parameterId, UUID rangeId, LabReferenceRangeRequest request) {
        requireParameter(parameterId);
        LabReferenceRange previous = rangeRepository.findById(rangeId)
                .filter(item -> item.getParameterId().equals(parameterId))
                .orElseThrow(() -> new AppException(ErrorCode.LAB_REFERENCE_RANGE_NOT_FOUND));
        if (request.version() == null || !Objects.equals(previous.getVersion(), request.version())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        Instant now = clock.instant();
        LocalDate today = now.atZone(CLINIC_ZONE).toLocalDate();
        // Only the range in force today can be revised; an ended one is history.
        if (!previous.isCurrentOn(today)) {
            throw new AppException(ErrorCode.LAB_REFERENCE_RANGE_INVALID, Map.of("referenceRange", "NOT_CURRENT"));
        }

        // The revision always starts today and stays open; the request's own dates are ignored.
        LabReferenceRange revised;
        try {
            revised = LabReferenceRange.create(
                    ids.next(), now, parameterId, request.referenceType(), request.lowerBound(),
                    request.upperBound(), inclusive(request.lowerInclusive()), inclusive(request.upperInclusive()),
                    request.normalValues(), request.referenceText(), request.sex(), request.ageMinDays(),
                    request.ageMaxDays(), today, null, request.source(), true);
        } catch (IllegalArgumentException exception) {
            throw invalid(exception);
        }
        Map<String, Object> previousValues = values(previous);
        previous.supersede(today, now);
        rangeRepository.saveAndFlush(previous);
        rangeRepository.saveAndFlush(revised);
        auditLogService.recordEntityChange(
                principal.accountId(), previous.getId(), AuditLogAction.UPDATE, ENTITY_TYPE,
                previousValues, values(previous), now);
        auditLogService.recordEntityChange(
                principal.accountId(), revised.getId(), AuditLogAction.CREATE, ENTITY_TYPE,
                Map.of(), values(revised), now);
        return toResponse(revised);
    }

    private void requireParameter(UUID parameterId) {
        if (!parameterRepository.existsById(parameterId)) {
            throw new AppException(ErrorCode.LAB_PARAMETER_INVALID);
        }
    }

    private AppException invalid(IllegalArgumentException exception) {
        return new AppException(ErrorCode.LAB_REFERENCE_RANGE_INVALID,
                Map.of("referenceRange", exception.getMessage()));
    }

    private boolean inclusive(Boolean value) {
        return value == null || value;
    }

    private Map<String, Object> values(LabReferenceRange range) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("parameterId", range.getParameterId());
        values.put("referenceType", range.getReferenceType());
        values.put("lowerBound", range.getLowerBound());
        values.put("upperBound", range.getUpperBound());
        values.put("normalValues", range.normalValueList());
        values.put("referenceText", range.getReferenceText());
        values.put("sex", range.getSex());
        values.put("ageMinDays", range.getAgeMinDays());
        values.put("ageMaxDays", range.getAgeMaxDays());
        values.put("effectiveFrom", range.getEffectiveFrom());
        values.put("effectiveTo", range.getEffectiveTo());
        values.put("source", range.getSource());
        values.put("active", range.isActive());
        return values;
    }

    public LabReferenceRangeResponse toResponse(LabReferenceRange range) {
        return new LabReferenceRangeResponse(
                range.getId(), range.getParameterId(), range.getReferenceType(), range.getLowerBound(),
                range.getUpperBound(), range.isLowerInclusive(), range.isUpperInclusive(),
                range.normalValueList(), range.getReferenceText(), range.getSex(), range.getAgeMinDays(),
                range.getAgeMaxDays(), range.getEffectiveFrom(), range.getEffectiveTo(), range.getSource(),
                range.isActive(), range.getVersion(), displayText(range));
    }

    private String displayText(LabReferenceRange range) {
        String value = switch (range.getReferenceType()) {
            case RANGE -> boundary(range.isLowerInclusive(), "≥", ">") + number(range.getLowerBound())
                    + " – " + boundary(range.isUpperInclusive(), "≤", "<") + number(range.getUpperBound());
            case UPPER_LIMIT -> boundary(range.isUpperInclusive(), "≤", "<") + number(range.getUpperBound());
            case LOWER_LIMIT -> boundary(range.isLowerInclusive(), "≥", ">") + number(range.getLowerBound());
            case QUALITATIVE -> String.join(", ", range.normalValueList());
            case TEXT -> range.getReferenceText();
        };
        List<String> qualifiers = new ArrayList<>();
        if (range.getSex() != null) qualifiers.add(switch (range.getSex()) {
            case 1 -> "Nam";
            case 2 -> "Nữ";
            default -> "Khác";
        });
        if (range.getAgeMinDays() != null || range.getAgeMaxDays() != null) {
            qualifiers.add("tuổi ngày " + Objects.toString(range.getAgeMinDays(), "0")
                    + "–" + Objects.toString(range.getAgeMaxDays(), "∞"));
        }
        return qualifiers.isEmpty() ? value : String.join(", ", qualifiers) + ": " + value;
    }

    private String boundary(boolean inclusive, String inclusiveSymbol, String exclusiveSymbol) {
        return inclusive ? inclusiveSymbol : exclusiveSymbol;
    }

    private String number(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
