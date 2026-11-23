package clinic.backend.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * UC-29 summary for the page: totals and the chart rows (busy departments, without their rooms). The department
 * table comes page by page from {@code /statistics/departments/rows}.
 */
public record DepartmentStatisticsOverviewResponse(
        LocalDate from,
        LocalDate to,
        long visits,
        long services,
        long departmentCount,
        long busyDepartments,
        long busyRooms,
        long roomCount,
        List<DepartmentLoad> busy) {

    /** Workload of one department for the chart. */
    public record DepartmentLoad(String departmentName, long visits, long services) {
    }
}
