package clinic.backend.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
//tra cho UI bảng
public class DepartmentRoomItemResponse {
    private UUID id;
    private String unitType; // "DEPARTMENT" or "ROOM"
    private String code;     // makhoa or maphongkham
    private String name;     // tenkhoa or phongkham
    private UUID departmentId;
    private String departmentName;
    private UUID serviceGroupId;
    private String serviceGroupName;
    private String type;     // CLINICAL, PARACLINICAL, EXAMINATION
    private String location;
    private String headPerson;
    private String status;   // "ACTIVE" or "INACTIVE"
    private String description;
    private Integer roomCount;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
    private List<RoomServiceItemResponse> assignedServices;
}
