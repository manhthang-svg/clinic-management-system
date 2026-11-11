package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import clinic.backend.dto.response.DashboardStatisticsResponse;
import clinic.backend.dto.response.DashboardStatisticsResponse.DailyVisits;
import clinic.backend.dto.response.DashboardStatisticsResponse.DiagnosisCount;
import clinic.backend.dto.response.DashboardStatisticsResponse.RevenueSummary;
import clinic.backend.dto.response.DashboardStatisticsResponse.RoomVisits;
import clinic.backend.dto.response.DashboardStatisticsResponse.VisitSummary;
import clinic.backend.dto.response.VisitStatisticsResponse;
import clinic.backend.dto.response.VisitStatisticsResponse.VisitCounts;
import clinic.backend.dto.response.VisitStatisticsResponse.VisitPeriod;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.StatisticsGroupBy;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.StatisticsRepository;
import clinic.backend.repository.StatisticsRepository.RevenueTotals;
import clinic.backend.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    /** Longest period one dashboard request may cover (one leap year). */
    static final long MAX_PERIOD_DAYS = 366;
    static final int TOP_LIMIT = 5;

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<VisitStatus> IN_PROGRESS = EnumSet.of(
            VisitStatus.WAITING_FEE_PAYMENT,
            VisitStatus.WAITING,
            VisitStatus.IN_EXAMINATION,
            VisitStatus.WAITING_PARACLINICAL,
            VisitStatus.WAITING_CONCLUSION);

    private final StatisticsRepository statisticsRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public DashboardStatisticsResponse getDashboard(LocalDate from, LocalDate to) {
        LocalDate start = resolveFrom(from);
        LocalDate end = resolveTo(to);
        validatePeriod(start, end);

        Instant startInstant = start.atStartOfDay(CLINIC_ZONE).toInstant();
        Instant endInstant = end.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();
        PageRequest top = PageRequest.of(0, TOP_LIMIT);

        return new DashboardStatisticsResponse(
                start,
                end,
                visitSummary(start, end),
                statisticsRepository.countDistinctPatients(start, end),
                statisticsRepository.countParaclinicalServices(
                        ParaclinicalOrderDetailStatus.COMPLETED, startInstant, endInstant),
                revenue(statisticsRepository.sumRevenue(PaymentStatus.PAID, startInstant, endInstant)),
                dailyVisits(start, end),
                statisticsRepository.topRoomsByVisits(start, end, top).stream()
                        .map(r -> new RoomVisits(r.getRoomId(), r.getRoomName(), r.getTotal()))
                        .toList(),
                statisticsRepository.topDiagnoses(startInstant, endInstant, top).stream()
                        .map(d -> new DiagnosisCount(d.getCode(), d.getName(), d.getTotal()))
                        .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public VisitStatisticsResponse getVisitStatistics(LocalDate from, LocalDate to, StatisticsGroupBy groupBy) {
        LocalDate start = resolveFrom(from);
        LocalDate end = resolveTo(to);
        validatePeriod(start, end);
        StatisticsGroupBy bucket = groupBy != null ? groupBy : StatisticsGroupBy.DAY;

        Map<LocalDate, VisitCounter> byPeriod = new LinkedHashMap<>();
        for (LocalDate key = bucketOf(start, bucket); !key.isAfter(end); key = next(key, bucket)) {
            byPeriod.put(key, new VisitCounter());
        }
        VisitCounter summary = new VisitCounter();
        for (StatisticsRepository.VisitBreakdown row : statisticsRepository.visitBreakdown(start, end)) {
            summary.add(row);
            VisitCounter counter = byPeriod.get(bucketOf(row.getDay(), bucket));
            if (counter != null) {
                counter.add(row);
            }
        }

        List<VisitPeriod> series = new ArrayList<>(byPeriod.size());
        byPeriod.forEach((period, counter) -> series.add(new VisitPeriod(period, counter.toCounts())));
        return new VisitStatisticsResponse(start, end, bucket, summary.toCounts(), series);
    }

    private LocalDate resolveFrom(LocalDate from) {
        return from != null ? from : today().withDayOfMonth(1);
    }

    private LocalDate resolveTo(LocalDate to) {
        return to != null ? to : today();
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(CLINIC_ZONE));
    }

    private static LocalDate bucketOf(LocalDate day, StatisticsGroupBy groupBy) {
        return groupBy == StatisticsGroupBy.MONTH ? day.withDayOfMonth(1) : day;
    }

    private static LocalDate next(LocalDate key, StatisticsGroupBy groupBy) {
        return groupBy == StatisticsGroupBy.MONTH ? key.plusMonths(1) : key.plusDays(1);
    }

    /** Running totals for one bucket of UC-27. */
    private static final class VisitCounter {
        private long total;
        private long completed;
        private long inProgress;
        private long cancelled;
        private long abandoned;
        private long insurance;
        private long selfPay;
        private long followUp;
        private long priority;

        void add(StatisticsRepository.VisitBreakdown row) {
            long n = row.getTotal();
            total += n;
            if (row.getStatus() == VisitStatus.COMPLETED) {
                completed += n;
            } else if (row.getStatus() == VisitStatus.CANCELLED) {
                cancelled += n;
            } else if (row.getStatus() == VisitStatus.ABANDONED) {
                abandoned += n;
            } else if (IN_PROGRESS.contains(row.getStatus())) {
                inProgress += n;
            }
            if (row.getPatientType() == VisitDoituongbnLoai.BHYT) {
                insurance += n;
            } else if (row.getPatientType() == VisitDoituongbnLoai.DV) {
                selfPay += n;
            }
            if (row.getPriority()) {
                priority += n;
            }
            followUp += row.getFollowUps();
        }

        VisitCounts toCounts() {
            return new VisitCounts(total, completed, inProgress, cancelled, abandoned, insurance, selfPay,
                    followUp, priority);
        }
    }

    private static void validatePeriod(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw new AppException(ErrorCode.REQUEST_PARAMETER_INVALID,
                    Map.of("from", "The start date must not be after the end date."));
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_PERIOD_DAYS) {
            throw new AppException(ErrorCode.REQUEST_PARAMETER_INVALID,
                    Map.of("to", "The period must not exceed " + MAX_PERIOD_DAYS + " days."));
        }
    }

    private VisitSummary visitSummary(LocalDate start, LocalDate end) {
        long total = 0;
        long completed = 0;
        long inProgress = 0;
        long cancelled = 0;
        long abandoned = 0;
        for (StatisticsRepository.StatusCount row : statisticsRepository.countVisitsByStatus(start, end)) {
            total += row.getTotal();
            if (row.getStatus() == VisitStatus.COMPLETED) {
                completed += row.getTotal();
            } else if (row.getStatus() == VisitStatus.CANCELLED) {
                cancelled += row.getTotal();
            } else if (row.getStatus() == VisitStatus.ABANDONED) {
                abandoned += row.getTotal();
            } else if (IN_PROGRESS.contains(row.getStatus())) {
                inProgress += row.getTotal();
            }
        }

        long insurance = 0;
        long selfPay = 0;
        for (StatisticsRepository.PatientTypeCount row
                : statisticsRepository.countVisitsByPatientType(start, end)) {
            if (row.getPatientType() == VisitDoituongbnLoai.BHYT) {
                insurance += row.getTotal();
            } else if (row.getPatientType() == VisitDoituongbnLoai.DV) {
                selfPay += row.getTotal();
            }
        }
        return new VisitSummary(total, completed, inProgress, cancelled, abandoned, insurance, selfPay);
    }

    private List<DailyVisits> dailyVisits(LocalDate start, LocalDate end) {
        Map<LocalDate, Long> byDay = new HashMap<>();
        for (StatisticsRepository.DayCount row : statisticsRepository.countVisitsByDay(start, end)) {
            byDay.put(row.getDay(), row.getTotal());
        }
        List<DailyVisits> days = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            days.add(new DailyVisits(day, byDay.getOrDefault(day, 0L)));
        }
        return days;
    }

    private static RevenueSummary revenue(RevenueTotals totals) {
        return new RevenueSummary(
                totals.getPaymentCount(),
                totals.getTotalAmount(),
                totals.getInsuranceAmount(),
                totals.getPatientAmount());
    }
}
