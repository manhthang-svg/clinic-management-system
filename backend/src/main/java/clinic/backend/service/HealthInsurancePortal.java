package clinic.backend.service;

import java.time.LocalDate;
import java.util.Optional;

public interface HealthInsurancePortal {
    Optional<Result> lookup(String socialInsuranceNumber);

    record Result(
            String socialInsuranceNumber,
            String cardNumber,
            String identityNumber,
            String resultCode,
            short status,
            String fullName,
            String dateOfBirth,
            short dateOfBirthType,
            short gender,
            LocalDate validFrom,
            LocalDate validTo,
            String primaryCareFacilityCode,
            String primaryCareFacilityName,
            short coverageRate) {
    }
}
