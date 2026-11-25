package clinic.backend.repository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

/** UC-75: the reminder lookup and the V62 column against the migrated schema. */
@Sql("/db/iteration1-schema-fixture.sql")
class FollowUpReminderDueIT extends PostgresIntegrationTest {

    private static final UUID DOCTOR = UUID.fromString("0192a000-0005-7000-8000-000000000001");
    private static final UUID RECEPTIONIST = UUID.fromString("0192a000-0005-7000-8000-000000000007");
    private static final UUID ROOM = UUID.fromString("0192a000-0004-7000-8000-000000000004");

    @Autowired
    private FollowUpAppointmentRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private LocalDate tomorrow;
    private int queueNumber;

    @BeforeEach
    void setUp() {
        tomorrow = LocalDate.now(clock).plusDays(1);
        queueNumber = 0;
    }

    @Test
    void findsOnlyScheduledAppointmentsOfTheDateWithoutReminderWhosePatientHasAnEmail() {
        UUID withEmail = patient("BN00000003", "an@example.com");
        UUID withoutEmail = patient("BN00000004", null);
        UUID blankEmail = patient("BN00000008", "   ");

        UUID due = appointment(withEmail, "WAITING", tomorrow, "SCHEDULED", false);
        appointment(withEmail, "ABANDONED", tomorrow, "SCHEDULED", true);
        appointment(withEmail, "ABANDONED", tomorrow.plusDays(1), "SCHEDULED", false);
        appointment(withEmail, "ABANDONED", tomorrow, "CANCELLED", false);
        appointment(withoutEmail, "WAITING", tomorrow, "SCHEDULED", false);
        appointment(blankEmail, "WAITING", tomorrow, "SCHEDULED", false);

        assertThat(repository.findReminderDueIds(tomorrow)).containsExactly(due);
    }

    @Test
    void storesWhenTheReminderWasSent() {
        UUID patient = patient("BN00000003", "an@example.com");
        UUID id = appointment(patient, "WAITING", tomorrow, "SCHEDULED", false);

        var appointment = repository.findById(id).orElseThrow();
        assertThat(appointment.needsReminder()).isTrue();
        appointment.markReminderSent(clock.instant());
        repository.saveAndFlush(appointment);

        assertThat(jdbc.queryForObject(
                "select reminder_sent_at is not null from follow_up_appointments where id = ?", Boolean.class, id))
                .isTrue();
        assertThat(repository.findReminderDueIds(tomorrow)).isEmpty();
    }

    private UUID patient(String mabenhnhan, String email) {
        jdbc.update("update patients set email = ? where mabenhnhan = ?", email, mabenhnhan);
        return jdbc.queryForObject("select id from patients where mabenhnhan = ?", UUID.class, mabenhnhan);
    }

    private UUID appointment(UUID patient, String visitStatus, LocalDate date, String status, boolean reminded) {
        UUID visit = UUID.randomUUID();
        jdbc.update("""
                insert into visits (id, sovaovien, patient_id, room_id, registered_by_account_id, thoigianvaovien,
                    doituongbn_loai, insurance_verify_status, lydovaovien, is_priority, queue_date, queue_number,
                    status, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, now(), 'DV', 'NOT_APPLICABLE', 'Khám bệnh', false, ?, ?, ?, now(), now(), 0)
                """, visit, "VV-" + visit.toString().substring(0, 12), patient, ROOM, RECEPTIONIST,
                LocalDate.now(clock).minusDays(7), ++queueNumber, visitStatus);
        UUID record = UUID.randomUUID();
        jdbc.update("""
                insert into medical_records (id, visit_id, soba, loaiba, status, opened_at, opened_by_account_id,
                    created_at, updated_at, version)
                values (?, ?, ?, 'NGOAITRU', 'OPEN', now(), ?, now(), now(), 0)
                """, record, visit, "BA-" + record.toString().substring(0, 12), DOCTOR);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into follow_up_appointments (id, medical_record_id, so_giay_hen, patient_id, doctor_account_id,
                    follow_up_date, status, cancel_reason, reminder_sent_at, created_by_account_id, created_at,
                    updated_at, version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now(), 0)
                """, id, record, "GH-" + id.toString().substring(0, 12), patient, DOCTOR, date, status,
                "CANCELLED".equals(status) ? "Bệnh nhân báo không đến" : null,
                reminded ? java.sql.Timestamp.from(clock.instant()) : null, DOCTOR);
        return id;
    }
}
