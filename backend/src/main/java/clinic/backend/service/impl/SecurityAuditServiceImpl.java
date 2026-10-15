package clinic.backend.service.impl;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.config.RequestCorrelationFilter;
import clinic.backend.entity.SecurityAuditEvent;
import clinic.backend.enums.AuditOutcome;
import clinic.backend.enums.SecurityAuditEventType;
import clinic.backend.repository.SecurityAuditEventRepository;
import clinic.backend.service.SecurityAuditService;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecurityAuditServiceImpl implements SecurityAuditService {

    private final SecurityAuditEventRepository securityAuditEventRepository;
    private final UuidV7Generator ids;

    @Override
    public void record(
            SecurityAuditEventType type,
            UUID actorAccountId,
            UUID targetAccountId,
            AuditOutcome outcome,
            Instant occurredAt) {

        securityAuditEventRepository.save(SecurityAuditEvent.builder()
                .id(ids.next())
                .occurredAt(occurredAt)
                .eventType(type)
                .actorAccountId(actorAccountId)
                .targetAccountId(targetAccountId)
                .outcome(outcome)
                .correlationId(MDC.get(RequestCorrelationFilter.ATTRIBUTE_NAME))
                .build());
    }
}
