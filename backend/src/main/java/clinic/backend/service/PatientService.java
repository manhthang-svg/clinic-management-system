package clinic.backend.service;

import clinic.backend.dto.request.CreatePatientRequest;
import clinic.backend.dto.request.UpdatePatientRequest;
import clinic.backend.dto.response.PatientResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PatientService {

    PatientResponse createPatient(CreatePatientRequest request);

    PatientResponse updatePatient(UUID id, UpdatePatientRequest request);

    Page<PatientResponse> listPatients(String keyword, Pageable pageable);

    PatientResponse getPatientById(UUID id);
}
