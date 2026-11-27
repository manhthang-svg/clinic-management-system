package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.StaffRoomAssignment;
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
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
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
    private final TechnicianQueueService technicianQueueService;
    private final ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;
    private final DutyScheduleRepository dutyScheduleRepository;
    private final StaffRoomAssignmentRepository staffRoomAssignmentRepository;
    private final Clock clock;

    // so lieu hom nay + 5 luot cho tiep theo + 5 ca cho nhap ket qua cua 1 phong
    @Override
    @Transactional(readOnly = true)
    public TechnicianDashboardResponse getDashboard(UUID roomId, UUID currentUserId) {
        List<TechnicianRoomResponse> rooms = technicianQueueService.getTechnicianRooms(currentUserId);
        LocalDate today = LocalDate.now(clock.withZone(CLINIC_ZONE));
        TechnicianRoomResponse room = chooseRoom(rooms, roomId, currentUserId, today);
        if (room == null) {
            return new TechnicianDashboardResponse(null, false, Stats.empty(), List.of(), List.of());
        }

        TechnicianQueueResponse queue = technicianQueueService.getDashboardQueue(room.id(), currentUserId);
        TechnicianPendingResultResponse pending =
                technicianQueueService.getDashboardPendingResults(room.id(), currentUserId);
        Instant startOfDay = today.atStartOfDay(CLINIC_ZONE).toInstant();
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
                new TechnicianRoomResponse(room.id(), room.code(), room.name(), room.location(), null,
                        room.isAssigned(), room.hasOwnWork()),
                queue.canManage(),
                stats,
                limitByPatientGroups(queue.items(),
                        item -> groupKeyOf(item.patientId(), item.orderCode(), item.orderId() != null ? item.orderId() : item.id(), item),
                        LIST_LIMIT),
                limitByPatientGroups(pending.items(),
                        item -> groupKeyOf(item.patientId(), item.orderCode(), item.id(), item),
                        LIST_LIMIT));
    }

    private static <T> List<T> limitByPatientGroups(List<T> items,
                                                      java.util.function.Function<T, String> keyExtractor,
                                                      int maxGroups) {
        if (items.size() <= maxGroups) {
            return items;
        }
        Set<String> seenGroups = new LinkedHashSet<>();
        List<T> result = new ArrayList<>();
        for (T item : items) {
            String key = keyExtractor.apply(item);
            if (seenGroups.size() >= maxGroups && !seenGroups.contains(key)) {
                break;
            }
            seenGroups.add(key);
            result.add(item);
        }
        return result;
    }

    private static String groupKeyOf(String patientId, String orderCode, UUID fallbackId, Object fallbackIdentity) {
        String baseOrderCode = orderCode == null ? "" : orderCode.replaceFirst("-\\d+$", "");
        if (patientId != null && !patientId.isBlank() && !"—".equals(patientId)) {
            return patientId + "_" + baseOrderCode;
        }
        if (fallbackId != null) {
            return fallbackId.toString();
        }
        return !baseOrderCode.isEmpty() ? baseOrderCode : ("item_" + System.identityHashCode(fallbackIdentity));
    }

    private TechnicianRoomResponse chooseRoom(List<TechnicianRoomResponse> rooms, UUID roomId,
                                              UUID accountId, LocalDate today) {
        Map<UUID, TechnicianRoomResponse> byId = new LinkedHashMap<>();
        rooms.forEach(room -> byId.put(room.id(), room));
        if (roomId != null && !byId.containsKey(roomId)) {
            throw new AppException(ErrorCode.ROOM_NOT_FOUND);
        }
        if (rooms.isEmpty() || accountId == null) {
            return null;
        }
        var ownRooms = new LinkedHashSet<UUID>();
        rooms.stream().filter(TechnicianRoomResponse::isAssigned).map(TechnicianRoomResponse::id).forEach(ownRooms::add);
        rooms.stream().filter(TechnicianRoomResponse::hasOwnWork).map(TechnicianRoomResponse::id).forEach(ownRooms::add);
        if (roomId == null && !ownRooms.isEmpty()) {
            return byId.get(ownRooms.getFirst());
        }
        if (roomId != null && ownRooms.contains(roomId)) {
            return byId.get(roomId);
        }

        List<DutySchedule> shifts = dutyScheduleRepository
                .findByStaffAccountIdAndWorkDateGreaterThanEqualOrderByWorkDateAscShiftAsc(accountId, today);
        shifts.reversed().stream().filter(shift -> shift.getWorkDate().equals(today))
                .map(DutySchedule::getRoomId).filter(byId::containsKey).forEach(ownRooms::add);
        staffRoomAssignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(accountId)
                .map(StaffRoomAssignment::getRoomId).filter(byId::containsKey).ifPresent(ownRooms::add);
        shifts.stream().map(DutySchedule::getRoomId).filter(byId::containsKey).forEach(ownRooms::add);

        if (roomId != null) {
            if (!ownRooms.contains(roomId)) {
                throw new AppException(ErrorCode.ROOM_NOT_FOUND);
            }
            return byId.get(roomId);
        }
        return ownRooms.isEmpty() ? null : byId.get(ownRooms.getFirst());
    }
}
