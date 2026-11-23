package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import clinic.backend.dto.request.TransferVisitRequest;
import clinic.backend.dto.response.TransferRoomOptionResponse;
import clinic.backend.dto.response.VisitTransferResponse;
import clinic.backend.entity.ClinicalExamination;
import clinic.backend.entity.Department;
import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.entity.Visit;
import clinic.backend.entity.VisitTransfer;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.DutyShift;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.Role;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicalExaminationRepository;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.RoomQueueCounterRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.repository.VisitTransferRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.DutyShiftConfigService;
import clinic.backend.service.VisitTransferService;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-48 Transfer Patient to Another Room, as decided in docs/state-transitions.md (V11, Q-24, Q-33) and
 * docs/business-context.md (BR-24, OQ-05): only a called-in visit whose examination has not been saved yet.
 */
@Service
@RequiredArgsConstructor
public class VisitTransferServiceImpl implements VisitTransferService {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int REASON_MIN = 5;
    private static final int REASON_MAX = 500;

    private final VisitRepository visitRepository;
    private final VisitTransferRepository visitTransferRepository;
    private final RoomRepository roomRepository;
    private final DepartmentRepository departmentRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final StaffRoomAssignmentRepository assignmentRepository;
    private final DutyScheduleRepository dutyScheduleRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final ClinicalExaminationRepository examinationRepository;
    private final RoomQueueCounterRepository queueCounterRepository;
    private final ExaminationRoomDuty examinationRoomDuty;
    // The shift hours the Admin configured, as the waiting list and the examination use (no hard-coded hours).
    private final DutyShiftConfigService dutyShiftConfigService;
    private final UuidV7Generator ids;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<TransferRoomOptionResponse> getTransferRooms(StaffPrincipal principal, UUID visitId) {
        Instant now = clock.instant();
        UUID sourceRoomId = requireDoctorRoomOnDuty(principal, now);
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new AppException(ErrorCode.VISIT_NOT_FOUND));
        requireTransferable(visit, sourceRoomId);

        List<Room> rooms = roomRepository.findByTypeAndStatusOrderByRoomCodeAsc(
                        RoomType.EXAMINATION, ActivationStatus.ACTIVE).stream()
                .filter(room -> !room.getId().equals(sourceRoomId))
                .toList();
        Map<UUID, String> departmentNames = departmentRepository.findAllById(
                        rooms.stream().map(Room::getDepartmentId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Department::getId, Department::getDepartmentName));
        List<TransferRoomOptionResponse> options = new ArrayList<>();
        for (Room room : rooms) {
            examinationRoomDuty.onDutyDoctor(room.getId(), now).ifPresent(doctor -> options.add(
                    new TransferRoomOptionResponse(
                            room.getId(), room.getRoomCode(), room.getRoomName(), room.getLocation(),
                            departmentNames.get(room.getDepartmentId()), ExaminationRoomDuty.displayName(doctor))));
        }
        return options;
    }

    @Override
    @Transactional
    public VisitTransferResponse transfer(StaffPrincipal principal, UUID visitId, TransferVisitRequest request) {
        Instant now = clock.instant();
        UUID sourceRoomId = requireDoctorRoomOnDuty(principal, now);
        String reason = request.reason() == null ? "" : request.reason().strip();
        if (reason.length() < REASON_MIN || reason.length() > REASON_MAX) {
            throw new AppException(ErrorCode.TRANSFER_REASON_INVALID);
        }
        // Locked: a second transfer of the same visit waits here, then finds it no longer transferable (EF-06).
        Visit visit = visitRepository.findByIdForUpdate(visitId)
                .orElseThrow(() -> new AppException(ErrorCode.VISIT_NOT_FOUND));
        requireTransferable(visit, sourceRoomId);
        Room destination = requireEligibleDestination(request.toRoomId(), sourceRoomId, now);

        int oldQueueNumber = visit.getQueueNumber();
        int newQueueNumber = queueCounterRepository.takeNext(destination.getId(), visit.getQueueDate());
        visit.transferTo(destination.getId(), newQueueNumber, now);
        visitRepository.save(visit);
        String notes = request.notes() == null || request.notes().isBlank() ? null : request.notes().strip();
        visitTransferRepository.save(new VisitTransfer(
                ids.next(), visit.getId(), sourceRoomId, destination.getId(), reason, notes,
                oldQueueNumber, newQueueNumber, principal.accountId(), now));

        return new VisitTransferResponse(
                visit.getId(), destination.getId(), destination.getRoomCode(), destination.getRoomName(),
                destination.getLocation(), newQueueNumber, now);
    }

    /** EF-05 / EF-01: still called in, in this room, and the examination never saved (OQ-05). */
    private void requireTransferable(Visit visit, UUID sourceRoomId) {
        if (!sourceRoomId.equals(visit.getRoomId())
                || visit.isSkipped()
                || visit.getStatus() != VisitStatus.IN_EXAMINATION) {
            throw new AppException(ErrorCode.VISIT_NOT_TRANSFERABLE);
        }
        Optional<MedicalRecord> record = medicalRecordRepository.findByVisitId(visit.getId());
        if (record.isEmpty()) return;
        if (record.get().getStatus() != MedicalRecordStatus.OPEN) {
            throw new AppException(ErrorCode.MEDICAL_RECORD_ALREADY_OPENED);
        }
        // Entering the examination screen creates the sheet at version 0; every save increments the version.
        boolean saved = examinationRepository.findByMedicalRecordId(record.get().getId())
                .map(ClinicalExamination::getVersion)
                .filter(version -> version > 0)
                .isPresent();
        if (saved) {
            throw new AppException(ErrorCode.MEDICAL_RECORD_ALREADY_OPENED);
        }
    }

    /** EF-04 / BR-96: another active examination room with a Doctor on duty now. */
    private Room requireEligibleDestination(UUID roomId, UUID sourceRoomId, Instant now) {
        return roomRepository.findById(roomId)
                .filter(room -> !room.getId().equals(sourceRoomId))
                .filter(room -> room.getType() == RoomType.EXAMINATION)
                .filter(room -> room.getStatus() == ActivationStatus.ACTIVE)
                .filter(room -> examinationRoomDuty.onDutyDoctor(room.getId(), now).isPresent())
                .orElseThrow(() -> new AppException(ErrorCode.TRANSFER_ROOM_NOT_ELIGIBLE));
    }

    /** The transferring Doctor's room; EF-08 (MSG48) when outside a shift scheduled in that room. */
    private UUID requireDoctorRoomOnDuty(StaffPrincipal principal, Instant now) {
        StaffAccount doctor = staffAccountRepository.findById(principal.accountId())
                .filter(account -> account.getRole() == Role.DOCTOR)
                .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));
        UUID roomId = assignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(doctor.getId())
                .map(StaffRoomAssignment::getRoomId)
                .flatMap(roomRepository::findById)
                .filter(room -> room.getType() == RoomType.EXAMINATION)
                .filter(room -> room.getStatus() == ActivationStatus.ACTIVE)
                .map(Room::getId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_ASSIGNMENT_REQUIRED));
        LocalDate today = LocalDate.ofInstant(now, CLINIC_ZONE);
        DutyShift shift = dutyShiftConfigService.shiftAt(today, LocalTime.ofInstant(now, CLINIC_ZONE)).orElse(null);
        boolean onDuty = shift != null && dutyScheduleRepository
                .findByWorkDateAndShiftAndStaffAccountId(today, shift, doctor.getId())
                .map(DutySchedule::getRoomId)
                .filter(roomId::equals)
                .isPresent();
        if (!onDuty) {
            throw new AppException(ErrorCode.DOCTOR_OFF_DUTY);
        }
        return roomId;
    }
}
