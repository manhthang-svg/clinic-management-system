package clinic.backend.service;

import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.LabReferenceRangeRequest;
import clinic.backend.dto.response.LabParameterManagementResponse;
import clinic.backend.dto.response.LabReferenceRangeResponse;
import clinic.backend.security.user.StaffPrincipal;

public interface LabReferenceRangeService {

    List<LabReferenceRangeResponse> list(UUID parameterId);

    LabReferenceRangeResponse create(
            StaffPrincipal principal, UUID parameterId, LabReferenceRangeRequest request);

    LabReferenceRangeResponse update(
            StaffPrincipal principal, UUID parameterId, UUID rangeId, LabReferenceRangeRequest request);

    /** Every lab parameter with its full reference range history, for the Admin parameter screen. */
    List<LabParameterManagementResponse> listParameters();

    /**
     * Replaces a current range with new values: the new range takes effect today and the old one ends yesterday,
     * so results already interpreted against the old values keep their history.
     */
    LabReferenceRangeResponse revise(
            StaffPrincipal principal, UUID parameterId, UUID rangeId, LabReferenceRangeRequest request);
}
