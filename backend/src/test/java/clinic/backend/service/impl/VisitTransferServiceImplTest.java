package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UC-48 Transfer Patient to Another Room. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VisitTransferServiceImplTest {

    // 09:00 in Vietnam: inside the morning shift.
    private static final Instant NOW = Instant.parse("2026-11-20T02:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final StaffPrincipal DOCTOR = new StaffPrincipal(DOCTOR_ID, UUID.randomUUID());

    @Mock VisitRepository visitRepository;
    @Mock VisitTransferRepository visitTransferRepository;
    @Mock RoomRepository roomRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock StaffAccountRepository staffAccountRepository;
    @Mock StaffRoomAssignmentRepository assignmentRepository;
    @Mock DutyScheduleRepository dutyScheduleRepository;
    @Mock MedicalRecordRepository medicalRecordRepository;
    @Mock ClinicalExaminationRepository examinationRepository;
    @Mock RoomQueueCounterRepository queueCounterRepository;
    @Mock ExaminationRoomDuty examinationRoomDuty;
    @Mock DutyShiftConfigService dutyShiftConfigService;
    @Mock UuidV7Generator ids;

    private VisitTransferServiceImpl service;
    private Room sourceRoom;
    private Room destinationRoom;
    private Visit visit;

    @BeforeEach
    void setUp() {
        service = new VisitTransferServiceImpl(visitRepository, visitTransferRepository, roomRepository,
                departmentRepository, staffAccountRepository, assignmentRepository, dutyScheduleRepository,
                medicalRecordRepository, examinationRepository, queueCounterRepository, examinationRoomDuty,
                dutyShiftConfigService, ids, Clock.fixed(NOW, ZoneOffset.UTC));
        sourceRoom = room("P101", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        destinationRoom = room("P102", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        doctorOnDutyIn(sourceRoom);
        visit = visit(sourceRoom.getId(), VisitStatus.IN_EXAMINATION, false);
        StaffAccount otherDoctor = account(UUID.randomUUID(), Role.DOCTOR, AccountStatus.ACTIVE);
        when(otherDoctor.getFullName()).thenReturn("Trần Thị B");
        when(otherDoctor.getAcademicTitle()).thenReturn("BS.");
        when(examinationRoomDuty.onDutyDoctor(destinationRoom.getId(), NOW)).thenReturn(Optional.of(otherDoctor));
        when(roomRepository.findById(destinationRoom.getId())).thenReturn(Optional.of(destinationRoom));
        when(queueCounterRepository.takeNext(destinationRoom.getId(), TODAY)).thenReturn(7);
        when(ids.next()).thenReturn(UUID.randomUUID());
    }

    @Test
    void listsOtherActiveExaminationRoomsWithADoctorOnDuty() {
        Room noDoctorRoom = room("P103", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        when(visitRepository.findById(visit.getId())).thenReturn(Optional.of(visit));
        when(roomRepository.findByTypeAndStatusOrderByRoomCodeAsc(RoomType.EXAMINATION, ActivationStatus.ACTIVE))
                .thenReturn(List.of(sourceRoom, destinationRoom, noDoctorRoom));
        UUID departmentId = destinationRoom.getDepartmentId();
        Department department = mock(Department.class);
        when(department.getId()).thenReturn(departmentId);
        when(department.getDepartmentName()).thenReturn("Khoa Nội");
        when(departmentRepository.findAllById(any())).thenReturn(List.of(department));
        when(examinationRoomDuty.onDutyDoctor(noDoctorRoom.getId(), NOW)).thenReturn(Optional.empty());

        List<TransferRoomOptionResponse> options = service.getTransferRooms(DOCTOR, visit.getId());

        assertThat(options).singleElement().satisfies(option -> {
            assertThat(option.roomCode()).isEqualTo("P102");
            assertThat(option.departmentName()).isEqualTo("Khoa Nội");
            assertThat(option.doctorName()).isEqualTo("BS. Trần Thị B");
        });
    }

    @Test
    void transfersCalledInVisitAndGivesANewQueueNumber() {
        when(visitRepository.findByIdForUpdate(visit.getId())).thenReturn(Optional.of(visit));

        VisitTransferResponse response = service.transfer(DOCTOR, visit.getId(),
                new TransferVisitRequest(destinationRoom.getId(), "  Cần khám chuyên khoa tim mạch  ", "  "));

        assertThat(response.toRoomCode()).isEqualTo("P102");
        assertThat(response.newQueueNumber()).isEqualTo(7);
        assertThat(response.transferredAt()).isEqualTo(NOW);
        verify(visit).transferTo(destinationRoom.getId(), 7, NOW);
        ArgumentCaptor<VisitTransfer> transfer = ArgumentCaptor.forClass(VisitTransfer.class);
        verify(visitTransferRepository).save(transfer.capture());
        assertThat(transfer.getValue().getReason()).isEqualTo("Cần khám chuyên khoa tim mạch");
        assertThat(transfer.getValue().getNotes()).isNull();
        assertThat(transfer.getValue().getOldQueueNumber()).isEqualTo(3);
    }

    @Test
    void acceptsReasonOfExactlyFiveCharacters() {
        when(visitRepository.findByIdForUpdate(visit.getId())).thenReturn(Optional.of(visit));

        VisitTransferResponse response = service.transfer(DOCTOR, visit.getId(),
                new TransferVisitRequest(destinationRoom.getId(), " Tim m ", null));

        assertThat(response.newQueueNumber()).isEqualTo(7);
    }

    @Test
    void rejectsReasonShorterThanFiveCharactersAfterTrimming() {
        assertThatThrownBy(() -> service.transfer(DOCTOR, visit.getId(),
                new TransferVisitRequest(destinationRoom.getId(), "  Tim  ", null)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TRANSFER_REASON_INVALID));
        verify(visitTransferRepository, never()).save(any());
    }

    @Test
    void rejectsReasonLongerThanFiveHundredCharacters() {
        assertThatThrownBy(() -> service.transfer(DOCTOR, visit.getId(),
                new TransferVisitRequest(destinationRoom.getId(), "a".repeat(501), null)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TRANSFER_REASON_INVALID));
    }

    @Test
    void rejectsVisitThatIsNotInExamination() {
        Visit waiting = visit(sourceRoom.getId(), VisitStatus.WAITING, false);
        when(visitRepository.findByIdForUpdate(waiting.getId())).thenReturn(Optional.of(waiting));

        assertThatThrownBy(() -> service.transfer(DOCTOR, waiting.getId(),
                new TransferVisitRequest(destinationRoom.getId(), "Chuyển khám tim mạch", null)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VISIT_NOT_TRANSFERABLE));
        verify(waiting, never()).transferTo(any(), anyInt(), any());
    }

    @Test
    void rejectsVisitOfAnotherRoom() {
        Visit elsewhere = visit(UUID.randomUUID(), VisitStatus.IN_EXAMINATION, false);
        when(visitRepository.findByIdForUpdate(elsewhere.getId())).thenReturn(Optional.of(elsewhere));

        assertThatThrownBy(() -> service.transfer(DOCTOR, elsewhere.getId(),
                new TransferVisitRequest(destinationRoom.getId(), "Chuyển khám tim mạch", null)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VISIT_NOT_TRANSFERABLE));
    }

    @Test
    void rejectsVisitWhoseExaminationWasAlreadySaved() {
        when(visitRepository.findByIdForUpdate(visit.getId())).thenReturn(Optional.of(visit));
        MedicalRecord record = mock(MedicalRecord.class);
        UUID recordId = UUID.randomUUID();
        when(record.getId()).thenReturn(recordId);
        when(record.getStatus()).thenReturn(MedicalRecordStatus.OPEN);
        when(medicalRecordRepository.findByVisitId(visit.getId())).thenReturn(Optional.of(record));
        ClinicalExamination examination = mock(ClinicalExamination.class);
        when(examination.getVersion()).thenReturn(1L);
        when(examinationRepository.findByMedicalRecordId(recordId)).thenReturn(Optional.of(examination));

        assertThatThrownBy(() -> service.transfer(DOCTOR, visit.getId(),
                new TransferVisitRequest(destinationRoom.getId(), "Chuyển khám tim mạch", null)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEDICAL_RECORD_ALREADY_OPENED));
    }

    @Test
    void rejectsDestinationWithoutADoctorOnDuty() {
        when(visitRepository.findByIdForUpdate(visit.getId())).thenReturn(Optional.of(visit));
        when(examinationRoomDuty.onDutyDoctor(destinationRoom.getId(), NOW)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transfer(DOCTOR, visit.getId(),
                new TransferVisitRequest(destinationRoom.getId(), "Chuyển khám tim mạch", null)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TRANSFER_ROOM_NOT_ELIGIBLE));
    }

    @Test
    void rejectsTransferToTheSameRoom() {
        when(visitRepository.findByIdForUpdate(visit.getId())).thenReturn(Optional.of(visit));

        assertThatThrownBy(() -> service.transfer(DOCTOR, visit.getId(),
                new TransferVisitRequest(sourceRoom.getId(), "Chuyển khám tim mạch", null)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TRANSFER_ROOM_NOT_ELIGIBLE));
    }

    @Test
    void rejectsDoctorOutsideTheScheduledShift() {
        when(dutyShiftConfigService.shiftAt(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transfer(DOCTOR, visit.getId(),
                new TransferVisitRequest(destinationRoom.getId(), "Chuyển khám tim mạch", null)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DOCTOR_OFF_DUTY));
    }

    @Test
    void rejectsDoctorWithoutAnExaminationRoom() {
        when(assignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(DOCTOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTransferRooms(DOCTOR, visit.getId()))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ROOM_ASSIGNMENT_REQUIRED));
    }

    @Test
    void rejectsAccountThatIsNotADoctor() {
        StaffAccount receptionist = account(DOCTOR_ID, Role.RECEPTIONIST, AccountStatus.ACTIVE);
        when(staffAccountRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(receptionist));

        assertThatThrownBy(() -> service.getTransferRooms(DOCTOR, visit.getId()))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    private void doctorOnDutyIn(Room room) {
        UUID roomId = room.getId();
        StaffAccount doctor = account(DOCTOR_ID, Role.DOCTOR, AccountStatus.ACTIVE);
        when(staffAccountRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        StaffRoomAssignment assignment = mock(StaffRoomAssignment.class);
        when(assignment.getRoomId()).thenReturn(roomId);
        when(assignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(DOCTOR_ID))
                .thenReturn(Optional.of(assignment));
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(dutyShiftConfigService.shiftAt(TODAY, LocalTime.of(9, 0))).thenReturn(Optional.of(DutyShift.MORNING));
        DutySchedule schedule = mock(DutySchedule.class);
        when(schedule.getRoomId()).thenReturn(roomId);
        when(dutyScheduleRepository.findByWorkDateAndShiftAndStaffAccountId(TODAY, DutyShift.MORNING, DOCTOR_ID))
                .thenReturn(Optional.of(schedule));
    }

    private static StaffAccount account(UUID id, Role role, AccountStatus status) {
        StaffAccount account = mock(StaffAccount.class);
        when(account.getId()).thenReturn(id);
        when(account.getRole()).thenReturn(role);
        when(account.getStatus()).thenReturn(status);
        return account;
    }

    private static Room room(String code, RoomType type, ActivationStatus status) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(UUID.randomUUID());
        when(room.getRoomCode()).thenReturn(code);
        when(room.getRoomName()).thenReturn("Phòng " + code);
        when(room.getDepartmentId()).thenReturn(UUID.randomUUID());
        when(room.getType()).thenReturn(type);
        when(room.getStatus()).thenReturn(status);
        return room;
    }

    private static Visit visit(UUID roomId, VisitStatus status, boolean skipped) {
        Visit visit = mock(Visit.class);
        when(visit.getId()).thenReturn(UUID.randomUUID());
        when(visit.getRoomId()).thenReturn(roomId);
        when(visit.getStatus()).thenReturn(status);
        when(visit.isSkipped()).thenReturn(skipped);
        when(visit.getQueueNumber()).thenReturn(3);
        when(visit.getQueueDate()).thenReturn(TODAY);
        return visit;
    }
}
