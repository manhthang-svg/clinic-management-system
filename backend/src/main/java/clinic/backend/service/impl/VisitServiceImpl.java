package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import clinic.backend.dto.request.CreateVisitRequest;
import clinic.backend.dto.response.RoomQueueStatusResponse;
import clinic.backend.dto.response.DoctorQueueItemResponse;
import clinic.backend.dto.response.DoctorQueueResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.VisitFeePreviewResponse;
import clinic.backend.dto.response.VisitResponse;
import clinic.backend.dto.response.VisitTicketResponse;
import clinic.backend.entity.FollowUpAppointment;
import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.Department;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Payment;
import clinic.backend.entity.PaymentItem;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.entity.VisitTransfer;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.DoctorQueueTab;
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
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.FollowUpAppointmentRepository;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.repository.RoomQueueCounterRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.repository.VisitTransferRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.DutyShiftConfigService;
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
    private static final List<VisitStatus> DOCTOR_QUEUE_STATUSES = List.of(
            VisitStatus.WAITING,
            VisitStatus.IN_EXAMINATION,
            VisitStatus.WAITING_PARACLINICAL,
            VisitStatus.WAITING_CONCLUSION);

    private final MedicalServiceRepository medicalServiceRepository;
    private final PaymentRepository paymentRepository;
    private final PatientRepository patientRepository;
    private final RoomRepository roomRepository;
    private final DepartmentRepository departmentRepository;
    private final VisitRepository visitRepository;
    private final VisitTransferRepository visitTransferRepository;
    private final StaffRoomAssignmentRepository staffRoomAssignmentRepository;
    private final DutyScheduleRepository dutyScheduleRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final ClinicFacilityRepository clinicFacilityRepository;
    private final FollowUpAppointmentRepository appointmentRepository;
    private final InsuranceVerificationService insuranceVerificationService;
    private final RoomQueueCounterRepository queueCounterRepository;
    private final AuditLogService auditLogService;
    private final UuidV7Generator ids;
    private final ExaminationRoomDuty examinationRoomDuty;
    private final DutyShiftConfigService dutyShiftConfigService;
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
                initialStatusFor(request.doituongbnLoai(), fee),
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
        // BR-96: an active examination room with a doctor on duty in the current registration shift.
        if (room.getStatus() != ActivationStatus.ACTIVE
                || room.getType() != RoomType.EXAMINATION
                || examinationRoomDuty.onDutyDoctor(roomId, clock.instant()).isEmpty()) {
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
        PriorityReason reason;
        try {
            reason = PriorityReason.valueOf(value == null ? "" : value.strip());
        } catch (IllegalArgumentException exception) {
            reason = null;
        }
        // Legacy reasons are kept for old visits only; a new visit uses the current BR-14 groups.
        if (reason == null || reason.isLegacy()) {
            throw new AppException(
                    ErrorCode.REQUEST_VALIDATION_FAILED,
                    Map.of("priorityReason", "Vui lòng chọn lý do ưu tiên hợp lệ"));
        }
        return reason;
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
        UUID targetRoomId;
        if (currentCallerIsDoctor()) {
            targetRoomId = currentAssignedRoom();
            if (targetRoomId == null) {
                throw new AppException(ErrorCode.ROOM_ASSIGNMENT_REQUIRED);
            }
        } else {
            targetRoomId = roomId;
        }
        List<Visit> visits = targetRoomId == null
                ? visitRepository.findAllByQueueDate(queryDate)
                : visitRepository.findRoomQueue(targetRoomId, queryDate);
        return visits.stream().map(this::toResponse).toList();
    }

    private static boolean currentCallerIsDoctor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_DOCTOR".equals(authority.getAuthority()));
    }

    @Override
    @Transactional(readOnly = true)
    public DoctorQueueResponse getDoctorQueue(
            StaffPrincipal principal,
            DoctorQueueTab tab,
            String keyword,
            Boolean priority,
            VisitDoituongbnLoai visitType,
            VisitStatus interruptedStage,
            int page,
            int size) {
        validateInterruptedStageFilter(tab, interruptedStage);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, CLINIC_ZONE);
        LocalTime localTime = LocalTime.ofInstant(now, CLINIC_ZONE);
        StaffAccount doctor = staffAccountRepository.findById(principal.accountId())
                .filter(account -> account.getRole() == Role.DOCTOR && account.canAuthenticate())
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));
        Room room = staffRoomAssignmentRepository
                .findByStaffAccountIdAndRemovedAtIsNull(principal.accountId())
                .flatMap(assignment -> roomRepository.findById(assignment.getRoomId()))
                .filter(candidate -> candidate.getType() == RoomType.EXAMINATION)
                .filter(candidate -> candidate.getStatus() == ActivationStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_ASSIGNMENT_REQUIRED));

        List<Visit> visits = visitRepository.findAllByRoomIdAndQueueDateAndStatusIn(
                room.getId(), today, DOCTOR_QUEUE_STATUSES);
        Map<UUID, Patient> patientsById = patientRepository.findAllById(
                        visits.stream().map(Visit::getPatientId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Patient::getId, patient -> patient));
        Map<UUID, VisitTransfer> latestTransfers = latestTransfers(visits, room.getId());
        Map<UUID, String> sourceRoomNames = sourceRoomNames(latestTransfers.values());

        List<DoctorQueueItemResponse> allItems = visits.stream()
                .map(visit -> toDoctorQueueItem(
                        visit,
                        patientsById.get(visit.getPatientId()),
                        latestTransfers.get(visit.getId()),
                        sourceRoomNames))
                .sorted(this::compareDoctorQueueItems)
                .toList();

        String normalizedKeyword = keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT);
        List<DoctorQueueItemResponse> filtered = allItems.stream()
                .filter(item -> matchesTab(item, tab))
                .filter(item -> priority == null || item.isPriority() == priority)
                .filter(item -> visitType == null || item.doituongbnLoai() == visitType)
                .filter(item -> interruptedStage == null || item.status() == interruptedStage)
                .filter(item -> matchesKeyword(item, normalizedKeyword))
                .toList();
        int fromIndex = (int) Math.min((long) page * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / size);
        PageResponse<DoctorQueueItemResponse> pageResponse = new PageResponse<>(
                filtered.subList(fromIndex, toIndex),
                new PageResponse.PageMetadata(page, size, filtered.size(), totalPages));

        return new DoctorQueueResponse(
                room.getId(), room.getRoomCode(), room.getRoomName(),
                staffDisplayName(doctor.getAcademicTitle(), doctor.getFullName()),
                isOnDuty(principal.accountId(), room.getId(), today, localTime),
                now,
                countTab(allItems, DoctorQueueTab.WAITING),
                countTab(allItems, DoctorQueueTab.IN_EXAMINATION),
                countWaitingParaclinical(allItems),
                countTab(allItems, DoctorQueueTab.WAITING_CONCLUSION),
                countTab(allItems, DoctorQueueTab.SKIPPED),
                pageResponse);
    }

    private static long countTab(List<DoctorQueueItemResponse> items, DoctorQueueTab tab) {
        return items.stream().filter(item -> matchesTab(item, tab)).count();
    }

    private static long countWaitingParaclinical(List<DoctorQueueItemResponse> items) {
        return items.stream()
                .filter(item -> !item.isSkipped() && item.status() == VisitStatus.WAITING_PARACLINICAL)
                .count();
    }

    // Waiting for Paraclinical stays in the patient list so the Doctor can review the examination and orders.
    private static boolean matchesTab(DoctorQueueItemResponse item, DoctorQueueTab tab) {
        return switch (tab) {
            case PATIENT_LIST -> !item.isSkipped()
                    && (item.status() == VisitStatus.WAITING
                        || item.status() == VisitStatus.IN_EXAMINATION
                        || item.status() == VisitStatus.WAITING_PARACLINICAL);
            case WAITING -> !item.isSkipped() && item.status() == VisitStatus.WAITING;
            case IN_EXAMINATION -> !item.isSkipped() && item.status() == VisitStatus.IN_EXAMINATION;
            case WAITING_CONCLUSION -> !item.isSkipped() && item.status() == VisitStatus.WAITING_CONCLUSION;
            case SKIPPED -> item.isSkipped();
        };
    }

    private static boolean matchesKeyword(DoctorQueueItemResponse item, String keyword) {
        if (keyword.isEmpty()) {
            return true;
        }
        return containsIgnoreCase(item.patientName(), keyword)
                || containsIgnoreCase(item.patientCode(), keyword)
                || containsIgnoreCase(item.mabhyt(), keyword);
    }

    private static void validateInterruptedStageFilter(
            DoctorQueueTab tab,
            VisitStatus interruptedStage) {
        if (interruptedStage == null) {
            return;
        }
        if (tab != DoctorQueueTab.SKIPPED || !isDoctorWaitingStage(interruptedStage)) {
            throw new AppException(ErrorCode.REQUEST_PARAMETER_INVALID);
        }
    }

    private static boolean containsIgnoreCase(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private Map<UUID, VisitTransfer> latestTransfers(List<Visit> visits, UUID roomId) {
        if (visits.isEmpty()) {
            return Map.of();
        }
        List<UUID> visitIds = visits.stream().map(Visit::getId).toList();
        Map<UUID, VisitTransfer> latest = new HashMap<>();
        visitTransferRepository.findAllByVisitIdInAndToRoomIdOrderByTransferredAtDesc(visitIds, roomId)
                .forEach(transfer -> latest.putIfAbsent(transfer.getVisitId(), transfer));
        return latest;
    }

    private Map<UUID, String> sourceRoomNames(java.util.Collection<VisitTransfer> transfers) {
        Set<UUID> roomIds = transfers.stream()
                .map(VisitTransfer::getFromRoomId)
                .collect(Collectors.toCollection(HashSet::new));
        if (roomIds.isEmpty()) {
            return Map.of();
        }
        return roomRepository.findAllById(roomIds).stream()
                .collect(Collectors.toMap(Room::getId, Room::getRoomName));
    }

    private DoctorQueueItemResponse toDoctorQueueItem(
            Visit visit,
            Patient patient,
            VisitTransfer transfer,
            Map<UUID, String> sourceRoomNames) {
        return new DoctorQueueItemResponse(
                visit.getId(),
                patient == null ? "—" : patient.getHoten(),
                patient == null ? "—" : patient.getMabenhnhan(),
                patient == null ? null : patient.getNgaysinh(),
                patient == null ? null : patientGender(patient),
                visit.getQueueNumber(), visit.isPriority(),
                visit.getPriorityReason() == null ? null : visit.getPriorityReason().name(),
                visit.getDoituongbnLoai(), visit.getMabhyt(), visit.getLydovaovien(),
                visit.getThoigianvaovien(), visit.getStatus(), visit.isSkipped(), visit.getSkippedAt(),
                canAcceptSkippedVisit(visit),
                transfer == null ? null : sourceRoomNames.get(transfer.getFromRoomId()),
                transfer == null ? null : transfer.getTransferredAt(),
                buildDoctorQueueNote(visit, transfer, sourceRoomNames));
    }

    private static String buildDoctorQueueNote(
            Visit visit,
            VisitTransfer transfer,
            Map<UUID, String> sourceRoomNames) {
        List<String> notes = new ArrayList<>(2);
        if (visit.isPriority() && visit.getPriorityReason() != null) {
            notes.add("Ưu tiên: " + visit.getPriorityReason().label());
        }
        if (transfer != null) {
            String sourceRoomName = sourceRoomNames.get(transfer.getFromRoomId());
            if (sourceRoomName != null && !sourceRoomName.isBlank()) {
                notes.add("Chuyển từ: " + sourceRoomName);
            }
        }
        return notes.isEmpty() ? null : String.join("\n", notes);
    }

    private int compareDoctorQueueItems(DoctorQueueItemResponse left, DoctorQueueItemResponse right) {
        int category = Integer.compare(queueCategory(left), queueCategory(right));
        if (category != 0) {
            return category;
        }
        if (left.isSkipped()) {
            int skippedAt = Comparator.nullsLast(Comparator.<Instant>naturalOrder())
                    .compare(left.skippedAt(), right.skippedAt());
            if (skippedAt != 0) {
                return skippedAt;
            }
        } else if (isWaiting(left)) {
            int group = Integer.compare(waitingGroup(left), waitingGroup(right));
            if (group != 0) {
                return group;
            }
            if (waitingGroup(left) == 1) {
                int transferredAt = Comparator.nullsLast(Comparator.<Instant>naturalOrder())
                        .compare(left.transferredAt(), right.transferredAt());
                if (transferredAt != 0) {
                    return transferredAt;
                }
            }
        } else if (left.status() == VisitStatus.WAITING_CONCLUSION) {
            int priority = Boolean.compare(right.isPriority(), left.isPriority());
            if (priority != 0) {
                return priority;
            }
        }
        return Integer.compare(left.queueNumber(), right.queueNumber());
    }

    private static int queueCategory(DoctorQueueItemResponse item) {
        if (item.isSkipped()) {
            return 4;
        }
        // The patient being examined leads the merged patient list, ahead of those still waiting,
        // and patients away for paraclinical services follow the waiting ones.
        return switch (item.status()) {
            case IN_EXAMINATION -> 0;
            case WAITING -> 1;
            case WAITING_PARACLINICAL -> 2;
            case WAITING_CONCLUSION -> 3;
            default -> 5;
        };
    }

    private static boolean isWaiting(DoctorQueueItemResponse item) {
        return !item.isSkipped() && item.status() == VisitStatus.WAITING;
    }

    private static int waitingGroup(DoctorQueueItemResponse item) {
        if (item.isPriority()) {
            return 0;
        }
        return item.transferredAt() == null ? 2 : 1;
    }

    private boolean isOnDuty(UUID doctorId, UUID roomId, LocalDate date, LocalTime time) {
        return dutyShiftConfigService.shiftAt(date, time)
                .flatMap(shift -> dutyScheduleRepository.findByWorkDateAndShiftAndStaffAccountId(date, shift, doctorId))
                .map(DutySchedule::getRoomId)
                .filter(roomId::equals)
                .isPresent();
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
    public VisitResponse startExamination(StaffPrincipal principal, UUID visitId) {
        UUID roomId = requireDoctorRoomOnDuty(principal);
        Visit visit = requireVisitInRoom(visitId, roomId);
        if (visit.isSkipped() || !isDoctorWaitingStage(visit.getStatus())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }

        Instant now = clock.instant();
        if (visit.getStatus() == VisitStatus.WAITING) {
            visit.startExamination(principal.accountId(), now);
        } else {
            visit.continueConclusion(principal.accountId(), now);
        }
        auditLogService.recordBusinessChange(
                principal.accountId(), AuditLogAction.UPDATE, VISIT_AUDIT_ENTITY, visit.getId(),
                visit.getSovaovien(), Map.of("action", "START", "status", visit.getStatus().name()), now);
        return toResponse(visitRepository.save(visit));
    }

    @Override
    @Transactional
    public VisitResponse skipVisit(StaffPrincipal principal, UUID visitId) {
        UUID roomId = requireDoctorRoomOnDuty(principal);
        Visit visit = requireVisitInRoom(visitId, roomId);
        if (visit.isSkipped() || !isDoctorWaitingStage(visit.getStatus())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }

        Instant now = clock.instant();
        visit.markSkipped(now);
        auditLogService.recordBusinessChange(
                principal.accountId(), AuditLogAction.UPDATE, VISIT_AUDIT_ENTITY, visit.getId(),
                visit.getSovaovien(), Map.of("action", "SKIP", "status", visit.getStatus().name()), now);
        return toResponse(visitRepository.save(visit));
    }

    @Override
    @Transactional
    public VisitResponse acceptSkippedVisit(StaffPrincipal principal, UUID visitId) {
        UUID roomId = requireDoctorRoomOnDuty(principal);
        Visit visit = requireVisitInRoom(visitId, roomId);
        if (!canAcceptSkippedVisit(visit)) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }

        VisitStatus interruptedStage = visit.getStatus();
        Instant now = clock.instant();
        visit.acceptSkipped(principal.accountId(), now);
        auditLogService.recordBusinessChange(
                principal.accountId(), AuditLogAction.UPDATE, VISIT_AUDIT_ENTITY, visit.getId(),
                visit.getSovaovien(), Map.of(
                        "action", "ACCEPT_SKIPPED",
                        "interruptedStage", interruptedStage.name(),
                        "status", visit.getStatus().name()), now);
        return toResponse(visitRepository.save(visit));
    }

    private Visit requireVisitInRoom(UUID visitId, UUID roomId) {
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        if (!roomId.equals(visit.getRoomId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        return visit;
    }

    private static boolean isDoctorWaitingStage(VisitStatus status) {
        return status == VisitStatus.WAITING || status == VisitStatus.WAITING_CONCLUSION;
    }

    private static boolean canAcceptSkippedVisit(Visit visit) {
        if (!visit.isSkipped() || !isDoctorWaitingStage(visit.getStatus())) {
            return false;
        }
        return visit.getStatus() != VisitStatus.WAITING_CONCLUSION
                || visit.getDoctorAccountId() != null;
    }

    private UUID requireDoctorRoomOnDuty(StaffPrincipal principal) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, CLINIC_ZONE);
        LocalTime localTime = LocalTime.ofInstant(now, CLINIC_ZONE);
        StaffAccount doctor = staffAccountRepository.findById(principal.accountId())
                .filter(account -> account.getRole() == Role.DOCTOR && account.canAuthenticate())
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));
        UUID roomId = staffRoomAssignmentRepository
                .findByStaffAccountIdAndRemovedAtIsNull(doctor.getId())
                .map(StaffRoomAssignment::getRoomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_ASSIGNMENT_REQUIRED));
        roomRepository.findById(roomId)
                .filter(room -> room.getType() == RoomType.EXAMINATION)
                .filter(room -> room.getStatus() == ActivationStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_ASSIGNMENT_REQUIRED));
        if (!isOnDuty(doctor.getId(), roomId, today, localTime)) {
            throw new AppException(ErrorCode.DOCTOR_OFF_DUTY);
        }
        return roomId;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomQueueStatusResponse> getRoomQueueStatus(LocalDate date) {
        LocalDate queryDate = date != null ? date : LocalDate.now(clock.withZone(CLINIC_ZONE));
        Map<UUID, List<VisitRepository.RoomQueueCount>> countsByRoom = visitRepository.countRoomQueues(queryDate)
                .stream().collect(Collectors.groupingBy(VisitRepository.RoomQueueCount::getRoomId));
        List<Room> rooms = roomRepository.findByTypeAndStatusOrderByRoomCodeAsc(
                RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        Map<UUID, String> departmentNames = departmentRepository.findAllById(rooms.stream()
                        .map(Room::getDepartmentId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Department::getId, Department::getDepartmentName));
        Map<UUID, List<String>> doctorsByRoom = activeDoctorNamesByRoom();
        // UC-42 bước 2: nhóm theo khoa (tên khoa A–Z), trong khoa giữ thứ tự mã phòng.
        return rooms.stream()
                .map(room -> toQueueStatus(
                        room,
                        departmentNames.get(room.getDepartmentId()),
                        doctorsByRoom.getOrDefault(room.getId(), List.of()),
                        countsByRoom.getOrDefault(room.getId(), List.of())))
                .sorted(Comparator.comparing(
                        RoomQueueStatusResponse::departmentName,
                        Comparator.nullsLast(Comparator.<String>naturalOrder())))
                .toList();
    }

    /** Active doctors currently assigned to each room, as "academic title + full name" (UC-42 bước 2). */
    private Map<UUID, List<String>> activeDoctorNamesByRoom() {
        List<StaffRoomAssignment> assignments = staffRoomAssignmentRepository.findByRemovedAtIsNull();
        Map<UUID, String> doctorNames = staffAccountRepository.findAllById(assignments.stream()
                        .map(StaffRoomAssignment::getStaffAccountId)
                        .collect(Collectors.toSet()))
                .stream()
                .filter(account -> account.getRole() == Role.DOCTOR)
                .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
                .collect(Collectors.toMap(
                        StaffAccount::getId,
                        account -> staffDisplayName(account.getAcademicTitle(), account.getFullName())));
        return assignments.stream()
                .filter(assignment -> doctorNames.containsKey(assignment.getStaffAccountId()))
                .collect(Collectors.groupingBy(
                        StaffRoomAssignment::getRoomId,
                        Collectors.collectingAndThen(
                                Collectors.mapping(
                                        assignment -> doctorNames.get(assignment.getStaffAccountId()),
                                        Collectors.toList()),
                                names -> names.stream().sorted().toList())));
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

    /**
     * BR-15: an insurance visit joins the waiting list at once and its fee stays pending until after the
     * conclusion; a self-paid visit waits for the cashier unless the patient pays nothing.
     */
    private static VisitStatus initialStatusFor(VisitDoituongbnLoai visitType, FeeCalculation fee) {
        if (visitType == VisitDoituongbnLoai.BHYT || fee.patientAmount().signum() == 0) {
            return VisitStatus.WAITING;
        }
        return VisitStatus.WAITING_FEE_PAYMENT;
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

    private RoomQueueStatusResponse toQueueStatus(
            Room room,
            String departmentName,
            List<String> doctorNames,
            List<VisitRepository.RoomQueueCount> counts) {
        long waitingPayment = 0;
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
                case WAITING_FEE_PAYMENT -> waitingPayment += count.getTotal();
                case WAITING -> waiting += count.getTotal();
                case IN_EXAMINATION -> inExamination += count.getTotal();
                case WAITING_PARACLINICAL -> waitingParaclinical += count.getTotal();
                case WAITING_CONCLUSION -> waitingConclusion += count.getTotal();
                default -> { }
            }
        }
        return new RoomQueueStatusResponse(
                room.getId(), room.getRoomCode(), room.getRoomName(), room.getLocation(),
                room.getDepartmentId(), departmentName, doctorNames, !doctorNames.isEmpty(),
                waitingPayment, waiting, inExamination, waitingParaclinical, waitingConclusion, skipped);
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
