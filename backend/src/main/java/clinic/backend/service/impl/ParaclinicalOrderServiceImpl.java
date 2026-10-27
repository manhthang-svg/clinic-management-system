package clinic.backend.service.impl;

import clinic.backend.dto.request.CreateParaclinicalOrderRequest;
import clinic.backend.dto.response.ParaclinicalOrderResponse;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Payment;
import clinic.backend.entity.PaymentItem;
import clinic.backend.entity.Room;
import clinic.backend.entity.Visit;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.ParaclinicalOrderStatus;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderRepository;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.ParaclinicalOrderService;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class ParaclinicalOrderServiceImpl implements ParaclinicalOrderService {

    private final VisitRepository visitRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final RoomRepository roomRepository;
    private final PaymentRepository paymentRepository;
    private final ParaclinicalOrderRepository paraclinicalOrderRepository;
    private final UuidV7Generator uuidV7Generator;

    private UUID getCurrentAccountId() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Không xác định được tài khoản đang đăng nhập"
            );
        }

        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Thông tin tài khoản đăng nhập không hợp lệ"
            );
        }
    }

    @Override
    @Transactional
    public ParaclinicalOrderResponse createOrder(CreateParaclinicalOrderRequest request) {
        UUID doctorAccountId = getCurrentAccountId();
        Instant now = Instant.now();

        // 1. Kiểm tra lượt khám
        Visit visit = visitRepository.findById(request.visitId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy lượt khám"
                ));

        // 2. Lấy hoặc tạo hồ sơ bệnh án (MedicalRecord)
        MedicalRecord medicalRecord = medicalRecordRepository.findByVisitId(visit.getId())
                .orElseGet(() -> {
                    String soba = "BA-" + visit.getSovaovien();
                    MedicalRecord record = new MedicalRecord(
                            uuidV7Generator.next(),
                            now,
                            visit.getId(),
                            soba,
                            "NGOAITRU",
                            MedicalRecordStatus.OPEN,
                            now,
                            doctorAccountId
                    );
                    return medicalRecordRepository.save(record);
                });

        // 3. Lấy thông tin các dịch vụ CLS được chọn
        List<MedicalService> services = new ArrayList<>();
        BigDecimal tongcong = BigDecimal.ZERO;

        for (UUID serviceId : request.serviceIds()) {
            MedicalService service = medicalServiceRepository.findById(serviceId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Không tìm thấy dịch vụ cận lâm sàng: " + serviceId
                    ));

            if (service.getStatus() != ActivationStatus.ACTIVE) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Dịch vụ " + service.getTenchidinh() + " đang không hoạt động"
                );
            }

            BigDecimal price = service.getClinicPrice();
            if (price == null || price.signum() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Giá dịch vụ " + service.getTenchidinh() + " không hợp lệ"
                );
            }

            services.add(service);
            tongcong = tongcong.add(price);
        }

        // 4. Tạo phiếu thu thanh toán lần 2 (PARACLINICAL_FEE)
        String paymentCode = "P" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 19);

        Payment payment = Payment.createParaclinicalFee(
                uuidV7Generator.next(),
                now,
                paymentCode,
                visit.getId(),
                tongcong,
                BigDecimal.ZERO,
                tongcong
        );

        // 5. Xác định nơi chỉ định (tên phòng khám hiện tại của lượt khám)
        Room room = roomRepository.findById(visit.getRoomId()).orElse(null);
        String noichidinh = room != null ? room.getRoomName() : "Phòng khám";
        short doituongbn = (short) (visit.getDoituongbnLoai() == VisitDoituongbnLoai.BHYT ? 1 : 2);

        String sophieu = "CD" + System.currentTimeMillis();

        ParaclinicalOrder order = new ParaclinicalOrder(
                uuidV7Generator.next(),
                now,
                sophieu,
                medicalRecord.getId(),
                doctorAccountId,
                payment.getId(),
                request.chandoansobo(),
                doituongbn,
                request.mucdochidinh() != null ? request.mucdochidinh() : "Thường",
                noichidinh,
                tongcong,
                ParaclinicalOrderStatus.PENDING_PAYMENT
        );

        // 6. Tạo chi tiết chỉ định và chi tiết phiếu thu
        for (MedicalService service : services) {
            UUID performingRoomId = null;
            if (service.getServiceGroupId() != null) {
                performingRoomId = roomRepository
                        .findAllByServiceGroupIdAndStatusOrderByRoomCodeAsc(
                                service.getServiceGroupId(),
                                ActivationStatus.ACTIVE
                        )
                        .stream()
                        .findFirst()
                        .map(Room::getId)
                        .orElse(null);
            }

            String machidinh = "CDD" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 999);

            ParaclinicalOrderDetail detail = new ParaclinicalOrderDetail(
                    uuidV7Generator.next(),
                    now,
                    order,
                    service.getId(),
                    machidinh,
                    now,
                    service.getTenchidinh(),
                    null,
                    null,
                    BigDecimal.ONE,
                    service.getClinicPrice(),
                    service.getClinicPrice(),
                    BigDecimal.ZERO,
                    service.getClinicPrice(),
                    performingRoomId,
                    ParaclinicalOrderDetailStatus.PENDING_PAYMENT
            );

            order.addDetail(detail);

            PaymentItem item = PaymentItem.createParaclinicalItem(
                    uuidV7Generator.next(),
                    now,
                    payment,
                    service,
                    BigDecimal.ONE,
                    detail.getId()
            );

            payment.addItem(item);
        }

        // 7. Lưu thanh toán và chỉ định
        paymentRepository.save(payment);
        ParaclinicalOrder savedOrder = paraclinicalOrderRepository.save(order);

        // 8. Chuyển lượt khám sang trạng thái chờ CLS
        visit.markAsWaitingParaclinical();
        visitRepository.save(visit);

        return toResponse(savedOrder, payment);
    }

    @Override
    @Transactional(readOnly = true)
    public ParaclinicalOrderResponse getOrderById(UUID orderId) {
        ParaclinicalOrder order = paraclinicalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy phiếu chỉ định cận lâm sàng"
                ));

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
                order.getStatus(),
                order.getCreatedAt(),
                detailResponses
        );
    }
}
