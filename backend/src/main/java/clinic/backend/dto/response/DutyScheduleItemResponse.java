package clinic.backend.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import clinic.backend.enums.DutyShift;
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
public class DutyScheduleItemResponse {
    private UUID id;
    private LocalDate workDate;
    private DutyShift shift;
    private UUID staffAccountId;
    private String staffName;
    private String staffUsername;
    private String staffRole;
    private UUID roomId;
    private String roomName;
    private String roomCode;
    private String notes;
    private UUID assignedByAccountId;
    private String assignedByName;
    private Instant createdAt;
    private Instant updatedAt;
}
