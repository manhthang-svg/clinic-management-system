package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import clinic.backend.dto.response.DashboardStatisticsResponse;
import clinic.backend.dto.response.DashboardStatisticsResponse.DailyVisits;
import clinic.backend.dto.response.DashboardStatisticsResponse.DiagnosisCount;
import clinic.backend.dto.response.DashboardStatisticsResponse.RevenueSummary;
import clinic.backend.dto.response.DashboardStatisticsResponse.RoomVisits;
import clinic.backend.dto.response.DashboardStatisticsResponse.VisitSummary;
import clinic.backend.dto.response.DepartmentStatisticsResponse;
import clinic.backend.dto.response.DepartmentStatisticsResponse.DepartmentRow;
import clinic.backend.dto.response.DepartmentStatisticsResponse.RoomRow;
import clinic.backend.dto.response.DiagnosisStatisticsResponse;
import clinic.backend.dto.response.DoctorStatisticsResponse;
import clinic.backend.dto.response.DoctorStatisticsResponse.DoctorRow;
import clinic.backend.dto.response.ParaclinicalStatisticsResponse;
import clinic.backend.dto.response.ParaclinicalStatisticsResponse.GroupCount;
import clinic.backend.dto.response.ParaclinicalStatisticsResponse.RoomCount;
import clinic.backend.dto.response.ParaclinicalStatisticsResponse.ServiceCount;
import clinic.backend.dto.response.ParaclinicalStatisticsResponse.TypeCount;
import clinic.backend.dto.response.RevenueStatisticsResponse;
import clinic.backend.dto.response.RevenueStatisticsResponse.Amounts;
import clinic.backend.dto.response.RevenueStatisticsResponse.MethodAmounts;
import clinic.backend.dto.response.RevenueStatisticsResponse.PeriodAmounts;
import clinic.backend.dto.response.RevenueStatisticsResponse.TypeAmounts;
import clinic.backend.dto.response.VisitStatisticsResponse;
import clinic.backend.dto.response.VisitStatisticsResponse.VisitCounts;
import clinic.backend.dto.response.VisitStatisticsResponse.VisitPeriod;
import clinic.backend.entity.Department;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.Room;
import clinic.backend.entity.ServiceGroup;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.enums.StatisticsGroupBy;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.PaymentMethod;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.PaymentType;
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

    // khoang thoi gian toi da 1 lan xem (366 ngay)
    static final long MAX_PERIOD_DAYS = 366;
    static final int TOP_LIMIT = 5;
    static final int DEFAULT_DIAGNOSIS_LIMIT = 10;
    static final int MAX_DIAGNOSIS_LIMIT = 100;

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<VisitStatus> IN_PROGRESS = EnumSet.of(
            VisitStatus.WAITING_FEE_PAYMENT,
            VisitStatus.WAITING,
            VisitStatus.IN_EXAMINATION,
            VisitStatus.WAITING_PARACLINICAL,
            VisitStatus.WAITING_CONCLUSION);

    private final StatisticsRepository statisticsRepository;
    private final Clock clock;

    // UC-26: so lieu trang Tong quan (luot kham, benh nhan, CLS, doanh thu, bieu do)
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

    // UC-27: thong ke luot kham theo ngay / thang
    @Override
    @Transactional(readOnly = true)
    public VisitStatisticsResponse getVisitStatistics(LocalDate from, LocalDate to, StatisticsGroupBy groupBy) {
        LocalDate start = resolveFrom(from);
        LocalDate end = resolveTo(to);
        validatePeriod(start, end);
        StatisticsGroupBy bucket = groupBy != null ? groupBy : StatisticsGroupBy.DAY;

        // tao san tung ngay / thang (ky khong co luot van hien 0), roi cong don tung dong
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

    // UC-28: thong ke theo bac si
    @Override
    @Transactional(readOnly = true)
    public DoctorStatisticsResponse getDoctorStatistics(LocalDate from, LocalDate to) {
        LocalDate start = resolveFrom(from);
        LocalDate end = resolveTo(to);
        validatePeriod(start, end);

        Map<UUID, long[]> byDoctor = new HashMap<>(); // completed, inProgress, abandoned, cancelled
        for (StatisticsRepository.DoctorStatusCount row
                : statisticsRepository.countVisitsByDoctorAndStatus(start, end)) {
            long[] counts = byDoctor.computeIfAbsent(row.getDoctorId(), id -> new long[4]);
            int slot = switch (row.getStatus()) {
                case COMPLETED -> 0;
                case ABANDONED -> 2;
                case CANCELLED -> 3;
                default -> 1;
            };
            counts[slot] += row.getTotal();
        }
        Map<UUID, Long> patients = new HashMap<>();
        statisticsRepository.countPatientsByDoctor(start, end)
                .forEach(row -> patients.put(row.getDoctorId(), row.getTotal()));

        // tranh IN rong: dung 1 id ngau nhien khong khop ai
        Set<UUID> examiners = byDoctor.isEmpty() ? Set.of(UUID.randomUUID()) : byDoctor.keySet();
        List<DoctorRow> rows = new ArrayList<>();
        for (StaffAccount account : statisticsRepository.doctorsOrAccounts(examiners)) {
            long[] counts = byDoctor.getOrDefault(account.getId(), new long[4]);
            boolean active = account.getStatus() == AccountStatus.ACTIVE;
            long visits = counts[0] + counts[1] + counts[2] + counts[3];
            if (!active && visits == 0) {
                continue; // a locked or deactivated Doctor without visits in the period is not listed
            }
            rows.add(new DoctorRow(account.getId(), displayName(account), active, visits,
                    patients.getOrDefault(account.getId(), 0L), counts[0], counts[1], counts[2], counts[3]));
        }
        rows.sort(Comparator.comparingLong(DoctorRow::visits).reversed()
                .thenComparing(DoctorRow::doctorName, Comparator.nullsLast(Comparator.naturalOrder())));

        long examined = rows.stream().mapToLong(DoctorRow::visits).sum();
        return new DoctorStatisticsResponse(
                start, end, examined, statisticsRepository.countVisitsWithoutDoctor(start, end), rows);
    }

    // UC-29: thong ke theo khoa / phong (luot kham phong kham, dich vu phong CLS)
    @Override
    @Transactional(readOnly = true)
    public DepartmentStatisticsResponse getDepartmentStatistics(LocalDate from, LocalDate to) {
        LocalDate start = resolveFrom(from);
        LocalDate end = resolveTo(to);
        validatePeriod(start, end);
        Instant startInstant = start.atStartOfDay(CLINIC_ZONE).toInstant();
        Instant endInstant = end.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();

        Map<UUID, long[]> visitsByRoom = new HashMap<>(); // total, completed, inProgress
        for (StatisticsRepository.RoomVisitCount row : statisticsRepository.countVisitsByRoomAndStatus(start, end)) {
            long[] counts = visitsByRoom.computeIfAbsent(row.getRoomId(), id -> new long[3]);
            counts[0] += row.getTotal();
            if (row.getStatus() == VisitStatus.COMPLETED) {
                counts[1] += row.getTotal();
            } else if (IN_PROGRESS.contains(row.getStatus())) {
                counts[2] += row.getTotal();
            }
        }
        Map<UUID, long[]> servicesByRoom = new HashMap<>(); // total, performed, waiting, cancelled
        for (StatisticsRepository.RoomServiceCount row
                : statisticsRepository.countServicesByRoomAndStatus(startInstant, endInstant)) {
            long[] counts = servicesByRoom.computeIfAbsent(row.getRoomId(), id -> new long[4]);
            int slot = switch (row.getStatus()) {
                case PENDING_RESULT, COMPLETED -> 1;
                case WAITING, IN_PROGRESS -> 2;
                case CANCELLED, ABANDONED -> 3;
                case PENDING_PAYMENT -> -1; // never released; not workload
            };
            if (slot > 0) {
                counts[0] += row.getTotal();
                counts[slot] += row.getTotal();
            }
        }

        Map<UUID, List<RoomRow>> roomsByDepartment = new HashMap<>();
        for (Room room : statisticsRepository.allRooms()) {
            long[] visits = visitsByRoom.getOrDefault(room.getId(), new long[3]);
            long[] services = servicesByRoom.getOrDefault(room.getId(), new long[4]);
            boolean active = room.getStatus() == ActivationStatus.ACTIVE;
            if (!active && visits[0] == 0 && services[0] == 0) {
                continue; // an inactive room without work in the period is not listed
            }
            roomsByDepartment.computeIfAbsent(room.getDepartmentId(), id -> new ArrayList<>()).add(new RoomRow(
                    room.getId(), room.getRoomCode(), room.getRoomName(), room.getType(), active,
                    visits[0], visits[1], visits[2], services[0], services[1], services[2], services[3]));
        }

        Comparator<RoomRow> busiestRoom = Comparator
                .comparingLong((RoomRow room) -> room.visits() + room.services()).reversed()
                .thenComparing(RoomRow::roomCode, Comparator.nullsLast(Comparator.naturalOrder()));
        List<DepartmentRow> departments = new ArrayList<>();
        for (Department department : statisticsRepository.allDepartments()) {
            List<RoomRow> rooms = new ArrayList<>(roomsByDepartment.getOrDefault(department.getId(), List.of()));
            if (!department.isActive() && rooms.isEmpty()) {
                continue;
            }
            rooms.sort(busiestRoom);
            departments.add(new DepartmentRow(
                    department.getId(), department.getDepartmentCode(), department.getDepartmentName(),
                    department.getType(), department.isActive(),
                    rooms.stream().mapToLong(RoomRow::visits).sum(),
                    rooms.stream().mapToLong(RoomRow::completedVisits).sum(),
                    rooms.stream().mapToLong(RoomRow::services).sum(),
                    rooms.stream().mapToLong(RoomRow::performedServices).sum(),
                    rooms));
        }
        departments.sort(Comparator
                .comparingLong((DepartmentRow row) -> row.visits() + row.services()).reversed()
                .thenComparing(DepartmentRow::departmentCode, Comparator.nullsLast(Comparator.naturalOrder())));

        return new DepartmentStatisticsResponse(start, end,
                departments.stream().mapToLong(DepartmentRow::visits).sum(),
                departments.stream().mapToLong(DepartmentRow::services).sum(),
                departments);
    }

    // UC-30: thong ke dich vu CLS da thuc hien theo loai, nhom, dich vu, phong
    @Override
    @Transactional(readOnly = true)
    public ParaclinicalStatisticsResponse getParaclinicalStatistics(LocalDate from, LocalDate to) {
        LocalDate start = resolveFrom(from);
        LocalDate end = resolveTo(to);
        validatePeriod(start, end);
        Instant startInstant = start.atStartOfDay(CLINIC_ZONE).toInstant();
        Instant endInstant = end.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();

        List<StatisticsRepository.PerformedServiceCount> rows =
                statisticsRepository.countPerformedServices(startInstant, endInstant);
        if (rows.isEmpty()) {
            return new ParaclinicalStatisticsResponse(start, end, 0, 0, List.of(), List.of(), List.of(), List.of());
        }

        Map<UUID, MedicalService> services = statisticsRepository
                .servicesByIds(rows.stream().map(StatisticsRepository.PerformedServiceCount::getServiceId)
                        .collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MedicalService::getId, Function.identity()));
        Set<UUID> groupIds = services.values().stream().map(MedicalService::getServiceGroupId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, ServiceGroup> groups = groupIds.isEmpty() ? Map.of() : statisticsRepository
                .serviceGroupsByIds(groupIds)
                .stream().collect(Collectors.toMap(ServiceGroup::getId, Function.identity()));
        Set<UUID> roomIds = rows.stream().map(StatisticsRepository.PerformedServiceCount::getRoomId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Room> rooms = roomIds.isEmpty() ? Map.of() : statisticsRepository.roomsByIds(roomIds)
                .stream().collect(Collectors.toMap(Room::getId, Function.identity()));

        // moi bo dem = {da thuc hien, da co ket qua}
        long[] total = new long[2];
        Map<MedicalServiceType, long[]> byType = new EnumMap<>(MedicalServiceType.class);
        Map<UUID, long[]> byGroup = new HashMap<>();
        Map<UUID, long[]> byService = new HashMap<>();
        Map<UUID, long[]> byRoom = new HashMap<>();
        for (StatisticsRepository.PerformedServiceCount row : rows) {
            long performed = row.getTotal();
            long withResult = row.getStatus() == ParaclinicalOrderDetailStatus.COMPLETED ? row.getTotal() : 0;
            MedicalService service = services.get(row.getServiceId());
            add(total, performed, withResult);
            add(byService.computeIfAbsent(row.getServiceId(), id -> new long[2]), performed, withResult);
            if (service != null) {
                add(byType.computeIfAbsent(service.getType(), type -> new long[2]), performed, withResult);
                if (service.getServiceGroupId() != null) {
                    add(byGroup.computeIfAbsent(service.getServiceGroupId(), id -> new long[2]), performed, withResult);
                }
            }
            if (row.getRoomId() != null) {
                add(byRoom.computeIfAbsent(row.getRoomId(), id -> new long[2]), performed, withResult);
            }
        }

        List<TypeCount> types = byType.entrySet().stream()
                .map(entry -> new TypeCount(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .sorted(Comparator.comparingLong(TypeCount::performed).reversed()
                        .thenComparing(TypeCount::type))
                .toList();
        List<GroupCount> groupRows = byGroup.entrySet().stream()
                .map(entry -> {
                    ServiceGroup group = groups.get(entry.getKey());
                    return new GroupCount(entry.getKey(),
                            group != null ? group.getServiceGroupCode() : null,
                            group != null ? group.getServiceGroupName() : null,
                            entry.getValue()[0], entry.getValue()[1]);
                })
                .sorted(Comparator.comparingLong(GroupCount::performed).reversed()
                        .thenComparing(GroupCount::groupCode, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<ServiceCount> serviceRows = byService.entrySet().stream()
                .map(entry -> {
                    MedicalService service = services.get(entry.getKey());
                    ServiceGroup group = service == null || service.getServiceGroupId() == null
                            ? null : groups.get(service.getServiceGroupId());
                    return new ServiceCount(entry.getKey(),
                            service != null ? service.getCode() : null,
                            service != null ? service.getTenchidinh() : null,
                            service != null ? service.getType() : null,
                            group != null ? group.getServiceGroupName() : null,
                            entry.getValue()[0], entry.getValue()[1]);
                })
                .sorted(Comparator.comparingLong(ServiceCount::performed).reversed()
                        .thenComparing(ServiceCount::serviceCode, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<RoomCount> roomRows = byRoom.entrySet().stream()
                .map(entry -> {
                    Room room = rooms.get(entry.getKey());
                    return new RoomCount(entry.getKey(),
                            room != null ? room.getRoomCode() : null,
                            room != null ? room.getRoomName() : null,
                            entry.getValue()[0], entry.getValue()[1]);
                })
                .sorted(Comparator.comparingLong(RoomCount::performed).reversed()
                        .thenComparing(RoomCount::roomCode, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return new ParaclinicalStatisticsResponse(
                start, end, total[0], total[1], types, groupRows, serviceRows, roomRows);
    }

    private static void add(long[] counter, long performed, long withResult) {
        counter[0] += performed;
        counter[1] += withResult;
    }

    // UC-31: doanh thu da thu (chi phieu da thu, theo ngay thu), tach BHYT / benh nhan tra
    @Override
    @Transactional(readOnly = true)
    public RevenueStatisticsResponse getRevenueStatistics(LocalDate from, LocalDate to, StatisticsGroupBy groupBy) {
        LocalDate start = resolveFrom(from);
        LocalDate end = resolveTo(to);
        validatePeriod(start, end);
        StatisticsGroupBy bucket = groupBy != null ? groupBy : StatisticsGroupBy.DAY;
        Instant startInstant = start.atStartOfDay(CLINIC_ZONE).toInstant();
        Instant endInstant = end.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();

        Map<LocalDate, Amounts> byPeriod = new LinkedHashMap<>();
        for (LocalDate key = bucketOf(start, bucket); !key.isAfter(end); key = next(key, bucket)) {
            byPeriod.put(key, Amounts.zero());
        }
        Amounts summary = Amounts.zero();
        Map<PaymentType, Amounts> byType = new EnumMap<>(PaymentType.class);
        Map<Optional<PaymentMethod>, Amounts> byMethod = new LinkedHashMap<>();
        for (StatisticsRepository.RevenueRow row
                : statisticsRepository.revenueByDayTypeAndMethod(startInstant, endInstant)) {
            long payments = row.getPayments();
            BigDecimal total = orZero(row.getTotalAmount());
            BigDecimal insurance = orZero(row.getInsuranceAmount());
            BigDecimal patient = orZero(row.getPatientAmount());
            summary = summary.plus(payments, total, insurance, patient);
            byPeriod.computeIfPresent(bucketOf(LocalDate.parse(row.getDay()), bucket),
                    (key, amounts) -> amounts.plus(payments, total, insurance, patient));
            if (row.getType() != null) {
                byType.merge(PaymentType.valueOf(row.getType()),
                        Amounts.zero().plus(payments, total, insurance, patient), StatisticsServiceImpl::sumAmounts);
            }
            Optional<PaymentMethod> method = Optional.ofNullable(row.getMethod()).map(PaymentMethod::valueOf);
            byMethod.merge(method, Amounts.zero().plus(payments, total, insurance, patient),
                    StatisticsServiceImpl::sumAmounts);
        }

        List<TypeAmounts> types = byType.entrySet().stream()
                .map(entry -> new TypeAmounts(entry.getKey(), entry.getValue()))
                .toList();
        List<MethodAmounts> methods = byMethod.entrySet().stream()
                .map(entry -> new MethodAmounts(entry.getKey().orElse(null), entry.getValue()))
                .sorted(Comparator.comparing((MethodAmounts row) -> row.amounts().patientAmount()).reversed())
                .toList();
        List<PeriodAmounts> series = new ArrayList<>(byPeriod.size());
        byPeriod.forEach((period, amounts) -> series.add(new PeriodAmounts(period, amounts)));
        return new RevenueStatisticsResponse(start, end, bucket, summary, types, methods, series);
    }

    private static BigDecimal orZero(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    // cong 2 tong tien (gop dong cung loai phieu / cung phuong thuc)
    private static Amounts sumAmounts(Amounts left, Amounts right) {
        return left.plus(right.payments(), right.totalAmount(), right.insuranceAmount(), right.patientAmount());
    }

    // UC-32: benh thuong gap theo ma ICD-10 (theo ngay ket luan)
    @Override
    @Transactional(readOnly = true)
    public DiagnosisStatisticsResponse getDiagnosisStatistics(LocalDate from, LocalDate to, Integer limit) {
        LocalDate start = resolveFrom(from);
        LocalDate end = resolveTo(to);
        validatePeriod(start, end);
        int size = limit != null ? limit : DEFAULT_DIAGNOSIS_LIMIT;
        if (size < 1 || size > MAX_DIAGNOSIS_LIMIT) {
            throw new AppException(ErrorCode.REQUEST_PARAMETER_INVALID,
                    Map.of("limit", "The limit must be between 1 and " + MAX_DIAGNOSIS_LIMIT + "."));
        }
        Instant startInstant = start.atStartOfDay(CLINIC_ZONE).toInstant();
        Instant endInstant = end.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();

        StatisticsRepository.ConclusionTotals totals = statisticsRepository.conclusionTotals(startInstant, endInstant);
        if (totals.getWithIcd() == 0) {
            return new DiagnosisStatisticsResponse(start, end, size, totals.getConclusions(), 0,
                    totals.getConclusions(), 0, List.of(), List.of(), List.of());
        }
        PageRequest top = PageRequest.of(0, size);
        return new DiagnosisStatisticsResponse(
                start, end, size,
                totals.getConclusions(),
                totals.getWithIcd(),
                totals.getConclusions() - totals.getWithIcd(),
                totals.getDistinctCodes(),
                statisticsRepository.topMainDiagnoses(startInstant, endInstant, top).stream()
                        .map(row -> new DiagnosisStatisticsResponse.DiagnosisRow(
                                row.getCode(), row.getName(), row.getCases(), row.getPatients()))
                        .toList(),
                statisticsRepository.topDiagnosisCategories(startInstant, endInstant, top).stream()
                        .map(row -> new DiagnosisStatisticsResponse.CategoryRow(
                                row.getCode(), row.getName(), row.getCases()))
                        .toList(),
                statisticsRepository.topSecondaryDiagnoses(startInstant, endInstant, top).stream()
                        .map(row -> new DiagnosisStatisticsResponse.SecondaryRow(
                                row.getCode(), row.getName(), row.getCases()))
                        .toList());
    }

    // ten hien thi: hoc ham + ho ten
    private static String displayName(StaffAccount account) {
        String title = account.getAcademicTitle();
        return title == null || title.isBlank() ? account.getFullName() : title.strip() + " " + account.getFullName();
    }

    // khong chon tu ngay thi lay dau thang
    private LocalDate resolveFrom(LocalDate from) {
        return from != null ? from : today().withDayOfMonth(1);
    }

    // khong chon den ngay thi lay hom nay
    private LocalDate resolveTo(LocalDate to) {
        return to != null ? to : today();
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(CLINIC_ZONE));
    }

    // ngay dai dien cua nhom (theo thang thi la ngay 1)
    private static LocalDate bucketOf(LocalDate day, StatisticsGroupBy groupBy) {
        return groupBy == StatisticsGroupBy.MONTH ? day.withDayOfMonth(1) : day;
    }

    private static LocalDate next(LocalDate key, StatisticsGroupBy groupBy) {
        return groupBy == StatisticsGroupBy.MONTH ? key.plusMonths(1) : key.plusDays(1);
    }

    // bo dem luot kham cua 1 ngay / thang (UC-27)
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

    // tu ngay khong sau den ngay, toi da 366 ngay
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

    // tong luot kham theo ket qua va doi tuong (cho trang Tong quan)
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

    // luot kham tung ngay, ngay khong co luot thi 0
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
