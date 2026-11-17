package clinic.backend.dto.response;

import clinic.backend.enums.ExportFormat;
import clinic.backend.enums.ExportLogPurpose;

/**
 * Response metadata and bytes for UC-80 / BR-43: Export Medical Record Data.
 */
public record ExportMedicalRecordDataResponse(
        String fileName,
        String contentType,
        byte[] content,
        String fileSha256,
        ExportFormat format,
        ExportLogPurpose purpose
) {
}

