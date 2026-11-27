package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import clinic.backend.dto.request.DutyScheduleSaveRequest;
import clinic.backend.dto.request.WeeklyBatchScheduleRequest;
import clinic.backend.dto.response.DutyScheduleItemResponse;
import clinic.backend.dto.response.DutyShiftConfigResponse;
import clinic.backend.dto.response.WeeklyScheduleResponse;
import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.DutyShift;
import clinic.backend.enums.Role;
import clinic.backend.enums.RoomType;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.VisitRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UC-95 View Duty Schedule List, UC-96 Create/Assign Duty Schedule, UC-97 Update Duty Schedule. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DutyScheduleSaveServiceImplTest {

    // Monday 16/11/2026, 09:00 in Vietnam (morning shift).
    private static final Instant NOW = Instant.parse("2026-11-16T02:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);
    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Mock DutyScheduleRepository dutyScheduleRepository;
    @Mock StaffAccountRepository staffAccountRepository;
    @Mock RoomRepository roomRepository;
    @Mock DutyShiftConfigService dutyShiftConfigService;
    @Mock UuidV7Generator uuidV7Generator;
    @Mock VisitRepository visitRepository;
    @Mock ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;

    private DutyScheduleServiceImpl service;
    private StaffAccount doctor;
    private StaffAccount technician;
    private Room examinationRoom;
    private Room paraclinicalRoom;

    @BeforeEach
    void setUp() {
        service = new DutyScheduleServiceImpl(dutyScheduleRepository, staffAccountRepository, roomRepository,
                dutyShiftConfigService, Clock.fixed(NOW, ZoneId.of("UTC")), uuidV7Generator, visitRepository,
                paraclinicalOrderDetailRepository);
        when(dutyShiftConfigService.getConfigForDate(any()))
                .thenReturn(new DutyShiftConfigResponse(TODAY, "08:00", "11:30", "13:00", "17:30"));
        doctor = staff(Role.DOCTOR, "Nguyễn Văn Bác", "ThS.BS.");
        technician = staff(Role.TECHNICIAN, "Phạm Thị Kỹ", null);
        examinationRoom = room(RoomType.EXAMINATION, "P101", "Phòng khám Nội 1");
        paraclinicalRoom = room(RoomType.PARACLINICAL, "XN01", "Phòng xét nghiệm");
        when(staffAccountRepository.findAll()).thenReturn(List.of(doctor, technician));
        when(roomRepository.findAll()).thenReturn(List.of(examinationRoom, paraclinicalRoom));
        when(uuidV7Generator.next()).thenAnswer(invocation -> UUID.randomUUID());
        when(dutyScheduleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // UC-95 View Duty Schedule List

    @Test
    void weeklyScheduleDefaultsToMondayToSundayOfThisWeek() {
        DutySchedule schedule = new DutySchedule(UUID.randomUUID(), NOW, TODAY, DutyShift.MORNING, doctor.getId(),
                examinationRoom.getId(), null, ADMIN_ID);
        when(dutyScheduleRepository.findByWorkDateBetweenOrderByWorkDateAscShiftAsc(TODAY, TODAY.plusDays(6)))
                .thenReturn(List.of(schedule));

        WeeklyScheduleResponse week = service.getWeeklySchedule(null, null);

        assertThat(week.getStartDate()).isEqualTo(TODAY);
        assertThat(week.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 22));
        assertThat(week.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getStaffName()).isEqualTo("ThS.BS. Nguyễn Văn Bác");
            assertThat(item.getRoomCode()).isEqualTo("P101");
        });
    }

    @Test
    void weeklyScheduleOfOneRoomOnlyReadsThatRoom() {
        LocalDate monday = LocalDate.of(2026, 11, 23);
        when(dutyScheduleRepository.findByRoomIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(
                paraclinicalRoom.getId(), monday, monday.plusDays(6))).thenReturn(List.of());

        WeeklyScheduleResponse week = service.getWeeklyScheduleByRoom(paraclinicalRoom.getId(), monday, null);

        assertThat(week.getItems()).isEmpty();
        assertThat(week.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 29));
    }

    // UC-96 Create/Assign Duty Schedule

    @Test
    void personalScheduleUsesOnlyTheTechniciansShiftsAndEachWeeksHours() {
        LocalDate nextWeek = TODAY.plusWeeks(1);
        UUID accountId = technician.getId();
        UUID roomId = paraclinicalRoom.getId();
        var first = new DutySchedule(UUID.randomUUID(), NOW, TODAY, DutyShift.MORNING,
                accountId, roomId, null, ADMIN_ID);
        var second = new DutySchedule(UUID.randomUUID(), NOW, nextWeek, DutyShift.MORNING,
                accountId, roomId, null, ADMIN_ID);
        when(dutyScheduleRepository.findByStaffAccountIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(
                accountId, TODAY, nextWeek)).thenReturn(List.of(first, second));
        when(roomRepository.findAllById(Set.of(roomId))).thenReturn(List.of(paraclinicalRoom));
        when(dutyShiftConfigService.getConfigForDate(nextWeek))
                .thenReturn(new DutyShiftConfigResponse(nextWeek, "07:30", "11:30", "13:00", "17:30"));

        var result = service.getPersonalSchedule(accountId, TODAY, nextWeek);

        assertThat(result.items()).extracting(item -> item.staffAccountId()).containsExactly(accountId, accountId);
        assertThat(result.items()).extracting(item -> item.startTime()).containsExactly("08:00", "07:30");
        assertThat(result.items()).extracting(item -> item.roomCode()).containsExactly("XN01", "XN01");
    }

    @Test
    void assignsDoctorToExaminationRoom() {
        DutyScheduleItemResponse item = service.saveDuty(
                request(null, TOMORROW, DutyShift.MORNING, doctor.getId(), examinationRoom.getId()), ADMIN_ID);

        assertThat(item.getRoomName()).isEqualTo("Phòng khám Nội 1");
        ArgumentCaptor<DutySchedule> saved = ArgumentCaptor.forClass(DutySchedule.class);
        verify(dutyScheduleRepository).save(saved.capture());
        assertThat(saved.getValue().getAssignedByAccountId()).isEqualTo(ADMIN_ID);
        assertThat(saved.getValue().getWorkDate()).isEqualTo(TOMORROW);
    }

    @Test
    void assignsTechnicianToParaclinicalRoom() {
        DutyScheduleItemResponse item = service.saveDuty(
                request(null, TOMORROW, DutyShift.AFTERNOON, technician.getId(), paraclinicalRoom.getId()), ADMIN_ID);

        assertThat(item.getStaffRole()).isEqualTo("TECHNICIAN");
    }

    @Test
    void assignsTheAfternoonShiftOfToday() {
        service.saveDuty(request(null, TODAY, DutyShift.AFTERNOON, doctor.getId(), examinationRoom.getId()), ADMIN_ID);

        verify(dutyScheduleRepository).save(any());
    }

    @Test
    void rejectsShiftThatAlreadyEnded() {
        assertBadRequest(() -> service.saveDuty(
                request(null, TODAY.minusDays(1), DutyShift.AFTERNOON, doctor.getId(), examinationRoom.getId()),
                ADMIN_ID));
    }

    @Test
    void rejectsDoctorInParaclinicalRoom() {
        assertBadRequest(() -> service.saveDuty(
                request(null, TOMORROW, DutyShift.MORNING, doctor.getId(), paraclinicalRoom.getId()), ADMIN_ID));
    }

    @Test
    void rejectsTechnicianInExaminationRoom() {
        assertBadRequest(() -> service.saveDuty(
                request(null, TOMORROW, DutyShift.MORNING, technician.getId(), examinationRoom.getId()), ADMIN_ID));
    }

    @Test
    void rejectsRoleOtherThanDoctorOrTechnician() {
        StaffAccount cashier = staff(Role.CASHIER, "Thu Ngân", null);

        assertBadRequest(() -> service.saveDuty(
                request(null, TOMORROW, DutyShift.MORNING, cashier.getId(), examinationRoom.getId()), ADMIN_ID));
    }

    @Test
    void rejectsMissingRoom() {
        assertBadRequest(() -> service.saveDuty(
                request(null, TOMORROW, DutyShift.MORNING, doctor.getId(), null), ADMIN_ID));
    }

    @Test
    void rejectsStaffAlreadyOnDutyInThatShift() {
        when(dutyScheduleRepository.findByWorkDateAndShiftAndStaffAccountId(TOMORROW, DutyShift.MORNING,
                doctor.getId())).thenReturn(Optional.of(mock(DutySchedule.class)));

        assertBadRequest(() -> service.saveDuty(
                request(null, TOMORROW, DutyShift.MORNING, doctor.getId(), examinationRoom.getId()), ADMIN_ID));
        verify(dutyScheduleRepository, never()).save(any());
    }

    @Test
    void rejectsRoomThatAlreadyHasSomeoneOnDuty() {
        when(dutyScheduleRepository.findByRoomIdAndWorkDateAndShift(examinationRoom.getId(), TOMORROW,
                DutyShift.MORNING)).thenReturn(Optional.of(mock(DutySchedule.class)));

        assertBadRequest(() -> service.saveDuty(
                request(null, TOMORROW, DutyShift.MORNING, doctor.getId(), examinationRoom.getId()), ADMIN_ID));
    }

    @Test
    void unknownStaffIsNotFound() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> service.saveDuty(
                request(null, TOMORROW, DutyShift.MORNING, unknown, examinationRoom.getId()), ADMIN_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // UC-97 Update Duty Schedule

    @Test
    void movesFutureDutyToAnotherRoomAndRecordsTheAdmin() {
        Room otherRoom = room(RoomType.EXAMINATION, "P102", "Phòng khám Nội 2");
        UUID scheduleId = UUID.randomUUID();
        DutySchedule schedule = new DutySchedule(scheduleId, NOW, TOMORROW, DutyShift.MORNING, doctor.getId(),
                examinationRoom.getId(), null, UUID.randomUUID());
        when(dutyScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

        service.saveDuty(request(scheduleId, TOMORROW, DutyShift.MORNING, doctor.getId(), otherRoom.getId()),
                ADMIN_ID);

        assertThat(schedule.getRoomId()).isEqualTo(otherRoom.getId());
        assertThat(schedule.getAssignedByAccountId()).isEqualTo(ADMIN_ID);
        verify(dutyScheduleRepository).save(schedule);
    }

    @Test
    void rejectsUpdateThatPutsTwoPeopleInOneRoomShift() {
        UUID scheduleId = UUID.randomUUID();
        DutySchedule schedule = new DutySchedule(scheduleId, NOW, TOMORROW, DutyShift.MORNING, doctor.getId(),
                examinationRoom.getId(), null, ADMIN_ID);
        when(dutyScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
        when(dutyScheduleRepository.existsByRoomIdAndWorkDateAndShiftAndIdNot(examinationRoom.getId(), TOMORROW,
                DutyShift.MORNING, scheduleId)).thenReturn(true);

        assertBadRequest(() -> service.saveDuty(
                request(scheduleId, TOMORROW, DutyShift.MORNING, doctor.getId(), examinationRoom.getId()), ADMIN_ID));
    }

    @Test
    void rejectsUpdateOfMorningShiftAfterItEnded() {
        // 12:30 in Vietnam: the morning shift (until 11:30) is over.
        DutyScheduleServiceImpl afternoon = new DutyScheduleServiceImpl(dutyScheduleRepository,
                staffAccountRepository, roomRepository, dutyShiftConfigService,
                Clock.fixed(Instant.parse("2026-11-16T05:30:00Z"), ZoneId.of("UTC")), uuidV7Generator,
                visitRepository, paraclinicalOrderDetailRepository);

        assertBadRequest(() -> afternoon.saveDuty(
                request(null, TODAY, DutyShift.MORNING, doctor.getId(), examinationRoom.getId()), ADMIN_ID));
    }

    @Test
    void copiesWeekSkippingPastShiftsWrongRoomsAndDuplicates() {
        LocalDate sunday = TODAY.plusDays(6);
        when(dutyScheduleRepository.findByWorkDateBetweenOrderByWorkDateAscShiftAsc(TODAY, sunday))
                .thenReturn(List.of());
        WeeklyBatchScheduleRequest batch = new WeeklyBatchScheduleRequest(TODAY.minusDays(7), sunday, List.of(
                request(null, TODAY.minusDays(1), DutyShift.MORNING, doctor.getId(), examinationRoom.getId()),
                request(null, TOMORROW, DutyShift.MORNING, doctor.getId(), examinationRoom.getId()),
                request(null, TOMORROW, DutyShift.MORNING, technician.getId(), examinationRoom.getId()),
                request(null, TOMORROW, DutyShift.MORNING, doctor.getId(), examinationRoom.getId())));

        service.batchSaveWeeklySchedule(batch, ADMIN_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DutySchedule>> saved = ArgumentCaptor.forClass(List.class);
        verify(dutyScheduleRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement()
                .satisfies(schedule -> assertThat(schedule.getWorkDate()).isEqualTo(TOMORROW));
    }

    @Test
    void rejectsCopyIntoAWeekThatIsOver() {
        assertBadRequest(() -> service.batchSaveWeeklySchedule(new WeeklyBatchScheduleRequest(
                TODAY.minusDays(14), TODAY.minusDays(8), List.of()), ADMIN_ID));
    }

    private static void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private static DutyScheduleSaveRequest request(
            UUID id, LocalDate date, DutyShift shift, UUID staffId, UUID roomId) {
        return new DutyScheduleSaveRequest(id, date, shift, staffId, roomId, null);
    }

    private StaffAccount staff(Role role, String name, String title) {
        StaffAccount account = mock(StaffAccount.class);
        UUID id = UUID.randomUUID();
        when(account.getId()).thenReturn(id);
        when(account.getRole()).thenReturn(role);
        when(account.getFullName()).thenReturn(name);
        when(account.getAcademicTitle()).thenReturn(title);
        when(staffAccountRepository.findById(id)).thenReturn(Optional.of(account));
        return account;
    }

    private Room room(RoomType type, String code, String name) {
        Room room = mock(Room.class);
        UUID id = UUID.randomUUID();
        when(room.getId()).thenReturn(id);
        when(room.getType()).thenReturn(type);
        when(room.getRoomCode()).thenReturn(code);
        when(room.getRoomName()).thenReturn(name);
        when(roomRepository.findById(id)).thenReturn(Optional.of(room));
        return room;
    }
}
