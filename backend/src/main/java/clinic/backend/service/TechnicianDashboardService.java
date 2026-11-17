package clinic.backend.service;

import java.util.UUID;

import clinic.backend.dto.response.TechnicianDashboardResponse;

/** Technician home dashboard: one paraclinical room's figures and short lists for today. */
public interface TechnicianDashboardService {

    /**
     * Dashboard of {@code roomId}; when it is null, of the room the current technician works in now, otherwise
     * of the first active paraclinical room.
     */
    TechnicianDashboardResponse getDashboard(UUID roomId, UUID currentUserId);
}
