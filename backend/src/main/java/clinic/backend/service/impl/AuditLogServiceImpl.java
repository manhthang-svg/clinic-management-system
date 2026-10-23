package clinic.backend.service.impl;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import clinic.backend.config.RequestCorrelationFilter;
import clinic.backend.entity.AuditLog;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.repository.AuditLogRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.utils.UuidV7Generator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final UuidV7Generator ids;

    @Override
    public void recordAccountUpdate(
            UUID accountId,
            Map<String, String> oldValues,
            Map<String, String> newValues,
            Instant occurredAt) {

        if (oldValues.isEmpty() || !oldValues.keySet().equals(newValues.keySet())) {
            throw new IllegalArgumentException("Account audit values must contain the same changed fields");
        }
        auditLogRepository.save(AuditLog.accountUpdated(
                ids.next(),
                accountId,
                occurredAt,
                oldValues.keySet().toArray(String[]::new),
                objectMapper.valueToTree(oldValues).toString(),
                objectMapper.valueToTree(newValues).toString(),
                MDC.get(RequestCorrelationFilter.ATTRIBUTE_NAME)));
    }

    @Override
    public void recordAccountChange(
            UUID actorAccountId,
            UUID targetAccountId,
            AuditLogAction action,
            Map<String, ?> oldValues,
            Map<String, ?> newValues,
            Instant occurredAt) {
        if (!oldValues.keySet().equals(newValues.keySet())) {
            throw new IllegalArgumentException("Account audit values must contain the same changed fields");
        }
        auditLogRepository.save(AuditLog.accountChanged(
                ids.next(), actorAccountId, targetAccountId, action, occurredAt,
                oldValues.keySet().toArray(String[]::new),
                objectMapper.valueToTree(oldValues).toString(),
                objectMapper.valueToTree(newValues).toString(),
                MDC.get(RequestCorrelationFilter.ATTRIBUTE_NAME)));
    }
}
