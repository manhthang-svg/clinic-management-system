package clinic.backend.service;

import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.LabReferenceRangeRequest;
import clinic.backend.dto.response.LabReferenceRangeResponse;
import clinic.backend.security.user.StaffPrincipal;

public interface LabReferenceRangeService {

    List<LabReferenceRangeResponse> list(UUID parameterId);

    LabReferenceRangeResponse create(
            StaffPrincipal principal, UUID parameterId, LabReferenceRangeRequest request);

    LabReferenceRangeResponse update(
            StaffPrincipal principal, UUID parameterId, UUID rangeId, LabReferenceRangeRequest request);
}
