package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.DutyScheduleSaveRequest;
import clinic.backend.dto.response.DutyShiftConfigResponse;
import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.enums.DutyShift;
import clinic.backend.enums.Role;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.VisitStatus;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DutyScheduleServiceImplTest {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    // Giả lập ngày hôm nay là 2026-11-15 09:00 sáng tại Việt Nam (thuộc ca sáng)
    private static final Instant NOW_VN_MORNING = Instant.parse("2026-11-15T02:00:00Z"); // 09:00 VN
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);

    @Mock DutyScheduleRepository dutyScheduleRepository;
    @Mock StaffAccountRepository staffAccountRepository;
    @Mock RoomRepository roomRepository;
    @Mock DutyShiftConfigService dutyShiftConfigService;
    @Mock UuidV7Generator uuidV7Generator;
    @Mock VisitRepository visitRepository;
    @Mock ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;

    DutyScheduleServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW_VN_MORNING, ZoneId.of("UTC"));
        service = new DutyScheduleServiceImpl(
                dutyScheduleRepository,
                staffAccountRepository,
                roomRepository,
                dutyShiftConfigService,
                fixedClock,
                uuidV7Generator,
                visitRepository,
                paraclinicalOrderDetailRepository
        );

        DutyShiftConfigResponse shiftCfg = new DutyShiftConfigResponse(
                TODAY, "08:00", "11:30", "13:00", "17:30"
        );
        when(dutyShiftConfigService.getConfigForDate(any())).thenReturn(shiftCfg);
    }

    @Test
    void deleteDuty_rejectsWhenDoctorHasVisitsInProgress() {
        UUID scheduleId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        DutySchedule schedule = new DutySchedule(
                scheduleId, Instant.now(), TODAY, DutyShift.MORNING, staffId, roomId, "", UUID.randomUUID()
        );
        when(dutyScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

        // Bác sĩ đang có 1 lượt khám IN_EXAMINATION
        when(visitRepository.countByDoctorAccountIdAndStatus(staffId, VisitStatus.IN_EXAMINATION)).thenReturn(1L);

        StaffAccount doctor = mock(StaffAccount.class);
        when(doctor.getFullName()).thenReturn("BS. Nguyễn Văn An");
        when(staffAccountRepository.findById(staffId)).thenReturn(Optional.of(doctor));

        assertThatThrownBy(() -> service.deleteDuty(scheduleId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).contains("BS. Nguyễn Văn An đang có lượt khám hoặc dịch vụ đang thực hiện dở dang");
                });
    }

    @Test
    void deleteDuty_rejectsWhenRoomHasPatientsWaitingInQueue() {
        UUID scheduleId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        DutySchedule schedule = new DutySchedule(
                scheduleId, Instant.now(), TODAY, DutyShift.MORNING, staffId, roomId, "", UUID.randomUUID()
        );
        when(dutyScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

        when(visitRepository.countByDoctorAccountIdAndStatus(staffId, VisitStatus.IN_EXAMINATION)).thenReturn(0L);
        when(paraclinicalOrderDetailRepository.countByPerformerAccountIdAndStatus(any(), any())).thenReturn(0L);

        Room room = mock(Room.class);
        when(room.getType()).thenReturn(RoomType.EXAMINATION);
        when(room.getRoomName()).thenReturn("Phòng Khám Nội 1");
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        when(dutyShiftConfigService.shiftAt(eq(TODAY), any(LocalTime.class)))
                .thenReturn(Optional.of(DutyShift.MORNING));

        Visit waitingVisit = mock(Visit.class);
        when(visitRepository.findAllByRoomIdAndQueueDateAndStatusIn(eq(roomId), eq(TODAY), any()))
                .thenReturn(List.of(waitingVisit));

        assertThatThrownBy(() -> service.deleteDuty(scheduleId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).contains("Phòng Phòng Khám Nội 1 đang có 1 bệnh nhân đang chờ hoặc đang khám");
                });
    }

    @Test
    void deleteDuty_succeedsWhenNoWorkInProgress() {
        UUID scheduleId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        DutySchedule schedule = new DutySchedule(
                scheduleId, Instant.now(), TODAY, DutyShift.MORNING, staffId, roomId, "", UUID.randomUUID()
        );
        when(dutyScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

        when(visitRepository.countByDoctorAccountIdAndStatus(staffId, VisitStatus.IN_EXAMINATION)).thenReturn(0L);
        when(paraclinicalOrderDetailRepository.countByPerformerAccountIdAndStatus(any(), any())).thenReturn(0L);

        Room room = mock(Room.class);
        when(room.getType()).thenReturn(RoomType.EXAMINATION);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        when(dutyShiftConfigService.shiftAt(eq(TODAY), any(LocalTime.class)))
                .thenReturn(Optional.of(DutyShift.MORNING));
        when(visitRepository.findAllByRoomIdAndQueueDateAndStatusIn(eq(roomId), eq(TODAY), any()))
                .thenReturn(List.of());

        service.deleteDuty(scheduleId);
        verify(dutyScheduleRepository).delete(schedule);
    }
}
