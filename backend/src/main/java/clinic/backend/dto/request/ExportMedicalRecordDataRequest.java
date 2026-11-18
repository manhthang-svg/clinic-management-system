package clinic.backend.dto.request;

import clinic.backend.enums.ExportFormat;
import clinic.backend.enums.ExportLogPurpose;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Request payload for UC-80 / BR-43: Export Medical Record Data (XML/JSON).
 */
@Builder
public record ExportMedicalRecordDataRequest(
        @NotNull(message = "Định dạng xuất không được để trống")
        ExportFormat format,

        @NotNull(message = "Mục đích xuất không được để trống")
        ExportLogPurpose purpose,

        @Size(max = 255, message = "Tên đơn vị tiếp nhận không được vượt quá 255 ký tự")
        String recipient
) {
}

