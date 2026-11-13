package clinic.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import clinic.backend.enums.PaymentMethod;
import clinic.backend.enums.PaymentType;
import clinic.backend.enums.StatisticsGroupBy;

/**
 * UC-31 View Revenue Statistics: receipts paid in the period (paid time, clinic time zone). Only Paid receipts are
 * revenue; pending, cancelled and fee-exempt receipts are not counted. Every amount separates the insurance-covered
 * part from the patient-paid part (BR-27, BR-38): {@code totalAmount = insuranceAmount + patientAmount}.
 * {@code series} has one entry per day or month of the period, zero-filled; a month is keyed by its first day.
 */
public record RevenueStatisticsResponse(
        LocalDate from,
        LocalDate to,
        StatisticsGroupBy groupBy,
        Amounts summary,
        List<TypeAmounts> byType,
        List<MethodAmounts> byMethod,
        List<PeriodAmounts> series) {

    public record Amounts(long payments, BigDecimal totalAmount, BigDecimal insuranceAmount, BigDecimal patientAmount) {

        public static Amounts zero() {
            return new Amounts(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        public Amounts plus(long otherPayments, BigDecimal total, BigDecimal insurance, BigDecimal patient) {
            return new Amounts(payments + otherPayments, totalAmount.add(total), insuranceAmount.add(insurance),
                    patientAmount.add(patient));
        }
    }

    /** EXAMINATION_FEE (phí khám) or PARACLINICAL_FEE (phí cận lâm sàng). */
    public record TypeAmounts(PaymentType type, Amounts amounts) {
    }

    /** CASH, BANK_TRANSFER (PayOS) or CARD; null when a paid receipt has no method recorded. */
    public record MethodAmounts(PaymentMethod method, Amounts amounts) {
    }

    public record PeriodAmounts(LocalDate period, Amounts amounts) {
    }
}
