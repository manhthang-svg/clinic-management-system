package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import clinic.backend.dto.response.TechnicianDashboardResponse;
import clinic.backend.dto.response.TechnicianDashboardResponse.Stats;
import clinic.backend.dto.response.TechnicianPendingResultResponse;
import clinic.backend.dto.response.TechnicianPendingStatsResponse;
import clinic.backend.dto.response.TechnicianQueueResponse;
import clinic.backend.dto.response.TechnicianQueueStatsResponse;
import clinic.backend.dto.response.TechnicianRoomResponse;
import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.service.TechnicianDashboardService;
import clinic.backend.service.TechnicianQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// dashboard KTV: dung lai so lieu man hang cho (UC-63, UC-64) nen luon khop voi cac man do
@Service
@RequiredArgsConstructor
public class TechnicianDashboardServiceImpl implements TechnicianDashboardService {

    // so dong moi danh sach ngan tren dashboard
    static final int LIST_LIMIT = 5;

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String WAITING = "WAITING";

    private final TechnicianQueueService technicianQueueService;
    private final ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;
    private final Clock clock;

    // so lieu hom nay + 5 luot cho tiep theo + 5 ca cho nhap ket qua cua 1 phong
    @Override
    @Transactional(readOnly = true)
    public TechnicianDashboardResponse getDashboard(UUID roomId, UUID currentUserId) {
        List<TechnicianRoomResponse> rooms = technicianQueueService.getTechnicianRooms(currentUserId);
        TechnicianRoomResponse room = chooseRoom(rooms, roomId);
        if (room == null) {
            return new TechnicianDashboardResponse(null, false, Stats.empty(), List.of(), List.of());
        }

        TechnicianQueueResponse queue = technicianQueueService.getQueue(room.id(), null, WAITING, null, currentUserId);
        TechnicianPendingResultResponse pending =
                technicianQueueService.getPendingResults(room.id(), null, null, null, currentUserId);
        Instant startOfDay = LocalDate.now(clock.withZone(CLINIC_ZONE)).atStartOfDay(CLINIC_ZONE).toInstant();
        long completedToday = paraclinicalOrderDetailRepository.countCompletedSince(room.id(), startOfDay, currentUserId);

        TechnicianQueueStatsResponse queueStats = queue.stats();
        TechnicianPendingStatsResponse pendingStats = pending.stats();
        Stats stats = new Stats(
                queueStats.waiting(),
                queueStats.priority(),
                queueStats.skipped(),
                pendingStats.pending(),
                pendingStats.draft(),
                pendingStats.overdue(),
                completedToday);

        return new TechnicianDashboardResponse(
                room,
                queue.canManage(),
                stats,
                queue.items().stream().limit(LIST_LIMIT).toList(),
                pending.items().stream().limit(LIST_LIMIT).toList());
    }

    // chon phong: phong yeu cau > phong KTV dang truc > phong dau tien
    private static TechnicianRoomResponse chooseRoom(List<TechnicianRoomResponse> rooms, UUID roomId) {
        if (roomId != null) {
            return rooms.stream()
                    .filter(room -> room.id().equals(roomId))
                    .findFirst()
                    .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        }
        return rooms.stream()
                .filter(TechnicianRoomResponse::hasOwnWork)
                .findFirst()
                .or(() -> rooms.stream()
                        .filter(TechnicianRoomResponse::isAssigned)
                        .findFirst())
                .orElse(rooms.isEmpty() ? null : rooms.getFirst());
    }
}
