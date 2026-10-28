package clinic.backend.service.impl;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.entity.SecurityAuditEvent;
import clinic.backend.enums.AuditOutcome;
import clinic.backend.enums.SecurityAuditEventType;
import clinic.backend.repository.SecurityAuditEventRepository;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityAuditServiceImplTest {

    @Mock
    private SecurityAuditEventRepository securityAuditEventRepository;

    @Mock
    private UuidV7Generator ids;

    private SecurityAuditServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new SecurityAuditServiceImpl(securityAuditEventRepository, ids);
    }

    @AfterEach
    void clearCorrelationId() {
        org.slf4j.MDC.clear();
    }

    @Test
    void recordsSecurityEventWithoutRequestContext() {
        UUID eventId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-10-28T01:00:00Z");
        when(ids.next()).thenReturn(eventId);
        org.slf4j.MDC.put("requestId", "req-456");

        service.record(
                SecurityAuditEventType.LOGIN_SUCCEEDED,
                actorId,
                targetId,
                AuditOutcome.SUCCESS,
                occurredAt);

        ArgumentCaptor<SecurityAuditEvent> captor =
                ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(securityAuditEventRepository).save(captor.capture());
        SecurityAuditEvent saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(eventId);
        assertThat(saved.getEventType()).isEqualTo(SecurityAuditEventType.LOGIN_SUCCEEDED);
        assertThat(saved.getActorAccountId()).isEqualTo(actorId);
        assertThat(saved.getTargetAccountId()).isEqualTo(targetId);
        assertThat(saved.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(saved.getCorrelationId()).isEqualTo("req-456");
        assertThat(saved.getIpAddress()).isNull();
    }
}
