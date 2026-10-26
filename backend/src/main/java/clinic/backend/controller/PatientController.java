package clinic.backend.controller;


import clinic.backend.dto.request.CreatePatientRequest;
import clinic.backend.dto.response.PatientResponse;
import clinic.backend.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    @Autowired
    private PatientService patientService;

    @PostMapping
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public PatientResponse createPatient(
            @Valid @RequestBody CreatePatientRequest request
    ) {
        return patientService.createPatient(request);
    }
}
