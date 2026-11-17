package clinic.backend.service.impl;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import clinic.backend.dto.response.RoomQueueStatusResponse;
import clinic.backend.service.VisitService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomQueueListServiceImplTest {

    @Mock
    private VisitService visitService;

    @InjectMocks
    private RoomQueueListServiceImpl service;

    private static RoomQueueStatusResponse room(int index, boolean hasDoctor) {
        return new RoomQueueStatusResponse(UUID.randomUUID(), "PK" + index, "Phòng " + index, "Tầng 1",
                UUID.randomUUID(), "Khoa Nội", hasDoctor ? List.of("BS A") : List.of(), hasDoctor, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void sendsOnePageOfRoomsAndCountsReceivableRoomsOverAll() {
        List<RoomQueueStatusResponse> rooms = IntStream.rangeClosed(1, 25).mapToObj(i -> room(i, i % 5 != 0)).toList();
        when(visitService.getRoomQueueStatus(null)).thenReturn(rooms);

        var result = service.getRoomQueueStatusPage(null, null, 1, 20);

        assertThat(result.rooms().content()).extracting(RoomQueueStatusResponse::roomCode)
                .containsExactly("PK21", "PK22", "PK23", "PK24", "PK25");
        assertThat(result.rooms().page().totalElements()).isEqualTo(25);
        assertThat(result.receivableRooms()).isEqualTo(20);
    }

    @Test
    void returnsOnlyTheRoomOfTheLink() {
        List<RoomQueueStatusResponse> rooms = IntStream.rangeClosed(1, 25).mapToObj(i -> room(i, true)).toList();
        when(visitService.getRoomQueueStatus(null)).thenReturn(rooms);

        var result = service.getRoomQueueStatusPage(null, rooms.get(22).roomId(), 0, 20);

        assertThat(result.rooms().content()).extracting(RoomQueueStatusResponse::roomCode).containsExactly("PK23");
        assertThat(result.receivableRooms()).isEqualTo(25);
    }
}
