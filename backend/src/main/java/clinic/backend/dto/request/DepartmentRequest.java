package clinic.backend.dto.request;

import clinic.backend.enums.DepartmentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class DepartmentRequest {

    @Size(max = 50, message = "Mã khoa tối đa 50 ký tự")
    private String departmentCode;
    private String makhoa;

    @Size(max = 255, message = "Tên khoa tối đa 255 ký tự")
    private String departmentName;
    private String tenkhoa;

    @NotNull(message = "Loại khoa không được để trống")
    private DepartmentType type;

    @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
    private String description;

    private String headPerson;

    @Builder.Default
    private Boolean active = true;

    public String getDepartmentCode() {
        if (departmentCode != null && !departmentCode.isBlank()) {
            return departmentCode;
        }
        return makhoa;
    }

    public String getMakhoa() {
        return getDepartmentCode();
    }

    public String getDepartmentName() {
        if (departmentName != null && !departmentName.isBlank()) {
            return departmentName;
        }
        return tenkhoa;
    }

    public String getTenkhoa() {
        return getDepartmentName();
    }
}
