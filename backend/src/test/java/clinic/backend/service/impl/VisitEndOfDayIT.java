package clinic.backend.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import clinic.backend.service.VisitEndOfDayService;
import clinic.backend.service.VisitEndOfDayService.Result;
import clinic.backend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** BR-22 end-of-day job against the migrated schema: closed rows satisfy every database constraint. */
@Sql("/db/iteration1-schema-fixture.sql")
class VisitEndOfDayIT extends PostgresIntegrationTest {

    private static final UUID DOCTOR = UUID.fromString("0192a000-0005-7000-8000-000000000001");
    private static final UUID RECEPTIONIST = UUID.fromString("0192a000-0005-7000-8000-000000000007");
    private static final UUID ROOM = UUID.fromString("0192a000-0004-7000-8000-000000000004");
    private static final UUID SERVICE = UUID.fromString("0192a000-0003-7000-8000-000000000001");

    @Autowired
    private VisitEndOfDayService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private LocalDate today;
    private int queueNumber;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(clock);
        queueNumber = 0;
    }

    @Test
    void closesUnfinishedVisitsOfEarlierDaysAndLeavesTodayAlone() {
        UUID unpaidVisit = visit("BN00000003", today.minusDays(1), "WAITING_FEE_PAYMENT");
        UUID unpaidFee = payment(unpaidVisit, "EXAMINATION_FEE", "PENDING");

        UUID labVisit = visit("BN00000004", today.minusDays(3), "WAITING_PARACLINICAL");
        UUID paidFee = payment(labVisit, "EXAMINATION_FEE", "PAID");
        UUID record = record(labVisit);
        UUID labFee = payment(labVisit, "PARACLINICAL_FEE", "PAID");
        UUID order = order(record, labFee);
        UUID performed = detail(order, "COMPLETED");
        UUID waiting = detail(order, "WAITING");

        UUID todayVisit = visit("BN00000008", today, "WAITING");

        Result result = service.closeUnfinishedVisits();

        assertThat(result).isEqualTo(new Result(1, 1, 0));
        assertThat(status("visits", unpaidVisit)).isEqualTo("CANCELLED");
        assertThat(status("payments", unpaidFee)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select cancelled_at is not null from payments where id = ?",
                Boolean.class, unpaidFee)).isTrue();

        assertThat(status("visits", labVisit)).isEqualTo("ABANDONED");
        assertThat(status("payments", paidFee)).isEqualTo("PAID");
        assertThat(status("payments", labFee)).isEqualTo("PAID");
        assertThat(status("medical_records", record)).isEqualTo("LOCKED");
        assertThat(status("paraclinical_orders", order)).isEqualTo("CANCELLED");
        assertThat(status("paraclinical_order_details", performed)).isEqualTo("COMPLETED");
        assertThat(status("paraclinical_order_details", waiting)).isEqualTo("ABANDONED");
        assertThat(jdbc.queryForObject("select cancel_reason from paraclinical_order_details where id = ?",
                String.class, waiting)).isEqualTo("BO_KHAM");

        assertThat(status("visits", todayVisit)).isEqualTo("WAITING");

        List<Map<String, Object>> audits = jdbc.queryForList("""
                select action, actor_account_id, new_value ->> 'reason' as reason
                from audit_logs where entity_type = 'visits' and entity_id in (?, ?)
                """, unpaidVisit, labVisit);
        assertThat(audits).hasSize(2).allSatisfy(row -> {
            assertThat(row.get("actor_account_id")).isNull();
            assertThat(row.get("reason")).isEqualTo("HET_NGAY");
        });
    }

    @Test
    void letsThePatientRegisterAgainOnceTheOldVisitIsClosed() {
        visit("BN00000003", today.minusDays(1), "IN_EXAMINATION");

        service.closeUnfinishedVisits();

        // BR-17: the one-active-visit index no longer blocks a new visit for the same patient.
        assertThatCode(() -> visit("BN00000003", today, "WAITING")).doesNotThrowAnyException();
    }

    @Test
    void aSecondRunChangesNothing() {
        visit("BN00000003", today.minusDays(1), "WAITING_CONCLUSION");
        service.closeUnfinishedVisits();
        long audits = jdbc.queryForObject("select count(*) from audit_logs", Long.class);

        Result again = service.closeUnfinishedVisits();

        assertThat(again).isEqualTo(new Result(0, 0, 0));
        assertThat(jdbc.queryForObject("select count(*) from audit_logs", Long.class)).isEqualTo(audits);
    }

    private String status(String table, UUID id) {
        return jdbc.queryForObject("select status from " + table + " where id = ?", String.class, id);
    }

    private UUID visit(String mabenhnhan, LocalDate queueDate, String status) {
        UUID patient = jdbc.queryForObject("select id from patients where mabenhnhan = ?", UUID.class, mabenhnhan);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into visits (id, sovaovien, patient_id, room_id, registered_by_account_id, thoigianvaovien,
                    doituongbn_loai, insurance_verify_status, lydovaovien, is_priority, queue_date, queue_number,
                    status, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, now(), 'DV', 'NOT_APPLICABLE', 'Khám bệnh', false, ?, ?, ?, now(), now(), 0)
                """, id, "VV-" + id.toString().substring(0, 12), patient, ROOM, RECEPTIONIST, queueDate,
                ++queueNumber, status);
        return id;
    }

    private UUID payment(UUID visit, String type, String status) {
        UUID id = UUID.randomUUID();
        boolean paid = "PAID".equals(status);
        jdbc.update("""
                insert into payments (id, payment_code, visit_id, type, status, total_amount, insurance_total_amount,
                    patient_total_amount, payment_method, receipt_number, cashier_account_id, paid_at,
                    created_at, updated_at, version)
                values (?, ?, ?, ?, ?, 150000, 0, 150000, ?, ?, ?, ?, now(), now(), 0)
                """, id, "HD-" + id.toString().substring(0, 12), visit, type, status,
                paid ? "CASH" : null, paid ? "PT-" + id.toString().substring(0, 12) : null,
                paid ? RECEPTIONIST : null, paid ? java.sql.Timestamp.from(clock.instant()) : null);
        return id;
    }

    private UUID record(UUID visit) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into medical_records (id, visit_id, soba, loaiba, status, opened_at, opened_by_account_id,
                    created_at, updated_at, version)
                values (?, ?, ?, 'NGOAITRU', 'OPEN', now(), ?, now(), now(), 0)
                """, id, visit, "BA-" + id.toString().substring(0, 12), DOCTOR);
        return id;
    }

    private UUID order(UUID record, UUID payment) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into paraclinical_orders (id, sophieu, medical_record_id, doctor_account_id, payment_id,
                    doituongbn, noichidinh, tongcong, status, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, 2, 'Phòng khám Nội tổng quát', 150000, 'RELEASED', now(), now(), 0)
                """, id, "CD-" + id.toString().substring(0, 12), record, DOCTOR, payment);
        return id;
    }

    private UUID detail(UUID order, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into paraclinical_order_details (id, order_id, service_id, machidinh, ngaychidinh, tenchidinh,
                    dongia, thanhtien, benhnhanphaitra, status, created_at, updated_at, version)
                values (?, ?, ?, ?, now(), 'Xét nghiệm sinh hóa', 75000, 75000, 75000, ?, now(), now(), 0)
                """, id, order, SERVICE, "CD-" + id.toString().substring(0, 12), status);
        return id;
    }
}
