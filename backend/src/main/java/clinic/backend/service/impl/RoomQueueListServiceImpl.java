package clinic.backend.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.RoomQueueStatusPageResponse;
import clinic.backend.dto.response.RoomQueueStatusResponse;
import clinic.backend.dto.response.VisitResponse;
import clinic.backend.service.RoomQueueListService;
import clinic.backend.service.VisitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// UC-42: hang doi cac phong / 1 phong cho le tan, cat trang o server
@Service
@RequiredArgsConstructor
public class RoomQueueListServiceImpl implements RoomQueueListService {

    private final VisitService visitService;

    // 1 trang phong (giu thu tu theo khoa) + so phong tiep nhan duoc tren toan bo
    @Override
    public RoomQueueStatusPageResponse getRoomQueueStatusPage(
            LocalDate date, UUID roomId, Integer page, Integer size) {
        List<RoomQueueStatusResponse> rooms = visitService.getRoomQueueStatus(date);
        long receivable = rooms.stream().filter(RoomQueueStatusResponse::hasDoctor).count();
        // mo 1 phong tu link: loc o server, chi tra phong do
        List<RoomQueueStatusResponse> shown = roomId == null
                ? rooms
                : rooms.stream().filter(room -> roomId.equals(room.roomId())).toList();
        return new RoomQueueStatusPageResponse(ListPage.of(shown, page, size), receivable);
    }

    // 1 trang danh sach cho cua 1 phong (loi phong khong hoat dong van nem nhu API cu)
    @Override
    public PageResponse<VisitResponse> getRoomQueuePage(UUID roomId, LocalDate date, Integer page, Integer size) {
        return ListPage.of(visitService.getRoomQueue(roomId, date), page, size);
    }
}
