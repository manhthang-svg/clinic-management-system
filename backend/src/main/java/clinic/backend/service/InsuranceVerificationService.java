package clinic.backend.service;

import clinic.backend.dto.request.InsuranceVerificationRequest;
import clinic.backend.dto.response.InsuranceVerificationResponse;

public interface InsuranceVerificationService {
    InsuranceVerificationResponse verify(InsuranceVerificationRequest request);
}
