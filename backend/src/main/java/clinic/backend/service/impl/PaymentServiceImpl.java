package clinic.backend.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import clinic.backend.dto.request.ConfirmPaymentRequest;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.PaymentHistoryResponse;
import clinic.backend.dto.response.ReceiptPrintResponse;
import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.dto.response.PaymentItemResponse;
import clinic.backend.dto.response.PaymentResponse;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Payment;
import clinic.backend.entity.PaymentItem;
import clinic.backend.entity.Room;
import clinic.backend.entity.Visit;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.PaymentMethod;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.PaymentType;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.PaymentCompletionService;
import clinic.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String PAYMENT_AUDIT_ENTITY = "payments";
    private static final int MIN_KEYWORD_LENGTH = 2;
    private static final Instant ALL_TIME_START = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant ALL_TIME_END = Instant.parse("9999-01-01T00:00:00Z");

    private final PaymentRepository paymentRepository;
    private final VisitRepository visitRepository;
    private final PatientRepository patientRepository;
    private final RoomRepository roomRepository;
    private final ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;
    private final PaymentCompletionService paymentCompletionService;
    private final AuditLogService auditLogService;
    private final StaffAccountRepository staffAccountRepository;
    private final ClinicFacilityRepository clinicFacilityRepository;
    private final Clock clock;

    // UC-69, UC-70: danh sach / tim kiem phieu thu (luon tra ve danh sach co phan trang)
    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPayments(
            PaymentStatus status,
            PaymentType type,
            String search,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    ) {
        // tu khoa it nhat 2 ky tu (MSG05)
        String keyword = search == null || search.isBlank() ? null : search.trim();
        if (keyword != null && keyword.length() < MIN_KEYWORD_LENGTH) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("search", "TOO_SHORT"));
        }
        Collection<PaymentStatus> statuses = status == null ? EnumSet.allOf(PaymentStatus.class) : EnumSet.of(status);
        return toResponses(paymentRepository.search(
                statuses, type, startOf(from, ALL_TIME_START), endOf(to, ALL_TIME_END), keyword, newestFirst(pageable)));
    }

    // khong chon cach sap xep thi phieu moi tao len dau (phieu vua dang ky khong bi day xuong trang cuoi)
    private static Pageable newestFirst(Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    // UC-73: lich su thanh toan + tong tien theo phuong thuc
    @Override
    @Transactional(readOnly = true)
    public PaymentHistoryResponse getHistory(
            UUID cashierAccountId,
            PaymentStatus status,
            PaymentType type,
            PaymentMethod method,
            String search,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    ) {
        // mac dinh hom nay; tu ngay sau den ngay thi bao loi
        LocalDate today = LocalDate.now(clock);
        LocalDate fromDate = from != null ? from : today;
        LocalDate toDate = to != null ? to : today;
        if (fromDate.isAfter(toDate)) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("dateRange", "INVALID_FORMAT"));
        }
        // tu khoa giong UC-70: it nhat 2 ky tu
        String keyword = search == null || search.isBlank() ? null : search.trim();
        if (keyword != null && keyword.length() < MIN_KEYWORD_LENGTH) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("search", "TOO_SHORT"));
        }
        // lich su chi gom phieu da thu va da huy
        if (status != null && status != PaymentStatus.PAID && status != PaymentStatus.CANCELLED) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("status", "INVALID_FORMAT"));
        }
        Collection<PaymentStatus> statuses = status == null
                ? EnumSet.of(PaymentStatus.PAID, PaymentStatus.CANCELLED)
                : EnumSet.of(status);
        Instant fromTime = startOf(fromDate, ALL_TIME_START);
        Instant toTime = endOf(toDate, ALL_TIME_END);

        Page<PaymentResponse> page = toResponses(paymentRepository
                .history(statuses, type, method, cashierAccountId, fromTime, toTime, keyword, pageable));
        List<PaymentHistoryResponse.MethodTotal> totals = status == PaymentStatus.CANCELLED
                ? List.of()
                : paymentRepository.historyTotals(type, method, cashierAccountId, fromTime, toTime, keyword).stream()
                        .map(row -> new PaymentHistoryResponse.MethodTotal(
                                row.getMethod(), row.getTotal(), row.getCount()))
                        .toList();
        BigDecimal grandTotal = totals.stream()
                .map(PaymentHistoryResponse.MethodTotal::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PaymentHistoryResponse(
                new PageResponse<>(page.getContent(), new PageResponse.PageMetadata(
                        page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages())),
                totals,
                grandTotal);
    }

    // dau ngay (khong chon ngay thi lay gia tri mac dinh)
    private Instant startOf(LocalDate date, Instant fallback) {
        return date == null ? fallback : date.atStartOfDay(clock.getZone()).toInstant();
    }

    // het ngay = dau ngay hom sau
    private Instant endOf(LocalDate date, Instant fallback) {
        return date == null ? fallback : date.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
    }

    // phieu phi kham dang cho thu
    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPendingExaminationPayments(
            Pageable pageable
    ) {
        return toResponses(paymentRepository.findByStatusAndType(
                PaymentStatus.PENDING,
                PaymentType.EXAMINATION_FEE,
                pageable
        ));
    }

    // chi tiet 1 phieu thu
    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        return toResponse(payment);
    }

    // UC-72: chuan bi du lieu in phieu thu
    @Override
    @Transactional
    public ReceiptPrintResponse prepareReceiptPrint(UUID cashierAccountId, UUID paymentId) {
        // tim phieu thu
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        // chi in phieu da thu hoac mien phi (BR-37)
        if (payment.getStatus() != PaymentStatus.PAID && payment.getStatus() != PaymentStatus.FEE_EXEMPT) {
            throw new AppException(ErrorCode.PAYMENT_NOT_PAID, Map.of("status", payment.getStatus().name()));
        }

                // da in roi thi lan nay la ban sao; lan nao cung ghi log
        boolean reprint = auditLogService.hasAction(AuditLogAction.PRINT, PAYMENT_AUDIT_ENTITY, paymentId);
        Visit visit = visitRepository.findById(payment.getVisitId()).orElse(null);
        // ghi log in
        auditLogService.recordBusinessChange(
                cashierAccountId, AuditLogAction.PRINT, PAYMENT_AUDIT_ENTITY, paymentId,
                visit != null ? visit.getSovaovien() : null,
                Map.of("receiptNumber", String.valueOf(payment.getReceiptNumber()), "reprint", reprint),
                clock.instant());

        // ten thu ngan da thu
        String cashierName = payment.getCashierAccountId() == null
                ? null
                : staffAccountRepository.findById(payment.getCashierAccountId())
                        .map(StaffAccount::getFullName)
                        .orElse(null);
        // ten phong kham in tren dau phieu
        String facilityName = clinicFacilityRepository.findCurrent()
                .map(ClinicFacility::getTenCskcb)
                .orElse(null);

        // tra du lieu de FE dung phieu, STT chi co voi phi kham
        return new ReceiptPrintResponse(
                reprint,
                facilityName,
                cashierName,
                visit != null ? visit.getDoituongbnLoai() : null,
                visit != null ? visit.getInsuranceCoverageRate() : null,
                visit != null && payment.getType() == PaymentType.EXAMINATION_FEE ? visit.getQueueNumber() : null,
                toResponse(payment));
    }

    // UC-71: xac nhan thu tien (tien mat / chuyen khoan)
    @Override
    @Transactional
    public PaymentResponse confirmPayment(
            UUID cashierAccountId,
            UUID paymentId,
            ConfirmPaymentRequest request
    ) {
        // khoa phieu de 2 thu ngan khong xac nhan cung luc
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        // phieu da thu hoac da huy thi bao loi
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new AppException(ErrorCode.PAYMENT_NOT_PENDING, Map.of(
                    "receiptNumber", payment.getReceiptNumber() != null
                            ? payment.getReceiptNumber()
                            : payment.getPaymentCode(),
                    "status", payment.getStatus().name()));
        }

        // luot BHYT chi thu sau khi bac si ket luan (muc huong da chot - BR-15, BR-26, BR-27)
        visitRepository.findById(payment.getVisitId())
                .filter(Visit::awaitsConclusionBeforePayment)
                .ifPresent(visit -> {
                    throw new AppException(ErrorCode.PAYMENT_AWAITS_CONCLUSION,
                            Map.of("paymentCode", payment.getPaymentCode()));
                });

        // ghi nhan da thu: phuong thuc, thu ngan, so bien lai
        String receiptNumber = "PT"
                + LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-"
                + payment.getPaymentCode();
        payment.markAsPaid(
                request.paymentMethod(),
                cashierAccountId,
                receiptNumber
        );
        paymentRepository.save(payment);

        // xu ly sau khi thu (dung chung voi PayOS): phi kham -> vao hang cho; phi CLS -> chia phong (BR-28)
        paymentCompletionService.completePaidReceipt(payment, cashierAccountId);

        return toResponse(payment);
    }

    // du lieu di kem phieu thu: luot kham, benh nhan, phong, chi dinh CLS (theo id)
    private record Lookups(
            Map<UUID, Visit> visits,
            Map<UUID, Patient> patients,
            Map<UUID, Room> rooms,
            Map<UUID, ParaclinicalOrderDetail> details
    ) {
    }

    // danh sach phieu: tai du lieu di kem cho ca trang trong vai cau truy van, khong goi DB rieng cho tung phieu
    private Page<PaymentResponse> toResponses(Page<Payment> payments) {
        List<Payment> content = payments.getContent();
        Map<UUID, Visit> visits = byId(visitRepository.findAllById(
                idsOf(content.stream().map(Payment::getVisitId))), Visit::getId);
        Map<UUID, Patient> patients = byId(patientRepository.findAllById(
                idsOf(visits.values().stream().map(Visit::getPatientId))), Patient::getId);
        Map<UUID, ParaclinicalOrderDetail> details = byId(paraclinicalOrderDetailRepository.findAllById(
                idsOf(content.stream().flatMap(payment -> orderDetailIdsOf(payment).stream()))),
                ParaclinicalOrderDetail::getId);
        Map<UUID, Room> rooms = byId(roomRepository.findAllById(idsOf(Stream.concat(
                visits.values().stream().map(Visit::getRoomId),
                details.values().stream().map(ParaclinicalOrderDetail::getRoomId)))), Room::getId);
        Lookups lookups = new Lookups(visits, patients, rooms, details);
        return payments.map(payment -> toResponse(payment, lookups));
    }

    // doi 1 phieu thu sang dang tra ve (kem thong tin luot kham, benh nhan, cac dong thu)
    private PaymentResponse toResponse(Payment payment) {
        Map<UUID, Visit> visits = new HashMap<>();
        Map<UUID, Patient> patients = new HashMap<>();
        Map<UUID, Room> rooms = new HashMap<>();
        Visit visit = visitRepository.findById(payment.getVisitId()).orElse(null);
        if (visit != null) {
            visits.put(visit.getId(), visit);
            patientRepository.findById(visit.getPatientId()).ifPresent(patient -> patients.put(patient.getId(), patient));
            roomRepository.findById(visit.getRoomId()).ifPresent(room -> rooms.put(room.getId(), room));
        }
        Map<UUID, ParaclinicalOrderDetail> details = byId(
                paraclinicalOrderDetailRepository.findAllById(orderDetailIdsOf(payment)), ParaclinicalOrderDetail::getId);
        rooms.putAll(byId(roomRepository.findAllById(idsOf(
                details.values().stream().map(ParaclinicalOrderDetail::getRoomId))), Room::getId));
        return toResponse(payment, new Lookups(visits, patients, rooms, details));
    }

    // chi dinh CLS cua cac dong thu (chi phieu thu phi CLS)
    private static List<UUID> orderDetailIdsOf(Payment payment) {
        if (payment.getType() != PaymentType.PARACLINICAL_FEE || payment.getItems() == null) {
            return List.of();
        }
        return payment.getItems().stream()
                .map(PaymentItem::getOrderDetailId)
                .filter(Objects::nonNull)
                .toList();
    }

    private static Set<UUID> idsOf(Stream<UUID> ids) {
        return ids.filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static <T> Map<UUID, T> byId(Iterable<T> items, Function<T, UUID> idOf) {
        Map<UUID, T> result = new HashMap<>();
        for (T item : items) {
            result.put(idOf.apply(item), item);
        }
        return result;
    }

    private PaymentResponse toResponse(Payment payment, Lookups lookups) {
        Visit visit = lookups.visits().get(payment.getVisitId());
        Patient patient = visit != null ? lookups.patients().get(visit.getPatientId()) : null;
        Room room = visit != null && visit.getRoomId() != null ? lookups.rooms().get(visit.getRoomId()) : null;
        Map<UUID, ParaclinicalOrderDetail> details = lookups.details();
        Map<UUID, Room> performingRooms = lookups.rooms();

        List<PaymentItemResponse> itemResponses = new ArrayList<>();
        if (payment.getItems() != null) {
            for (PaymentItem item : payment.getItems()) {
                ParaclinicalOrderDetail detail = item.getOrderDetailId() != null
                        ? details.get(item.getOrderDetailId())
                        : null;
                Room performingRoom = detail != null && detail.getRoomId() != null
                        ? performingRooms.get(detail.getRoomId())
                        : null;
                itemResponses.add(new PaymentItemResponse(
                        item.getId(),
                        item.getServiceId(),
                        item.getServiceCodeSnapshot(),
                        item.getTenchidinh(),
                        item.getSoluong(),
                        item.getDongia(),
                        item.getThanhtien(),
                        item.getBhytChitra(),
                        item.getBenhnhanphaitra(),
                        performingRoom != null ? performingRoom.getRoomName() : null,
                        performingRoom != null ? performingRoom.getLocation() : null,
                        detail != null ? detail.getQueueNumber() : null
                ));
            }
        }

        String genderText = null;
        if (patient != null) {
            genderText = switch (patient.getGioitinh()) {
                case 1 -> "Nam";
                case 2 -> "Nữ";
                default -> "Khác";
            };
        }

        return new PaymentResponse(
                payment.getId(),
                payment.getPaymentCode(),
                payment.getVisitId(),
                visit != null ? visit.getSovaovien() : null,
                patient != null ? patient.getId() : null,
                patient != null ? patient.getHoten() : "—",
                patient != null ? patient.getMabenhnhan() : "—",
                patient != null && patient.getNgaysinh() != null ? patient.getNgaysinh().toString() : null,
                genderText,
                patient != null ? patient.getSodienthoai() : null,
                room != null ? room.getRoomName() : null,
                payment.getType(),
                payment.getStatus(),
                payment.getTotalAmount(),
                payment.getInsuranceTotalAmount(),
                payment.getPatientTotalAmount(),
                payment.getPaymentMethod(),
                payment.getReceiptNumber(),
                payment.getPaidAt(),
                payment.getCreatedAt(),
                payment.getNote(),
                payment.getPayosOrderCode(),
                payment.getPayosCheckoutUrl(),
                payment.getPayosQrCode(),
                itemResponses,
                payment.getStatus() == PaymentStatus.PENDING && visit != null
                        && visit.awaitsConclusionBeforePayment(),
                visit != null && payment.getType() == PaymentType.EXAMINATION_FEE ? visit.getQueueNumber() : null
        );
    }
}
