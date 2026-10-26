package clinic.backend.service;


import clinic.backend.dto.request.CreatePatientRequest;
import clinic.backend.dto.response.PatientResponse;

public interface PatientService {

    PatientResponse createPatient(CreatePatientRequest request);

}