package clinic.backend.service;

import clinic.backend.dto.request.CreatePatientRequest;
import clinic.backend.dto.request.UpdatePatientRequest;
import clinic.backend.dto.response.PatientOverviewResponse;
import clinic.backend.dto.response.PatientResponse;
import clinic.backend.enums.PatientSearchType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PatientService {

    PatientResponse createPatient(CreatePatientRequest request);

    PatientResponse updatePatient(UUID id, UpdatePatientRequest request);

    Page<PatientResponse> listPatients(String keyword, PatientSearchType type, Pageable pageable);

    PatientResponse getPatientById(UUID id);

    /** UC-36: administrative data and visit history; the view is recorded in the audit log (BR-48). */
    PatientOverviewResponse getPatientOverview(UUID actorAccountId, UUID id);
}
