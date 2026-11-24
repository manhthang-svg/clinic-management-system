package clinic.backend.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.config.properties.FollowUpReminderProperties;
import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.FollowUpAppointment;
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
import clinic.backend.service.FollowUpReminderService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * UC-75: one reminder email per scheduled follow-up appointment, sent over the configured free SMTP server. Each
 * appointment is handled in its own transaction under a row lock: the email is sent, then {@code reminder_sent_at}
 * and the audit row are written; a failed send rolls back, so the next run retries it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "clinic.mail.follow-up-reminder.enabled", havingValue = "true")
public class FollowUpReminderServiceImpl implements FollowUpReminderService {

    static final String AUDIT_ENTITY = "follow_up_appointments";
    static final String CHANNEL_EMAIL = "EMAIL";

    private enum Outcome { SENT, SKIPPED }

    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final FollowUpReminderProperties properties;
    private final JavaMailSender mailSender;
    private final FollowUpAppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final VisitRepository visitRepository;
    private final ClinicFacilityRepository clinicFacilityRepository;
    private final AuditLogService auditLogService;

    @Override
    public Result sendDueReminders() {
        LocalDate followUpDate = LocalDate.now(clock).plusDays(properties.daysBefore());
        List<UUID> ids = appointmentRepository.findReminderDueIds(followUpDate);
        int sent = 0;
        int failed = 0;
        int skipped = 0;
        for (UUID id : ids) {
            try {
                Outcome outcome = transactionTemplate.execute(status -> remind(id, followUpDate));
                if (outcome == Outcome.SENT) {
                    sent++;
                } else {
                    skipped++;
                }
            } catch (MailException | IllegalStateException ex) {
                failed++;
                // The address is personal data (docs/security.md): only the appointment is logged.
                log.warn("Follow-up reminder email failed; the next run retries it [appointmentId={}]", id, ex);
            }
        }
        return new Result(sent, failed, skipped);
    }

    private Outcome remind(UUID id, LocalDate followUpDate) {
        FollowUpAppointment appointment = appointmentRepository.findByIdForUpdate(id).orElse(null);
        if (appointment == null || !appointment.needsReminder()
                || !followUpDate.equals(appointment.getFollowUpDate())) {
            return Outcome.SKIPPED;
        }
        Patient patient = patientRepository.findById(appointment.getPatientId()).orElse(null);
        if (patient == null || patient.getEmail() == null || patient.getEmail().isBlank()) {
            return Outcome.SKIPPED;
        }
        Optional<ClinicFacility> facility = clinicFacilityRepository.findCurrent();
        String doctorName = staffAccountRepository.findById(appointment.getDoctorAccountId())
                .map(StaffAccount::getFullName).orElse(null);
        FollowUpReminderEmail email = FollowUpReminderEmail.of(
                patient.getHoten(), appointment.getFollowUpDate(), appointment.getSoGiayHen(), doctorName,
                facility.map(ClinicFacility::getTenCskcb).orElse(null),
                facility.map(ClinicFacility::getDiachi).orElse(null),
                facility.map(ClinicFacility::getSodienthoai).orElse(null));
        mailSender.send(message(patient.getEmail().strip(), email));

        Instant now = clock.instant();
        appointment.markReminderSent(now);
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("reminderSentAt", now);
        audit.put("channel", CHANNEL_EMAIL);
        audit.put("followUpDate", appointment.getFollowUpDate());
        auditLogService.recordSystemChange(
                AuditLogAction.UPDATE, AUDIT_ENTITY, appointment.getId(), sovaovien(appointment), audit, now);
        return Outcome.SENT;
    }

    private MimeMessage message(String to, FollowUpReminderEmail email) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            if (properties.from() != null && !properties.from().isBlank()) {
                helper.setFrom(properties.from().strip());
            }
            helper.setTo(to);
            helper.setSubject(email.subject());
            helper.setText(email.body(), false);
        } catch (MessagingException ex) {
            throw new IllegalStateException("Could not build the follow-up reminder email", ex);
        }
        return message;
    }

    private String sovaovien(FollowUpAppointment appointment) {
        return medicalRecordRepository.findById(appointment.getMedicalRecordId())
                .flatMap(record -> visitRepository.findById(record.getVisitId()))
                .map(Visit::getSovaovien)
                .orElse(null);
    }
}
