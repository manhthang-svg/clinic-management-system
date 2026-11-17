package clinic.backend.service;

import java.util.UUID;

import clinic.backend.dto.request.ExportMedicalRecordRequest;
import clinic.backend.security.user.StaffPrincipal;

/** UC-53 Export Medical Record (docs/uc-53-export-medical-record.md). */
public interface MedicalRecordExportService {

    /**
     * Builds form 15/BV1 from the archive package of a closed record, stores it and logs the export
     * (BR-53-01 to BR-53-08).
     */
    ExportedFile exportPdf(StaffPrincipal principal, UUID medicalRecordId, ExportMedicalRecordRequest request);

    record ExportedFile(String fileName, byte[] content) {
    }
}
