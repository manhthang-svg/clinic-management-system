package clinic.backend.dto.request;

import java.time.Instant;
import java.util.List;

import clinic.backend.enums.AllergySeverity;
import clinic.backend.enums.MedicalHistoryCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * UC-39 step 3: the full medical history and drug allergies of the patient after the Doctor's changes.
 *
 * @param lastUpdatedAt the "last updated" time the Doctor saw; null when the patient had no data yet
 */
public record UpdateMedicalHistoryRequest(
        @NotNull(message = "Vui lòng gửi danh sách tiền sử")
        @Size(max = 50, message = "Tối đa 50 mục tiền sử")
        List<@Valid @NotNull HistoryItem> history,

        @NotNull(message = "Vui lòng gửi danh sách dị ứng thuốc")
        @Size(max = 50, message = "Tối đa 50 mục dị ứng thuốc")
        List<@Valid @NotNull AllergyItem> allergies,

        boolean noKnownDrugAllergy,

        Instant lastUpdatedAt
) {

    public record HistoryItem(
            @NotNull(message = "Vui lòng chọn nhóm tiền sử")
            MedicalHistoryCategory category,

            @NotBlank(message = "Vui lòng nhập nội dung tiền sử")
            @Size(max = 255, message = "Mỗi mục tiền sử không được vượt quá 255 ký tự")
            String content
    ) {
    }

    public record AllergyItem(
            @NotBlank(message = "Vui lòng nhập thuốc hoặc nhóm thuốc gây dị ứng")
            @Size(max = 255, message = "Thuốc hoặc nhóm thuốc không được vượt quá 255 ký tự")
            String drug,

            @Size(max = 255, message = "Biểu hiện dị ứng không được vượt quá 255 ký tự")
            String reaction,

            AllergySeverity severity
    ) {
    }
}
