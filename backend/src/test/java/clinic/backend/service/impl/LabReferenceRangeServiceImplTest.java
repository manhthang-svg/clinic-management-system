package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.LabReferenceRangeRequest;
import clinic.backend.entity.LabReferenceRange;
import clinic.backend.enums.LabReferenceType;
import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.LabParameterRepository;
import clinic.backend.repository.LabReferenceRangeRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabReferenceRangeServiceImplTest {

    @Mock LabParameterRepository parameterRepository;
    @Mock LabReferenceRangeRepository rangeRepository;
    @Mock AuditLogService auditLogService;
    @Mock UuidV7Generator ids;

    @Test
    void adminCanCreateAnActiveRangeWithoutVerification() {
        UUID parameterId = UUID.randomUUID();
        UUID rangeId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-04T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        var service = new LabReferenceRangeServiceImpl(
                parameterRepository, rangeRepository, auditLogService, ids, clock);
        when(parameterRepository.existsById(parameterId)).thenReturn(true);
        when(ids.next()).thenReturn(rangeId);
        var request = new LabReferenceRangeRequest(
                LabReferenceType.RANGE, BigDecimal.valueOf(4), BigDecimal.valueOf(10),
                true, true, null, "4.0 – 10.0", null, 16 * 365, 150 * 365,
                LocalDate.of(2026, 1, 1), null, "Clinic laboratory catalog", true, null);

        var response = service.create(
                new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID()), parameterId, request);

        assertThat(response.id()).isEqualTo(rangeId);
        assertThat(response.active()).isTrue();
        assertThat(response.displayText()).contains("4", "10");
        verify(rangeRepository).saveAndFlush(ArgumentCaptor.forClass(
                clinic.backend.entity.LabReferenceRange.class).capture());
    }

    @Test
    void rejectsValuesThatDoNotMatchTheReferenceTypeBeforeSaving() {
        UUID parameterId = UUID.randomUUID();
        Clock clock = Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ZoneOffset.UTC);
        var service = new LabReferenceRangeServiceImpl(
                parameterRepository, rangeRepository, auditLogService, ids, clock);
        when(parameterRepository.existsById(parameterId)).thenReturn(true);
        when(ids.next()).thenReturn(UUID.randomUUID());
        var request = new LabReferenceRangeRequest(
                LabReferenceType.RANGE, BigDecimal.valueOf(4), BigDecimal.valueOf(10),
                true, true, List.of("unexpected"), null, null, null, null,
                LocalDate.of(2026, 1, 1), null, "Clinic laboratory catalog", true, null);

        assertThatThrownBy(() -> service.create(
                new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID()), parameterId, request))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.LAB_REFERENCE_RANGE_INVALID));
    }

    @Test
    void revisingARangeStartsANewOneTodayAndEndsTheOldOneYesterday() {
        UUID parameterId = UUID.randomUUID();
        UUID previousId = UUID.randomUUID();
        UUID revisedId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-04T03:00:00Z");
        LocalDate today = LocalDate.of(2026, 11, 4);
        var service = new LabReferenceRangeServiceImpl(
                parameterRepository, rangeRepository, auditLogService, ids, Clock.fixed(now, ZoneOffset.UTC));
        LabReferenceRange previous = mock(LabReferenceRange.class);
        when(previous.getParameterId()).thenReturn(parameterId);
        when(previous.getVersion()).thenReturn(3L);
        when(previous.isCurrentOn(today)).thenReturn(true);
        when(parameterRepository.existsById(parameterId)).thenReturn(true);
        when(rangeRepository.findById(previousId)).thenReturn(Optional.of(previous));
        when(ids.next()).thenReturn(revisedId);
        var request = new LabReferenceRangeRequest(
                LabReferenceType.RANGE, BigDecimal.valueOf(6), BigDecimal.valueOf(7),
                true, true, null, null, (short) 2, null, null,
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31), "Clinic laboratory catalog", false, 3L);

        var response = service.revise(
                new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID()), parameterId, previousId, request);

        verify(previous).supersede(today, now);
        assertThat(response.id()).isEqualTo(revisedId);
        assertThat(response.sex()).isEqualTo((short) 2);
        assertThat(response.lowerBound()).isEqualByComparingTo("6");
        assertThat(response.upperBound()).isEqualByComparingTo("7");
        // The revision ignores the requested dates and status: it is active from today with no end.
        assertThat(response.effectiveFrom()).isEqualTo(today);
        assertThat(response.effectiveTo()).isNull();
        assertThat(response.active()).isTrue();
    }

    @Test
    void rejectsRevisingARangeThatIsNoLongerInForce() {
        UUID parameterId = UUID.randomUUID();
        UUID previousId = UUID.randomUUID();
        var service = new LabReferenceRangeServiceImpl(
                parameterRepository, rangeRepository, auditLogService, ids,
                Clock.fixed(Instant.parse("2026-11-04T03:00:00Z"), ZoneOffset.UTC));
        LabReferenceRange previous = mock(LabReferenceRange.class);
        when(previous.getParameterId()).thenReturn(parameterId);
        when(previous.getVersion()).thenReturn(1L);
        when(previous.isCurrentOn(LocalDate.of(2026, 11, 4))).thenReturn(false);
        when(parameterRepository.existsById(parameterId)).thenReturn(true);
        when(rangeRepository.findById(previousId)).thenReturn(Optional.of(previous));
        var request = new LabReferenceRangeRequest(
                LabReferenceType.RANGE, BigDecimal.valueOf(6), BigDecimal.valueOf(7),
                true, true, null, null, null, null, null,
                LocalDate.of(2026, 11, 4), null, "Clinic laboratory catalog", true, 1L);

        assertThatThrownBy(() -> service.revise(
                new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID()), parameterId, previousId, request))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LAB_REFERENCE_RANGE_INVALID);
                    assertThat(exception.getDetails()).containsEntry("referenceRange", "NOT_CURRENT");
                });
        verify(previous, never()).supersede(any(), any());
    }

    @Test
    void supersedingEndsTheRangeTheDayBeforeOrSwitchesOffOneStartedToday() {
        Instant now = Instant.parse("2026-11-04T03:00:00Z");
        LocalDate today = LocalDate.of(2026, 11, 4);
        LabReferenceRange older = LabReferenceRange.create(
                UUID.randomUUID(), now, UUID.randomUUID(), LabReferenceType.RANGE, BigDecimal.valueOf(5),
                BigDecimal.valueOf(6), true, true, null, null, (short) 2, null, null,
                LocalDate.of(2026, 1, 1), null, "Clinic laboratory catalog", true);
        LabReferenceRange startedToday = LabReferenceRange.create(
                UUID.randomUUID(), now, UUID.randomUUID(), LabReferenceType.RANGE, BigDecimal.valueOf(5),
                BigDecimal.valueOf(6), true, true, null, null, null, null, null,
                today, null, "Clinic laboratory catalog", true);

        older.supersede(today, now);
        startedToday.supersede(today, now);

        assertThat(older.getEffectiveTo()).isEqualTo(today.minusDays(1));
        assertThat(older.isCurrentOn(today)).isFalse();
        assertThat(older.isCurrentOn(today.minusDays(1))).isTrue();
        assertThat(startedToday.isActive()).isFalse();
        assertThat(startedToday.getEffectiveTo()).isNull();
    }
}
