package clinic.backend.service.impl;

import clinic.backend.dto.request.CreateParaclinicalOrderRequest;
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
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderRepository;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.ServiceGroupRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.ParaclinicalOrderService;
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
    private final AuditLogService auditLogService;
    private final PaymentCompletionService paymentCompletionService;
    private final UuidV7Generator uuidV7Generator;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<ParaclinicalServiceOptionResponse> listOrderableServices() {
        // UC-49 bước 2: chỉ dịch vụ CLS đang hoạt động, kèm giá dịch vụ và giá BHYT (BR-08, BR-10)
        Map<UUID, String> groupNames = serviceGroupRepository.findAll().stream()
                .collect(Collectors.toMap(ServiceGroup::getId, ServiceGroup::getServiceGroupName));
        return medicalServiceRepository
                .findAllByStatusAndTypeInOrderByTenchidinhAsc(ActivationStatus.ACTIVE, PARACLINICAL_TYPES)
                .stream()
                .map(service -> new ParaclinicalServiceOptionResponse(
                        service.getId(),
                        service.getCode(),
                        service.getTenchidinh(),
                        service.getType(),
                        service.getServiceGroupId(),
                        groupNames.get(service.getServiceGroupId()),
                        service.getClinicPrice(),
                        service.getInsurancePrice()))
                .toList();
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

        // 2. Bước 7: không trùng dịch vụ (MSG07), dịch vụ CLS tồn tại và đang hoạt động (MSG42)
        List<MedicalService> services = requireOrderableServices(request.serviceIds());

        // 3. Bệnh án đang mở; chưa có thì mở bệnh án ngoại trú (từ đây không chuyển phòng được nữa, BR-24)
        MedicalRecord medicalRecord = medicalRecordRepository.findByVisitId(visit.getId())
                .orElseGet(() -> medicalRecordRepository.save(new MedicalRecord(
                        uuidV7Generator.next(), now, visit.getId(), "BA-" + visit.getSovaovien(),
                        OUTPATIENT_RECORD, MedicalRecordStatus.OPEN, now, doctorAccountId)));
        if (medicalRecord.getStatus() != MedicalRecordStatus.OPEN) {
            throw new AppException(ErrorCode.MEDICAL_RECORD_CLOSED);
        }

        // 4. Bước 4, 8: một phiếu thu phí CLS với giá tại thời điểm chỉ định (BR-09). Lượt BHYT có thẻ hợp lệ:
        // dịch vụ có giá BHYT được tách phần BHYT chi trả; dịch vụ không thuộc BHYT bệnh nhân trả đủ (BR-27, AF-03).
        Short coverageRate = insuranceCoverageRate(visit);
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal insuranceTotal = BigDecimal.ZERO;
        for (MedicalService service : services) {
            total = total.add(service.getClinicPrice());
            if (coverageRate != null && service.getInsurancePrice() != null) {
                insuranceTotal = insuranceTotal.add(PaymentItem.insuranceShare(
                        service.getClinicPrice(), service.getInsurancePrice(), coverageRate));
            }
        }
        String paymentCode = "HD%010d".formatted(visitRepository.nextPaymentCode());
        Payment payment = Payment.createParaclinicalFee(
                uuidV7Generator.next(), now, paymentCode, visit.getId(), total, insuranceTotal,
                total.subtract(insuranceTotal));

        Room room = roomRepository.findById(visit.getRoomId()).orElse(null);
        String sophieu = "CD" + paymentCode.substring(2);
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
                    item.getBenhnhanphaitra(), null, ParaclinicalOrderDetailStatus.PENDING_PAYMENT);
            order.addDetail(detail);
            paymentItems.add(item);
        }

        // 6. Lưu theo thứ tự khóa ngoại: phiếu thu → chỉ định (và chi tiết) → dòng phiếu thu trỏ tới chi tiết
        paymentRepository.saveAndFlush(payment);
        ParaclinicalOrder savedOrder = paraclinicalOrderRepository.saveAndFlush(order);
        paymentItems.forEach(payment::addItem);
        paymentRepository.save(payment);

        // 7. BR-26: lượt BHYT chuyển chỉ định tới phòng CLS ngay, phần bệnh nhân trả thu sau khi kết luận;
        // phiếu bệnh nhân trả 0đ cũng chuyển ngay (AF-01). Lượt dịch vụ chờ thu ngân xác nhận (UC-71, BR-28).
        boolean releaseNow = visit.getDoituongbnLoai() == VisitDoituongbnLoai.BHYT
                || payment.getStatus() == PaymentStatus.FEE_EXEMPT;
        List<String> unroutedServices = releaseNow
                ? paymentCompletionService.releaseOrder(savedOrder)
                : List.of();

        // 8. Bước 9: lượt khám chờ CLS (cả khi chỉ định thêm từ chờ kết luận) và ghi audit
        visit.markAsWaitingParaclinical();
        visitRepository.save(visit);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("sophieu", sophieu);
        audit.put("paymentCode", paymentCode);
        audit.put("services", services.stream().map(MedicalService::getTenchidinh).toList());
        audit.put("total", total);
        audit.put("insuranceAmount", insuranceTotal);
        audit.put("patientAmount", payment.getPatientTotalAmount());
        audit.put("released", releaseNow);
        if (!unroutedServices.isEmpty()) {
            audit.put("unroutedServices", unroutedServices);
        }
        auditLogService.recordBusinessChange(
                doctorAccountId, AuditLogAction.CREATE, "paraclinical_orders", savedOrder.getId(),
                visit.getSovaovien(), audit, now);

        return toResponse(savedOrder, payment);
    }

    /** Coverage rate of a valid insurance card, or {@code null} when the visit is billed at the clinic price. */
    private static Short insuranceCoverageRate(Visit visit) {
        boolean insured = visit.getDoituongbnLoai() == VisitDoituongbnLoai.BHYT
                && visit.getInsuranceVerifyStatus() == VisitInsuranceVerifyStatus.VALID;
        return insured ? visit.getInsuranceCoverageRate() : null;
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
            orders.add(toResponse(order, payment));
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
        return toResponse(order, payment);
    }

    private ParaclinicalOrderResponse toResponse(ParaclinicalOrder order, Payment payment) {
        List<ParaclinicalOrderResponse.ParaclinicalOrderDetailResponse> detailResponses = new ArrayList<>();

        if (order.getDetails() != null) {
            for (ParaclinicalOrderDetail detail : order.getDetails()) {
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
                        detail.getStatus().name()
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
