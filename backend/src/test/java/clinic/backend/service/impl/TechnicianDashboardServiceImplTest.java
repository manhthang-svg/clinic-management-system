package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import clinic.backend.dto.response.TechnicianDashboardResponse;
import clinic.backend.dto.response.TechnicianPendingItemResponse;
import clinic.backend.dto.response.TechnicianPendingResultResponse;
import clinic.backend.dto.response.TechnicianPendingStatsResponse;
import clinic.backend.dto.response.TechnicianQueueItemResponse;
import clinic.backend.dto.response.TechnicianQueueResponse;
import clinic.backend.dto.response.TechnicianQueueStatsResponse;
import clinic.backend.dto.response.TechnicianRoomResponse;
import clinic.backend.entity.DutySchedule;
import clinic.backend.enums.DutyShift;
import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.service.TechnicianQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TechnicianDashboardServiceImplTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** 17/11/2026 15:30 in Vietnam. */
    private static final Instant NOW = Instant.parse("2026-11-17T08:30:00Z");
    /** 17/11/2026 00:00 in Vietnam. */
    private static final Instant START_OF_DAY = Instant.parse("2026-11-16T17:00:00Z");

    @Mock TechnicianQueueService queueService;
    @Mock ParaclinicalOrderDetailRepository detailRepository;
    @Mock DutyScheduleRepository dutyScheduleRepository;
    @Mock StaffRoomAssignmentRepository staffRoomAssignmentRepository;

    private TechnicianDashboardServiceImpl service;
    private final UUID technicianId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TechnicianDashboardServiceImpl(queueService, detailRepository, dutyScheduleRepository,
                staffRoomAssignmentRepository, Clock.fixed(NOW, CLINIC_ZONE));
    }

    @Test
    void showsTheRoomTheTechnicianWorksInWithTodaysFiguresAndShortLists() {
        TechnicianRoomResponse other = room("P.201", false);
        TechnicianRoomResponse mine = room("P.204", true);
        when(queueService.getTechnicianRooms(technicianId)).thenReturn(List.of(other, mine));
        List<TechnicianQueueItemResponse> waiting = IntStream.range(0, 7)
                .mapToObj(i -> mock(TechnicianQueueItemResponse.class)).toList();
        when(queueService.getDashboardQueue(mine.id(), technicianId)).thenReturn(
                new TechnicianQueueResponse(new TechnicianQueueStatsResponse(12, 7, 2, 3), waiting, true));
        List<TechnicianPendingItemResponse> pending = IntStream.range(0, 3)
                .mapToObj(i -> mock(TechnicianPendingItemResponse.class)).toList();
        when(queueService.getDashboardPendingResults(mine.id(), technicianId)).thenReturn(
                new TechnicianPendingResultResponse(new TechnicianPendingStatsResponse(3, 2, 1, 1), pending, true));
        when(detailRepository.countCompletedSince(mine.id(), START_OF_DAY, technicianId)).thenReturn(9L);

        TechnicianDashboardResponse dashboard = service.getDashboard(null, technicianId);

        assertThat(dashboard.room().id()).isEqualTo(mine.id());
        assertThat(dashboard.room().activeTech()).isNull();
        assertThat(dashboard.canManage()).isTrue();
        assertThat(dashboard.stats()).isEqualTo(new TechnicianDashboardResponse.Stats(7, 2, 3, 2, 1, 1, 9));
        assertThat(dashboard.nextQueue()).containsExactlyElementsOf(waiting.subList(0, 5));
        assertThat(dashboard.pendingResults()).containsExactlyElementsOf(pending);
        verify(detailRepository).countCompletedSince(mine.id(), START_OF_DAY, technicianId);
    }

    @Test
    void showsTheRequestedRoomWhenItBelongsToTheTechnician() {
        TechnicianRoomResponse first = room("P.201", true);
        TechnicianRoomResponse requested = room("P.205", false);
        when(queueService.getTechnicianRooms(technicianId)).thenReturn(List.of(first, requested));
        LocalDate today = LocalDate.of(2026, 11, 17);
        var scheduled = new DutySchedule(UUID.randomUUID(), NOW, today, DutyShift.MORNING, technicianId,
                requested.id(), null, UUID.randomUUID());
        when(dutyScheduleRepository.findByStaffAccountIdAndWorkDateGreaterThanEqualOrderByWorkDateAscShiftAsc(
                technicianId, today)).thenReturn(List.of(scheduled));
        when(queueService.getDashboardQueue(requested.id(), technicianId)).thenReturn(
                new TechnicianQueueResponse(new TechnicianQueueStatsResponse(0, 0, 0, 0), List.of(), false));
        when(queueService.getDashboardPendingResults(requested.id(), technicianId)).thenReturn(
                new TechnicianPendingResultResponse(new TechnicianPendingStatsResponse(0, 0, 0, 0), List.of(), false));

        TechnicianDashboardResponse dashboard = service.getDashboard(requested.id(), technicianId);

        assertThat(dashboard.room().id()).isEqualTo(requested.id());
        assertThat(dashboard.canManage()).isFalse();
        assertThat(dashboard.stats()).isEqualTo(TechnicianDashboardResponse.Stats.empty());
    }

    @Test
    void showsNoRoomWhenTheTechnicianHasNoPersonalRoomAndRejectsAnUnrelatedRoom() {
        TechnicianRoomResponse first = room("P.201", false);
        when(queueService.getTechnicianRooms(technicianId)).thenReturn(List.of(first, room("P.202", false)));
        assertThat(service.getDashboard(null, technicianId).room()).isNull();
        assertThatThrownBy(() -> service.getDashboard(first.id(), technicianId))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.ROOM_NOT_FOUND));
        verifyNoInteractions(detailRepository);
    }

    @Test
    void rejectsAnUnknownRoom() {
        when(queueService.getTechnicianRooms(technicianId)).thenReturn(List.of(room("P.201", true)));

        assertThatThrownBy(() -> service.getDashboard(UUID.randomUUID(), technicianId))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.ROOM_NOT_FOUND));
        verifyNoInteractions(detailRepository);
    }

    @Test
    void isEmptyWhenNoParaclinicalRoomIsActive() {
        when(queueService.getTechnicianRooms(technicianId)).thenReturn(List.of());

        TechnicianDashboardResponse dashboard = service.getDashboard(null, technicianId);

        assertThat(dashboard.room()).isNull();
        assertThat(dashboard.stats()).isEqualTo(TechnicianDashboardResponse.Stats.empty());
        assertThat(dashboard.nextQueue()).isEmpty();
        assertThat(dashboard.pendingResults()).isEmpty();
        verifyNoInteractions(detailRepository);
    }

    private static TechnicianRoomResponse room(String code, boolean assigned) {
        return new TechnicianRoomResponse(UUID.randomUUID(), code, "Phòng " + code, "Tầng 2", "KTV Mai", assigned);
    }
}
