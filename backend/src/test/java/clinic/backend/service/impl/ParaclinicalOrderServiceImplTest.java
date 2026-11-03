package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.CreateParaclinicalOrderRequest;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.Payment;
import clinic.backend.entity.Visit;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.ParaclinicalOrderStatus;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.PaymentType;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderRepository;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.ServiceGroupRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParaclinicalOrderServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-03T03:00:00Z");
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID VISIT_ID = UUID.randomUUID();

    @Mock private VisitRepository visitRepository;
    @Mock private MedicalRecordRepository medicalRecordRepository;
    @Mock private MedicalServiceRepository medicalServiceRepository;
    @Mock private ServiceGroupRepository serviceGroupRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ParaclinicalOrderRepository paraclinicalOrderRepository;
    @Mock private AuditLogService auditLogService;

    private ParaclinicalOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh"));
        service = new ParaclinicalOrderServiceImpl(
                visitRepository, medicalRecordRepository, medicalServiceRepository, serviceGroupRepository,
                roomRepository, paymentRepository, paraclinicalOrderRepository, auditLogService,
                new UuidV7Generator(clock, new SecureRandom()), clock);
    }

    @Test
    void createsPendingOrdersAndOneReceiptWithoutPickingARoom() {
        Visit visit = visit(VisitStatus.IN_EXAMINATION, DOCTOR_ID);
        MedicalService ultrasound = service("SA-OB", "Siêu âm ổ bụng", MedicalServiceType.IMAGING, 200000);
        MedicalService blood = service("XN-CTM", "Tổng phân tích máu", MedicalServiceType.LAB, 80000);
        stubOrder(visit, List.of(ultrasound, blood));

        var response = service.createOrder(DOCTOR_ID, request(ultrasound.getId(), blood.getId()));

        assertThat(response.status()).isEqualTo(ParaclinicalOrderStatus.PENDING_PAYMENT);
        assertThat(response.tongcong()).isEqualByComparingTo("280000");
        assertThat(response.paymentCode()).isEqualTo("HD0000000042");
        assertThat(response.sophieu()).isEqualTo("CD0000000042");
        assertThat(response.details())
                .extracting(d -> d.machidinh(), d -> d.status(), d -> d.roomId())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("CD0000000042-01", "PENDING_PAYMENT", null),
                        org.assertj.core.groups.Tuple.tuple("CD0000000042-02", "PENDING_PAYMENT", null));
        assertThat(visit.getStatus()).isEqualTo(VisitStatus.WAITING_PARACLINICAL);

        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(payment.capture());
        assertThat(payment.getValue().getType()).isEqualTo(PaymentType.PARACLINICAL_FEE);
        assertThat(payment.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getValue().getItems()).hasSize(2);
        verify(auditLogService).recordBusinessChange(
                eq(DOCTOR_ID), eq(AuditLogAction.CREATE), eq("paraclinical_orders"), any(),
                eq(visit.getSovaovien()), any(), eq(NOW));
    }

    @Test
    void savesTheReceiptBeforeTheOrderAndItsLinesAfterIt() {
        Visit visit = visit(VisitStatus.IN_EXAMINATION, DOCTOR_ID);
        MedicalService ultrasound = service("SA-OB", "Siêu âm ổ bụng", MedicalServiceType.IMAGING, 200000);
        stubOrder(visit, List.of(ultrasound));

        service.createOrder(DOCTOR_ID, request(ultrasound.getId()));

        InOrder order = inOrder(paymentRepository, paraclinicalOrderRepository);
        order.verify(paymentRepository).saveAndFlush(any(Payment.class));
        order.verify(paraclinicalOrderRepository).saveAndFlush(any(ParaclinicalOrder.class));
        order.verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void allowsAdditionalTestsFromWaitingForConclusion() {
        Visit visit = visit(VisitStatus.WAITING_CONCLUSION, DOCTOR_ID);
        MedicalService ultrasound = service("SA-OB", "Siêu âm ổ bụng", MedicalServiceType.IMAGING, 200000);
        stubOrder(visit, List.of(ultrasound));

        service.createOrder(DOCTOR_ID, request(ultrasound.getId()));

        assertThat(visit.getStatus()).isEqualTo(VisitStatus.WAITING_PARACLINICAL);
    }

    @Test
    void refusesAVisitThatIsNotBeingExaminedByThisDoctor() {
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.of(visit(VisitStatus.IN_EXAMINATION, UUID.randomUUID())));

        assertThatThrownBy(() -> service.createOrder(DOCTOR_ID, request(UUID.randomUUID())))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VISIT_NOT_IN_EXAMINATION));
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesDuplicateServices() {
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.of(visit(VisitStatus.IN_EXAMINATION, DOCTOR_ID)));
        UUID serviceId = UUID.randomUUID();

        assertThatThrownBy(() -> service.createOrder(DOCTOR_ID, request(serviceId, serviceId)))
                .isInstanceOfSatisfying(AppException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REQUEST_VALIDATION_FAILED);
                    assertThat(e.getDetails()).containsEntry("serviceIds", "DUPLICATE");
                });
    }

    @Test
    void refusesAServiceDeactivatedMeanwhile() {
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.of(visit(VisitStatus.IN_EXAMINATION, DOCTOR_ID)));
        MedicalService ultrasound = service("SA-OB", "Siêu âm ổ bụng", MedicalServiceType.IMAGING, 200000);
        ultrasound.changeStatus(ActivationStatus.INACTIVE, NOW);
        when(medicalServiceRepository.findAllById(List.of(ultrasound.getId()))).thenReturn(List.of(ultrasound));

        assertThatThrownBy(() -> service.createOrder(DOCTOR_ID, request(ultrasound.getId())))
                .isInstanceOfSatisfying(AppException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SERVICE_INACTIVE);
                    assertThat(e.getDetails()).containsEntry("serviceName", "Siêu âm ổ bụng");
                });
    }

    @Test
    void refusesTheExaminationServiceAsAParaclinicalOrder() {
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.of(visit(VisitStatus.IN_EXAMINATION, DOCTOR_ID)));
        MedicalService exam = MedicalService.create(UUID.randomUUID(), NOW, "KB", "Khám bệnh",
                MedicalServiceType.EXAMINATION, null, null, BigDecimal.valueOf(100000), null, null);
        when(medicalServiceRepository.findAllById(List.of(exam.getId()))).thenReturn(List.of(exam));

        assertThatThrownBy(() -> service.createOrder(DOCTOR_ID, request(exam.getId())))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SERVICE_NOT_FOUND));
    }

    @Test
    void refusesAClosedMedicalRecord() {
        Visit visit = visit(VisitStatus.IN_EXAMINATION, DOCTOR_ID);
        MedicalService ultrasound = service("SA-OB", "Siêu âm ổ bụng", MedicalServiceType.IMAGING, 200000);
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.of(visit));
        when(medicalServiceRepository.findAllById(List.of(ultrasound.getId()))).thenReturn(List.of(ultrasound));
        when(medicalRecordRepository.findByVisitId(VISIT_ID)).thenReturn(Optional.of(new MedicalRecord(
                UUID.randomUUID(), NOW, VISIT_ID, "BA-1", "NGOAITRU", MedicalRecordStatus.LOCKED, NOW, DOCTOR_ID)));

        assertThatThrownBy(() -> service.createOrder(DOCTOR_ID, request(ultrasound.getId())))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEDICAL_RECORD_CLOSED));
    }

    @Test
    void listsOnlyActiveParaclinicalServices() {
        MedicalService ultrasound = service("SA-OB", "Siêu âm ổ bụng", MedicalServiceType.IMAGING, 200000);
        when(medicalServiceRepository.findAllByStatusAndTypeInOrderByTenchidinhAsc(
                ActivationStatus.ACTIVE,
                List.of(MedicalServiceType.LAB, MedicalServiceType.IMAGING, MedicalServiceType.FUNCTIONAL)))
                .thenReturn(List.of(ultrasound));

        assertThat(service.listOrderableServices())
                .singleElement()
                .satisfies(option -> {
                    assertThat(option.name()).isEqualTo("Siêu âm ổ bụng");
                    assertThat(option.clinicPrice()).isEqualByComparingTo("200000");
                });
    }

    private void stubOrder(Visit visit, List<MedicalService> services) {
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.of(visit));
        when(medicalServiceRepository.findAllById(services.stream().map(MedicalService::getId).toList()))
                .thenReturn(services);
        when(medicalRecordRepository.findByVisitId(VISIT_ID)).thenReturn(Optional.empty());
        when(medicalRecordRepository.save(any(MedicalRecord.class))).thenAnswer(i -> i.getArgument(0));
        when(visitRepository.nextPaymentCode()).thenReturn(42L);
        when(paraclinicalOrderRepository.saveAndFlush(any(ParaclinicalOrder.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static CreateParaclinicalOrderRequest request(UUID... serviceIds) {
        return new CreateParaclinicalOrderRequest(VISIT_ID, "Theo dõi đau bụng", null, List.of(serviceIds));
    }

    private static MedicalService service(String code, String name, MedicalServiceType type, long price) {
        return MedicalService.create(UUID.randomUUID(), NOW, code, name, type, UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.valueOf(price), null, null);
    }

    private static Visit visit(VisitStatus status, UUID doctorId) {
        Visit visit = new Visit(VISIT_ID, NOW, "DK0000000001", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), NOW, VisitDoituongbnLoai.DV, null, null, null, "Đau bụng", false, null,
                LocalDate.of(2026, 11, 3), 1, VisitStatus.WAITING, VisitInsuranceVerifyStatus.NOT_APPLICABLE);
        if (doctorId != null) {
            visit.startExamination(doctorId, NOW);
        }
        if (status != visit.getStatus()) {
            forceStatus(visit, status);
        }
        return visit;
    }

    private static void forceStatus(Visit visit, VisitStatus status) {
        try {
            var field = Visit.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(visit, status);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
