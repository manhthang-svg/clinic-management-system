package clinic.backend.service;

import clinic.backend.dto.request.CreatePatientRequest;
import clinic.backend.dto.request.UpdatePatientRequest;
import clinic.backend.dto.response.PatientOverviewResponse;
import clinic.backend.dto.response.PatientResponse;
import clinic.backend.enums.PatientSearchType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PatientService {

    PatientResponse createPatient(CreatePatientRequest request);

    /** UC-37 step 7: profiles with the same full name and date of birth. */
    List<PatientResponse> findPossibleDuplicates(String fullName, LocalDate birthDate);

    PatientResponse updatePatient(UUID id, UpdatePatientRequest request);

    Page<PatientResponse> listPatients(String keyword, PatientSearchType type, Pageable pageable);

    PatientResponse getPatientById(UUID id);

    /** UC-36: administrative data and visit history; the view is recorded in the audit log (BR-48). */
    PatientOverviewResponse getPatientOverview(UUID actorAccountId, UUID id);
}
