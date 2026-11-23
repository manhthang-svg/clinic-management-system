package clinic.backend.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;

import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.RecordArchiveResponse;
import clinic.backend.enums.RecordArchiveRetentionCategory;
import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.RecordArchiveRepository;
import clinic.backend.service.RecordArchiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordArchiveServiceImpl implements RecordArchiveService {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final RecordArchiveRepository recordArchiveRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RecordArchiveResponse> searchArchives(
            String keyword,
            RecordArchiveRetentionCategory category,
            LocalDate from,
            LocalDate to,
            Integer page,
            Integer size
    ) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new AppException(ErrorCode.REQUEST_PARAMETER_INVALID, "Ngày bắt đầu không được sau ngày kết thúc.");
        }

        int pageNum = page != null ? Math.max(0, page) : 0;
        int pageSize = size != null ? Math.min(MAX_PAGE_SIZE, Math.max(1, size)) : DEFAULT_PAGE_SIZE;

        String search = (keyword != null && !keyword.isBlank()) ? keyword.strip() : null;
        if (search != null && search.length() > 100) {
            search = search.substring(0, 100);
        }

        Page<RecordArchiveRepository.RecordArchiveRow> rows = recordArchiveRepository.searchArchives(
                category, from, to, search, PageRequest.of(pageNum, pageSize)
        );

        LocalDate today = LocalDate.now(clock.withZone(CLINIC_ZONE));

        List<RecordArchiveResponse> items = rows.getContent().stream()
                .map(row -> {
                    Integer age = null;
                    if (row.getDateOfBirth() != null) {
                        age = Math.max(0, Period.between(row.getDateOfBirth(), today).getYears());
                    }

                    return new RecordArchiveResponse(
                            row.getArchiveId(),
                            row.getMedicalRecordId(),
                            row.getSoluutru(),
                            row.getSoba(),
                            row.getVisitId(),
                            row.getVisitCode(),
                            row.getAdmissionTime(),
                            row.getPatientId(),
                            row.getPatientCode(),
                            row.getPatientName(),
                            row.getPatientCccd(),
                            row.getDateOfBirth(),
                            age,
                            row.getGender(),
                            row.getRetentionCategory(),
                            row.getRetentionYears(),
                            row.getExpiresOn(),
                            row.getPackageSha256(),
                            row.getStatus(),
                            row.getArchivedAt()
                    );
                })
                .toList();

        return new PageResponse<>(
                items,
                new PageResponse.PageMetadata(pageNum, pageSize, rows.getTotalElements(), rows.getTotalPages())
        );
    }
}
