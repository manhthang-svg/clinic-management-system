package clinic.backend.dto.request;

import clinic.backend.enums.DepartmentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    @Size(min = 2, max = 50, message = "Mã khoa phải từ 2 đến 50 ký tự")
    @Pattern(regexp = "^[A-Z0-9-]+$", message = "Mã khoa chỉ bao gồm chữ in hoa, chữ số hoặc gạch ngang (-)")
    private String departmentCode;

    @Size(min = 2, max = 50, message = "Mã khoa phải từ 2 đến 50 ký tự")
    @Pattern(regexp = "^[A-Z0-9-]+$", message = "Mã khoa chỉ bao gồm chữ in hoa, chữ số hoặc gạch ngang (-)")
    private String makhoa;

    @Size(min = 2, max = 100, message = "Tên khoa phải từ 2 đến 100 ký tự")
    private String departmentName;

    @Size(min = 2, max = 100, message = "Tên khoa phải từ 2 đến 100 ký tự")
    private String tenkhoa;

    @NotNull(message = "Loại khoa không được để trống")
    private DepartmentType type;

    @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
    private String description;

    private String headPerson;

    @Builder.Default
    private Boolean active = true;

    private Long version;

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
