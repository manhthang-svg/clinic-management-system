package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import clinic.backend.dto.request.CancelFollowUpAppointmentRequest;
import clinic.backend.dto.response.FollowUpAppointmentResponse;
import clinic.backend.entity.FollowUpAppointment;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.FollowUpAppointmentStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.FollowUpAppointmentRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.FollowUpAppointmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FollowUpAppointmentServiceImpl implements FollowUpAppointmentService {

    private final FollowUpAppointmentRepository appointmentRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final VisitRepository visitRepository;
    private final RoomRepository roomRepository;
    private final StaffAccountRepository accountRepository;
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final FollowUpValidity followUpValidity;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public FollowUpAppointmentResponse findScheduledForPatient(UUID patientId) {
        List<FollowUpAppointment> scheduled = appointmentRepository
                .findByPatientIdAndStatusOrderByFollowUpDateAsc(patientId, FollowUpAppointmentStatus.SCHEDULED);
        // BR-124: an appointment still in its validity period comes first, so an expired one never hides a newer
        // appointment; an expired one is returned only when nothing else is scheduled (UC-40 AF-09 notice).
        LocalDate today = today();
        return scheduled.stream()
                .filter(appointment -> !isExpired(appointment, today))
                .findFirst()
                .or(() -> scheduled.stream().reduce((first, later) -> later))
                .map(this::toResponse)
                .orElse(null);
    }

    @Override
    @Transactional
    public int markExpiredAsMissed() {
        LocalDate today = today();
        Instant now = clock.instant();
        int marked = 0;
        for (FollowUpAppointment appointment : appointmentRepository
                .findByStatusAndFollowUpDateBefore(FollowUpAppointmentStatus.SCHEDULED, today)) {
            if (!isExpired(appointment, today)) {
                continue;
            }
            appointment.markMissed(now);
            appointmentRepository.save(appointment);
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("fromStatus", FollowUpAppointmentStatus.SCHEDULED.name());
            values.put("status", FollowUpAppointmentStatus.MISSED.name());
            values.put("followUpDate", appointment.getFollowUpDate().toString());
            values.put("reason", "HET_HAN_LICH_HEN");
            auditLogService.recordSystemChange(
                    AuditLogAction.UPDATE, FollowUpScheduleServiceImpl.AUDIT_ENTITY, appointment.getId(), null, values, now);
            marked++;
        }
        return marked;
    }

    private boolean isExpired(FollowUpAppointment appointment, LocalDate today) {
        return today.isAfter(followUpValidity.validUntil(appointment.getFollowUpDate()));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(CLINIC_ZONE));
    }

    @Override
    @Transactional
    public FollowUpAppointmentResponse cancel(
            StaffPrincipal principal, UUID appointmentId, CancelFollowUpAppointmentRequest request) {
        FollowUpAppointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new AppException(ErrorCode.FOLLOW_UP_APPOINTMENT_NOT_FOUND));
        if (!appointment.getVersion().equals(request.version())
                || appointment.getStatus() != FollowUpAppointmentStatus.SCHEDULED) {
            throw new AppException(ErrorCode.FOLLOW_UP_APPOINTMENT_NOT_SCHEDULED);
        }
        Instant now = clock.instant();
        appointment.cancel(request.reason(), now);
        appointmentRepository.save(appointment);
        return toResponse(appointment);
    }

    private FollowUpAppointmentResponse toResponse(FollowUpAppointment appointment) {
        String doctorName = accountRepository.findById(appointment.getDoctorAccountId())
                .map(StaffAccount::getFullName).orElse(null);
        UUID suggestedRoomId = null;
        String suggestedRoomName = null;
        MedicalRecord record = medicalRecordRepository.findById(appointment.getMedicalRecordId()).orElse(null);
        Visit previousVisit = record == null ? null : visitRepository.findById(record.getVisitId()).orElse(null);
        if (previousVisit != null) {
            suggestedRoomId = previousVisit.getRoomId();
            suggestedRoomName = roomRepository.findById(suggestedRoomId).map(Room::getRoomName).orElse(null);
        }
        return new FollowUpAppointmentResponse(
                appointment.getId(), appointment.getFollowUpDate(), doctorName,
                suggestedRoomId, suggestedRoomName, appointment.getStatus().name(), appointment.getVersion(),
                followUpValidity.validUntil(appointment.getFollowUpDate()));
    }
}
