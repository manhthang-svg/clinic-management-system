package clinic.backend.service.impl;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import clinic.backend.dto.request.TransferVisitRequest;
import clinic.backend.dto.response.TransferRoomOptionResponse;
import clinic.backend.dto.response.VisitTransferResponse;
import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicalExaminationRepository;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.RoomQueueCounterRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.repository.VisitTransferRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.DutyShiftConfigService;
import clinic.backend.support.PostgresIntegrationTest;
import clinic.backend.utils.UuidV7Generator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC-48 against the migrated schema: queue numbers, row locking and the stored transfer history. */
@Sql("/db/iteration1-schema-fixture.sql")
class VisitTransferIT extends PostgresIntegrationTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final UUID DOCTOR_AN = UUID.fromString("0192a000-0005-7000-8000-000000000001");
    private static final UUID DOCTOR_BINH = UUID.fromString("0192a000-0005-7000-8000-000000000002");
    private static final UUID RECEPTIONIST = UUID.fromString("0192a000-0005-7000-8000-000000000007");
    private static final UUID ROOM_NOI = UUID.fromString("0192a000-0004-7000-8000-000000000004");
    private static final UUID ROOM_NGOAI = UUID.fromString("0192a000-0004-7000-8000-000000000005");
    private static final StaffPrincipal AN = new StaffPrincipal(DOCTOR_AN, UUID.randomUUID());

    @Autowired VisitRepository visitRepository;
    @Autowired VisitTransferRepository visitTransferRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired StaffAccountRepository staffAccountRepository;
    @Autowired StaffRoomAssignmentRepository assignmentRepository;
    @Autowired DutyScheduleRepository dutyScheduleRepository;
    @Autowired MedicalRecordRepository medicalRecordRepository;
    @Autowired ClinicalExaminationRepository examinationRepository;
    @Autowired RoomQueueCounterRepository queueCounterRepository;
    // The default shift hours, not the git-ignored data/duty_shift_config.json of the machine running the test.
    private DutyShiftConfigService dutyShiftConfigService;
    private ExaminationRoomDuty examinationRoomDuty;
    @Autowired UuidV7Generator ids;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    private LocalDate today;
    private int queueNumber;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(CLINIC_ZONE);
        queueNumber = 0;
        dutyShiftConfigService = new DutyShiftConfigServiceImpl(
                new ObjectMapper().findAndRegisterModules(), Path.of("target", "visit-transfer-it-shifts.json"));
        examinationRoomDuty = new ExaminationRoomDuty(dutyScheduleRepository, staffAccountRepository,
                dutyShiftConfigService);
        duty(DOCTOR_AN, ROOM_NOI);
        duty(DOCTOR_BINH, ROOM_NGOAI);
    }

    @Test
    void movesTheCalledInVisitToTheNextQueueNumberOfTheDestination() {
        visit(ROOM_NGOAI, "WAITING", null);                     // queue 1 in the destination, so the next is 2
        UUID visit = visit(ROOM_NOI, "IN_EXAMINATION", DOCTOR_AN);
        openEmptyExamination(visit);                            // entering the screen does not block it

        VisitTransferResponse result = inTransaction(() -> service(at(9, 0)).transfer(AN, visit,
                new TransferVisitRequest(ROOM_NGOAI, "  Đau khớp gối, cần khám Ngoại  ", " ")));

        assertThat(result.toRoomId()).isEqualTo(ROOM_NGOAI);
        assertThat(result.newQueueNumber()).isEqualTo(2);
        Map<String, Object> row = jdbc.queryForMap(
                "select room_id, queue_number, status, doctor_account_id, denkhambenhluc from visits where id = ?",
                visit);
        assertThat(row.get("room_id")).isEqualTo(ROOM_NGOAI);
        assertThat(row.get("queue_number")).isEqualTo(2);
        assertThat(row.get("status")).isEqualTo("WAITING");
        assertThat(row.get("doctor_account_id")).isNull();
        assertThat(row.get("denkhambenhluc")).isNull();
        Map<String, Object> transfer = jdbc.queryForMap(
                "select from_room_id, to_room_id, reason, notes, old_queue_number, new_queue_number,"
                        + " transferred_by_account_id from visit_transfers where visit_id = ?", visit);
        assertThat(transfer.get("from_room_id")).isEqualTo(ROOM_NOI);
        assertThat(transfer.get("reason")).isEqualTo("Đau khớp gối, cần khám Ngoại");
        assertThat(transfer.get("notes")).isNull();
        assertThat(transfer.get("old_queue_number")).isEqualTo(2);
        assertThat(transfer.get("new_queue_number")).isEqualTo(2);
        assertThat(transfer.get("transferred_by_account_id")).isEqualTo(DOCTOR_AN);
    }

    @Test
    void listsOnlyOtherRoomsWithADoctorOnDuty() {
        UUID visit = visit(ROOM_NOI, "IN_EXAMINATION", DOCTOR_AN);

        List<TransferRoomOptionResponse> rooms = inTransaction(() -> service(at(9, 0)).getTransferRooms(AN, visit));

        assertThat(rooms).extracting(TransferRoomOptionResponse::roomId).containsExactly(ROOM_NGOAI);
        assertThat(rooms.getFirst().doctorName()).isEqualTo("Lê Thị Bình");
        // In the afternoon nobody is scheduled in the destination: no room is eligible (AF-03).
        duty(DOCTOR_AN, ROOM_NOI, "AFTERNOON");
        assertThat(inTransaction(() -> service(at(14, 0)).getTransferRooms(AN, visit))).isEmpty();
    }

    @Test
    void rejectsAVisitWhoseExaminationWasSaved() {
        UUID visit = visit(ROOM_NOI, "IN_EXAMINATION", DOCTOR_AN);
        UUID examination = openEmptyExamination(visit);
        jdbc.update("update clinical_examinations set version = 1 where id = ?", examination);

        assertError(() -> service(at(9, 0)).transfer(AN, visit, request(ROOM_NGOAI)),
                ErrorCode.MEDICAL_RECORD_ALREADY_OPENED);
        assertThat(jdbc.queryForObject("select room_id from visits where id = ?", UUID.class, visit))
                .isEqualTo(ROOM_NOI);
    }

    @Test
    void rejectsWaitingVisitsShortReasonsIneligibleRoomsAndOffDutyDoctors() {
        UUID waiting = visit(ROOM_NOI, "WAITING", null);
        UUID called = visit(ROOM_NOI, "IN_EXAMINATION", DOCTOR_AN);

        assertError(() -> service(at(9, 0)).transfer(AN, waiting, request(ROOM_NGOAI)),
                ErrorCode.VISIT_NOT_TRANSFERABLE);
        assertError(() -> service(at(9, 0)).transfer(AN, called,
                new TransferVisitRequest(ROOM_NGOAI, " abc ", null)), ErrorCode.TRANSFER_REASON_INVALID);
        assertError(() -> service(at(9, 0)).transfer(AN, called, request(ROOM_NOI)),
                ErrorCode.TRANSFER_ROOM_NOT_ELIGIBLE);
        assertError(() -> service(at(12, 0)).transfer(AN, called, request(ROOM_NGOAI)),
                ErrorCode.DOCTOR_OFF_DUTY);
    }

    @Test
    void transfersTheSameVisitOnlyOnceWhenTwoRequestsRace() throws Exception {
        UUID visit = visit(ROOM_NOI, "IN_EXAMINATION", DOCTOR_AN);
        VisitTransferServiceImpl service = service(at(9, 0));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> outcomes = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                outcomes.add(pool.submit(() -> {
                    start.await();
                    try {
                        return inTransaction(() -> service.transfer(AN, visit, request(ROOM_NGOAI)));
                    } catch (AppException ex) {
                        return ex.getErrorCode();
                    }
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> outcome : outcomes) results.add(outcome.get());

            assertThat(results).filteredOn(VisitTransferResponse.class::isInstance).hasSize(1);
            assertThat(results).contains(ErrorCode.VISIT_NOT_TRANSFERABLE);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from visit_transfers where visit_id = ?", Long.class, visit))
                .isEqualTo(1);
    }

    private VisitTransferServiceImpl service(Clock clock) {
        return new VisitTransferServiceImpl(
                visitRepository, visitTransferRepository, roomRepository, departmentRepository,
                staffAccountRepository, assignmentRepository, dutyScheduleRepository, medicalRecordRepository,
                examinationRepository, queueCounterRepository, examinationRoomDuty, dutyShiftConfigService,
                ids, clock);
    }

    private Clock at(int hour, int minute) {
        return Clock.fixed(today.atTime(LocalTime.of(hour, minute)).atZone(CLINIC_ZONE).toInstant(), CLINIC_ZONE);
    }

    private <T> T inTransaction(Callable<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try {
                return work.call();
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    private void assertError(Callable<?> work, ErrorCode expected) {
        assertThatThrownBy(() -> inTransaction(work))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    }

    private static TransferVisitRequest request(UUID room) {
        return new TransferVisitRequest(room, "Cần khám chuyên khoa khác", "Ghi chú");
    }

    private void duty(UUID doctor, UUID room) {
        duty(doctor, room, "MORNING");
    }

    private void duty(UUID doctor, UUID room, String shift) {
        jdbc.update("""
                insert into duty_schedules (id, work_date, shift, staff_account_id, room_id, created_at, updated_at,
                    version)
                values (?, ?, ?, ?, ?, now(), now(), 0)
                """, UUID.randomUUID(), today, shift, doctor, room);
    }

    private UUID visit(UUID room, String status, UUID doctor) {
        UUID patient = jdbc.queryForObject(
                "select id from patients order by mabenhnhan offset ? limit 1", UUID.class, queueNumber);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into visits (id, sovaovien, patient_id, room_id, doctor_account_id, registered_by_account_id,
                    thoigianvaovien, doituongbn_loai, insurance_verify_status, lydovaovien, is_priority, queue_date,
                    queue_number, status, denkhambenhluc, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, ?, now(), 'DV', 'NOT_APPLICABLE', 'Khám bệnh', false, ?, ?, ?, ?, now(), now(), 0)
                """, id, "VV-" + id.toString().substring(0, 12), patient, room, doctor, RECEPTIONIST, today,
                ++queueNumber, status, doctor == null ? null : java.sql.Timestamp.from(java.time.Instant.now()));
        return id;
    }

    /** What entering the examination screen leaves behind: an open record and a never-saved sheet. */
    private UUID openEmptyExamination(UUID visit) {
        UUID record = UUID.randomUUID();
        jdbc.update("""
                insert into medical_records (id, visit_id, soba, loaiba, status, opened_at, opened_by_account_id,
                    created_at, updated_at, version)
                values (?, ?, ?, 'NGOAITRU', 'OPEN', now(), ?, now(), now(), 0)
                """, record, visit, "BA-" + record.toString().substring(0, 12), DOCTOR_AN);
        UUID examination = UUID.randomUUID();
        jdbc.update("""
                insert into clinical_examinations (id, medical_record_id, sophieu, examined_by_account_id,
                    created_at, updated_at, version)
                values (?, ?, ?, ?, now(), now(), 0)
                """, examination, record, "KB-" + examination.toString().substring(0, 12), DOCTOR_AN);
        return examination;
    }
}
