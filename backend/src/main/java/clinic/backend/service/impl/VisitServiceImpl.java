package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import clinic.backend.dto.request.CreateVisitRequest;
import clinic.backend.dto.response.RoomQueueStatusResponse;
import clinic.backend.dto.response.VisitFeePreviewResponse;
import clinic.backend.dto.response.VisitResponse;
import clinic.backend.dto.response.VisitTicketResponse;
import clinic.backend.entity.FollowUpAppointment;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Payment;
import clinic.backend.entity.PaymentItem;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.FollowUpAppointmentStatus;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.enums.PaymentType;
import clinic.backend.enums.PriorityReason;
import clinic.backend.enums.Role;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.FollowUpAppointmentRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.repository.RoomQueueCounterRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.InsuranceVerificationService;
import clinic.backend.service.VisitService;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VisitServiceImpl implements VisitService {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String VISIT_AUDIT_ENTITY = "visits";

    private final MedicalServiceRepository medicalServiceRepository;
    private final PaymentRepository paymentRepository;
    private final PatientRepository patientRepository;
    private final RoomRepository roomRepository;
    private final VisitRepository visitRepository;
    private final StaffRoomAssignmentRepository staffRoomAssignmentRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final ClinicFacilityRepository clinicFacilityRepository;
    private final FollowUpAppointmentRepository appointmentRepository;
    private final InsuranceVerificationService insuranceVerificationService;
    private final RoomQueueCounterRepository queueCounterRepository;
    private final AuditLogService auditLogService;
    private final UuidV7Generator ids;
    private final Clock clock;

    @Override
    @Transactional
    public VisitResponse createVisit(StaffPrincipal principal, CreateVisitRequest request) {
        rejectExistingActiveVisit(request.patientId());
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new AppException(ErrorCode.PATIENT_NOT_FOUND));
        Room room = requireAvailableExaminationRoom(request.roomId());
        PriorityReason priorityReason = parsePriorityReason(request.isPriority(), request.priorityReason());
        InsuranceData insurance = resolveInsurance(patient, request);
        FollowUpAppointment appointment = lockAppointment(request, patient);
        MedicalService examinationService = requireExaminationService();
        FeeCalculation fee = calculateFee(examinationService, insurance);

        Instant now = clock.instant();
        LocalDate queueDate = LocalDate.ofInstant(now, CLINIC_ZONE);
        int queueNumber = queueCounterRepository.takeNext(room.getId(), queueDate);
        String registrationCode = "DK%010d".formatted(visitRepository.nextRegistrationCode());

        Visit visit = new Visit(
                ids.next(), now, registrationCode, patient.getId(), room.getId(), principal.accountId(), now,
                request.doituongbnLoai(), insurance.submittedCode(), insurance.socialInsuranceNumber(),
                insurance.validFrom(), insurance.validTo(), insurance.primaryCareFacilityCode(),
                insurance.primaryCareFacility(), null, insurance.coverageRate(),
                normalize(request.lydotiepnhan()), normalize(request.noigioithieuLoai()),
                request.lydovaovien().strip(), Boolean.TRUE.equals(request.isPriority()), priorityReason,
                queueDate, queueNumber,
                initialStatusFor(fee),
                insurance.status());

        if (appointment != null) {
            visit.linkFollowUpAppointment(appointment.getId());
        }
        Visit savedVisit;
        try {
            savedVisit = visitRepository.saveAndFlush(visit);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, "uq_visits_active_patient")) {
                throw new AppException(ErrorCode.ACTIVE_VISIT_EXISTS);
            }
            throw exception;
        }
        paymentRepository.save(createExaminationPayment(savedVisit, examinationService, fee, now));

        if (appointment != null) {
            appointment.markArrived(savedVisit.getId(), now);
            appointmentRepository.save(appointment);
        }

        auditLogService.recordBusinessChange(
                principal.accountId(), AuditLogAction.CREATE, VISIT_AUDIT_ENTITY, savedVisit.getId(),
                savedVisit.getSovaovien(),
                Map.of("status", savedVisit.getStatus().name(), "roomId", savedVisit.getRoomId()), now);
        return toResponse(savedVisit);
    }

    @Override
    @Transactional(readOnly = true)
    public VisitResponse getActiveVisit(UUID patientId) {
        return visitRepository.findFirstByPatientIdAndStatusNotInOrderByThoigianvaovienDesc(
                        patientId, VisitRepository.FINISHED_STATUSES)
                .map(this::toResponse)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public VisitFeePreviewResponse previewFee(
            UUID patientId,
            VisitDoituongbnLoai visitType,
            String insuranceCardNumber) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new AppException(ErrorCode.PATIENT_NOT_FOUND));
        MedicalService service = requireExaminationService();
        InsuranceData insurance = visitType == VisitDoituongbnLoai.DV
                ? InsuranceData.notApplicable()
                : resolveInsurance(patient, insuranceCardNumber);
        FeeCalculation fee = calculateFee(service, insurance);
        return new VisitFeePreviewResponse(
                service.getTenchidinh(), service.getClinicPrice(), fee.appliedPrice(),
                fee.insuranceAmount(), fee.patientAmount());
    }

    private void rejectExistingActiveVisit(UUID patientId) {
        if (visitRepository.existsByPatientIdAndStatusNotIn(patientId, VisitRepository.FINISHED_STATUSES)) {
            throw new AppException(ErrorCode.ACTIVE_VISIT_EXISTS);
        }
    }

    private Room requireAvailableExaminationRoom(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_AVAILABLE));
        if (room.getStatus() != ActivationStatus.ACTIVE
                || room.getType() != RoomType.EXAMINATION
                || !staffRoomAssignmentRepository.hasActiveDoctor(roomId)) {
            throw new AppException(ErrorCode.ROOM_NOT_AVAILABLE);
        }
        return room;
    }

    private MedicalService requireExaminationService() {
        return medicalServiceRepository
                .findByTypeAndDepartmentIdIsNullAndServiceGroupIdIsNullAndStatus(
                        MedicalServiceType.EXAMINATION, ActivationStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.EXAMINATION_SERVICE_NOT_CONFIGURED));
    }

    private PriorityReason parsePriorityReason(Boolean priority, String value) {
        if (!Boolean.TRUE.equals(priority)) {
            return null;
        }
        try {
            return PriorityReason.valueOf(value == null ? "" : value.strip());
        } catch (IllegalArgumentException exception) {
            throw new AppException(
                    ErrorCode.REQUEST_VALIDATION_FAILED,
                    Map.of("priorityReason", "Vui lòng chọn lý do ưu tiên hợp lệ"));
        }
    }

    private InsuranceData resolveInsurance(Patient patient, CreateVisitRequest request) {
        if (request.doituongbnLoai() == VisitDoituongbnLoai.DV) {
            return InsuranceData.notApplicable();
        }
        return resolveInsurance(patient, request.mabhyt());
    }

    private InsuranceData resolveInsurance(Patient patient, String cardNumber) {
        String normalizedCard = normalize(cardNumber);
        if (normalizedCard == null) {
            throw new AppException(
                    ErrorCode.REQUEST_VALIDATION_FAILED,
                    Map.of("mabhyt", "Vui lòng nhập số thẻ BHYT"));
        }
        var result = insuranceVerificationService.verify(
                new clinic.backend.dto.request.InsuranceVerificationRequest(patient.getId(), normalizedCard));
        if ("INVALID".equals(result.status())) {
            throw new AppException(ErrorCode.INSURANCE_CARD_INVALID);
        }
        if ("MISMATCH".equals(result.status())) {
            throw new AppException(ErrorCode.INSURANCE_PATIENT_MISMATCH);
        }
        return new InsuranceData(
                result.submittedCode(), result.socialInsuranceNumber(), result.validFrom(), result.validTo(),
                result.primaryCareFacilityCode(), result.primaryCareFacility(), result.coverageRate(),
                VisitInsuranceVerifyStatus.VALID);
    }

    private FollowUpAppointment lockAppointment(CreateVisitRequest request, Patient patient) {
        if (request.followUpAppointmentId() == null) {
            return null;
        }
        FollowUpAppointment appointment = appointmentRepository.findByIdForUpdate(request.followUpAppointmentId())
                .orElseThrow(() -> new AppException(ErrorCode.FOLLOW_UP_APPOINTMENT_NOT_SCHEDULED));
        boolean staleVersion = request.followUpAppointmentVersion() == null
                || !request.followUpAppointmentVersion().equals(appointment.getVersion());
        if (staleVersion || appointment.getStatus() != FollowUpAppointmentStatus.SCHEDULED
                || !appointment.getPatientId().equals(patient.getId())) {
            throw new AppException(ErrorCode.FOLLOW_UP_APPOINTMENT_NOT_SCHEDULED);
        }
        return appointment;
    }

    private FeeCalculation calculateFee(MedicalService service, InsuranceData insurance) {
        boolean covered = insurance.status() == VisitInsuranceVerifyStatus.VALID
                && service.getInsurancePrice() != null;
        BigDecimal appliedPrice = covered ? service.getInsurancePrice() : service.getClinicPrice();
        BigDecimal insuranceAmount = covered
                ? appliedPrice.multiply(BigDecimal.valueOf(insurance.coverageRate()))
                        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new FeeCalculation(
                appliedPrice, insuranceAmount, appliedPrice.subtract(insuranceAmount), covered);
    }

    private Payment createExaminationPayment(
            Visit visit, MedicalService service, FeeCalculation fee, Instant now) {
        Payment payment = Payment.createExaminationFee(
                ids.next(), now, "HD%010d".formatted(visitRepository.nextPaymentCode()), visit.getId(),
                fee.appliedPrice(), fee.insuranceAmount(), fee.patientAmount());
        PaymentItem item = fee.insuranceCovered()
                ? PaymentItem.createInsuranceItem(
                        ids.next(), now, payment, service, visit.getInsuranceCoverageRate())
                : PaymentItem.createClinicItem(ids.next(), now, payment, service, BigDecimal.ONE);
        payment.addItem(item);
        return payment;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VisitResponse> getRoomQueue(UUID roomId, LocalDate date) {
        LocalDate queryDate = date != null ? date : LocalDate.now(clock.withZone(CLINIC_ZONE));
        UUID targetRoomId = roomId != null ? roomId : currentAssignedRoom();
        List<Visit> visits = targetRoomId == null
                ? visitRepository.findAllByQueueDate(queryDate)
                : visitRepository.findRoomQueue(targetRoomId, queryDate);
        return visits.stream().map(this::toResponse).toList();
    }

    private UUID currentAssignedRoom() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        try {
            return staffRoomAssignmentRepository
                    .findByStaffAccountIdAndRemovedAtIsNull(UUID.fromString(jwt.getSubject()))
                    .map(StaffRoomAssignment::getRoomId)
                    .orElse(null);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    @Override
    @Transactional
    public VisitResponse startExamination(UUID visitId) {
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        if (visit.getStatus() != VisitStatus.WAITING) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        UUID doctorId = staffRoomAssignmentRepository.findByRoomIdAndRemovedAtIsNull(visit.getRoomId())
                .map(StaffRoomAssignment::getStaffAccountId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_AVAILABLE));
        visit.startExamination(doctorId);
        return toResponse(visitRepository.save(visit));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomQueueStatusResponse> getRoomQueueStatus(LocalDate date) {
        LocalDate queryDate = date != null ? date : LocalDate.now(clock.withZone(CLINIC_ZONE));
        Map<UUID, List<VisitRepository.RoomQueueCount>> countsByRoom = visitRepository.countRoomQueues(queryDate)
                .stream().collect(Collectors.groupingBy(VisitRepository.RoomQueueCount::getRoomId));
        return roomRepository.findByTypeAndStatusOrderByRoomCodeAsc(RoomType.EXAMINATION, ActivationStatus.ACTIVE)
                .stream()
                .map(room -> toQueueStatus(room, countsByRoom.getOrDefault(room.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VisitTicketResponse getTicket(UUID visitId) {
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        return toTicketResponse(visit, false);
    }

    @Override
    @Transactional
    public VisitTicketResponse prepareTicketPrint(StaffPrincipal principal, UUID visitId) {
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        boolean reprint = auditLogService.hasAction(AuditLogAction.PRINT, VISIT_AUDIT_ENTITY, visitId);
        auditLogService.recordBusinessChange(
                principal.accountId(), AuditLogAction.PRINT, VISIT_AUDIT_ENTITY, visitId,
                visit.getSovaovien(), Map.of("reprint", reprint), clock.instant());
        return toTicketResponse(visit, reprint);
    }

    private VisitTicketResponse toTicketResponse(Visit visit, boolean reprint) {
        Patient patient = patientRepository.findById(visit.getPatientId())
                .orElseThrow(() -> new AppException(ErrorCode.PATIENT_NOT_FOUND));
        Room room = roomRepository.findById(visit.getRoomId())
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_AVAILABLE));
        Payment payment = paymentRepository.findByVisitIdAndType(visit.getId(), PaymentType.EXAMINATION_FEE)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        List<String> doctorNames = activeDoctorNames(room.getId());
        String facilityName = requireFacilityName();
        return new VisitTicketResponse(
                visit.getId(), facilityName, visit.getSovaovien(), patient.getMabenhnhan(),
                patient.getHoten(), patient.getNgaysinh().getYear(), patientGender(patient), visit.getQueueNumber(),
                room.getRoomName(), room.getLocation(), doctorNames, visit.getDoituongbnLoai(),
                visit.getInsuranceCoverageRate(), visit.isPriority(),
                visit.getPriorityReason() == null ? null : visit.getPriorityReason().name(),
                visit.getThoigianvaovien(), payment.getPatientTotalAmount(), visit.getStatus(), reprint);
    }

    private static VisitStatus initialStatusFor(FeeCalculation fee) {
        return fee.patientAmount().signum() == 0
                ? VisitStatus.WAITING
                : VisitStatus.WAITING_FEE_PAYMENT;
    }

    private List<String> activeDoctorNames(UUID roomId) {
        List<UUID> assignedAccountIds = staffRoomAssignmentRepository
                .findAllByRoomIdAndRemovedAtIsNull(roomId).stream()
                .map(StaffRoomAssignment::getStaffAccountId)
                .toList();
        return staffAccountRepository.findAllById(assignedAccountIds).stream()
                .filter(account -> account.getRole() == Role.DOCTOR)
                .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
                .map(account -> staffDisplayName(account.getAcademicTitle(), account.getFullName()))
                .sorted()
                .toList();
    }

    private String requireFacilityName() {
        return clinicFacilityRepository.findCurrent()
                .map(facility -> facility.getTenCskcb())
                .orElseThrow(() -> new AppException(ErrorCode.CLINIC_FACILITY_NOT_CONFIGURED));
    }

    private static String staffDisplayName(String academicTitle, String fullName) {
        return academicTitle == null || academicTitle.isBlank()
                ? fullName
                : academicTitle.strip() + " " + fullName;
    }

    private static String patientGender(Patient patient) {
        return switch (patient.getGioitinh()) {
            case 1 -> "Nam";
            case 2 -> "Nữ";
            default -> "Khác";
        };
    }

    private RoomQueueStatusResponse toQueueStatus(Room room, List<VisitRepository.RoomQueueCount> counts) {
        long waiting = 0;
        long inExamination = 0;
        long waitingParaclinical = 0;
        long waitingConclusion = 0;
        long skipped = 0;
        for (VisitRepository.RoomQueueCount count : counts) {
            if (Boolean.TRUE.equals(count.getSkipped())) {
                skipped += count.getTotal();
                continue;
            }
            switch (count.getStatus()) {
                case WAITING -> waiting += count.getTotal();
                case IN_EXAMINATION -> inExamination += count.getTotal();
                case WAITING_PARACLINICAL -> waitingParaclinical += count.getTotal();
                case WAITING_CONCLUSION -> waitingConclusion += count.getTotal();
                default -> { }
            }
        }
        return new RoomQueueStatusResponse(
                room.getId(), room.getRoomCode(), room.getRoomName(), room.getLocation(), waiting,
                inExamination, waitingParaclinical, waitingConclusion, skipped);
    }

    private VisitResponse toResponse(Visit visit) {
        Patient patient = patientRepository.findById(visit.getPatientId()).orElse(null);
        Room room = roomRepository.findById(visit.getRoomId()).orElse(null);
        String gender = patient == null ? null : patientGender(patient);
        return new VisitResponse(
                visit.getId(), visit.getSovaovien(), visit.getPatientId(),
                patient == null ? "—" : patient.getHoten(), patient == null ? "—" : patient.getMabenhnhan(),
                patient == null ? null : patient.getNgaysinh().toString(), gender,
                patient == null ? null : patient.getSodienthoai(), visit.getRoomId(),
                room == null ? null : room.getRoomName(), visit.getDoctorAccountId(),
                visit.getRegisteredByAccountId(), visit.getThoigianvaovien(), visit.getDenkhambenhluc(),
                visit.getDoituongbnLoai(), visit.getMabhyt(), visit.getTungaybhyt(), visit.getDenngaybhyt(),
                visit.getNoidangkykcbbd(), visit.getLydotiepnhan(), visit.getNoigioithieuLoai(),
                visit.getLydovaovien(), visit.isPriority(),
                visit.getPriorityReason() == null ? null : visit.getPriorityReason().name(), visit.getQueueDate(),
                visit.getQueueNumber(), visit.getStatus(), visit.isSkipped(), visit.getSkippedAt(),
                visit.getCompletedAt());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static boolean hasConstraint(Throwable exception, String constraintName) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(constraintName)) {
                return true;
            }
        }
        return false;
    }

    private record FeeCalculation(
            BigDecimal appliedPrice,
            BigDecimal insuranceAmount,
            BigDecimal patientAmount,
            boolean insuranceCovered) {
    }

    private record InsuranceData(
            String submittedCode,
            String socialInsuranceNumber,
            LocalDate validFrom,
            LocalDate validTo,
            String primaryCareFacilityCode,
            String primaryCareFacility,
            Short coverageRate,
            VisitInsuranceVerifyStatus status) {

        static InsuranceData notApplicable() {
            return new InsuranceData(null, null, null, null, null, null, null,
                    VisitInsuranceVerifyStatus.NOT_APPLICABLE);
        }
    }
}
