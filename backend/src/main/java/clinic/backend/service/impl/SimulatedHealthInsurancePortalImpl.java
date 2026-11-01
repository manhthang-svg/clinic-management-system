package clinic.backend.service.impl;

import java.util.Optional;

import clinic.backend.config.properties.InsuranceSimulatorProperties;
import clinic.backend.repository.HealthInsuranceRecordRepository;
import clinic.backend.service.HealthInsurancePortal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SimulatedHealthInsurancePortalImpl implements HealthInsurancePortal {
    private final HealthInsuranceRecordRepository repository;
    private final InsuranceSimulatorProperties properties;

    @Override
    @Transactional(readOnly = true)
    public Optional<Result> lookup(String socialInsuranceNumber) {
        delayResponse();
        return repository.findBySocialInsuranceNumber(socialInsuranceNumber).map(record -> new Result(
                record.getSocialInsuranceNumber(), record.getCardNumber(), record.getIdentityNumber(),
                record.getResultCode(), record.getStatus(),
                record.getFullName(), record.getDateOfBirth(), record.getDateOfBirthType(), record.getGender(),
                record.getValidFrom(), record.getValidTo(), record.getPrimaryCareFacilityCode(),
                record.getPrimaryCareFacilityName(), record.getCoverageRate()));
    }

    private void delayResponse() {
        long delayMillis = properties.responseDelay().toMillis();
        if (delayMillis <= 0) return;
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Insurance simulator request was interrupted", exception);
        }
    }
}
