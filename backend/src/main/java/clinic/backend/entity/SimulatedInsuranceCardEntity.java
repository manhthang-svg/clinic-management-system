package clinic.backend.entity;

import java.time.LocalDate;

import clinic.backend.enums.SimulatedInsuranceCardStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * A card of the simulated insurance portal (I1-D38). Read only by the simulator adapter, never by domain code.
 */
@Entity
@Immutable
@Table(name = "simulated_insurance_cards")
public class SimulatedInsuranceCardEntity {

    @Id
    @Column(name = "card_number", nullable = false, updatable = false, length = 15)
    private String cardNumber;

    @Column(name = "full_name", nullable = false, updatable = false, length = 100)
    private String fullName;

    @Column(name = "date_of_birth", nullable = false, updatable = false)
    private LocalDate dateOfBirth;

    @Column(name = "benefit_code", nullable = false, updatable = false)
    private short benefitCode;

    @Column(name = "coverage_rate", nullable = false, updatable = false)
    private short coverageRate;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false, updatable = false)
    private LocalDate validTo;

    @Column(name = "primary_care_facility", nullable = false, updatable = false)
    private String primaryCareFacility;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 20)
    private SimulatedInsuranceCardStatus status;

    protected SimulatedInsuranceCardEntity() {
    }

    public SimulatedInsuranceCardEntity(
            String cardNumber,
            String fullName,
            LocalDate dateOfBirth,
            short benefitCode,
            short coverageRate,
            LocalDate validFrom,
            LocalDate validTo,
            String primaryCareFacility,
            SimulatedInsuranceCardStatus status) {

        this.cardNumber = cardNumber;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.benefitCode = benefitCode;
        this.coverageRate = coverageRate;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.primaryCareFacility = primaryCareFacility;
        this.status = status;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public short getBenefitCode() {
        return benefitCode;
    }

    public short getCoverageRate() {
        return coverageRate;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public String getPrimaryCareFacility() {
        return primaryCareFacility;
    }

    public SimulatedInsuranceCardStatus getStatus() {
        return status;
    }
}
