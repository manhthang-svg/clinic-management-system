package clinic.backend.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import clinic.backend.dto.response.AuditLogDetailResponse;
import clinic.backend.dto.response.AuditLogItemResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.entity.AuditLog;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.AuditOutcome;
import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.AuditLogRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.service.AuditLogViewService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-78 View Audit Log. ASSUMPTION: the SRS has no description for UC-78 yet; the filters (time range,
 * action, object type, outcome, keyword) and the 20-row default page follow the other Admin lists.
 */
@Service
@RequiredArgsConstructor
public class AuditLogViewServiceImpl implements AuditLogViewService {

    static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_KEYWORD_LENGTH = 100;

    private final AuditLogRepository auditLogRepository;
    private final StaffAccountRepository staffAccountRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogItemResponse> search(
            String keyword,
            AuditLogAction action,
            String entityType,
            AuditOutcome outcome,
            LocalDate from,
            LocalDate to,
            Integer page,
            Integer size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new AppException(ErrorCode.REQUEST_PARAMETER_INVALID, Map.of("from", "MUST_NOT_BE_AFTER_TO"));
        }
        int pageNum = page == null ? 0 : Math.max(0, page);
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(MAX_PAGE_SIZE, Math.max(1, size));

        Page<AuditLog> rows = auditLogRepository.findAll(
                specification(normalize(keyword), action, blankToNull(entityType), outcome, from, to),
                PageRequest.of(pageNum, pageSize,
                        Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));

        Map<UUID, StaffAccount> actors = actorsOf(rows.getContent());
        List<AuditLogItemResponse> items = rows.getContent().stream()
                .map(log -> toItem(log,
                        log.getActorAccountId() == null ? null : actors.get(log.getActorAccountId())))
                .toList();
        return new PageResponse<>(items,
                new PageResponse.PageMetadata(pageNum, pageSize, rows.getTotalElements(), rows.getTotalPages()));
    }

    @Override
    @Transactional(readOnly = true)
    public AuditLogDetailResponse getDetail(UUID id) {
        AuditLog log = auditLogRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.AUDIT_LOG_NOT_FOUND));
        StaffAccount actor = log.getActorAccountId() == null
                ? null
                : staffAccountRepository.findById(log.getActorAccountId()).orElse(null);
        return new AuditLogDetailResponse(
                log.getId(), log.getOccurredAt(), log.getActorAccountId(),
                actor == null ? null : actor.getUsername(),
                actor == null ? null : actor.getFullName(),
                actor == null ? null : actor.getRole(),
                log.getAction(), log.getEntityType(), log.getEntityId(), log.getSovaovien(), log.getOutcome(),
                copy(log.getChangedFields()), log.getOldValue(), log.getNewValue(),
                log.getIpAddress(), log.getUserAgent(), log.getCorrelationId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listEntityTypes() {
        return auditLogRepository.findDistinctEntityTypes();
    }

    private Map<UUID, StaffAccount> actorsOf(List<AuditLog> logs) {
        Set<UUID> ids = logs.stream()
                .map(AuditLog::getActorAccountId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return staffAccountRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(StaffAccount::getId, Function.identity()));
    }

    private static AuditLogItemResponse toItem(AuditLog log, StaffAccount actor) {
        return new AuditLogItemResponse(
                log.getId(), log.getOccurredAt(), log.getActorAccountId(),
                actor == null ? null : actor.getUsername(),
                actor == null ? null : actor.getFullName(),
                actor == null ? null : actor.getRole(),
                log.getAction(), log.getEntityType(), log.getEntityId(), log.getSovaovien(), log.getOutcome(),
                copy(log.getChangedFields()), log.getIpAddress());
    }

    static Specification<AuditLog> specification(
            String keyword,
            AuditLogAction action,
            String entityType,
            AuditOutcome outcome,
            LocalDate from,
            LocalDate to) {
        return (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            if (action != null) {
                predicate = cb.and(predicate, cb.equal(root.get("action"), action));
            }
            if (entityType != null) {
                predicate = cb.and(predicate, cb.equal(root.get("entityType"), entityType));
            }
            if (outcome != null) {
                predicate = cb.and(predicate, cb.equal(root.get("outcome"), outcome));
            }
            if (from != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(
                        root.get("occurredAt"), from.atStartOfDay(CLINIC_ZONE).toInstant()));
            }
            if (to != null) {
                predicate = cb.and(predicate, cb.lessThan(
                        root.get("occurredAt"), to.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant()));
            }
            if (keyword != null) {
                predicate = cb.and(predicate, keywordPredicate(keyword, root, query, cb));
            }
            return predicate;
        };
    }

    private static Predicate keywordPredicate(
            String keyword, Root<AuditLog> root, jakarta.persistence.criteria.CriteriaQuery<?> query,
            CriteriaBuilder cb) {
        UUID exactId = parseUuid(keyword);
        if (exactId != null) {
            return cb.or(cb.equal(root.get("id"), exactId), cb.equal(root.get("entityId"), exactId),
                    cb.equal(root.get("actorAccountId"), exactId));
        }
        Expression<String> term = cb.literal("%" + escapeLike(keyword.toLowerCase()) + "%");

        Subquery<UUID> matchingActors = query.subquery(UUID.class);
        Root<StaffAccount> account = matchingActors.from(StaffAccount.class);
        matchingActors.select(account.get("id")).where(cb.or(
                cb.like(searchable(cb, account.get("username")), term, '\\'),
                cb.like(searchable(cb, account.get("fullName")), term, '\\')));

        return cb.or(
                cb.like(cb.lower(root.get("sovaovien")), term, '\\'),
                root.get("actorAccountId").in(matchingActors));
    }

    // I1-D46: contains-match ignoring case and Vietnamese accents (unaccent, V11).
    private static Expression<String> searchable(CriteriaBuilder cb, Expression<String> value) {
        return cb.lower(cb.function("unaccent", String.class, value));
    }

    // The keyword is matched literally: % and _ typed by the Admin are not wildcards.
    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    static String normalize(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String collapsed = String.join(" ", keyword.strip().split("\\s+"));
        return collapsed.length() > MAX_KEYWORD_LENGTH ? collapsed.substring(0, MAX_KEYWORD_LENGTH) : collapsed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static UUID parseUuid(String value) {
        if (value.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String[] copy(String[] values) {
        return values == null ? null : values.clone();
    }
}
