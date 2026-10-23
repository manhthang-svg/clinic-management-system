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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
                .ipAddress(currentIpAddress())
                .build());
    }

    private String currentIpAddress() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        // Do not trust X-Forwarded-For until a trusted-proxy boundary is configured.
        String value = attributes.getRequest().getRemoteAddr();
        return value == null || value.length() > 45 ? null : value;
    }
}
