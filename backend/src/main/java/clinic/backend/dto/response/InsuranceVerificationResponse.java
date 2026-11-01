package clinic.backend.dto.response;

import java.time.LocalDate;

public record InsuranceVerificationResponse(
        String status,
        String submittedCode,
        String socialInsuranceNumber,
        String resultCode,
        Boolean active,
        String cardNumber,
        String identityNumber,
        String verificationMethod,
        Boolean manualReviewRequired,
        String fullName,
        String dateOfBirth,
        Short dateOfBirthType,
        Short gender,
        Short coverageRate,
        LocalDate validFrom,
        LocalDate validTo,
        String primaryCareFacilityCode,
        String primaryCareFacility) {
}
