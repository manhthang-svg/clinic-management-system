package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

import clinic.backend.dto.request.CreateVisitRequest;
import clinic.backend.dto.response.InsuranceVerificationResponse;
import clinic.backend.dto.response.VisitResponse;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Payment;
import clinic.backend.entity.Room;
import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.Department;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.entity.Visit;
import clinic.backend.entity.VisitTransfer;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.DutyShift;
import clinic.backend.enums.DoctorQueueTab;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.repository.VisitTransferRepository;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.FollowUpAppointmentRepository;
import clinic.backend.repository.RoomQueueCounterRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.config.properties.InsuranceBillingProperties;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.InsuranceVerificationService;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.utils.UuidV7Generator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class VisitServiceImplTest {

    private static final LocalDate QUEUE_DATE = LocalDate.of(2026, 10, 30);
    private static final UUID BUSY_ROOM_ID = UUID.randomUUID();
    private static final UUID EMPTY_ROOM_ID = UUID.randomUUID();
    private static final UUID DOCTOR_ID = UUID.randomUUID();

    @Mock
    private MedicalServiceRepository medicalServiceRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private VisitRepository visitRepository;

    @Mock
    private VisitTransferRepository visitTransferRepository;

    @Mock
    private StaffRoomAssignmentRepository staffRoomAssignmentRepository;

    @Mock
    private DutyScheduleRepository dutyScheduleRepository;

    @Mock
    private StaffAccountRepository staffAccountRepository;

    @Mock
    private ClinicFacilityRepository clinicFacilityRepository;

    @Mock
    private UuidV7Generator uuidV7Generator;

    @Mock
    private FollowUpAppointmentRepository followUpAppointmentRepository;

    @Mock
    private InsuranceVerificationService insuranceVerificationService;

    @Mock
    private RoomQueueCounterRepository roomQueueCounterRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ExaminationRoomDuty examinationRoomDuty;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-10-30T02:00:00Z"), ZoneOffset.UTC);

    private VisitServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VisitServiceImpl(
                medicalServiceRepository,
                paymentRepository,
                patientRepository,
                roomRepository,
                departmentRepository,
                visitRepository,
                visitTransferRepository,
                staffRoomAssignmentRepository,
                dutyScheduleRepository,
                staffAccountRepository,
                clinicFacilityRepository,
                followUpAppointmentRepository,
                insuranceVerificationService,
                roomQueueCounterRepository,
                auditLogService,
                uuidV7Generator,
                examinationRoomDuty,
                new DutyShiftConfigServiceImpl(new ObjectMapper().findAndRegisterModules()),
                new InsuranceCoverageServiceImpl(
                        new InsuranceBillingProperties(BigDecimal.valueOf(2_340_000), BigDecimal.valueOf(15)),
                        paymentRepository, null, auditLogService, clock),
                clock);
    }

    @Test
    void doctorQueueIsRoomScopedAndOrdersPriorityTransferThenRegular() {
        Room assignedRoom = new Room(
                BUSY_ROOM_ID, clock.instant(), UUID.randomUUID(), null,
                "PK01", "Phòng PK01", "Tầng 1", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        Room sourceRoom = new Room(
                EMPTY_ROOM_ID, clock.instant(), UUID.randomUUID(), null,
                "PK02", "Phòng PK02", "Tầng 2", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        StaffAccount doctor = StaffAccount.builder()
                .id(DOCTOR_ID).fullName("Nguyễn Văn An").academicTitle("BS")
                .role(clinic.backend.enums.Role.DOCTOR).status(AccountStatus.ACTIVE)
                .build();
        StaffRoomAssignment assignment = new StaffRoomAssignment(
                UUID.randomUUID(), DOCTOR_ID, BUSY_ROOM_ID, clock.instant(), null, null, null, 0L);
        DutySchedule schedule = mock(DutySchedule.class);
        when(schedule.getRoomId()).thenReturn(BUSY_ROOM_ID);
        when(staffAccountRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        when(staffRoomAssignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(DOCTOR_ID))
                .thenReturn(Optional.of(assignment));
        when(roomRepository.findById(BUSY_ROOM_ID)).thenReturn(Optional.of(assignedRoom));
        when(dutyScheduleRepository.findByWorkDateAndShiftAndStaffAccountId(
                QUEUE_DATE, DutyShift.MORNING, DOCTOR_ID)).thenReturn(Optional.of(schedule));

        Visit regular = queuedVisit(30, false);
        Visit transferred = queuedVisit(20, false);
        Visit priority = queuedVisit(10, true);
        when(visitRepository.findAllByRoomIdAndQueueDateAndStatusIn(
                BUSY_ROOM_ID, QUEUE_DATE, List.of(
                        VisitStatus.WAITING, VisitStatus.IN_EXAMINATION, VisitStatus.WAITING_PARACLINICAL,
                        VisitStatus.WAITING_CONCLUSION)))
                .thenReturn(List.of(regular, transferred, priority));

        Patient regularPatient = patient(regular.getPatientId(), "Thường");
        Patient transferredPatient = patient(transferred.getPatientId(), "Chuyển phòng");
        Patient priorityPatient = patient(priority.getPatientId(), "Ưu tiên");
        when(patientRepository.findAllById(any())).thenReturn(
                List.of(regularPatient, transferredPatient, priorityPatient));
        VisitTransfer transfer = mock(VisitTransfer.class);
        UUID transferredVisitId = transferred.getId();
        when(transfer.getVisitId()).thenReturn(transferredVisitId);
        when(transfer.getFromRoomId()).thenReturn(EMPTY_ROOM_ID);
        when(transfer.getTransferredAt()).thenReturn(clock.instant().minusSeconds(60));
        when(visitTransferRepository.findAllByVisitIdInAndToRoomIdOrderByTransferredAtDesc(
                any(), org.mockito.ArgumentMatchers.eq(BUSY_ROOM_ID))).thenReturn(List.of(transfer));
        when(roomRepository.findAllById(any())).thenReturn(List.of(sourceRoom));

        var queue = service.getDoctorQueue(
                new StaffPrincipal(DOCTOR_ID, UUID.randomUUID()),
                DoctorQueueTab.WAITING, null, null, null, null, 0, 20);

        assertThat(queue.roomId()).isEqualTo(BUSY_ROOM_ID);
        assertThat(queue.doctorName()).isEqualTo("BS Nguyễn Văn An");
        assertThat(queue.onDuty()).isTrue();
        assertThat(queue.waitingCount()).isEqualTo(3);
        assertThat(queue.visits().page().totalElements()).isEqualTo(3);
        assertThat(queue.visits().content()).extracting("patientName")
                .containsExactly("Ưu tiên", "Chuyển phòng", "Thường");
        assertThat(queue.visits().content().get(1).sourceRoomName()).isEqualTo(sourceRoom.getRoomName());
        assertThat(queue.visits().content().get(0).note()).isEqualTo("Ưu tiên: Người cao tuổi");
        assertThat(queue.visits().content().get(1).note()).isEqualTo("Chuyển từ: " + sourceRoom.getRoomName());

        var filtered = service.getDoctorQueue(
                new StaffPrincipal(DOCTOR_ID, UUID.randomUUID()),
                DoctorQueueTab.WAITING, "chuyển", false, VisitDoituongbnLoai.DV, null, 0, 10);
        assertThat(filtered.visits().content()).extracting("patientName")
                .containsExactly("Chuyển phòng");
    }

    @Test
    void patientListMergesWaitingInExaminationAndWaitingParaclinicalWithTheExaminedPatientFirst() {
        stubDoctorOnDuty();
        Visit waiting = queuedVisit(10, false);
        Visit inExamination = queuedVisit(20, false, VisitStatus.IN_EXAMINATION);
        Visit waitingParaclinical = queuedVisit(3, false, VisitStatus.WAITING_PARACLINICAL);
        Visit waitingConclusion = queuedVisit(5, false, VisitStatus.WAITING_CONCLUSION);
        Visit skipped = skippedVisit(1, VisitStatus.WAITING, clock.instant().minusSeconds(60));
        when(visitRepository.findAllByRoomIdAndQueueDateAndStatusIn(
                BUSY_ROOM_ID, QUEUE_DATE,
                List.of(VisitStatus.WAITING, VisitStatus.IN_EXAMINATION, VisitStatus.WAITING_PARACLINICAL,
                        VisitStatus.WAITING_CONCLUSION)))
                .thenReturn(List.of(waiting, waitingConclusion, waitingParaclinical, skipped, inExamination));
        List<Patient> patients = List.of(
                patient(waiting.getPatientId(), "Đang chờ"),
                patient(waitingParaclinical.getPatientId(), "Chờ CLS"),
                patient(inExamination.getPatientId(), "Đang khám"),
                patient(waitingConclusion.getPatientId(), "Chờ kết luận"),
                patient(skipped.getPatientId(), "Vắng mặt"));
        when(patientRepository.findAllById(any())).thenReturn(patients);
        when(visitTransferRepository.findAllByVisitIdInAndToRoomIdOrderByTransferredAtDesc(
                any(), org.mockito.ArgumentMatchers.eq(BUSY_ROOM_ID))).thenReturn(List.of());

        var queue = service.getDoctorQueue(
                new StaffPrincipal(DOCTOR_ID, UUID.randomUUID()),
                DoctorQueueTab.PATIENT_LIST, null, null, null, null, 0, 20);

        assertThat(queue.visits().page().totalElements()).isEqualTo(3);
        assertThat(queue.visits().content()).extracting("patientName")
                .containsExactly("Đang khám", "Đang chờ", "Chờ CLS");
        assertThat(queue.waitingCount()).isEqualTo(1);
        assertThat(queue.inExaminationCount()).isEqualTo(1);
        assertThat(queue.waitingParaclinicalCount()).isEqualTo(1);
    }

    @Test
    void doctorWithoutActiveExaminationRoomCannotReadAnotherQueue() {
        StaffAccount doctor = StaffAccount.builder()
                .id(DOCTOR_ID).fullName("Nguyễn Văn An")
                .role(clinic.backend.enums.Role.DOCTOR).status(AccountStatus.ACTIVE)
                .build();
        when(staffAccountRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        when(staffRoomAssignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(DOCTOR_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDoctorQueue(
                new StaffPrincipal(DOCTOR_ID, UUID.randomUUID()),
                DoctorQueueTab.WAITING, null, null, null, null, 0, 20))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ROOM_ASSIGNMENT_REQUIRED));
        verifyNoInteractions(visitRepository);
    }

    @Test
    void doctorOutsideAssignedShiftCannotStartExamination() {
        Room assignedRoom = new Room(
                BUSY_ROOM_ID, clock.instant(), UUID.randomUUID(), null,
                "PK01", "Phòng PK01", "Tầng 1", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        StaffAccount doctor = StaffAccount.builder()
                .id(DOCTOR_ID).fullName("Nguyễn Văn An")
                .role(clinic.backend.enums.Role.DOCTOR).status(AccountStatus.ACTIVE)
                .build();
        StaffRoomAssignment assignment = new StaffRoomAssignment(
                UUID.randomUUID(), DOCTOR_ID, BUSY_ROOM_ID, clock.instant(), null, null, null, 0L);
        when(staffAccountRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        when(staffRoomAssignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(DOCTOR_ID))
                .thenReturn(Optional.of(assignment));
        when(roomRepository.findById(BUSY_ROOM_ID)).thenReturn(Optional.of(assignedRoom));
        when(dutyScheduleRepository.findByWorkDateAndShiftAndStaffAccountId(
                QUEUE_DATE, DutyShift.MORNING, DOCTOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startExamination(
                new StaffPrincipal(DOCTOR_ID, UUID.randomUUID()), UUID.randomUUID()))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DOCTOR_OFF_DUTY));
    }

    @Test
    void skippedQueueOrdersBySkippedTimeAndFiltersInterruptedStage() {
        stubDoctorOnDuty();
        Visit waitingConclusion = skippedVisit(
                20, VisitStatus.WAITING_CONCLUSION, clock.instant().minusSeconds(60));
        Visit waiting = skippedVisit(
                10, VisitStatus.WAITING, clock.instant().minusSeconds(120));
        Visit examinedConclusion = skippedVisit(
                30, VisitStatus.WAITING_CONCLUSION, clock.instant().minusSeconds(30));
        when(examinedConclusion.getDoctorAccountId()).thenReturn(DOCTOR_ID);
        Patient waitingPatient = patient(waiting.getPatientId(), "Chờ khám");
        Patient conclusionPatient = patient(waitingConclusion.getPatientId(), "Chờ kết luận");
        Patient examinedPatient = patient(examinedConclusion.getPatientId(), "Đã khám");
        when(visitRepository.findAllByRoomIdAndQueueDateAndStatusIn(
                BUSY_ROOM_ID, QUEUE_DATE,
                List.of(VisitStatus.WAITING, VisitStatus.IN_EXAMINATION, VisitStatus.WAITING_PARACLINICAL,
                        VisitStatus.WAITING_CONCLUSION)))
                .thenReturn(List.of(waitingConclusion, examinedConclusion, waiting));
        when(patientRepository.findAllById(any()))
                .thenReturn(List.of(conclusionPatient, examinedPatient, waitingPatient));
        when(visitTransferRepository.findAllByVisitIdInAndToRoomIdOrderByTransferredAtDesc(
                any(), org.mockito.ArgumentMatchers.eq(BUSY_ROOM_ID))).thenReturn(List.of());

        var queue = service.getDoctorQueue(
                new StaffPrincipal(DOCTOR_ID, UUID.randomUUID()),
                DoctorQueueTab.SKIPPED, null, null, null, null, 0, 20);

        assertThat(queue.skippedCount()).isEqualTo(3);
        assertThat(queue.visits().content()).extracting("patientName")
                .containsExactly("Chờ khám", "Chờ kết luận", "Đã khám");
        // A skipped conclusion with no examining Doctor recorded cannot be accepted again (UC-46).
        assertThat(queue.visits().content()).extracting("canAcceptAgain")
                .containsExactly(true, false, true);

        var filtered = service.getDoctorQueue(
                new StaffPrincipal(DOCTOR_ID, UUID.randomUUID()),
                DoctorQueueTab.SKIPPED, null, null, null, VisitStatus.WAITING_CONCLUSION, 0, 20);
        assertThat(filtered.visits().content()).extracting("patientName")
                .containsExactly("Chờ kết luận", "Đã khám");
    }

    @Test
    void queueStatusCountsEveryActiveExaminationRoom() {
        Room busyRoom = room(BUSY_ROOM_ID, "PK01");
        Room emptyRoom = room(EMPTY_ROOM_ID, "PK02");
        when(roomRepository.findByTypeAndStatusOrderByRoomCodeAsc(RoomType.EXAMINATION, ActivationStatus.ACTIVE))
                .thenReturn(List.of(busyRoom, emptyRoom));
        when(visitRepository.countRoomQueues(QUEUE_DATE)).thenReturn(List.of(
                count(BUSY_ROOM_ID, VisitStatus.WAITING, false, 3),
                count(BUSY_ROOM_ID, VisitStatus.IN_EXAMINATION, false, 1),
                count(BUSY_ROOM_ID, VisitStatus.WAITING_PARACLINICAL, false, 2),
                count(BUSY_ROOM_ID, VisitStatus.WAITING_CONCLUSION, false, 4)));

        var status = service.getRoomQueueStatus(QUEUE_DATE);

        assertThat(status).extracting("roomCode").containsExactly("PK01", "PK02");
        var busy = status.getFirst();
        assertThat(busy.waitingCount()).isEqualTo(3);
        assertThat(busy.inExaminationCount()).isEqualTo(1);
        assertThat(busy.waitingParaclinicalCount()).isEqualTo(2);
        assertThat(busy.waitingConclusionCount()).isEqualTo(4);
        assertThat(busy.skippedCount()).isZero();
    }

    @Test
    void queueStatusCountsVisitsWaitingForPayment() {
        Room room = room(BUSY_ROOM_ID, "PK01");
        when(roomRepository.findByTypeAndStatusOrderByRoomCodeAsc(RoomType.EXAMINATION, ActivationStatus.ACTIVE))
                .thenReturn(List.of(room));
        when(visitRepository.countRoomQueues(QUEUE_DATE)).thenReturn(List.of(
                count(BUSY_ROOM_ID, VisitStatus.WAITING_FEE_PAYMENT, false, 2),
                count(BUSY_ROOM_ID, VisitStatus.WAITING, false, 1)));

        var status = service.getRoomQueueStatus(QUEUE_DATE).getFirst();

        assertThat(status.waitingPaymentCount()).isEqualTo(2);
        assertThat(status.waitingCount()).isEqualTo(1);
    }

    @Test
    void queueStatusShowsActiveAssignedDoctorsAndFlagsRoomsWithoutDoctor() {
        Room busyRoom = room(BUSY_ROOM_ID, "PK01");
        Room emptyRoom = room(EMPTY_ROOM_ID, "PK02");
        UUID lockedDoctorId = UUID.randomUUID();
        UUID nurseId = UUID.randomUUID();
        when(roomRepository.findByTypeAndStatusOrderByRoomCodeAsc(RoomType.EXAMINATION, ActivationStatus.ACTIVE))
                .thenReturn(List.of(busyRoom, emptyRoom));
        when(visitRepository.countRoomQueues(QUEUE_DATE)).thenReturn(List.of());
        when(staffRoomAssignmentRepository.findByRemovedAtIsNull()).thenReturn(List.of(
                assignment(DOCTOR_ID, BUSY_ROOM_ID),
                assignment(lockedDoctorId, EMPTY_ROOM_ID),
                assignment(nurseId, EMPTY_ROOM_ID)));
        when(staffAccountRepository.findAllById(any())).thenReturn(List.of(
                staff(DOCTOR_ID, "BS", "Nguyễn Văn An", clinic.backend.enums.Role.DOCTOR, AccountStatus.ACTIVE),
                staff(lockedDoctorId, null, "Trần Bình", clinic.backend.enums.Role.DOCTOR, AccountStatus.LOCKED),
                staff(nurseId, null, "Lê Cúc", clinic.backend.enums.Role.RECEPTIONIST, AccountStatus.ACTIVE)));

        var status = service.getRoomQueueStatus(QUEUE_DATE);

        assertThat(status.get(0).doctorNames()).containsExactly("BS Nguyễn Văn An");
        assertThat(status.get(0).hasDoctor()).isTrue();
        assertThat(status.get(1).doctorNames()).isEmpty();
        assertThat(status.get(1).hasDoctor()).isFalse();
    }

    @Test
    void queueStatusGroupsRoomsByDepartmentName() {
        UUID cardiologyId = UUID.randomUUID();
        UUID internalId = UUID.randomUUID();
        Room internalRoom = room(BUSY_ROOM_ID, "PK01");
        Room cardiologyRoom = room(EMPTY_ROOM_ID, "PK02");
        Room noDepartmentRoom = room(UUID.randomUUID(), "PK00");
        when(internalRoom.getDepartmentId()).thenReturn(internalId);
        when(cardiologyRoom.getDepartmentId()).thenReturn(cardiologyId);
        when(roomRepository.findByTypeAndStatusOrderByRoomCodeAsc(RoomType.EXAMINATION, ActivationStatus.ACTIVE))
                .thenReturn(List.of(noDepartmentRoom, internalRoom, cardiologyRoom));
        when(visitRepository.countRoomQueues(QUEUE_DATE)).thenReturn(List.of());
        List<Department> departments = List.of(
                department(internalId, "Khoa Nội"), department(cardiologyId, "Khoa Tim mạch"));
        when(departmentRepository.findAllById(any())).thenReturn(departments);

        var status = service.getRoomQueueStatus(QUEUE_DATE);

        assertThat(status).extracting("roomCode").containsExactly("PK01", "PK02", "PK00");
        assertThat(status).extracting("departmentName").containsExactly("Khoa Nội", "Khoa Tim mạch", null);
    }

    @Test
    void roomWithoutQueuedVisitsReportsZeroCounts() {
        Room emptyRoom = room(EMPTY_ROOM_ID, "PK02");
        when(roomRepository.findByTypeAndStatusOrderByRoomCodeAsc(RoomType.EXAMINATION, ActivationStatus.ACTIVE))
                .thenReturn(List.of(emptyRoom));
        when(visitRepository.countRoomQueues(QUEUE_DATE)).thenReturn(List.of());

        var empty = service.getRoomQueueStatus(QUEUE_DATE).getFirst();

        assertThat(empty.waitingCount()).isZero();
        assertThat(empty.inExaminationCount()).isZero();
        assertThat(empty.waitingParaclinicalCount()).isZero();
        assertThat(empty.waitingConclusionCount()).isZero();
        assertThat(empty.skippedCount()).isZero();
    }

    @Test
    void skippedVisitsAreCountedOnlyAsSkipped() {
        Room room = room(BUSY_ROOM_ID, "PK01");
        when(roomRepository.findByTypeAndStatusOrderByRoomCodeAsc(RoomType.EXAMINATION, ActivationStatus.ACTIVE))
                .thenReturn(List.of(room));
        when(visitRepository.countRoomQueues(QUEUE_DATE)).thenReturn(List.of(
                count(BUSY_ROOM_ID, VisitStatus.WAITING, false, 2),
                count(BUSY_ROOM_ID, VisitStatus.WAITING, true, 1),
                count(BUSY_ROOM_ID, VisitStatus.WAITING_CONCLUSION, true, 1)));

        var status = service.getRoomQueueStatus(QUEUE_DATE).getFirst();

        assertThat(status.waitingCount()).isEqualTo(2);
        assertThat(status.waitingConclusionCount()).isZero();
        assertThat(status.skippedCount()).isEqualTo(2);
    }

    @Test
    void countsOfRoomsThatAreNotActiveExaminationRoomsAreIgnored() {
        when(roomRepository.findByTypeAndStatusOrderByRoomCodeAsc(RoomType.EXAMINATION, ActivationStatus.ACTIVE))
                .thenReturn(List.of());
        when(visitRepository.countRoomQueues(QUEUE_DATE)).thenReturn(List.of(
                count(BUSY_ROOM_ID, VisitStatus.WAITING, false, 5)));

        assertThat(service.getRoomQueueStatus(QUEUE_DATE)).isEmpty();
    }

    @Test
    void defaultsToTodayInClinicTimeZone() {
        LocalDate today = QUEUE_DATE;
        when(roomRepository.findByTypeAndStatusOrderByRoomCodeAsc(RoomType.EXAMINATION, ActivationStatus.ACTIVE))
                .thenReturn(List.of());
        when(visitRepository.countRoomQueues(today)).thenReturn(List.of());

        service.getRoomQueueStatus(null);

        verify(visitRepository).countRoomQueues(today);
    }

    @Test
    void cashierCannotLoadOrPrintTheTicketBeforeTheExaminationFeeIsPaid() {
        // I1-D48: the Cashier prints the queue-number ticket, so only once the visit has its number.
        UUID visitId = UUID.randomUUID();
        Visit visit = mock(Visit.class);
        when(visit.getQueueNumber()).thenReturn(null);
        when(visitRepository.findById(visitId)).thenReturn(Optional.of(visit));
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("cashier", null, "ROLE_CASHIER"));
        try {
            assertThatThrownBy(() -> service.getTicket(visitId))
                    .isInstanceOfSatisfying(AppException.class,
                            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_PAID));
            assertThatThrownBy(() -> service.prepareTicketPrint(
                    new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID()), visitId))
                    .isInstanceOfSatisfying(AppException.class,
                            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_PAID));
            verifyNoInteractions(auditLogService);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void registersSelfPaidVisitAndCreatesPendingExaminationPayment() {
        UUID patientId = UUID.randomUUID();
        UUID receptionistId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Patient patient = mock(Patient.class);
        Room room = new Room(
                BUSY_ROOM_ID, clock.instant(), UUID.randomUUID(), null,
                "PK01", "Phòng khám 01", "Tầng 1", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        MedicalService examination = MedicalService.create(
                UUID.randomUUID(), clock.instant(), "KB", "Khám bệnh", MedicalServiceType.EXAMINATION,
                null, null, new BigDecimal("150000"), new BigDecimal("100000"), null);

        when(patient.getId()).thenReturn(patientId);
        when(patient.getHoten()).thenReturn("Nguyễn Văn A");
        when(patient.getMabenhnhan()).thenReturn("BN00000001");
        when(patient.getNgaysinh()).thenReturn(LocalDate.of(1990, 1, 1));
        when(patient.getGioitinh()).thenReturn((short) 1);
        when(visitRepository.existsByPatientIdAndStatusNotIn(patientId, VisitRepository.FINISHED_STATUSES))
                .thenReturn(false);
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(roomRepository.findById(BUSY_ROOM_ID)).thenReturn(Optional.of(room));
        when(examinationRoomDuty.onDutyDoctor(BUSY_ROOM_ID, clock.instant()))
                .thenReturn(Optional.of(mock(StaffAccount.class)));
        when(medicalServiceRepository.findByTypeAndDepartmentIdIsNullAndServiceGroupIdIsNullAndStatus(
                MedicalServiceType.EXAMINATION, ActivationStatus.ACTIVE)).thenReturn(Optional.of(examination));
        when(visitRepository.nextRegistrationCode()).thenReturn(1L);
        when(visitRepository.nextPaymentCode()).thenReturn(1L);
        when(uuidV7Generator.next()).thenReturn(visitId, paymentId, itemId, UUID.randomUUID());
        when(visitRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createVisit(
                new StaffPrincipal(receptionistId, UUID.randomUUID()),
                new CreateVisitRequest(
                        patientId, BUSY_ROOM_ID, VisitDoituongbnLoai.DV, null,
                        "Đau đầu", "EXAMINATION", "TU_DEN", false, null,
                        null, null));

        assertThat(response.sovaovien()).isEqualTo("DK0000000001");
        assertThat(response.status()).isEqualTo(VisitStatus.WAITING_FEE_PAYMENT);
        // I1-D48: no queue number until the examination fee is paid.
        assertThat(response.queueNumber()).isNull();
        verify(roomQueueCounterRepository, never()).takeNext(any(), any());
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getPatientTotalAmount()).isEqualByComparingTo("150000");
        assertThat(paymentCaptor.getValue().getItems()).hasSize(1);
    }

    @Test
    void insuredVisitBelowTheExemptionThresholdIsCoveredInFullButPaysThePriceDifference() {
        // BR-27: 100 000 within scope is below 15% of 2 340 000, so the 80% card is applied at 100%; the patient
        // still pays the difference between the clinic price and the insurance price.
        var result = registerInsuredVisit((short) 80);

        // BR-15: the insured patient waits for the examination now; the receipt stays pending until the end.
        assertThat(result.response().status()).isEqualTo(VisitStatus.WAITING);
        assertThat(result.payment().getTotalAmount()).isEqualByComparingTo("150000");
        assertThat(result.payment().getInsuranceTotalAmount()).isEqualByComparingTo("100000");
        assertThat(result.payment().getPatientTotalAmount()).isEqualByComparingTo("50000");
        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.payment().getItems()).singleElement()
                .satisfies(item -> assertThat(item.getCoverageRate()).isEqualTo((short) 100));
    }

    @Test
    void insuredVisitFromTheExemptionThresholdUsesTheCardRate() {
        // 380 000 within scope is not below the 351 000 threshold: 80% of it is covered.
        var result = registerInsuredVisit((short) 80, "400000", "380000");

        assertThat(result.payment().getInsuranceTotalAmount()).isEqualByComparingTo("304000");
        assertThat(result.payment().getPatientTotalAmount()).isEqualByComparingTo("96000");
    }

    @Test
    void fullyCoveredInsuredVisitGoesDirectlyToWaitingRoom() {
        var result = registerInsuredVisit((short) 100, "150000", "150000");

        assertThat(result.response().status()).isEqualTo(VisitStatus.WAITING);
        assertThat(result.payment().getPatientTotalAmount()).isZero();
        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.FEE_EXEMPT);
    }

    @Test
    void rejectsALegacyPriorityReasonForANewVisit() {
        UUID patientId = UUID.randomUUID();
        Room room = new Room(
                BUSY_ROOM_ID, clock.instant(), UUID.randomUUID(), null,
                "PK01", "Phòng PK01", "Tầng 1", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        when(visitRepository.existsByPatientIdAndStatusNotIn(patientId, VisitRepository.FINISHED_STATUSES))
                .thenReturn(false);
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(mock(Patient.class)));
        when(roomRepository.findById(BUSY_ROOM_ID)).thenReturn(Optional.of(room));
        when(examinationRoomDuty.onDutyDoctor(BUSY_ROOM_ID, clock.instant()))
                .thenReturn(Optional.of(mock(StaffAccount.class)));

        // ELDERLY predates V46; a new visit must use one of the BR-14 groups.
        assertThatThrownBy(() -> service.createVisit(
                new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID()),
                new CreateVisitRequest(
                        patientId, BUSY_ROOM_ID, VisitDoituongbnLoai.DV, null,
                        "Đau đầu", "EXAMINATION", "TU_DEN", true, "ELDERLY",
                        null, null)))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REQUEST_VALIDATION_FAILED);
                    assertThat(exception.getDetails()).containsKey("priorityReason");
                });
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void rejectsRegistrationWhenPatientAlreadyHasActiveVisit() {
        UUID patientId = UUID.randomUUID();
        when(visitRepository.existsByPatientIdAndStatusNotIn(patientId, VisitRepository.FINISHED_STATUSES))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createVisit(
                new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID()),
                new CreateVisitRequest(
                        patientId, BUSY_ROOM_ID, VisitDoituongbnLoai.DV, null,
                        "Đau đầu", "EXAMINATION", "TU_DEN", false, null,
                        null, null)))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACTIVE_VISIT_EXISTS));
        verifyNoInteractions(paymentRepository);
    }

    private StaffRoomAssignment assignment(UUID staffAccountId, UUID roomId) {
        return new StaffRoomAssignment(
                UUID.randomUUID(), staffAccountId, roomId, clock.instant(), null, null, null, 0L);
    }

    private static StaffAccount staff(
            UUID id, String title, String name, clinic.backend.enums.Role role, AccountStatus status) {
        return StaffAccount.builder()
                .id(id).academicTitle(title).fullName(name).role(role).status(status)
                .build();
    }

    private static Department department(UUID id, String name) {
        Department department = mock(Department.class);
        when(department.getId()).thenReturn(id);
        when(department.getDepartmentName()).thenReturn(name);
        return department;
    }

    @Test
    void acceptSkippedWaitingVisitStartsExamination() {
        stubDoctorOnDuty();
        Visit visit = visit(VisitStatus.WAITING);
        visit.markSkipped(clock.instant().minusSeconds(60));
        when(visitRepository.findById(visit.getId())).thenReturn(Optional.of(visit));
        when(visitRepository.save(visit)).thenReturn(visit);

        VisitResponse response = service.acceptSkippedVisit(
                new StaffPrincipal(DOCTOR_ID, UUID.randomUUID()), visit.getId());

        assertThat(response.status()).isEqualTo(VisitStatus.IN_EXAMINATION);
        assertThat(response.isSkipped()).isFalse();
        assertThat(response.skippedAt()).isNull();
        assertThat(visit.getDoctorAccountId()).isEqualTo(DOCTOR_ID);
        assertThat(visit.getDenkhambenhluc()).isEqualTo(clock.instant());
        verify(auditLogService).recordBusinessChange(
                DOCTOR_ID, AuditLogAction.UPDATE, "visits", visit.getId(), visit.getSovaovien(),
                Map.of(
                        "action", "ACCEPT_SKIPPED",
                        "interruptedStage", "WAITING",
                        "status", "IN_EXAMINATION"),
                clock.instant());
    }

    @Test
    void acceptSkippedConclusionVisitPreservesAssignedDoctor() {
        stubDoctorOnDuty();
        UUID assignedDoctorId = UUID.randomUUID();
        Visit visit = visit(VisitStatus.WAITING_CONCLUSION);
        visit.continueConclusion(assignedDoctorId, clock.instant().minusSeconds(120));
        visit.markSkipped(clock.instant().minusSeconds(60));
        when(visitRepository.findById(visit.getId())).thenReturn(Optional.of(visit));
        when(visitRepository.save(visit)).thenReturn(visit);

        VisitResponse response = service.acceptSkippedVisit(
                new StaffPrincipal(DOCTOR_ID, UUID.randomUUID()), visit.getId());

        assertThat(response.status()).isEqualTo(VisitStatus.WAITING_CONCLUSION);
        assertThat(response.isSkipped()).isFalse();
        assertThat(response.skippedAt()).isNull();
        assertThat(visit.getDoctorAccountId()).isEqualTo(assignedDoctorId);
        verify(auditLogService).recordBusinessChange(
                DOCTOR_ID, AuditLogAction.UPDATE, "visits", visit.getId(), visit.getSovaovien(),
                Map.of(
                        "action", "ACCEPT_SKIPPED",
                        "interruptedStage", "WAITING_CONCLUSION",
                        "status", "WAITING_CONCLUSION"),
                clock.instant());
    }

    @Test
    void rejectsAcceptWhenVisitIsNotSkipped() {
        stubDoctorOnDuty();
        Visit visit = visit(VisitStatus.WAITING);
        when(visitRepository.findById(visit.getId())).thenReturn(Optional.of(visit));

        assertThatThrownBy(() -> service.acceptSkippedVisit(
                new StaffPrincipal(DOCTOR_ID, UUID.randomUUID()), visit.getId()))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));

        verifyNoInteractions(auditLogService);
    }

    private static Room room(UUID id, String code) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(id);
        when(room.getRoomCode()).thenReturn(code);
        when(room.getRoomName()).thenReturn("Phòng " + code);
        return room;
    }

    private void stubDoctorOnDuty() {
        Room assignedRoom = new Room(
                BUSY_ROOM_ID, clock.instant(), UUID.randomUUID(), null,
                "PK01", "Phòng PK01", "Tầng 1", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        StaffAccount doctor = StaffAccount.builder()
                .id(DOCTOR_ID)
                .fullName("Nguyễn Văn An")
                .role(clinic.backend.enums.Role.DOCTOR)
                .status(AccountStatus.ACTIVE)
                .build();
        StaffRoomAssignment assignment = new StaffRoomAssignment(
                UUID.randomUUID(), DOCTOR_ID, BUSY_ROOM_ID, clock.instant(), null, null, null, 0L);
        DutySchedule schedule = mock(DutySchedule.class);
        when(schedule.getRoomId()).thenReturn(BUSY_ROOM_ID);
        when(staffAccountRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        when(staffRoomAssignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(DOCTOR_ID))
                .thenReturn(Optional.of(assignment));
        when(roomRepository.findById(BUSY_ROOM_ID)).thenReturn(Optional.of(assignedRoom));
        when(dutyScheduleRepository.findByWorkDateAndShiftAndStaffAccountId(
                QUEUE_DATE, DutyShift.MORNING, DOCTOR_ID)).thenReturn(Optional.of(schedule));
    }

    private Visit visit(VisitStatus status) {
        Instant registeredAt = clock.instant().minusSeconds(300);
        return new Visit(
                UUID.randomUUID(), registeredAt, "DK0000000046", UUID.randomUUID(), BUSY_ROOM_ID,
                UUID.randomUUID(), registeredAt, VisitDoituongbnLoai.DV, null,
                "Đau đầu", "TU_DEN", "Đau đầu", false, null, QUEUE_DATE, 46, status, null);
    }

    private static Visit skippedVisit(int queueNumber, VisitStatus status, Instant skippedAt) {
        Visit visit = mock(Visit.class);
        when(visit.getId()).thenReturn(UUID.randomUUID());
        when(visit.getPatientId()).thenReturn(UUID.randomUUID());
        when(visit.getQueueNumber()).thenReturn(queueNumber);
        when(visit.getDoituongbnLoai()).thenReturn(VisitDoituongbnLoai.DV);
        when(visit.getThoigianvaovien()).thenReturn(skippedAt.minusSeconds(300));
        when(visit.getStatus()).thenReturn(status);
        when(visit.isSkipped()).thenReturn(true);
        when(visit.getSkippedAt()).thenReturn(skippedAt);
        return visit;
    }

    private static Visit queuedVisit(int queueNumber, boolean priority) {
        return queuedVisit(queueNumber, priority, VisitStatus.WAITING);
    }

    private static Visit queuedVisit(int queueNumber, boolean priority, VisitStatus status) {
        Visit visit = mock(Visit.class);
        when(visit.getId()).thenReturn(UUID.randomUUID());
        when(visit.getPatientId()).thenReturn(UUID.randomUUID());
        when(visit.getQueueNumber()).thenReturn(queueNumber);
        when(visit.isPriority()).thenReturn(priority);
        when(visit.getPriorityReason()).thenReturn(priority ? clinic.backend.enums.PriorityReason.ELDERLY : null);
        when(visit.getDoituongbnLoai()).thenReturn(VisitDoituongbnLoai.DV);
        when(visit.getLydovaovien()).thenReturn("Đau đầu");
        when(visit.getThoigianvaovien()).thenReturn(Instant.parse("2026-10-30T01:00:00Z"));
        when(visit.getStatus()).thenReturn(status);
        return visit;
    }

    private static Patient patient(UUID id, String name) {
        Patient patient = mock(Patient.class);
        when(patient.getId()).thenReturn(id);
        when(patient.getHoten()).thenReturn(name);
        when(patient.getMabenhnhan()).thenReturn("BN-" + name);
        when(patient.getNgaysinh()).thenReturn(LocalDate.of(1990, 1, 1));
        when(patient.getGioitinh()).thenReturn((short) 1);
        return patient;
    }

    private RegistrationResult registerInsuredVisit(short coverageRate) {
        return registerInsuredVisit(coverageRate, "150000", "100000");
    }

    private RegistrationResult registerInsuredVisit(short coverageRate, String clinicPrice, String insurancePrice) {
        UUID patientId = UUID.randomUUID();
        UUID receptionistId = UUID.randomUUID();
        Patient patient = mock(Patient.class);
        Room room = new Room(
                BUSY_ROOM_ID, clock.instant(), UUID.randomUUID(), null,
                "PK01", "Phòng khám 01", "Tầng 1", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        MedicalService examination = MedicalService.create(
                UUID.randomUUID(), clock.instant(), "KB", "Khám bệnh", MedicalServiceType.EXAMINATION,
                null, null, new BigDecimal(clinicPrice), new BigDecimal(insurancePrice), null);
        when(patient.getId()).thenReturn(patientId);
        when(patient.getHoten()).thenReturn("Nguyễn Văn A");
        when(patient.getMabenhnhan()).thenReturn("BN00000001");
        when(patient.getNgaysinh()).thenReturn(LocalDate.of(1990, 1, 1));
        when(patient.getGioitinh()).thenReturn((short) 1);
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(roomRepository.findById(BUSY_ROOM_ID)).thenReturn(Optional.of(room));
        when(examinationRoomDuty.onDutyDoctor(BUSY_ROOM_ID, clock.instant()))
                .thenReturn(Optional.of(mock(StaffAccount.class)));
        when(medicalServiceRepository.findByTypeAndDepartmentIdIsNullAndServiceGroupIdIsNullAndStatus(
                MedicalServiceType.EXAMINATION, ActivationStatus.ACTIVE)).thenReturn(Optional.of(examination));
        when(insuranceVerificationService.verify(any())).thenReturn(new InsuranceVerificationResponse(
                "VALID", "1234567890", "1234567890", "00", true, null, null,
                "CCCD", false, "Nguyễn Văn A", "1990-01-01", (short) 3, (short) 1,
                coverageRate, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                "79001", "Cơ sở đăng ký ban đầu"));
        when(roomQueueCounterRepository.takeNext(BUSY_ROOM_ID, QUEUE_DATE)).thenReturn(1);
        when(visitRepository.nextRegistrationCode()).thenReturn(1L);
        when(visitRepository.nextPaymentCode()).thenReturn(1L);
        when(uuidV7Generator.next()).thenReturn(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(visitRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createVisit(
                new StaffPrincipal(receptionistId, UUID.randomUUID()),
                new CreateVisitRequest(
                        patientId, BUSY_ROOM_ID, VisitDoituongbnLoai.BHYT, "1234567890",
                        "Đau đầu", "EXAMINATION", "TU_DEN", false, null, null, null));
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        return new RegistrationResult(response, captor.getValue());
    }

    private record RegistrationResult(VisitResponse response, Payment payment) {
    }

    private static VisitRepository.RoomQueueCount count(
            UUID roomId, VisitStatus status, boolean skipped, long total) {
        return new VisitRepository.RoomQueueCount() {
            @Override
            public UUID getRoomId() {
                return roomId;
            }

            @Override
            public VisitStatus getStatus() {
                return status;
            }

            @Override
            public Boolean getSkipped() {
                return skipped;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
