package clinic.backend.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import clinic.backend.dto.response.AuditLogDetailResponse;
import clinic.backend.dto.response.AuditLogItemResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.AuditOutcome;

/** UC-78: read-only access to the business audit trail for the Admin. */
public interface AuditLogViewService {

    /**
     * Newest entries first. {@code from} and {@code to} are clinic-local dates, both inclusive.
     * {@code keyword} matches the actor's username or full name, the visit code, or an exact entry/object id.
     */
    PageResponse<AuditLogItemResponse> search(
            String keyword,
            AuditLogAction action,
            String entityType,
            AuditOutcome outcome,
            LocalDate from,
            LocalDate to,
            Integer page,
            Integer size);

    AuditLogDetailResponse getDetail(UUID id);

    List<String> listEntityTypes();
}
