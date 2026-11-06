package clinic.backend.dto.response;

import java.time.LocalDate;

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
public class DutyShiftConfigResponse {
    private LocalDate startDate;
    private String morningStartTime;
    private String morningEndTime;
    private String afternoonStartTime;
    private String afternoonEndTime;
}

