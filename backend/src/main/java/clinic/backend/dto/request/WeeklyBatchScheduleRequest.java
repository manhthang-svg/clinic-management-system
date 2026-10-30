package clinic.backend.dto.request;

import java.time.LocalDate;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class WeeklyBatchScheduleRequest {
    @NotNull(message = "Ngày bắt đầu tuần không được để trống")
    private LocalDate startDate;

    @NotNull(message = "Ngày kết thúc tuần không được để trống")
    private LocalDate endDate;

    @Valid
    private List<DutyScheduleSaveRequest> items;
}
