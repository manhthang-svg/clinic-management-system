package clinic.backend.dto.response;

import java.time.LocalDate;
import java.util.List;
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
public class WeeklyScheduleResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private List<DutyScheduleItemResponse> items;
}
