package clinic.backend.service.impl;

import clinic.backend.dto.request.CreateVisitRequest;
import clinic.backend.dto.response.VisitResponse;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Payment;
import clinic.backend.entity.PaymentItem;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.entity.Visit;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.PriorityReason;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.VisitService;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class VisitServiceImpl implements VisitService {

    private final MedicalServiceRepository medicalServiceRepository;
    private final PaymentRepository paymentRepository;
    private final PatientRepository patientRepository;
    private final RoomRepository roomRepository;
    private final VisitRepository visitRepository;
    private final StaffRoomAssignmentRepository staffRoomAssignmentRepository;
    private final UuidV7Generator uuidV7Generator;

    private static final ZoneId VIETNAM_ZONE =
            ZoneId.of("Asia/Ho_Chi_Minh");

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

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

    private Room validateExaminationRoom(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy phòng khám"
                ));

        if (room.getStatus() != ActivationStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Phòng khám hiện không hoạt động"
            );
        }

        if (room.getType() != RoomType.EXAMINATION) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Đăng ký lượt khám phải chọn phòng khám bệnh, không phải phòng cận lâm sàng"
            );
        }

        return room;
    }

    private String generateSoVaoVien() {
        String date = LocalDate.now(VIETNAM_ZONE)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // Mã minh họa: VV + ngày + số ngẫu nhiên.
        // Cần kiểm tra trùng mã và thử sinh lại nếu bị trùng.
        for (int i = 0; i < 5; i++) {
            int number = ThreadLocalRandom.current()
                    .nextInt(1_000_000, 10_000_000);

            String code = "VV" + date + number;

            if (!visitRepository.existsBySovaovien(code)) {
                return code;
            }
        }

        throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Không thể tạo mã số vào viện, vui lòng thử lại"
        );
    }

    private PriorityReason parsePriorityReason(
            boolean isPriority,
            String reason
    ) {
        if (!isPriority) {
            return null;
        }

        if (reason == null || reason.isBlank()) {
            return PriorityReason.ELDERLY;
        }

        try {
            return PriorityReason.valueOf(reason.trim());
        } catch (IllegalArgumentException ex) {
            return PriorityReason.ELDERLY;
        }
    }

    @Override
    @Transactional
    public VisitResponse createVisit(CreateVisitRequest request) {

        // 1. Xác định tài khoản lễ tân đang đăng nhập
        UUID registeredByAccountId = getCurrentAccountId();

        // 2. Kiểm tra bệnh nhân
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy bệnh nhân"
                ));

        // 3. Kiểm tra phòng khám
        Room room = validateExaminationRoom(request.roomId());

        // 4. Kiểm tra đối tượng bệnh nhân
        VisitDoituongbnLoai loai = request.doituongbnLoai();

        String maBHYT = normalize(request.mabhyt());

        if (loai == VisitDoituongbnLoai.BHYT
                && maBHYT == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Bệnh nhân BHYT phải có mã BHYT"
            );
        }

        if (loai == VisitDoituongbnLoai.DV) {
            maBHYT = null;
        }

        // 5. Kiểm tra thông tin ưu tiên
        boolean isPriority = Boolean.TRUE.equals(request.isPriority());

        PriorityReason priorityReason = parsePriorityReason(
                isPriority,
                request.priorityReason()
        );

        // 6. Xác định thời điểm tiếp nhận và ngày hàng đợi
        Instant now = Instant.now();

        LocalDate queueDate = LocalDate.ofInstant(
                now,
                VIETNAM_ZONE
        );

        int queueNumber = visitRepository.findMaxQueueNumber(
                queueDate,
                room.getId()
        ) + 1;

        String sovaovien = generateSoVaoVien();

        // 8. Xác định trạng thái ban đầu
        VisitStatus status =
                loai == VisitDoituongbnLoai.DV
                        ? VisitStatus.WAITING_FEE_PAYMENT
                        : VisitStatus.WAITING;

        // Chỉ đánh dấu NOT_APPLICABLE cho lượt dịch vụ.
        // BHYT chưa được xác minh thì không coi là đã hợp lệ.
        VisitInsuranceVerifyStatus insuranceStatus =
                loai == VisitDoituongbnLoai.DV
                        ? VisitInsuranceVerifyStatus.NOT_APPLICABLE
                        : null;

        Visit visit = new Visit(
                uuidV7Generator.next(),
                now,
                sovaovien,
                patient.getId(),
                room.getId(),
                registeredByAccountId,
                now,
                loai,
                maBHYT,
                normalize(request.lydotiepnhan()),
                normalize(request.noigioithieuLoai()),
                request.lydovaovien().trim(),
                isPriority,
                priorityReason,
                queueDate,
                queueNumber,
                status,
                insuranceStatus
        );

        Visit savedVisit = visitRepository.save(visit);

        // Chỉ tạo phiếu thu tự động cho bệnh nhân dịch vụ.
        if (loai == VisitDoituongbnLoai.DV) {

            MedicalService service = medicalServiceRepository
                    .findByTypeAndDepartmentIdIsNullAndServiceGroupIdIsNullAndStatus(
                            MedicalServiceType.EXAMINATION,
                            ActivationStatus.ACTIVE
                    )
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Chưa cấu hình dịch vụ khám đang hoạt động"
                    ));

            BigDecimal examinationFee = service.getClinicPrice();

            if (examinationFee == null || examinationFee.signum() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Giá dịch vụ khám chưa hợp lệ"
                );
            }

            String paymentCode = "P" + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 19);

            Payment payment = Payment.createExaminationFee(
                    uuidV7Generator.next(),
                    now,
                    paymentCode,
                    savedVisit.getId(),
                    examinationFee,
                    BigDecimal.ZERO,
                    examinationFee
            );
            PaymentItem item = PaymentItem.createClinicItem(
                    uuidV7Generator.next(),
                    now,
                    payment,
                    service,
                    BigDecimal.ONE
            );

            payment.addItem(item);
            Payment savedPayment = paymentRepository.save(payment);

            if (savedPayment.getStatus() == PaymentStatus.FEE_EXEMPT) {
                savedVisit.markAsWaiting();
                visitRepository.save(savedVisit);
            }
        }

        return toResponse(savedVisit);
    }

    @Override
    public List<VisitResponse> getRoomQueue(UUID roomId, LocalDate date) {
        LocalDate queryDate = date != null ? date : LocalDate.now(VIETNAM_ZONE);

        UUID targetRoomId = roomId;
        if (targetRoomId == null) {
            try {
                UUID currentAccountId = getCurrentAccountId();
                targetRoomId = staffRoomAssignmentRepository
                        .findByStaffAccountIdAndRemovedAtIsNull(currentAccountId)
                        .map(StaffRoomAssignment::getRoomId)
                        .orElse(null);
            } catch (Exception ex) {
                // Not authenticated or other roles
            }
        }

        List<Visit> visits;
        if (targetRoomId != null) {
            visits = visitRepository.findRoomQueue(targetRoomId, queryDate);
        } else {
            visits = visitRepository.findAllByQueueDate(queryDate);
        }

        return visits.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public VisitResponse startExamination(UUID visitId) {
        UUID doctorAccountId = getCurrentAccountId();

        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy lượt khám"
                ));

        if (visit.getStatus() != VisitStatus.WAITING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Lượt khám không ở trạng thái chờ khám (trạng thái: " + visit.getStatus() + ")"
            );
        }

        visit.startExamination(doctorAccountId);
        Visit saved = visitRepository.save(visit);
        return toResponse(saved);
    }

    private VisitResponse toResponse(Visit visit) {
        Patient patient = patientRepository.findById(visit.getPatientId()).orElse(null);
        Room room = roomRepository.findById(visit.getRoomId()).orElse(null);

        String genderText = null;
        if (patient != null) {
            genderText = switch (patient.getGioitinh()) {
                case 1 -> "Nam";
                case 2 -> "Nữ";
                default -> "Khác";
            };
        }

        return new VisitResponse(
                visit.getId(),
                visit.getSovaovien(),
                visit.getPatientId(),
                patient != null ? patient.getHoten() : "—",
                patient != null ? patient.getMabenhnhan() : "—",
                patient != null && patient.getNgaysinh() != null ? patient.getNgaysinh().toString() : null,
                genderText,
                patient != null ? patient.getSodienthoai() : null,
                visit.getRoomId(),
                room != null ? room.getRoomName() : null,
                visit.getDoctorAccountId(),
                visit.getRegisteredByAccountId(),
                visit.getThoigianvaovien(),
                visit.getDenkhambenhluc(),
                visit.getDoituongbnLoai(),
                visit.getMabhyt(),
                visit.getTungaybhyt(),
                visit.getDenngaybhyt(),
                visit.getNoidangkykcbbd(),
                visit.getLydotiepnhan(),
                visit.getNoigioithieuLoai(),
                visit.getLydovaovien(),
                visit.isPriority(),
                visit.getPriorityReason() == null
                        ? null
                        : visit.getPriorityReason().name(),
                visit.getQueueDate(),
                visit.getQueueNumber(),
                visit.getStatus(),
                visit.isSkipped(),
                visit.getSkippedAt(),
                visit.getCompletedAt()
        );
    }
}
