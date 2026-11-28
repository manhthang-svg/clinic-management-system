package clinic.backend.controller;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.TechnicianDashboardResponse;
import clinic.backend.dto.response.TechnicianQueueItemResponse;
import clinic.backend.dto.response.TechnicianQueueResponse;
import clinic.backend.dto.response.TechnicianQueueStatsResponse;
import clinic.backend.dto.response.TechnicianRoomResponse;
import clinic.backend.service.TechnicianDashboardService;
import clinic.backend.service.TechnicianQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TechnicianQueueControllerTest {

    @Mock
    private TechnicianQueueService technicianQueueService;

    @Mock
    private TechnicianDashboardService technicianDashboardService;

    private TechnicianQueueController controller;

    @BeforeEach
    void setUp() {
        controller = new TechnicianQueueController(technicianQueueService, technicianDashboardService);
    }

    @Test
    void getRooms_delegatesToService() {
        TechnicianRoomResponse room = new TechnicianRoomResponse(
                UUID.randomUUID(), "P.105", "Phòng Siêu âm", "Khu B", "KTV Mai"
        );
        when(technicianQueueService.getTechnicianRooms(null)).thenReturn(List.of(room));

        ApiResponse<List<TechnicianRoomResponse>> response = controller.getRooms(null);

        assertThat(response.getData()).containsExactly(room);
        verify(technicianQueueService).getTechnicianRooms(null);
    }

    @Test
    void getDashboard_delegatesToServiceWithRoomAndPrincipalId() {
        UUID roomId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(technicianId.toString());
        when(jwt.getClaimAsString("sid")).thenReturn(UUID.randomUUID().toString());
        TechnicianDashboardResponse dashboard = new TechnicianDashboardResponse(
                null, false, TechnicianDashboardResponse.Stats.empty(), List.of(), List.of());
        when(technicianDashboardService.getDashboard(roomId, technicianId)).thenReturn(dashboard);

        ApiResponse<TechnicianDashboardResponse> response = controller.getDashboard(jwt, roomId);

        assertThat(response.getData()).isEqualTo(dashboard);
        verify(technicianDashboardService).getDashboard(roomId, technicianId);
    }

    @Test
    void getQueue_delegatesToServiceWithParams() {
        UUID roomId = UUID.randomUUID();
        TechnicianQueueResponse queueResponse = new TechnicianQueueResponse(
                new TechnicianQueueStatsResponse(5, 3, 1, 0),
                Collections.emptyList(),
                true
        );
        when(technicianQueueService.getQueue(roomId, "BN01", "WAITING", "P.101", null))
                .thenReturn(queueResponse);

        ApiResponse<TechnicianQueueResponse> response = controller.getQueue(null, roomId, "BN01", "WAITING", "P.101");

        assertThat(response.getData()).isEqualTo(queueResponse);
        verify(technicianQueueService).getQueue(roomId, "BN01", "WAITING", "P.101", null);
    }

    @Test
    void checkInOrder_delegatesToServiceWithPrincipalId() {
        UUID detailId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(technicianId.toString());
        when(jwt.getClaimAsString("sid")).thenReturn(UUID.randomUUID().toString());

        TechnicianQueueItemResponse item = mock(TechnicianQueueItemResponse.class);
        when(technicianQueueService.checkInOrder(detailId, technicianId)).thenReturn(item);

        ApiResponse<TechnicianQueueItemResponse> response = controller.checkInOrder(jwt, detailId);

        assertThat(response.getData()).isEqualTo(item);
        verify(technicianQueueService).checkInOrder(detailId, technicianId);
    }

    @Test
    void acceptOrder_delegatesToServiceWithPrincipalId() {
        UUID detailId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(technicianId.toString());
        when(jwt.getClaimAsString("sid")).thenReturn(UUID.randomUUID().toString());

        TechnicianQueueItemResponse item = mock(TechnicianQueueItemResponse.class);
        when(technicianQueueService.acceptOrder(detailId, technicianId)).thenReturn(item);

        ApiResponse<TechnicianQueueItemResponse> response = controller.acceptOrder(jwt, detailId);

        assertThat(response.getData()).isEqualTo(item);
        verify(technicianQueueService).acceptOrder(detailId, technicianId);
    }
}
