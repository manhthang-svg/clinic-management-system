package clinic.backend.dto.request;

import java.time.LocalDate;
import java.util.UUID;
import clinic.backend.enums.DutyShift;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
public class DutyScheduleSaveRequest {
    private UUID id;

    @NotNull(message = "Ngày trực không được để trống")
    private LocalDate workDate;

    @NotNull(message = "Ca trực không được để trống")
    private DutyShift shift;

    @NotNull(message = "Nhân viên trực không được để trống")
    private UUID staffAccountId;

    private UUID roomId;
    private String notes;
}
