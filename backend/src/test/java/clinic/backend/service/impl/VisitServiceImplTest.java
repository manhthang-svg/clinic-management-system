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
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Payment;
import clinic.backend.entity.Room;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalServiceType;
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
import clinic.backend.repository.FollowUpAppointmentRepository;
import clinic.backend.repository.RoomQueueCounterRepository;
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
    private StaffRoomAssignmentRepository staffRoomAssignmentRepository;

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
            Instant.parse("2026-10-29T17:00:00Z"), ZoneOffset.UTC);

    private VisitServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VisitServiceImpl(
                medicalServiceRepository,
                paymentRepository,
                patientRepository,
                roomRepository,
                visitRepository,
                staffRoomAssignmentRepository,
                followUpAppointmentRepository,
                insuranceVerificationService,
                roomQueueCounterRepository,
                auditLogService,
                uuidV7Generator,
                clock);
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
        return room;
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
