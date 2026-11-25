package clinic.backend.service;

import java.util.UUID;

import clinic.backend.dto.request.CancelFollowUpAppointmentRequest;
import clinic.backend.dto.response.FollowUpAppointmentResponse;
import clinic.backend.security.user.StaffPrincipal;

public interface FollowUpAppointmentService {
    FollowUpAppointmentResponse findScheduledForPatient(UUID patientId);

    FollowUpAppointmentResponse cancel(
            StaffPrincipal principal, UUID appointmentId, CancelFollowUpAppointmentRequest request);

    /**
     * BR-124: marks "Lỡ hẹn" (MISSED) every scheduled appointment whose validity period ended before today.
     * Returns how many were marked.
     */
    int markExpiredAsMissed();
}
