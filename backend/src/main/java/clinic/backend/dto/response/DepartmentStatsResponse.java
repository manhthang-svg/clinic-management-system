package clinic.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
// trả cho KPI card
public class DepartmentStatsResponse {
    /** Tổng số khoa chuyên môn */
    private long totalDepartments;

    /** Số chuyên khoa lâm sàng */
    private long clinicalDepartments;

    /** Số chuyên khoa cận lâm sàng */
    private long paraclinicalDepartments;

    /** Tổng số phòng chức năng */
    private long totalRooms;

    /** Số phòng khám lâm sàng */
    private long clinicalRooms;

    /** Số phòng cận lâm sàng */
    private long paraclinicalRooms;

    /** Số phòng đang hoạt động (Tính trên cấp Phòng) */
    private long activeRooms;

    /** Số phòng tạm ngưng hoạt động (Tính trên cấp Phòng) */
    private long maintenanceRooms;

    /** Tỷ lệ phần trăm phòng đang hoạt động (vd: 93.8) */
    private double activeRate;

    /** Ghi chú tình trạng tạm ngưng (vd: "1 phòng đang tạm ngưng hoạt động") */
    private String maintenanceNote;

    /** Giữ để tương thích ngược */
    private long activeCount;
    private long maintenanceCount;
}
