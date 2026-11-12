package clinic.backend.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import clinic.backend.enums.DepartmentType;
import clinic.backend.enums.RoomType;

/**
 * UC-29 View Department and Room Statistics: the workload of every department and room in the period.
 * An examination room counts the visits registered in it (queue date); after a room transfer the visit counts for
 * its current room. A paraclinical room counts the services sent to it (released in the period).
 */
public record DepartmentStatisticsResponse(
        LocalDate from,
        LocalDate to,
        long visits,
        long services,
        List<DepartmentRow> departments) {

    /** One department: the sums of its rooms, which are listed busiest first. */
    public record DepartmentRow(
            UUID departmentId,
            String departmentCode,
            String departmentName,
            DepartmentType type,
            boolean active,
            long visits,
            long completedVisits,
            long services,
            long performedServices,
            List<RoomRow> rooms) {
    }

    /**
     * One room. Visit counts are for examination rooms and service counts for paraclinical rooms; the other kind
     * stays zero. {@code performedServices + waitingServices + cancelledServices = services}.
     */
    public record RoomRow(
            UUID roomId,
            String roomCode,
            String roomName,
            RoomType type,
            boolean active,
            long visits,
            long completedVisits,
            long inProgressVisits,
            long services,
            long performedServices,
            long waitingServices,
            long cancelledServices) {
    }
}
