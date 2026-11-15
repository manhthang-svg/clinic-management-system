package clinic.backend.service;

import java.util.UUID;

import clinic.backend.dto.request.SaveFollowUpAppointmentRequest;
import clinic.backend.dto.response.FollowUpScheduleResponse;
import clinic.backend.dto.response.FollowUpSlipResponse;
import clinic.backend.security.user.StaffPrincipal;

/** UC-61 Create Follow-up Appointment and UC-62 Print Follow-up Appointment, by the Doctor of the visit. */
public interface FollowUpScheduleService {

    /** UC-61 step 2: the follow-up form of the visit, with its appointment if one is saved. */
    FollowUpScheduleResponse get(StaffPrincipal principal, UUID visitId);

    /** UC-61 steps 4-5: creates the visit's appointment or changes it while it is scheduled. */
    FollowUpScheduleResponse save(StaffPrincipal principal, UUID visitId, SaveFollowUpAppointmentRequest request);

    /** UC-62 step 2: the slip for the preview; nothing is recorded. */
    FollowUpSlipResponse printPreview(StaffPrincipal principal, UUID appointmentId);

    /** UC-62 step 3: records the print ({@code PRINT} audit row, first {@code printed_at}) and returns the slip. */
    FollowUpSlipResponse preparePrint(StaffPrincipal principal, UUID appointmentId);
}
