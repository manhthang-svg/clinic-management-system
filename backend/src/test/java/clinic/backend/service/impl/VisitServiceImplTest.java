package clinic.backend.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import clinic.backend.entity.Room;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.VisitStatus;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                uuidV7Generator);
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
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        when(roomRepository.findByTypeAndStatusOrderByRoomCodeAsc(RoomType.EXAMINATION, ActivationStatus.ACTIVE))
                .thenReturn(List.of());
        when(visitRepository.countRoomQueues(today)).thenReturn(List.of());

        service.getRoomQueueStatus(null);

        verify(visitRepository).countRoomQueues(today);
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
