package clinic.backend.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Pattern;
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
public class DutyShiftConfigRequest {

    private LocalDate startDate;

    @Pattern(regexp = "^(0[0-9]|1[0-1]):[0-5][0-9]$", message = "Giờ bắt đầu ca sáng phải có định dạng HH:mm trong khoảng 00:00 - 11:59")
    private String morningStartTime;

    @Pattern(regexp = "^(0[0-9]|1[0-1]):[0-5][0-9]$", message = "Giờ kết thúc ca sáng phải có định dạng HH:mm trong khoảng 00:00 - 11:59")
    private String morningEndTime;

    @Pattern(regexp = "^(1[2-9]|2[0-3]):[0-5][0-9]$", message = "Giờ bắt đầu ca chiều phải có định dạng HH:mm trong khoảng 12:00 - 23:59")
    private String afternoonStartTime;

    @Pattern(regexp = "^(1[2-9]|2[0-3]):[0-5][0-9]$", message = "Giờ kết thúc ca chiều phải có định dạng HH:mm trong khoảng 12:00 - 23:59")
    private String afternoonEndTime;
}

