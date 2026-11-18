package clinic.backend.service;

import java.util.UUID;

import clinic.backend.dto.request.ExportMedicalRecordDataRequest;
import clinic.backend.dto.response.ExportMedicalRecordDataResponse;

/** UC-80 / BR-43: XML or JSON export of a medical record in the CV 365 structure. */
public interface MedicalRecordDataExportService {

    ExportMedicalRecordDataResponse exportMedicalRecordData(
            UUID medicalRecordId,
            ExportMedicalRecordDataRequest request,
            UUID actorAccountId
    );
}
