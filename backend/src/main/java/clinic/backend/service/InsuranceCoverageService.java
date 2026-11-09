package clinic.backend.service;

import java.math.BigDecimal;
import java.util.UUID;

import clinic.backend.entity.Visit;

/**
 * BR-27 applied coverage rate of an insurance visit: 100% while the visit's total cost within insurance scope is
 * below the co-payment exemption threshold (15% of the base salary), otherwise the card's rate from the portal.
 */
public interface InsuranceCoverageService {

    /** 15% of the base salary, in VND. */
    BigDecimal coPaymentExemptionThreshold();

    /** Applied rate for a visit whose total within scope is {@code scopeTotal}. */
    short appliedCoverageRate(BigDecimal scopeTotal, short cardCoverageRate);

    /** Total within scope of the visit's lines that are not cancelled, on receipts that are not cancelled. */
    BigDecimal scopeTotal(UUID visitId);

    /**
     * Reprices every unpaid receipt of an open insurance visit at the applied rate for its current total within
     * scope. Call it in the same transaction, after a service is added to or cancelled from the visit. Does
     * nothing for a self-paid visit or once the rate is fixed at the conclusion.
     */
    void recalculate(Visit visit);
}
