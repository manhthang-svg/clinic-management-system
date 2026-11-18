package clinic.backend.service;

import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.dto.response.MedicalRecordDetailResponse;
import clinic.backend.dto.response.MedicalRecordPatientItemResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.PatientVisitHistoryResponse;
import clinic.backend.dto.response.ResultFileResponse;
import clinic.backend.enums.MedicalRecordVisitFilter;

/** UC-51, UC-52 (docs/uc-51-52-medical-record-view.md). Every read writes one audit row (BR-51-07). */
public interface MedicalRecordService {

    /** UC-51 level 1: patients matching the keyword with a visit that meets every visit filter (BR-51-04). */
    PageResponse<MedicalRecordPatientItemResponse> searchPatients(
            String keyword,
            LocalDate from,
            LocalDate to,
            UUID roomId,
            String icd10,
            MedicalRecordVisitFilter status,
            int page,
            int size,
            UUID actorAccountId
    );

    /** UC-51 level 2 and AF-01: the patient and all of their visits (BR-51-06). */
    PatientVisitHistoryResponse getPatientVisits(UUID patientId, UUID actorAccountId);

    /** UC-52 opened by record ID (BR-52-01). */
    MedicalRecordDetailResponse getMedicalRecordDetail(UUID id, UUID actorAccountId);

    /** UC-52 opened by visit ID (BR-52-01). */
    MedicalRecordDetailResponse getMedicalRecordByVisitId(UUID visitId, UUID actorAccountId);

    /**
     * UC-52 step 4, BR-52-06: one image of a submitted result of this record, read from the file storage after the
     * access check; writes one audit row.
     */
    ResultFileResponse downloadResultFile(UUID medicalRecordId, UUID fileId, UUID actorAccountId);
}
