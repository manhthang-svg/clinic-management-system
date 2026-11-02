package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.entity.Visit;
import clinic.backend.entity.VisitTransfer;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.ActivationStatus;
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
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.repository.VisitTransferRepository;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.FollowUpAppointmentRepository;
import clinic.backend.repository.RoomQueueCounterRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.InsuranceVerificationService;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
                        VisitStatus.WAITING, VisitStatus.IN_EXAMINATION, VisitStatus.WAITING_CONCLUSION)))
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
                DoctorQueueTab.WAITING, null, null, null, 0, 20);

        assertThat(queue.roomId()).isEqualTo(BUSY_ROOM_ID);
        assertThat(queue.doctorName()).isEqualTo("BS Nguyễn Văn An");
        assertThat(queue.onDuty()).isTrue();
        assertThat(queue.waitingCount()).isEqualTo(3);
        assertThat(queue.visits().page().totalElements()).isEqualTo(3);
        assertThat(queue.visits().content()).extracting("patientName")
                .containsExactly("Ưu tiên", "Chuyển phòng", "Thường");
        assertThat(queue.visits().content().get(1).sourceRoomName()).isEqualTo(sourceRoom.getRoomName());

        var filtered = service.getDoctorQueue(
                new StaffPrincipal(DOCTOR_ID, UUID.randomUUID()),
                DoctorQueueTab.WAITING, "chuyển", false, VisitDoituongbnLoai.DV, 0, 10);
        assertThat(filtered.visits().content()).extracting("patientName")
                .containsExactly("Chuyển phòng");
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
                DoctorQueueTab.WAITING, null, null, null, 0, 20))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ROOM_ASSIGNMENT_REQUIRED));
        verifyNoInteractions(visitRepository);
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
        when(staffRoomAssignmentRepository.hasActiveDoctor(BUSY_ROOM_ID)).thenReturn(true);
        when(medicalServiceRepository.findByTypeAndDepartmentIdIsNullAndServiceGroupIdIsNullAndStatus(
                MedicalServiceType.EXAMINATION, ActivationStatus.ACTIVE)).thenReturn(Optional.of(examination));
        when(roomQueueCounterRepository.takeNext(BUSY_ROOM_ID, QUEUE_DATE)).thenReturn(1);
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
        assertThat(response.queueNumber()).isEqualTo(1);
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getPatientTotalAmount()).isEqualByComparingTo("150000");
        assertThat(paymentCaptor.getValue().getItems()).hasSize(1);
    }

    @Test
    void insuredVisitWithPatientShareWaitsForPayment() {
        var result = registerInsuredVisit((short) 80);

        assertThat(result.response().status()).isEqualTo(VisitStatus.WAITING_FEE_PAYMENT);
        assertThat(result.payment().getPatientTotalAmount()).isEqualByComparingTo("20000");
        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void fullyCoveredInsuredVisitGoesDirectlyToWaitingRoom() {
        var result = registerInsuredVisit((short) 100);

        assertThat(result.response().status()).isEqualTo(VisitStatus.WAITING);
        assertThat(result.payment().getPatientTotalAmount()).isZero();
        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.FEE_EXEMPT);
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

    private static Room room(UUID id, String code) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(id);
        when(room.getRoomCode()).thenReturn(code);
        when(room.getRoomName()).thenReturn("Phòng " + code);
        return room;
    }

    private static Visit queuedVisit(int queueNumber, boolean priority) {
        Visit visit = mock(Visit.class);
        when(visit.getId()).thenReturn(UUID.randomUUID());
        when(visit.getPatientId()).thenReturn(UUID.randomUUID());
        when(visit.getQueueNumber()).thenReturn(queueNumber);
        when(visit.isPriority()).thenReturn(priority);
        when(visit.getDoituongbnLoai()).thenReturn(VisitDoituongbnLoai.DV);
        when(visit.getLydovaovien()).thenReturn("Đau đầu");
        when(visit.getThoigianvaovien()).thenReturn(Instant.parse("2026-10-30T01:00:00Z"));
        when(visit.getStatus()).thenReturn(VisitStatus.WAITING);
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
        UUID patientId = UUID.randomUUID();
        UUID receptionistId = UUID.randomUUID();
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
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(roomRepository.findById(BUSY_ROOM_ID)).thenReturn(Optional.of(room));
        when(staffRoomAssignmentRepository.hasActiveDoctor(BUSY_ROOM_ID)).thenReturn(true);
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
