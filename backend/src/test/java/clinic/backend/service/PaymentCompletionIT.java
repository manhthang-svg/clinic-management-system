package clinic.backend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import clinic.backend.entity.Payment;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** A receipt confirmed by PayOS has no cashier; completing it must still write its audit row. */
@Sql("/db/iteration1-schema-fixture.sql")
class PaymentCompletionIT extends PostgresIntegrationTest {

    private static final UUID RECEPTIONIST = UUID.fromString("0192a000-0005-7000-8000-000000000007");
    private static final UUID ROOM = UUID.fromString("0192a000-0004-7000-8000-000000000004");

    @Autowired
    private PaymentCompletionService service;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @Test
    void completesAReceiptConfirmedByPayOSWithoutACashier() {
        UUID visit = UUID.randomUUID();
        UUID patient = jdbc.queryForObject("select id from patients where mabenhnhan = 'BN00000003'", UUID.class);
        jdbc.update("""
                insert into visits (id, sovaovien, patient_id, room_id, registered_by_account_id, thoigianvaovien,
                    doituongbn_loai, insurance_verify_status, lydovaovien, is_priority, queue_date, queue_number,
                    status, created_at, updated_at, version)
                values (?, 'VV-PAYOS-1', ?, ?, ?, now(), 'DV', 'NOT_APPLICABLE', 'Khám bệnh', false, ?, null,
                    'WAITING_FEE_PAYMENT', now(), now(), 0)
                """, visit, patient, ROOM, RECEPTIONIST, LocalDate.now(clock));
        UUID paymentId = UUID.randomUUID();
        jdbc.update("""
                insert into payments (id, payment_code, visit_id, type, status, total_amount, insurance_total_amount,
                    patient_total_amount, created_at, updated_at, version)
                values (?, 'HD-PAYOS-1', ?, 'EXAMINATION_FEE', 'PENDING', 150000, 0, 150000, now(), now(), 0)
                """, paymentId, visit);

        transactionTemplate.executeWithoutResult(status -> {
            Payment payment = paymentRepository.findById(paymentId).orElseThrow();
            payment.markAsPaidByPayOS("PT-PAYOS-1");
            paymentRepository.save(payment);
            service.completePaidReceipt(payment, null);
        });

        assertThat(jdbc.queryForObject("select status from payments where id = ?", String.class, paymentId))
                .isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select status from visits where id = ?", String.class, visit))
                .isEqualTo("WAITING");
        // I1-D48: the unpaid visit had no queue number; paying issues one.
        assertThat(jdbc.queryForObject("select queue_number from visits where id = ?", Integer.class, visit))
                .isNotNull()
                .isPositive();
        Map<String, Object> audit = jdbc.queryForMap(
                "select action, actor_account_id from audit_logs where entity_type = 'payments' and entity_id = ?",
                paymentId);
        assertThat(audit.get("action")).isEqualTo("UPDATE");
        assertThat(audit.get("actor_account_id")).isNull();
    }
}
