package clinic.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.config.properties.FollowUpProperties;
import clinic.backend.entity.FollowUpAppointment;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.FollowUpAppointmentStatus;
import clinic.backend.repository.FollowUpAppointmentRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** BR-124: picking the usable appointment and marking expired ones "Lỡ hẹn". */
class FollowUpAppointmentServiceImplTest {

    // Tuesday 2026-11-24, 10:00 in the clinic (03:00 UTC).
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T03:00:00Z"), ZoneOffset.UTC);
    private static final UUID PATIENT = UUID.randomUUID();

    private final FollowUpAppointmentRepository appointmentRepository = mock(FollowUpAppointmentRepository.class);
    private final MedicalRecordRepository medicalRecordRepository = mock(MedicalRecordRepository.class);
    private final StaffAccountRepository accountRepository = mock(StaffAccountRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private FollowUpAppointmentServiceImpl service;

    @BeforeEach
    void setUp() {
        when(accountRepository.findById(any())).thenReturn(Optional.empty());
        when(medicalRecordRepository.findById(any())).thenReturn(Optional.empty());
        service = new FollowUpAppointmentServiceImpl(
                appointmentRepository, medicalRecordRepository, mock(VisitRepository.class),
                mock(RoomRepository.class), accountRepository,
                new FollowUpValidity(new FollowUpProperties(10)), auditLogService, CLOCK);
    }

    private static FollowUpAppointment appointment(LocalDate date) {
        return FollowUpAppointment.schedule(
                UUID.randomUUID(), Instant.parse("2026-10-01T00:00:00Z"), UUID.randomUUID(), "GH1", PATIENT,
                UUID.randomUUID(), "J02", date, "Tái khám");
    }

    @Test
    void anExpiredAppointmentDoesNotHideANewerOneStillInItsPeriod() {
        // 2026-10-01 + 10 working days = 2026-10-15: expired. 2026-11-20 is due and in its period.
        FollowUpAppointment expired = appointment(LocalDate.of(2026, 10, 1));
        FollowUpAppointment current = appointment(LocalDate.of(2026, 11, 20));
        when(appointmentRepository.findByPatientIdAndStatusOrderByFollowUpDateAsc(
                PATIENT, FollowUpAppointmentStatus.SCHEDULED)).thenReturn(List.of(expired, current));

        var result = service.findScheduledForPatient(PATIENT);

        assertThat(result.id()).isEqualTo(current.getId());
        assertThat(result.validUntil()).isEqualTo(LocalDate.of(2026, 12, 4));
    }

    @Test
    void anExpiredAppointmentIsNotShownEvenBeforeTheExpiryJobRuns() {
        // BR-124: still "Đã hẹn" in the database, but its period ended on 2026-10-15; the visit is a normal one.
        FollowUpAppointment expired = appointment(LocalDate.of(2026, 10, 1));
        when(appointmentRepository.findByPatientIdAndStatusOrderByFollowUpDateAsc(
                PATIENT, FollowUpAppointmentStatus.SCHEDULED)).thenReturn(List.of(expired));

        assertThat(service.findScheduledForPatient(PATIENT)).isNull();
    }

    @Test
    void marksOnlyAppointmentsPastTheirValidityPeriodAsMissed() {
        // 2026-11-09 + 10 working days = 2026-11-23 (yesterday): expired.
        FollowUpAppointment expired = appointment(LocalDate.of(2026, 11, 9));
        // 2026-11-10 + 10 working days = 2026-11-24 (today): still usable.
        FollowUpAppointment lastDay = appointment(LocalDate.of(2026, 11, 10));
        when(appointmentRepository.findByStatusAndFollowUpDateBefore(
                FollowUpAppointmentStatus.SCHEDULED, LocalDate.of(2026, 11, 24)))
                .thenReturn(List.of(expired, lastDay));

        int marked = service.markExpiredAsMissed();

        assertThat(marked).isEqualTo(1);
        assertThat(expired.getStatus()).isEqualTo(FollowUpAppointmentStatus.MISSED);
        assertThat(lastDay.getStatus()).isEqualTo(FollowUpAppointmentStatus.SCHEDULED);
        verify(appointmentRepository).save(expired);
        verify(appointmentRepository, never()).save(lastDay);
        verify(auditLogService).recordSystemChange(
                eq(AuditLogAction.UPDATE), eq("follow_up_appointments"), eq(expired.getId()), isNull(), anyMap(),
                any());
    }
}
