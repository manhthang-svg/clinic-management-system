package clinic.backend.repository;

import clinic.backend.config.CorrelationIdFilter;
import clinic.backend.entity.SecurityAuditEventEntity;
import clinic.backend.repository.SecurityAuditLog;
import clinic.backend.util.UuidV7Generator;
import org.slf4j.MDC;
import org.springframework.stereotype.Repository;

/**
 * Writes audit events in the caller's transaction, so an action and its audit commit together.
 */
@Repository
class JpaSecurityAuditLog implements SecurityAuditLog {

    private final SecurityAuditEventJpaRepository repository;
    private final UuidV7Generator ids;

    JpaSecurityAuditLog(SecurityAuditEventJpaRepository repository, UuidV7Generator ids) {
        this.repository = repository;
        this.ids = ids;
    }

    @Override
    public void record(Event event) {
        repository.save(new SecurityAuditEventEntity(
                ids.next(),
                event.occurredAt(),
                event.type(),
                event.actorAccountId(),
                event.targetAccountId(),
                event.outcome(),
                MDC.get(CorrelationIdFilter.MDC_KEY)));
    }
}
