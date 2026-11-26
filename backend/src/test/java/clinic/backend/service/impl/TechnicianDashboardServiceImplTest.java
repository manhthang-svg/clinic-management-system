package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
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
import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
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

    private TechnicianDashboardServiceImpl service;
    private final UUID technicianId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TechnicianDashboardServiceImpl(queueService, detailRepository, Clock.fixed(NOW, CLINIC_ZONE));
    }

    @Test
    void showsTheRoomTheTechnicianWorksInWithTodaysFiguresAndShortLists() {
        TechnicianRoomResponse other = room("P.201", false);
        TechnicianRoomResponse mine = room("P.204", true);
        when(queueService.getTechnicianRooms(technicianId)).thenReturn(List.of(other, mine));
        List<TechnicianQueueItemResponse> waiting = IntStream.range(0, 7)
                .mapToObj(i -> mock(TechnicianQueueItemResponse.class)).toList();
        when(queueService.getQueue(mine.id(), null, "WAITING", null, technicianId)).thenReturn(
                new TechnicianQueueResponse(new TechnicianQueueStatsResponse(12, 7, 2, 3), waiting, true));
        List<TechnicianPendingItemResponse> pending = IntStream.range(0, 3)
                .mapToObj(i -> mock(TechnicianPendingItemResponse.class)).toList();
        when(queueService.getPendingResults(mine.id(), null, null, null, technicianId)).thenReturn(
                new TechnicianPendingResultResponse(new TechnicianPendingStatsResponse(3, 2, 1, 1), pending, true));
        when(detailRepository.countCompletedSince(mine.id(), START_OF_DAY, technicianId)).thenReturn(9L);

        TechnicianDashboardResponse dashboard = service.getDashboard(null, technicianId);

        assertThat(dashboard.room()).isEqualTo(mine);
        assertThat(dashboard.canManage()).isTrue();
        assertThat(dashboard.stats()).isEqualTo(new TechnicianDashboardResponse.Stats(7, 2, 3, 2, 1, 1, 9));
        assertThat(dashboard.nextQueue()).containsExactlyElementsOf(waiting.subList(0, 5));
        assertThat(dashboard.pendingResults()).containsExactlyElementsOf(pending);
        verify(detailRepository).countCompletedSince(mine.id(), START_OF_DAY, technicianId);
    }

    @Test
    void showsTheRequestedRoom() {
        TechnicianRoomResponse first = room("P.201", true);
        TechnicianRoomResponse requested = room("P.205", false);
        when(queueService.getTechnicianRooms(technicianId)).thenReturn(List.of(first, requested));
        when(queueService.getQueue(requested.id(), null, "WAITING", null, technicianId)).thenReturn(
                new TechnicianQueueResponse(new TechnicianQueueStatsResponse(0, 0, 0, 0), List.of(), false));
        when(queueService.getPendingResults(requested.id(), null, null, null, technicianId)).thenReturn(
                new TechnicianPendingResultResponse(new TechnicianPendingStatsResponse(0, 0, 0, 0), List.of(), false));

        TechnicianDashboardResponse dashboard = service.getDashboard(requested.id(), technicianId);

        assertThat(dashboard.room()).isEqualTo(requested);
        assertThat(dashboard.canManage()).isFalse();
        assertThat(dashboard.stats()).isEqualTo(TechnicianDashboardResponse.Stats.empty());
    }

    @Test
    void fallsBackToTheFirstRoomWhenTheTechnicianWorksInNoRoomNow() {
        TechnicianRoomResponse first = room("P.201", false);
        when(queueService.getTechnicianRooms(technicianId)).thenReturn(List.of(first, room("P.202", false)));
        when(queueService.getQueue(first.id(), null, "WAITING", null, technicianId)).thenReturn(
                new TechnicianQueueResponse(new TechnicianQueueStatsResponse(0, 0, 0, 0), List.of(), false));
        when(queueService.getPendingResults(first.id(), null, null, null, technicianId)).thenReturn(
                new TechnicianPendingResultResponse(new TechnicianPendingStatsResponse(0, 0, 0, 0), List.of(), false));

        assertThat(service.getDashboard(null, technicianId).room()).isEqualTo(first);
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
