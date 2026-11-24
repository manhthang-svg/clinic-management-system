package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.config.properties.FollowUpReminderProperties;
import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.FollowUpAppointment;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.Patient;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.FollowUpAppointmentRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.FollowUpReminderService.Result;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowUpReminderServiceImplTest {

    /** 08:00 on 24/11/2026 in clinic time, when the first run of the day starts. */
    private static final Instant NOW = Instant.parse("2026-11-24T01:00:00Z");
    private static final LocalDate TOMORROW = LocalDate.of(2026, 11, 25);

    @Mock private TransactionTemplate transactionTemplate;
    @Mock private JavaMailSender mailSender;
    @Mock private FollowUpAppointmentRepository appointmentRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private StaffAccountRepository staffAccountRepository;
    @Mock private MedicalRecordRepository medicalRecordRepository;
    @Mock private VisitRepository visitRepository;
    @Mock private ClinicFacilityRepository clinicFacilityRepository;
    @Mock private AuditLogService auditLogService;

    private FollowUpReminderServiceImpl service;
    private FollowUpAppointment appointment;
    private Patient patient;

    @BeforeEach
    void setUp() {
        service = new FollowUpReminderServiceImpl(
                Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")), transactionTemplate,
                new FollowUpReminderProperties(true, 1, "phongkham@gmail.com"), mailSender, appointmentRepository,
                patientRepository, staffAccountRepository, medicalRecordRepository, visitRepository,
                clinicFacilityRepository, auditLogService);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(
                call -> call.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        lenient().when(mailSender.createMimeMessage()).thenAnswer(call -> new MimeMessage((Session) null));

        UUID recordId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        appointment = FollowUpAppointment.schedule(
                UUID.randomUUID(), NOW.minusSeconds(86_400), recordId, "GH-DK0000000123", patientId, doctorId,
                "I10", TOMORROW, "Uống thuốc đều");
        patient = mock(Patient.class);
        lenient().when(patient.getHoten()).thenReturn("Nguyễn Văn An");
        lenient().when(patient.getEmail()).thenReturn(" an.nguyen@example.com ");
        lenient().when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        StaffAccount doctor = mock(StaffAccount.class);
        lenient().when(doctor.getFullName()).thenReturn("Trần Thị Bình");
        lenient().when(staffAccountRepository.findById(doctorId)).thenReturn(Optional.of(doctor));
        ClinicFacility facility = mock(ClinicFacility.class);
        lenient().when(facility.getTenCskcb()).thenReturn("Phòng khám Đa khoa An Tâm");
        lenient().when(facility.getDiachi()).thenReturn("12 Lê Lợi, Hà Nội");
        lenient().when(facility.getSodienthoai()).thenReturn("0241234567");
        lenient().when(clinicFacilityRepository.findCurrent()).thenReturn(Optional.of(facility));
        MedicalRecord record = mock(MedicalRecord.class);
        UUID visitId = UUID.randomUUID();
        lenient().when(record.getVisitId()).thenReturn(visitId);
        lenient().when(medicalRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        Visit visit = mock(Visit.class);
        lenient().when(visit.getSovaovien()).thenReturn("DK0000000123");
        lenient().when(visitRepository.findById(visitId)).thenReturn(Optional.of(visit));
    }

    @Test
    void sendsTheReminderForTomorrowInClinicTimeAndRecordsIt() throws Exception {
        when(appointmentRepository.findReminderDueIds(TOMORROW)).thenReturn(List.of(appointment.getId()));
        when(appointmentRepository.findByIdForUpdate(appointment.getId())).thenReturn(Optional.of(appointment));

        Result result = service.sendDueReminders();

        assertThat(result).isEqualTo(new Result(1, 0, 0));
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        MimeMessage message = sent.getValue();
        assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("an.nguyen@example.com");
        assertThat(message.getFrom()[0].toString()).isEqualTo("phongkham@gmail.com");
        assertThat(message.getSubject())
                .isEqualTo("Nhắc lịch tái khám ngày 25/11/2026 - Phòng khám Đa khoa An Tâm");
        assertThat(String.valueOf(message.getContent()))
                .contains("Kính gửi Nguyễn Văn An", "GH-DK0000000123", "Trần Thị Bình")
                .doesNotContain("Uống thuốc đều", "I10");
        assertThat(appointment.getReminderSentAt()).isEqualTo(NOW);
        verify(auditLogService).recordSystemChange(
                AuditLogAction.UPDATE, "follow_up_appointments", appointment.getId(), "DK0000000123",
                Map.of("reminderSentAt", NOW, "channel", "EMAIL", "followUpDate", TOMORROW), NOW);
    }

    @Test
    void neverSendsTheReminderTwice() {
        appointment.markReminderSent(NOW.minusSeconds(3_600));
        when(appointmentRepository.findReminderDueIds(TOMORROW)).thenReturn(List.of(appointment.getId()));
        when(appointmentRepository.findByIdForUpdate(appointment.getId())).thenReturn(Optional.of(appointment));

        Result result = service.sendDueReminders();

        assertThat(result).isEqualTo(new Result(0, 0, 1));
        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(auditLogService, never()).recordSystemChange(any(), any(), any(), any(), anyMap(), any());
    }

    @Test
    void skipsAnAppointmentCancelledOrRescheduledAfterTheLookup() {
        appointment.cancel("Bệnh nhân báo không đến", NOW);
        when(appointmentRepository.findReminderDueIds(TOMORROW)).thenReturn(List.of(appointment.getId()));
        when(appointmentRepository.findByIdForUpdate(appointment.getId())).thenReturn(Optional.of(appointment));

        assertThat(service.sendDueReminders()).isEqualTo(new Result(0, 0, 1));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void skipsAPatientWhoseEmailWasRemoved() {
        when(patient.getEmail()).thenReturn("  ");
        when(appointmentRepository.findReminderDueIds(TOMORROW)).thenReturn(List.of(appointment.getId()));
        when(appointmentRepository.findByIdForUpdate(appointment.getId())).thenReturn(Optional.of(appointment));

        assertThat(service.sendDueReminders()).isEqualTo(new Result(0, 0, 1));
        verify(mailSender, never()).send(any(MimeMessage.class));
        assertThat(appointment.getReminderSentAt()).isNull();
    }

    @Test
    void aFailedSendIsCountedAndLeftForTheNextRun() {
        when(appointmentRepository.findReminderDueIds(TOMORROW)).thenReturn(List.of(appointment.getId()));
        when(appointmentRepository.findByIdForUpdate(appointment.getId())).thenReturn(Optional.of(appointment));
        doThrow(new MailSendException("SMTP unavailable")).when(mailSender).send(any(MimeMessage.class));

        Result result = service.sendDueReminders();

        assertThat(result).isEqualTo(new Result(0, 1, 0));
        assertThat(appointment.getReminderSentAt()).isNull();
        verify(auditLogService, never()).recordSystemChange(any(), any(), any(), any(), anyMap(), any());
    }

    @Test
    void oneFailureDoesNotStopTheOtherReminders() {
        FollowUpAppointment other = FollowUpAppointment.schedule(
                UUID.randomUUID(), NOW, appointment.getMedicalRecordId(), "GH-DK0000000124",
                appointment.getPatientId(), appointment.getDoctorAccountId(), null, TOMORROW, null);
        when(appointmentRepository.findReminderDueIds(TOMORROW))
                .thenReturn(List.of(appointment.getId(), other.getId()));
        when(appointmentRepository.findByIdForUpdate(appointment.getId())).thenReturn(Optional.of(appointment));
        when(appointmentRepository.findByIdForUpdate(other.getId())).thenReturn(Optional.of(other));
        doThrow(new MailSendException("SMTP unavailable")).doNothing().when(mailSender).send(any(MimeMessage.class));

        assertThat(service.sendDueReminders()).isEqualTo(new Result(1, 1, 0));
        assertThat(other.getReminderSentAt()).isEqualTo(NOW);
    }

    @Test
    void honoursTheConfiguredDaysBefore() {
        service = new FollowUpReminderServiceImpl(
                Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")), transactionTemplate,
                new FollowUpReminderProperties(true, 3, null), mailSender, appointmentRepository,
                patientRepository, staffAccountRepository, medicalRecordRepository, visitRepository,
                clinicFacilityRepository, auditLogService);
        when(appointmentRepository.findReminderDueIds(LocalDate.of(2026, 11, 27))).thenReturn(List.of());

        assertThat(service.sendDueReminders()).isEqualTo(new Result(0, 0, 0));
        verify(appointmentRepository).findReminderDueIds(eq(LocalDate.of(2026, 11, 27)));
    }

    @Test
    void anAppointmentIsMarkedRemindedOnlyOnce() {
        appointment.markReminderSent(NOW);

        assertThatThrownBy(() -> appointment.markReminderSent(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void daysBeforeBelowOneFallsBackToOne() {
        assertThat(new FollowUpReminderProperties(true, 0, null).daysBefore()).isEqualTo(1);
    }
}
