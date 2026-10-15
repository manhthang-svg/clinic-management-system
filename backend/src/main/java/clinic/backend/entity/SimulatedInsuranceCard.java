package clinic.backend.entity;

import java.time.LocalDate;

import clinic.backend.enums.SimulatedInsuranceCardStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * A card of the simulated insurance portal (I1-D38). Read only by the simulator adapter, never by domain code.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Entity
@Immutable
@Table(name = "simulated_insurance_cards")
public class SimulatedInsuranceCard {

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
}
