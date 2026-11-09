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
import clinic.backend.repository.MedicalHistoryRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
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

    private Clock clock;
    private TechnicianQueueServiceImpl service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, CLINIC_ZONE);
        service = new TechnicianQueueServiceImpl(
                roomRepository,
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
}
