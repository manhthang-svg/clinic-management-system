package clinic.backend.service.impl;

import clinic.backend.dto.request.CancelParaclinicalServiceRequest;
import clinic.backend.dto.request.CreateParaclinicalOrderRequest;
import clinic.backend.dto.response.ParaclinicalCancellationResponse;
import clinic.backend.dto.response.ParaclinicalOrderResponse;
import clinic.backend.dto.response.ParaclinicalServiceOptionResponse;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Payment;
import clinic.backend.entity.PaymentItem;
import clinic.backend.entity.Room;
import clinic.backend.entity.ServiceGroup;
import clinic.backend.entity.Visit;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.ParaclinicalOrderStatus;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.ParaclinicalOrderRepository;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.ServiceGroupRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.InsuranceCoverageService;
import clinic.backend.service.ParaclinicalOrderService;
import clinic.backend.service.ParaclinicalProgressService;
import clinic.backend.service.PaymentCompletionService;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ParaclinicalOrderServiceImpl implements ParaclinicalOrderService {

    private static final List<MedicalServiceType> PARACLINICAL_TYPES =
            List.of(MedicalServiceType.LAB, MedicalServiceType.IMAGING, MedicalServiceType.FUNCTIONAL);
    private static final String OUTPATIENT_RECORD = "NGOAITRU";

    private final VisitRepository visitRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final ServiceGroupRepository serviceGroupRepository;
    private final RoomRepository roomRepository;
    private final PaymentRepository paymentRepository;
    private final ParaclinicalOrderRepository paraclinicalOrderRepository;
    private final ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;
    private final AuditLogService auditLogService;
    private final PaymentCompletionService paymentCompletionService;
    private final InsuranceCoverageService insuranceCoverageService;
    private final ParaclinicalProgressService paraclinicalProgressService;
    private final ParaclinicalRoomDuty paraclinicalRoomDuty;
    private final UuidV7Generator uuidV7Generator;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<ParaclinicalServiceOptionResponse> listOrderableServices() {
        // UC-49 bước 2: chỉ dịch vụ CLS đang hoạt động, kèm giá dịch vụ và giá BHYT (BR-08, BR-10)
        Map<UUID, String> groupNames = serviceGroupRepository.findAll().stream()
                .collect(Collectors.toMap(ServiceGroup::getId, ServiceGroup::getServiceGroupName));
        // I1-D47: các phòng đang hoạt động của nhóm dịch vụ, chọn sẵn phòng ít người chờ nhất (BR-50-01).
        // Số truy vấn cố định (không phụ thuộc số phòng, số nhóm) để màn hình chỉ định mở nhanh.
        Map<UUID, List<ParaclinicalServiceOptionResponse.RoomOption>> roomsByGroup = activeRoomOptions();
        return medicalServiceRepository
                .findAllByStatusAndTypeInOrderByTenchidinhAsc(ActivationStatus.ACTIVE, PARACLINICAL_TYPES)
                .stream()
                .map(service -> {
                    List<ParaclinicalServiceOptionResponse.RoomOption> rooms = service.getServiceGroupId() == null
                            ? List.of()
                            : roomsByGroup.getOrDefault(service.getServiceGroupId(), List.of());
                    return new ParaclinicalServiceOptionResponse(
                            service.getId(),
                            service.getCode(),
                            service.getTenchidinh(),
                            service.getType(),
                            service.getServiceGroupId(),
                            groupNames.get(service.getServiceGroupId()),
                            service.getClinicPrice(),
                            service.getInsurancePrice(),
                            rooms,
                            leastBusy(rooms));
                })
                .toList();
    }

    /**
     * Active paraclinical rooms by service group, in room-code order, with their waiting counts: two queries in
     * all instead of one per group and one per room.
     */
    private Map<UUID, List<ParaclinicalServiceOptionResponse.RoomOption>> activeRoomOptions() {
        List<Room> rooms = roomRepository
                .findByTypeAndStatusOrderByRoomCodeAsc(RoomType.PARACLINICAL, ActivationStatus.ACTIVE)
                .stream()
                .filter(room -> room.getServiceGroupId() != null)
                .toList();
        if (rooms.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> waiting = paraclinicalOrderDetailRepository
                .countByRoomIdsAndStatus(rooms.stream().map(Room::getId).toList(), ParaclinicalOrderDetailStatus.WAITING)
                .stream()
                .collect(Collectors.toMap(
                        ParaclinicalOrderDetailRepository.RoomCount::getRoomId,
                        ParaclinicalOrderDetailRepository.RoomCount::getTotal));
        Set<UUID> staffed = paraclinicalRoomDuty.staffedRooms(clock.instant());
        return rooms.stream().collect(Collectors.groupingBy(
                Room::getServiceGroupId,
                LinkedHashMap::new,
                Collectors.mapping(room -> new ParaclinicalServiceOptionResponse.RoomOption(
                        room.getId(), room.getRoomCode(), room.getRoomName(), room.getLocation(),
                        waiting.getOrDefault(room.getId(), 0L), staffed.contains(room.getId())),
                        Collectors.toList())));
    }

    /**
     * The preselected room: one with staff on duty now first, then the fewest waiting, then the lower room code.
     * Without any staffed room the same order applies to all rooms ({@link PaymentCompletionService#leastBusyRoom}).
     */
    private static UUID leastBusy(List<ParaclinicalServiceOptionResponse.RoomOption> rooms) {
        return rooms.stream()
                .min(Comparator
                        .comparing((ParaclinicalServiceOptionResponse.RoomOption room) -> !room.onDuty())
                        .thenComparingLong(ParaclinicalServiceOptionResponse.RoomOption::waitingCount)
                        .thenComparing(ParaclinicalServiceOptionResponse.RoomOption::roomCode,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .map(ParaclinicalServiceOptionResponse.RoomOption::roomId)
                .orElse(null);
    }

    @Override
    @Transactional
    public ParaclinicalOrderResponse createOrder(UUID doctorAccountId, CreateParaclinicalOrderRequest request) {
        Instant now = clock.instant();

        // 1. Lượt khám đang được chính bác sĩ này khám, hoặc chờ kết luận khi cần chỉ định thêm (AF-04)
        Visit visit = visitRepository.findById(request.visitId())
                .orElseThrow(() -> new AppException(ErrorCode.VISIT_NOT_FOUND));
        boolean orderable = visit.getStatus() == VisitStatus.IN_EXAMINATION
                || visit.getStatus() == VisitStatus.WAITING_CONCLUSION;
        if (!orderable || !doctorAccountId.equals(visit.getDoctorAccountId())) {
            throw new AppException(ErrorCode.VISIT_NOT_IN_EXAMINATION, Map.of("status", visit.getStatus().name()));
        }

        // chon dich vu
        List<MedicalService> services = requireOrderableServices(request.items().stream()
                .map(CreateParaclinicalOrderRequest.Item::serviceId)
                .toList());
        // lay danh sach phong co dch vu
        // BR-50-01: only an insurance visit gets its rooms now. A self-pay visit gets them when the receipt is paid
        // (BR-50-02, PaymentCompletionService), or at once from the least busy room when the receipt is 0 VND.
        boolean roomsAtOrder = visit.getDoituongbnLoai() == VisitDoituongbnLoai.BHYT;
        Map<UUID, List<ParaclinicalServiceOptionResponse.RoomOption>> roomsByGroup = null;
        List<UUID> roomIds = new ArrayList<>();
        for (int i = 0; i < services.size(); i++) {
            if (!roomsAtOrder) {
                roomIds.add(null);
                continue;
            }
            UUID requestedRoom = request.items().get(i).roomId();
            if (requestedRoom == null && roomsByGroup == null) {
                roomsByGroup = activeRoomOptions();
            }
            roomIds.add(requestedRoom == null
                    ? autoRoom(services.get(i), roomsByGroup)
                    : chosenRoom(services.get(i), requestedRoom));
        }

        // 3. Bệnh án đang mở; chưa có thì mở bệnh án ngoại trú (từ đây không chuyển phòng được nữa, BR-24)
        MedicalRecord medicalRecord = medicalRecordRepository.findByVisitId(visit.getId())
                .orElseGet(() -> medicalRecordRepository.save(new MedicalRecord(
                        uuidV7Generator.next(), now, visit.getId(), "BA-" + visit.getSovaovien(),
                        OUTPATIENT_RECORD, MedicalRecordStatus.OPEN, now, doctorAccountId)));
        if (medicalRecord.getStatus() != MedicalRecordStatus.OPEN) {
            throw new AppException(ErrorCode.MEDICAL_RECORD_CLOSED);
        }

        // 4. Bước 4, 8: một phiếu thu phí CLS với giá tại thời điểm chỉ định (BR-09). Lượt BHYT có thẻ hợp lệ:
        // dịch vụ có giá BHYT được tách phần BHYT chi trả theo mức hưởng áp dụng của cả lượt, tính trên tổng chi
        // phí trong phạm vi BHYT kể cả các dịch vụ mới; dịch vụ không thuộc BHYT bệnh nhân trả đủ (BR-27, AF-03).
        // tong ten va bhyt ch tra
        Short coverageRate = appliedCoverageRate(visit, services);
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal insuranceTotal = BigDecimal.ZERO;
        for (MedicalService service : services) {
            total = total.add(service.getClinicPrice());
            if (coverageRate != null && service.getInsurancePrice() != null) {
                insuranceTotal = insuranceTotal.add(
                        PaymentItem.insuranceAmount(PaymentItem.insuranceScope(service), coverageRate));
            }
        }
        // snh ma pheu thu ten
        String paymentCode = "HD%010d".formatted(visitRepository.nextPaymentCode());
        // tạo phiếu thu
        Payment payment = Payment.createParaclinicalFee(
                uuidV7Generator.next(), now, paymentCode, visit.getId(), total, insuranceTotal,
                total.subtract(insuranceTotal));
        // lấy phòng khám của lượt khám
        Room room = roomRepository.findById(visit.getRoomId()).orElse(null);
        // số phiếu chỉ định
        String sophieu = "CD" + paymentCode.substring(2);
        // tạo phiếu chỉ định
        ParaclinicalOrder order = new ParaclinicalOrder(
                uuidV7Generator.next(), now, sophieu, medicalRecord.getId(), doctorAccountId, payment.getId(),
                request.chandoansobo(),
                (short) (visit.getDoituongbnLoai() == VisitDoituongbnLoai.BHYT ? 1 : 2),
                request.mucdochidinh(),
                room != null ? room.getRoomName() : "Phòng khám",
                total,
                ParaclinicalOrderStatus.PENDING_PAYMENT);

        // 5. Mỗi dịch vụ là một chỉ định chờ thanh toán, cùng số tiền với dòng phiếu thu của nó
        List<PaymentItem> paymentItems = new ArrayList<>();
        for (int i = 0; i < services.size(); i++) {
            MedicalService service = services.get(i);
            UUID detailId = uuidV7Generator.next();
            PaymentItem item = coverageRate != null && service.getInsurancePrice() != null
                    ? PaymentItem.createParaclinicalInsuranceItem(
                            uuidV7Generator.next(), now, payment, service, coverageRate, detailId)
                    : PaymentItem.createParaclinicalItem(
                            uuidV7Generator.next(), now, payment, service, BigDecimal.ONE, detailId);
            ParaclinicalOrderDetail detail = new ParaclinicalOrderDetail(
                    detailId, now, order, service.getId(),
                    "%s-%02d".formatted(sophieu, i + 1), now, service.getTenchidinh(), null, null,
                    BigDecimal.ONE, item.getDongia(), item.getThanhtien(), item.getBhytChitra(),
                    item.getBenhnhanphaitra(), roomIds.get(i), ParaclinicalOrderDetailStatus.PENDING_PAYMENT);
            order.addDetail(detail);
            paymentItems.add(item);
        }

        // 6. Lưu theo thứ tự khóa ngoại: phiếu thu → chỉ định (và chi tiết) → dòng phiếu thu trỏ tới chi tiết
        paymentRepository.saveAndFlush(payment);
        ParaclinicalOrder savedOrder = paraclinicalOrderRepository.saveAndFlush(order);
        paymentItems.forEach(payment::addItem);
        paymentRepository.save(payment);

        // BR-27: mức hưởng áp dụng có thể đổi (vượt ngưỡng 15% lương cơ sở) nên tính lại các phiếu chưa thu của lượt
        insuranceCoverageService.recalculate(visit);

        // 7. BR-26: lượt BHYT chuyển chỉ định tới phòng CLS ngay, phần bệnh nhân trả thu sau khi kết luận;
        // phiếu bệnh nhân trả 0đ cũng chuyển ngay (AF-01). Lượt dịch vụ chờ thu ngân xác nhận (UC-71, BR-28).
        // chec d lam cls luon
        boolean releaseNow = visit.getDoituongbnLoai() == VisitDoituongbnLoai.BHYT
                || payment.getStatus() == PaymentStatus.FEE_EXEMPT;
        if (releaseNow) {
            paymentCompletionService.releaseOrder(savedOrder);
        }

        // 8. Bước 10: lượt khám chờ CLS (cả khi chỉ định thêm từ chờ kết luận). The order is not audited (UC-49).
        visit.markAsWaitingParaclinical();
        visitRepository.save(visit);

        return toResponse(savedOrder, payment, visit.getStatus() == VisitStatus.WAITING_PARACLINICAL);
    }

    /**
     * BR-27 applied coverage rate once the new services are added, or {@code null} when the visit is billed at the
     * clinic price (self-paid visit).
     */
    private Short appliedCoverageRate(Visit visit, List<MedicalService> services) {
        if (!visit.isInsuranceCovered()) {
            return null;
        }
        BigDecimal scopeTotal = services.stream()
                .map(PaymentItem::insuranceScope)
                .reduce(insuranceCoverageService.scopeTotal(visit.getId()), BigDecimal::add);
        return insuranceCoverageService.appliedCoverageRate(scopeTotal, visit.getInsuranceCoverageRate());
    }

    /**
     * The room the system assigns: a room of the service's group with staff on duty now, then the fewest waiting,
     * then the lower room code ({@link #leastBusy}); {@code null} when no Active room performs the service, and the
     * release then reports it as unrouted (UC-71 AF-02).
     */
    private static UUID autoRoom(
            MedicalService service, Map<UUID, List<ParaclinicalServiceOptionResponse.RoomOption>> roomsByGroup) {
        if (service.getServiceGroupId() == null) {
            return null;
        }
        return leastBusy(roomsByGroup.getOrDefault(service.getServiceGroupId(), List.of()));
    }

    /**
     * I1-D47, BR-50-01: a room sent by the client (older clients), which must be an Active paraclinical room of the
     * service's group.
     */
    private UUID chosenRoom(MedicalService service, UUID roomId) {
        Room room = roomRepository.findById(roomId).orElse(null);
        boolean eligible = room != null
                && room.getStatus() == ActivationStatus.ACTIVE
                && room.getType() == RoomType.PARACLINICAL
                && room.getServiceGroupId() != null
                && room.getServiceGroupId().equals(service.getServiceGroupId());
        if (!eligible) {
            Map<String, String> details = new LinkedHashMap<>();
            details.put("roomId", roomId.toString());
            details.put("serviceId", service.getId().toString());
            details.put("serviceName", service.getTenchidinh());
            if (room != null) {
                details.put("roomName", room.getRoomName());
            }
            throw new AppException(ErrorCode.PARACLINICAL_ROOM_NOT_ELIGIBLE, details);
        }
        return room.getId();
    }

    private List<MedicalService> requireOrderableServices(List<UUID> serviceIds) {
        if (new HashSet<>(serviceIds).size() != serviceIds.size()) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("serviceIds", "DUPLICATE"));
        }
        Map<UUID, MedicalService> found = medicalServiceRepository.findAllById(serviceIds).stream()
                .collect(Collectors.toMap(MedicalService::getId, Function.identity()));
        List<MedicalService> services = new ArrayList<>();
        for (UUID serviceId : serviceIds) {
            MedicalService service = found.get(serviceId);
            if (service == null || !PARACLINICAL_TYPES.contains(service.getType())) {
                throw new AppException(ErrorCode.SERVICE_NOT_FOUND, Map.of("serviceId", serviceId.toString()));
            }
            if (service.getStatus() != ActivationStatus.ACTIVE) {
                throw new AppException(ErrorCode.SERVICE_INACTIVE, Map.of(
                        "serviceId", serviceId.toString(), "serviceName", service.getTenchidinh()));
            }
            services.add(service);
        }
        return services;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParaclinicalOrderResponse> listOrdersByVisit(UUID doctorAccountId, UUID visitId) {
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new AppException(ErrorCode.VISIT_NOT_FOUND));
        if (!doctorAccountId.equals(visit.getDoctorAccountId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        MedicalRecord medicalRecord = medicalRecordRepository.findByVisitId(visitId).orElse(null);
        if (medicalRecord == null) {
            return List.of();
        }

        List<ParaclinicalOrderResponse> orders = new ArrayList<>();
        for (ParaclinicalOrder order : paraclinicalOrderRepository.findAllByMedicalRecordId(medicalRecord.getId())) {
            Payment payment = paymentRepository.findById(order.getPaymentId()).orElse(null);
            orders.add(toResponse(order, payment, visit.getStatus() == VisitStatus.WAITING_PARACLINICAL));
        }
        orders.sort(Comparator.comparing(ParaclinicalOrderResponse::createdAt));
        return orders;
    }

    @Override
    @Transactional(readOnly = true)
    public ParaclinicalOrderResponse getOrderById(UUID orderId) {
        ParaclinicalOrder order = paraclinicalOrderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));

        Payment payment = paymentRepository.findById(order.getPaymentId()).orElse(null);
        return toResponse(order, payment, false);
    }

    @Override
    @Transactional
    public ParaclinicalCancellationResponse cancelService(
            UUID doctorAccountId, UUID orderDetailId, CancelParaclinicalServiceRequest request) {
        Instant now = clock.instant();

        // 1. Dịch vụ thuộc lượt khám đang chờ CLS của chính bác sĩ này, hồ sơ còn mở
        ParaclinicalOrderDetail detail = paraclinicalOrderDetailRepository.findById(orderDetailId)
                .orElseThrow(() -> new AppException(ErrorCode.PARACLINICAL_SERVICE_NOT_FOUND));
        ParaclinicalOrder order = detail.getOrder();
        MedicalRecord medicalRecord = medicalRecordRepository.findById(order.getMedicalRecordId())
                .orElseThrow(() -> new AppException(ErrorCode.PARACLINICAL_SERVICE_NOT_FOUND));
        Visit visit = visitRepository.findById(medicalRecord.getVisitId())
                .orElseThrow(() -> new AppException(ErrorCode.VISIT_NOT_FOUND));
        if (!doctorAccountId.equals(visit.getDoctorAccountId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        if (medicalRecord.getStatus() != MedicalRecordStatus.OPEN) {
            throw new AppException(ErrorCode.MEDICAL_RECORD_CLOSED);
        }
        if (visit.getStatus() != VisitStatus.WAITING_PARACLINICAL) {
            throw new AppException(ErrorCode.VISIT_NOT_IN_EXAMINATION, Map.of("status", visit.getStatus().name()));
        }

        // 2. BR-25: chưa thu tiền (lượt dịch vụ) và KTV chưa tiếp nhận (lượt BHYT)
        Payment payment = paymentRepository.findById(order.getPaymentId())
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!isCancellable(detail, payment)) {
            throw new AppException(ErrorCode.PARACLINICAL_SERVICE_NOT_CANCELLABLE, Map.of(
                    "serviceName", detail.getTenchidinh(),
                    "status", detail.getStatus().name(),
                    "paymentStatus", payment.getStatus().name()));
        }

        // 3. Hủy dịch vụ (lý do bắt buộc), bỏ dòng khỏi phiếu thu; hủy hết thì phiếu và phiếu chỉ định cũng hủy
        detail.cancelByDoctor(doctorAccountId, request.reason(), now);
        payment.cancelLine(detail.getId(), now);
        order.cancelIfAllServicesCancelled(now);
        paraclinicalOrderRepository.save(order);
        paymentRepository.save(payment);

        // 4. BR-27: lượt BHYT tính lại mức hưởng (có thể về 100% khi tổng xuống dưới ngưỡng) cho mọi phiếu chưa thu
        insuranceCoverageService.recalculate(visit);

        // 5. UC-49 AF-01: phiếu lượt dịch vụ còn lại 0đ thì chuyển các dịch vụ còn lại tới phòng CLS ngay
        if (payment.getStatus() == PaymentStatus.FEE_EXEMPT
                && order.getStatus() == ParaclinicalOrderStatus.PENDING_PAYMENT) {
            paymentCompletionService.releaseOrder(order);
        }

        // 6. BR-41: không còn dịch vụ nào phải làm thì lượt khám chuyển sang chờ kết luận
        paraclinicalProgressService.moveToConclusionIfDone(visit);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("sophieu", order.getSophieu());
        audit.put("service", detail.getTenchidinh());
        audit.put("reason", detail.getCancelReason());
        audit.put("paymentCode", payment.getPaymentCode());
        audit.put("paymentStatus", payment.getStatus().name());
        audit.put("patientAmount", payment.getPatientTotalAmount());
        auditLogService.recordBusinessChange(
                doctorAccountId, AuditLogAction.CANCEL, "paraclinical_order_details", detail.getId(),
                visit.getSovaovien(), audit, now);

        return new ParaclinicalCancellationResponse(
                detail.getTenchidinh(), payment.getPaymentCode(), payment.getStatus(),
                payment.getPatientTotalAmount(), visit.getStatus());
    }

    /**
     * BR-25: no Technician has accepted the service, and its receipt is not paid (nor handed to PayOS). An insurance
     * visit's receipt is never paid before the conclusion, so only acceptance matters there.
     */
    private static boolean isCancellable(ParaclinicalOrderDetail detail, Payment payment) {
        return payment != null && detail.isCancellableBeforeAcceptance() && payment.isRepriceable();
    }

    private ParaclinicalOrderResponse toResponse(ParaclinicalOrder order, Payment payment, boolean visitOpen) {
        List<ParaclinicalOrderResponse.ParaclinicalOrderDetailResponse> detailResponses = new ArrayList<>();

        if (order.getDetails() != null) {
            // Performing rooms of all lines in one query.
            Map<UUID, Room> rooms = roomRepository.findAllById(order.getDetails().stream()
                            .map(ParaclinicalOrderDetail::getRoomId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet()))
                    .stream()
                    .collect(Collectors.toMap(Room::getId, Function.identity()));
            for (ParaclinicalOrderDetail detail : order.getDetails()) {
                Room room = detail.getRoomId() == null ? null : rooms.get(detail.getRoomId());
                detailResponses.add(new ParaclinicalOrderResponse.ParaclinicalOrderDetailResponse(
                        detail.getId(),
                        detail.getServiceId(),
                        detail.getMachidinh(),
                        detail.getTenchidinh(),
                        detail.getSoluong(),
                        detail.getDongia(),
                        detail.getThanhtien(),
                        detail.getBhytChitra(),
                        detail.getBenhnhanphaitra(),
                        detail.getRoomId(),
                        room == null ? null : room.getRoomName(),
                        room == null ? null : room.getLocation(),
                        detail.getStatus().name(),
                        visitOpen && isCancellable(detail, payment),
                        detail.getStatus() == ParaclinicalOrderDetailStatus.CANCELLED ? detail.getCancelReason() : null
                ));
            }
        }

        return new ParaclinicalOrderResponse(
                order.getId(),
                order.getSophieu(),
                payment != null ? payment.getVisitId() : null,
                order.getPaymentId(),
                payment != null ? payment.getPaymentCode() : null,
                order.getChandoansobo(),
                order.getTongcong(),
                payment != null ? payment.getInsuranceTotalAmount() : null,
                payment != null ? payment.getPatientTotalAmount() : null,
                order.getStatus(),
                order.getCreatedAt(),
                detailResponses
        );
    }
}
