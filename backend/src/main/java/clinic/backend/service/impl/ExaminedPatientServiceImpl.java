package clinic.backend.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import clinic.backend.dto.response.ExaminedPatientListResponse;
import clinic.backend.dto.response.ExaminedPatientListResponse.ExaminedVisit;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.VisitRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.ExaminedPatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// UC-34: danh sach benh nhan da kham cua bac si dang dang nhap (BR-39)
@Service
@RequiredArgsConstructor
public class ExaminedPatientServiceImpl implements ExaminedPatientService {

    static final long MAX_PERIOD_DAYS = 366;
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final VisitRepository visitRepository;
    private final Clock clock;

    // kiem tra khoang ngay + trang, tra ve tong so va 1 trang luot kham (moi nhat truoc)
    @Override
    @Transactional(readOnly = true)
    public ExaminedPatientListResponse getExaminedPatients(
            StaffPrincipal doctor, LocalDate from, LocalDate to, String keyword, Integer page, Integer size) {
        LocalDate today = LocalDate.now(clock.withZone(CLINIC_ZONE));
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate end = to != null ? to : today;
        if (start.isAfter(end)) {
            throw invalid("from", "The start date must not be after the end date.");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_PERIOD_DAYS) {
            throw invalid("to", "The period must not exceed " + MAX_PERIOD_DAYS + " days.");
        }
        int number = page != null ? page : 0;
        int pageSize = size != null ? size : DEFAULT_PAGE_SIZE;
        if (number < 0) {
            throw invalid("page", "The page must not be negative.");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw invalid("size", "The size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        String search = keyword == null ? "" : keyword.strip();

        VisitRepository.ExaminedTotals totals = visitRepository.countExamined(doctor.accountId(), start, end);
        Page<VisitRepository.ExaminedVisitRow> rows = visitRepository.findExaminedVisits(
                doctor.accountId(), start, end, search, PageRequest.of(number, pageSize));

        PageResponse<ExaminedVisit> visits = new PageResponse<>(
                rows.getContent().stream().map(ExaminedPatientServiceImpl::toVisit).toList(),
                new PageResponse.PageMetadata(number, pageSize, rows.getTotalElements(), rows.getTotalPages()));
        return new ExaminedPatientListResponse(start, end, totals.getVisits(), totals.getPatients(), visits);
    }

    private static ExaminedVisit toVisit(VisitRepository.ExaminedVisitRow row) {
        return new ExaminedVisit(row.getVisitId(), row.getRegistrationCode(), row.getQueueDate(), row.getExaminedAt(),
                row.getStatus(), row.getPatientType(), row.getPatientId(), row.getPatientCode(), row.getPatientName(),
                row.getGender(), row.getDateOfBirth(), row.getRoomName(), row.getIcdCode(), row.getIcdName());
    }

    private static AppException invalid(String field, String message) {
        return new AppException(ErrorCode.REQUEST_PARAMETER_INVALID, Map.of(field, message));
    }
}
