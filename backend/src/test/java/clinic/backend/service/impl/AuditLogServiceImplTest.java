package clinic.backend.service.impl;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import clinic.backend.entity.AuditLog;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.repository.AuditLogRepository;
import clinic.backend.utils.UuidV7Generator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceImplTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UuidV7Generator ids;

    @InjectMocks
    private AuditLogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuditLogServiceImpl(auditLogRepository, new ObjectMapper(), ids);
    }

    @AfterEach
    void clearCorrelationId() {
        org.slf4j.MDC.clear();
    }

    @Test
    void recordsAccountUpdateWithChangedFieldsAndJsonSnapshots() {
        UUID auditId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-10-28T01:00:00Z");
        when(ids.next()).thenReturn(auditId);
        org.slf4j.MDC.put("requestId", "req-123");

        service.recordAccountUpdate(
                accountId,
                Map.of("email", "old@example.test"),
                Map.of("email", "new@example.test"),
                occurredAt);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(auditId);
        assertThat(saved.getEntityId()).isEqualTo(accountId);
        assertThat(saved.getChangedFields()).containsExactly("email");
        assertThat(saved.getOldValue()).isEqualTo("{\"email\":\"old@example.test\"}");
        assertThat(saved.getNewValue()).isEqualTo("{\"email\":\"new@example.test\"}");
        assertThat(saved.getCorrelationId()).isEqualTo("req-123");
    }

    @Test
    void rejectsAccountUpdateWhenChangedFieldSetsDiffer() {
        assertThatThrownBy(() -> service.recordAccountUpdate(
                UUID.randomUUID(),
                Map.of("email", "old@example.test"),
                Map.of("phoneNumber", "0900000000"),
                Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordsAdministrativeAccountChange() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(ids.next()).thenReturn(UUID.randomUUID());

        service.recordAccountChange(
                actorId,
                targetId,
                AuditLogAction.UPDATE,
                Map.of("fullName", "Old"),
                Map.of("fullName", "New"),
                Instant.now());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getActorAccountId()).isEqualTo(actorId);
        assertThat(captor.getValue().getEntityId()).isEqualTo(targetId);
        assertThat(captor.getValue().getAction()).isEqualTo(AuditLogAction.UPDATE);
    }
}
