package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.RejectParaclinicalOrderRequest;
import clinic.backend.dto.response.TechnicianQueueItemResponse;
import clinic.backend.dto.response.TechnicianQueueResponse;
import clinic.backend.dto.response.TechnicianRoomResponse;
import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.entity.Visit;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.DutyShift;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.ParaclinicalOrderStatus;
import clinic.backend.enums.Role;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicalExaminationRepository;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.ImagingResultRepository;
import clinic.backend.repository.LabResultRepository;
import clinic.backend.repository.MedicalHistoryRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.RoomQueueCounterRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TechnicianQueueServiceImplTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW = Instant.parse("2026-11-08T08:30:00Z");

    @Mock
    private RoomRepository roomRepository;
    @Mock
    private RoomQueueCounterRepository roomQueueCounterRepository;
    @Mock
    private StaffAccountRepository staffAccountRepository;
    @Mock
    private StaffRoomAssignmentRepository staffRoomAssignmentRepository;
    @Mock
    private ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;
    @Mock
    private MedicalRecordRepository medicalRecordRepository;
    @Mock
    private VisitRepository visitRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private ClinicalExaminationRepository clinicalExaminationRepository;
    @Mock
    private MedicalHistoryRepository medicalHistoryRepository;
    @Mock
    private MedicalServiceRepository medicalServiceRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private DutyScheduleRepository dutyScheduleRepository;
    @Mock
    private ImagingResultRepository imagingResultRepository;
    @Mock
    private LabResultRepository labResultRepository;

    private Clock clock;
    private DutyShiftConfigServiceImpl dutyShiftConfigService;
    private TechnicianQueueServiceImpl service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, CLINIC_ZONE);
        dutyShiftConfigService = new DutyShiftConfigServiceImpl(new ObjectMapper().findAndRegisterModules());
        service = new TechnicianQueueServiceImpl(
                roomRepository,
                roomQueueCounterRepository,
                staffAccountRepository,
                staffRoomAssignmentRepository,
                paraclinicalOrderDetailRepository,
                medicalRecordRepository,
                visitRepository,
                patientRepository,
                clinicalExaminationRepository,
                medicalHistoryRepository,
                medicalServiceRepository,
                dutyScheduleRepository,
                dutyShiftConfigService,
                imagingResultRepository,
                labResultRepository,
                auditLogService,
                clock
        );
    }

    @Test
    void getTechnicianRooms_returnsActiveParaclinicalRooms() {
        UUID roomId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Room room = new Room(roomId, NOW, UUID.randomUUID(), UUID.randomUUID(), "P.105", "Phòng Siêu âm 01", "Khu B", RoomType.PARACLINICAL, ActivationStatus.ACTIVE);
        when(roomRepository.findByTypeAndStatusOrderByRoomCodeAsc(RoomType.PARACLINICAL, ActivationStatus.ACTIVE))
                .thenReturn(List.of(room));

        StaffRoomAssignment assignment = new StaffRoomAssignment(UUID.randomUUID(), staffId, room.getId(), NOW, null, null, null, 0L);
        when(staffRoomAssignmentRepository.findByRemovedAtIsNull())
                .thenReturn(List.of(assignment));

        StaffAccount tech = org.mockito.Mockito.mock(StaffAccount.class);
        when(tech.getId()).thenReturn(staffId);
        when(tech.getFullName()).thenReturn("Lê Thị Mai");
        when(tech.getAcademicTitle()).thenReturn("KTV");
        when(tech.getRole()).thenReturn(Role.TECHNICIAN);

        when(staffAccountRepository.findAllById(any())).thenReturn(List.of(tech));

        List<TechnicianRoomResponse> responses = service.getTechnicianRooms();

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().code()).isEqualTo("P.105");
        assertThat(responses.getFirst().name()).isEqualTo("Phòng Siêu âm 01");
        assertThat(responses.getFirst().activeTech()).isEqualTo("KTV Lê Thị Mai");
    }

    @Test
    void acceptOrder_transitionsToInProgressAndAudits() {
        UUID detailId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        ParaclinicalOrder order = new ParaclinicalOrder(
                orderId, NOW, "CD0000000001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Chẩn đoán", (short) 1, "Thường", "Phòng khám Nội", BigDecimal.valueOf(100000),
                ParaclinicalOrderStatus.RELEASED
        );

        ParaclinicalOrderDetail detail = new ParaclinicalOrderDetail(
                detailId, NOW, order, serviceId, "CD0000000001-01", NOW, "Siêu âm bụng", null, null,
                BigDecimal.ONE, BigDecimal.valueOf(100000), BigDecimal.valueOf(100000), BigDecimal.ZERO,
                BigDecimal.valueOf(100000), UUID.randomUUID(), ParaclinicalOrderDetailStatus.WAITING
        );
        detail.checkIn(1, technicianId, NOW);

        when(paraclinicalOrderDetailRepository.findById(detailId)).thenReturn(Optional.of(detail));
        StaffAccount tech = org.mockito.Mockito.mock(StaffAccount.class);
        when(tech.getRole()).thenReturn(Role.TECHNICIAN);
        when(staffAccountRepository.findById(technicianId)).thenReturn(Optional.of(tech));
        StaffRoomAssignment assignment = new StaffRoomAssignment(UUID.randomUUID(), technicianId, detail.getRoomId(), NOW, null, null, null, 0L);
        when(staffRoomAssignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(technicianId)).thenReturn(Optional.of(assignment));

        TechnicianQueueItemResponse response = service.acceptOrder(detailId, technicianId);

        assertThat(detail.getStatus()).isEqualTo(ParaclinicalOrderDetailStatus.IN_PROGRESS);
        assertThat(detail.getAcceptedAt()).isEqualTo(NOW);
        assertThat(detail.getPerformerAccountId()).isEqualTo(technicianId);

        verify(paraclinicalOrderDetailRepository).save(detail);
        verify(auditLogService).recordBusinessChange(
                eq(technicianId), eq(AuditLogAction.UPDATE), eq("paraclinical_order_details"),
                eq(detailId), eq("CD0000000001-01"), any(), eq(NOW)
        );
    }

    @Test
    void skipOrder_marksAsSkippedAndAudits() {
        UUID detailId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();

        ParaclinicalOrder order = new ParaclinicalOrder(
                UUID.randomUUID(), NOW, "CD0000000002", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Chẩn đoán", (short) 2, "Thường", "Phòng khám", BigDecimal.valueOf(50000),
                ParaclinicalOrderStatus.RELEASED
        );

        ParaclinicalOrderDetail detail = new ParaclinicalOrderDetail(
                detailId, NOW, order, UUID.randomUUID(), "CD0000000002-01", NOW, "X-quang", null, null,
                BigDecimal.ONE, BigDecimal.valueOf(50000), BigDecimal.valueOf(50000), BigDecimal.ZERO,
                BigDecimal.valueOf(50000), UUID.randomUUID(), ParaclinicalOrderDetailStatus.WAITING
        );

        when(paraclinicalOrderDetailRepository.findById(detailId)).thenReturn(Optional.of(detail));
        StaffAccount tech = org.mockito.Mockito.mock(StaffAccount.class);
        when(tech.getRole()).thenReturn(Role.TECHNICIAN);
        when(staffAccountRepository.findById(technicianId)).thenReturn(Optional.of(tech));
        StaffRoomAssignment assignment = new StaffRoomAssignment(UUID.randomUUID(), technicianId, detail.getRoomId(), NOW, null, null, null, 0L);
        when(staffRoomAssignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(technicianId)).thenReturn(Optional.of(assignment));

        TechnicianQueueItemResponse response = service.skipOrder(detailId, technicianId);

        assertThat(detail.isSkipped()).isTrue();
        assertThat(detail.getSkippedAt()).isEqualTo(NOW);

        verify(paraclinicalOrderDetailRepository).save(detail);
        verify(auditLogService).recordBusinessChange(
                eq(technicianId), eq(AuditLogAction.UPDATE), eq("paraclinical_order_details"),
                eq(detailId), eq("CD0000000002-01"), any(), eq(NOW)
        );
    }

    @Test
    void rejectOrder_cancelsOrderWithReason() {
        UUID detailId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();

        ParaclinicalOrder order = new ParaclinicalOrder(
                UUID.randomUUID(), NOW, "CD0000000003", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Chẩn đoán", (short) 2, "Thường", "Phòng khám", BigDecimal.valueOf(80000),
                ParaclinicalOrderStatus.RELEASED
        );

        ParaclinicalOrderDetail detail = new ParaclinicalOrderDetail(
                detailId, NOW, order, UUID.randomUUID(), "CD0000000003-01", NOW, "Nội soi", null, null,
                BigDecimal.ONE, BigDecimal.valueOf(80000), BigDecimal.valueOf(80000), BigDecimal.ZERO,
                BigDecimal.valueOf(80000), UUID.randomUUID(), ParaclinicalOrderDetailStatus.WAITING
        );

        when(paraclinicalOrderDetailRepository.findById(detailId)).thenReturn(Optional.of(detail));
        StaffAccount tech = org.mockito.Mockito.mock(StaffAccount.class);
        when(tech.getRole()).thenReturn(Role.TECHNICIAN);
        when(staffAccountRepository.findById(technicianId)).thenReturn(Optional.of(tech));
        StaffRoomAssignment assignment = new StaffRoomAssignment(UUID.randomUUID(), technicianId, detail.getRoomId(), NOW, null, null, null, 0L);
        when(staffRoomAssignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(technicianId)).thenReturn(Optional.of(assignment));

        RejectParaclinicalOrderRequest request = new RejectParaclinicalOrderRequest("Bệnh nhân chống chỉ định với thuốc thử");
        TechnicianQueueItemResponse response = service.rejectOrder(detailId, technicianId, request);

        assertThat(detail.getStatus()).isEqualTo(ParaclinicalOrderDetailStatus.CANCELLED);
        assertThat(detail.getCancelReason()).isEqualTo("Bệnh nhân chống chỉ định với thuốc thử");
        assertThat(detail.getCancelledByAccountId()).isEqualTo(technicianId);
        assertThat(detail.getCancelledAt()).isEqualTo(NOW);

        verify(paraclinicalOrderDetailRepository).save(detail);
        verify(auditLogService).recordBusinessChange(
                eq(technicianId), eq(AuditLogAction.CANCEL), eq("paraclinical_order_details"),
                eq(detailId), eq("CD0000000003-01"), any(), eq(NOW)
        );
    }

    @Test
    void acceptOrder_TechnicianNotAssignedToRoom_ThrowsException() {
        UUID detailId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID otherRoomId = UUID.randomUUID();

        ParaclinicalOrder order = new ParaclinicalOrder(
                UUID.randomUUID(), NOW, "CD0000000004", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Chẩn đoán", (short) 1, "Thường", "Phòng khám", BigDecimal.valueOf(100000),
                ParaclinicalOrderStatus.RELEASED
        );

        ParaclinicalOrderDetail detail = new ParaclinicalOrderDetail(
                detailId, NOW, order, UUID.randomUUID(), "CD0000000004-01", NOW, "Siêu âm", null, null,
                BigDecimal.ONE, BigDecimal.valueOf(100000), BigDecimal.valueOf(100000), BigDecimal.ZERO,
                BigDecimal.valueOf(100000), roomId, ParaclinicalOrderDetailStatus.WAITING
        );

        when(paraclinicalOrderDetailRepository.findById(detailId)).thenReturn(Optional.of(detail));

        StaffAccount tech = org.mockito.Mockito.mock(StaffAccount.class);
        when(tech.getRole()).thenReturn(Role.TECHNICIAN);
        when(staffAccountRepository.findById(technicianId)).thenReturn(Optional.of(tech));
        StaffRoomAssignment assignment = new StaffRoomAssignment(UUID.randomUUID(), technicianId, otherRoomId, NOW, null, null, null, 0L);
        when(staffRoomAssignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(technicianId)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.acceptOrder(detailId, technicianId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    AppException appException = (AppException) e;
                    assertThat(appException.getErrorCode()).isEqualTo(ErrorCode.TECHNICIAN_NOT_ASSIGNED_TO_ROOM);
                })
                .hasMessageContaining("Bạn không được phân công phụ trách phòng máy này");
    }
    @Test
    void checkInOrder_assignsQueueNumberAndAudits() {
        UUID detailId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        ParaclinicalOrder order = new ParaclinicalOrder(
                UUID.randomUUID(), NOW, "CD0000000005", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Chẩn đoán", (short) 1, "Thường", "Phòng khám", BigDecimal.valueOf(100000),
                ParaclinicalOrderStatus.RELEASED
        );

        ParaclinicalOrderDetail detail = new ParaclinicalOrderDetail(
                detailId, NOW, order, UUID.randomUUID(), "CD0000000005-01", NOW, "Siêu âm", null, null,
                BigDecimal.ONE, BigDecimal.valueOf(100000), BigDecimal.valueOf(100000), BigDecimal.ZERO,
                BigDecimal.valueOf(100000), roomId, ParaclinicalOrderDetailStatus.WAITING
        );

        when(paraclinicalOrderDetailRepository.findById(detailId)).thenReturn(Optional.of(detail));
        StaffAccount tech = org.mockito.Mockito.mock(StaffAccount.class);
        when(tech.getRole()).thenReturn(Role.TECHNICIAN);
        when(staffAccountRepository.findById(technicianId)).thenReturn(Optional.of(tech));
        StaffRoomAssignment assignment = new StaffRoomAssignment(UUID.randomUUID(), technicianId, roomId, NOW, null, null, null, 0L);
        when(staffRoomAssignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(technicianId)).thenReturn(Optional.of(assignment));

        when(roomQueueCounterRepository.takeNextParaclinical(eq(roomId), any(), any(), any())).thenReturn(5);

        service.checkInOrder(detailId, technicianId);

        assertThat(detail.getQueueNumber()).isEqualTo(5);
        assertThat(detail.getCheckedInAt()).isEqualTo(NOW);
        assertThat(detail.getCheckedInByAccountId()).isEqualTo(technicianId);
        assertThat(detail.isCheckedIn()).isTrue();

        verify(paraclinicalOrderDetailRepository).save(detail);
        verify(auditLogService).recordBusinessChange(
                eq(technicianId), eq(AuditLogAction.UPDATE), eq("paraclinical_order_details"),
                eq(detailId), eq("CD0000000005-01"), any(), eq(NOW)
        );
    }

    @Test
    void checkInOrder_whenAlreadyCheckedIn_throwsConflict() {
        UUID detailId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        ParaclinicalOrder order = new ParaclinicalOrder(
                UUID.randomUUID(), NOW, "CD0000000006", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Chẩn đoán", (short) 1, "Thường", "Phòng khám", BigDecimal.valueOf(100000),
                ParaclinicalOrderStatus.RELEASED
        );

        ParaclinicalOrderDetail detail = new ParaclinicalOrderDetail(
                detailId, NOW, order, UUID.randomUUID(), "CD0000000006-01", NOW, "Siêu âm", null, null,
                BigDecimal.ONE, BigDecimal.valueOf(100000), BigDecimal.valueOf(100000), BigDecimal.ZERO,
                BigDecimal.valueOf(100000), roomId, ParaclinicalOrderDetailStatus.WAITING
        );
        detail.checkIn(1, technicianId, NOW);

        when(paraclinicalOrderDetailRepository.findById(detailId)).thenReturn(Optional.of(detail));
        StaffAccount tech = org.mockito.Mockito.mock(StaffAccount.class);
        when(tech.getRole()).thenReturn(Role.TECHNICIAN);
        when(staffAccountRepository.findById(technicianId)).thenReturn(Optional.of(tech));
        StaffRoomAssignment assignment = new StaffRoomAssignment(UUID.randomUUID(), technicianId, roomId, NOW, null, null, null, 0L);
        when(staffRoomAssignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(technicianId)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.checkInOrder(detailId, technicianId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.ORDER_ALREADY_CHECKED_IN));
    }

    @Test
    void acceptOrder_whenNotCheckedIn_throwsConflict() {
        UUID detailId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        ParaclinicalOrder order = new ParaclinicalOrder(
                UUID.randomUUID(), NOW, "CD0000000007", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Chẩn đoán", (short) 1, "Thường", "Phòng khám", BigDecimal.valueOf(100000),
                ParaclinicalOrderStatus.RELEASED
        );

        ParaclinicalOrderDetail detail = new ParaclinicalOrderDetail(
                detailId, NOW, order, UUID.randomUUID(), "CD0000000007-01", NOW, "Siêu âm", null, null,
                BigDecimal.ONE, BigDecimal.valueOf(100000), BigDecimal.valueOf(100000), BigDecimal.ZERO,
                BigDecimal.valueOf(100000), roomId, ParaclinicalOrderDetailStatus.WAITING
        );

        when(paraclinicalOrderDetailRepository.findById(detailId)).thenReturn(Optional.of(detail));
        StaffAccount tech = org.mockito.Mockito.mock(StaffAccount.class);
        when(tech.getRole()).thenReturn(Role.TECHNICIAN);
        when(staffAccountRepository.findById(technicianId)).thenReturn(Optional.of(tech));
        StaffRoomAssignment assignment = new StaffRoomAssignment(UUID.randomUUID(), technicianId, roomId, NOW, null, null, null, 0L);
        when(staffRoomAssignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(technicianId)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.acceptOrder(detailId, technicianId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_CHECKED_IN));
    }

    @Test
    void acceptOrder_whenOffDutyBecauseColleagueIsScheduled_throwsTechnicianNotAssigned() {
        UUID detailId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        UUID colleagueId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        LocalDate today = LocalDate.ofInstant(NOW, CLINIC_ZONE);

        ParaclinicalOrder order = new ParaclinicalOrder(
                UUID.randomUUID(), NOW, "CD0000000008", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Chẩn đoán", (short) 1, "Thường", "Phòng khám", BigDecimal.valueOf(100000),
                ParaclinicalOrderStatus.RELEASED
        );

        ParaclinicalOrderDetail detail = new ParaclinicalOrderDetail(
                detailId, NOW, order, UUID.randomUUID(), "CD0000000008-01", NOW, "Siêu âm", null, null,
                BigDecimal.ONE, BigDecimal.valueOf(100000), BigDecimal.valueOf(100000), BigDecimal.ZERO,
                BigDecimal.valueOf(100000), roomId, ParaclinicalOrderDetailStatus.WAITING
        );
        detail.checkIn(1, technicianId, NOW);

        when(paraclinicalOrderDetailRepository.findById(detailId)).thenReturn(Optional.of(detail));
        StaffAccount tech = org.mockito.Mockito.mock(StaffAccount.class);
        when(tech.getRole()).thenReturn(Role.TECHNICIAN);
        when(staffAccountRepository.findById(technicianId)).thenReturn(Optional.of(tech));

        // Phòng hôm nay có lịch trực phân công cho colleagueId (ví dụ Tạ Thị Loan)
        DutySchedule colleagueSchedule = new DutySchedule(
                UUID.randomUUID(), NOW, today, DutyShift.AFTERNOON, colleagueId, roomId, null, UUID.randomUUID()
        );
        when(dutyScheduleRepository.findByRoomIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(roomId, today, today))
                .thenReturn(List.of(colleagueSchedule));

        assertThatThrownBy(() -> service.acceptOrder(detailId, technicianId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.TECHNICIAN_NOT_ASSIGNED_TO_ROOM));
    }

    @Test
    void acceptOrder_whenScheduledOnDuty_succeeds() {
        UUID detailId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        LocalDate today = LocalDate.ofInstant(NOW, CLINIC_ZONE);

        ParaclinicalOrder order = new ParaclinicalOrder(
                UUID.randomUUID(), NOW, "CD0000000009", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Chẩn đoán", (short) 1, "Thường", "Phòng khám", BigDecimal.valueOf(100000),
                ParaclinicalOrderStatus.RELEASED
        );

        ParaclinicalOrderDetail detail = new ParaclinicalOrderDetail(
                detailId, NOW, order, UUID.randomUUID(), "CD0000000009-01", NOW, "Siêu âm", null, null,
                BigDecimal.ONE, BigDecimal.valueOf(100000), BigDecimal.valueOf(100000), BigDecimal.ZERO,
                BigDecimal.valueOf(100000), roomId, ParaclinicalOrderDetailStatus.WAITING
        );
        detail.checkIn(1, technicianId, NOW);

        when(paraclinicalOrderDetailRepository.findById(detailId)).thenReturn(Optional.of(detail));
        StaffAccount tech = org.mockito.Mockito.mock(StaffAccount.class);
        when(tech.getRole()).thenReturn(Role.TECHNICIAN);
        when(staffAccountRepository.findById(technicianId)).thenReturn(Optional.of(tech));

        // KTV được phân công trực đúng ca hiện tại (AFTERNOON)
        DutySchedule schedule = new DutySchedule(
                UUID.randomUUID(), NOW, today, DutyShift.AFTERNOON, technicianId, roomId, null, UUID.randomUUID()
        );
        when(dutyScheduleRepository.findByRoomIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(roomId, today, today))
                .thenReturn(List.of(schedule));

        TechnicianQueueItemResponse response = service.acceptOrder(detailId, technicianId);

        assertThat(detail.getStatus()).isEqualTo(ParaclinicalOrderDetailStatus.IN_PROGRESS);
        assertThat(detail.getPerformerAccountId()).isEqualTo(technicianId);
    }

    @Test
    void rejectsAnUnknownStatusFilterOrATooLongSearch() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> TechnicianQueueServiceImpl.validateFilters(
                        null, "DONE", TechnicianQueueServiceImpl.QUEUE_STATUSES))
                .isInstanceOfSatisfying(clinic.backend.exceptions.AppException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getDetails()).containsKey("status"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> TechnicianQueueServiceImpl.validateFilters(
                        "a".repeat(101), null, TechnicianQueueServiceImpl.PENDING_RESULT_STATUSES))
                .isInstanceOfSatisfying(clinic.backend.exceptions.AppException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getDetails()).containsKey("search"));
    }

    @Test
    void acceptsTheStatusFiltersTheScreensUse() {
        for (String status : new String[] {"ALL", "WAITING", "SKIPPED", "IN_PROGRESS", "CANCELLED", "waiting", "", null}) {
            TechnicianQueueServiceImpl.validateFilters("an", status, TechnicianQueueServiceImpl.QUEUE_STATUSES);
        }
        for (String status : new String[] {"ALL", "PENDING", "DRAFT"}) {
            TechnicianQueueServiceImpl.validateFilters(null, status, TechnicianQueueServiceImpl.PENDING_RESULT_STATUSES);
        }
    }
}
