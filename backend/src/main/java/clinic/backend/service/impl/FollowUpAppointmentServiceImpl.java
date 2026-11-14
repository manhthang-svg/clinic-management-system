package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import clinic.backend.dto.request.CancelFollowUpAppointmentRequest;
import clinic.backend.dto.response.FollowUpAppointmentResponse;
import clinic.backend.entity.FollowUpAppointment;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.FollowUpAppointmentStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.FollowUpAppointmentRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.security.user.StaffPrincipal;
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
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public FollowUpAppointmentResponse findScheduledForPatient(UUID patientId) {
        return appointmentRepository
                .findByPatientIdAndStatusOrderByFollowUpDateAsc(patientId, FollowUpAppointmentStatus.SCHEDULED)
                .stream().findFirst().map(this::toResponse).orElse(null);
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
                suggestedRoomId, suggestedRoomName, appointment.getStatus().name(), appointment.getVersion());
    }
}
