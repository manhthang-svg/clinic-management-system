package clinic.backend.service;

import java.time.LocalDate;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.RecordArchiveResponse;
import clinic.backend.enums.RecordArchiveRetentionCategory;

public interface RecordArchiveService {

    PageResponse<RecordArchiveResponse> searchArchives(
            String keyword,
            RecordArchiveRetentionCategory category,
            LocalDate from,
            LocalDate to,
            Integer page,
            Integer size
    );
}

