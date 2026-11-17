package clinic.backend.service.impl;

import clinic.backend.entity.ExportLog;
import clinic.backend.repository.ExportLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** UC-53 EF-03: a failed export is logged in its own transaction, so it survives the rollback of the export. */
@Component
@RequiredArgsConstructor
public class ExportFailureRecorder {

    private final ExportLogRepository exportLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ExportLog failure) {
        exportLogRepository.save(failure);
    }
}
