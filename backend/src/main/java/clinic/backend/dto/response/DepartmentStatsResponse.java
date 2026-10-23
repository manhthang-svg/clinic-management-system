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

    /** Tổng số phòng chức năng */
    private long totalRooms;

    /** Số phòng đang hoạt động (Tính trên cấp Phòng) */
    private long activeRooms;

    /** Số phòng tạm ngưng hoặc bảo trì (Tính trên cấp Phòng) */
    private long maintenanceRooms;

    /** Tỷ lệ phần trăm phòng đang hoạt động (vd: 93.8) */
    private double activeRate;

    /** Ghi chú tình trạng bảo trì (vd: "1 phòng đang kiểm tra thiết bị") */
    private String maintenanceNote;

    /** Giữ để tương thích ngược */
    private long activeCount;
    private long maintenanceCount;
}
