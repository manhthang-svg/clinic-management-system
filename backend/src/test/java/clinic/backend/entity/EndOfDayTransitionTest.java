package clinic.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.ParaclinicalOrderStatus;
import clinic.backend.enums.PaymentMethod;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Forbidden end-of-day transitions (BR-22; docs/state-transitions.md sections 8 and 9). */
class EndOfDayTransitionTest {

    private static final Instant NOW = Instant.parse("2026-11-05T17:05:00Z");

    @ParameterizedTest
    @EnumSource(value = VisitStatus.class,
            names = {"WAITING", "IN_EXAMINATION", "WAITING_PARACLINICAL", "WAITING_CONCLUSION"})
    void abandonsEveryUnfinishedVisitAfterTheFeeIsSettled(VisitStatus status) {
        Visit visit = visit(status);

        visit.abandonAtEndOfDay(NOW);

        assertThat(visit.getStatus()).isEqualTo(VisitStatus.ABANDONED);
        assertThat(visit.getUpdatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @EnumSource(value = VisitStatus.class,
            names = {"WAITING_FEE_PAYMENT", "COMPLETED", "CANCELLED", "ABANDONED"})
    void refusesToAbandonAVisitThatIsFinishedOrStillUnpaid(VisitStatus status) {
        Visit visit = visit(status);

        assertThatThrownBy(() -> visit.abandonAtEndOfDay(NOW)).isInstanceOf(IllegalStateException.class);
        assertThat(visit.getStatus()).isEqualTo(status);
    }

    @Test
    void cancelsOnlyAVisitWaitingForTheFee() {
        Visit unpaid = visit(VisitStatus.WAITING_FEE_PAYMENT);
        Visit completed = visit(VisitStatus.COMPLETED);

        unpaid.cancelAtEndOfDay(NOW);

        assertThat(unpaid.getStatus()).isEqualTo(VisitStatus.CANCELLED);
        assertThatThrownBy(() -> completed.cancelAtEndOfDay(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void neverCancelsAPaidReceipt() {
        Payment payment = Payment.createExaminationFee(UUID.randomUUID(), NOW, "HD1", UUID.randomUUID(),
                BigDecimal.valueOf(150000), BigDecimal.ZERO, BigDecimal.valueOf(150000));
        payment.markAsPaid(PaymentMethod.CASH, UUID.randomUUID(), "PT1");

        assertThatThrownBy(() -> payment.cancelAtEndOfDay(NOW)).isInstanceOf(IllegalStateException.class);
        assertThat(payment.getCancelledAt()).isNull();
    }

    @Test
    void locksOnlyAnOpenRecord() {
        MedicalRecord record = new MedicalRecord(UUID.randomUUID(), NOW, UUID.randomUUID(), "BA1", "NGOAITRU",
                MedicalRecordStatus.OPEN, NOW, UUID.randomUUID());

        record.lockAtEndOfDay(NOW);

        assertThat(record.getStatus()).isEqualTo(MedicalRecordStatus.LOCKED);
        assertThatThrownBy(() -> record.lockAtEndOfDay(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ParaclinicalOrderDetailStatus.class, names = {"COMPLETED", "CANCELLED", "ABANDONED"})
    void leavesAFinishedServiceAlone(ParaclinicalOrderDetailStatus status) {
        ParaclinicalOrderDetail detail = detail(status);

        assertThat(detail.isFinished()).isTrue();
        assertThatThrownBy(() -> detail.abandonAtEndOfDay(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelsOnlyAnOpenOrder() {
        ParaclinicalOrder completed = order(ParaclinicalOrderStatus.COMPLETED);

        assertThatThrownBy(() -> completed.cancelAtEndOfDay(NOW)).isInstanceOf(IllegalStateException.class);
    }

    private static Visit visit(VisitStatus status) {
        return new Visit(UUID.randomUUID(), NOW, "VV1", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                NOW, VisitDoituongbnLoai.DV, null, null, null, "Khám bệnh", false, null,
                LocalDate.of(2026, 11, 5), 1, status, VisitInsuranceVerifyStatus.NOT_APPLICABLE);
    }

    private static ParaclinicalOrder order(ParaclinicalOrderStatus status) {
        return new ParaclinicalOrder(UUID.randomUUID(), NOW, "CD1", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, (short) 2, null, "Phòng khám 1", BigDecimal.valueOf(200000), status);
    }

    private static ParaclinicalOrderDetail detail(ParaclinicalOrderDetailStatus status) {
        return new ParaclinicalOrderDetail(UUID.randomUUID(), NOW, order(ParaclinicalOrderStatus.RELEASED),
                UUID.randomUUID(), "CD1-01", NOW, "Siêu âm ổ bụng", null, null, BigDecimal.ONE,
                BigDecimal.valueOf(200000), BigDecimal.valueOf(200000), BigDecimal.ZERO,
                BigDecimal.valueOf(200000), null, status);
    }
}
