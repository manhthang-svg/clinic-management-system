package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.config.properties.InsuranceBillingProperties;
import clinic.backend.dto.request.CancelParaclinicalServiceRequest;
import clinic.backend.dto.request.CreateParaclinicalOrderRequest;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Payment;
import clinic.backend.entity.PaymentItem;
import clinic.backend.entity.Room;
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
import clinic.backend.enums.PriceType;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.ParaclinicalOrderRepository;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.ServiceGroupRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.PaymentCompletionService;
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
import static org.mockito.Mockito.lenient;
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
    @Mock private PaymentCompletionService paymentCompletionService;
    @Mock private ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;

    private ParaclinicalOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh"));
        service = new ParaclinicalOrderServiceImpl(
                visitRepository, medicalRecordRepository, medicalServiceRepository, serviceGroupRepository,
                roomRepository, paymentRepository, paraclinicalOrderRepository, paraclinicalOrderDetailRepository,
                auditLogService, paymentCompletionService,
                new InsuranceCoverageServiceImpl(
                        new InsuranceBillingProperties(BigDecimal.valueOf(2_340_000), BigDecimal.valueOf(15)),
                        paymentRepository, paraclinicalOrderRepository, auditLogService, clock),
                new ParaclinicalProgressServiceImpl(
                        medicalRecordRepository, paraclinicalOrderRepository, visitRepository, auditLogService, clock),
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
        verify(paymentCompletionService, never()).releaseOrder(any());
        verify(auditLogService).recordBusinessChange(
                eq(DOCTOR_ID), eq(AuditLogAction.CREATE), eq("paraclinical_orders"), any(),
                eq(visit.getSovaovien()), any(), eq(NOW));
    }

    @Test
    void splitsInsuranceAndPatientAmountsAndReleasesAnInsuranceVisitAtOnce() {
        Visit visit = insuredVisit((short) 80);
        // Insurance price below the clinic price: the patient also pays the difference (BR-27).
        MedicalService mri = service("MRI", "Chụp MRI sọ não", MedicalServiceType.IMAGING, 500000, 300000L);
        // Insurance price above the clinic price: insurance pays on the clinic price.
        MedicalService blood = service("XN-CTM", "Tổng phân tích máu", MedicalServiceType.LAB, 80000, 90000L);
        stubOrder(visit, List.of(mri, blood));
        when(paymentCompletionService.releaseOrder(any(ParaclinicalOrder.class))).thenReturn(List.of());

        var response = service.createOrder(DOCTOR_ID, request(mri.getId(), blood.getId()));

        // 300 000 + 80 000 within scope is not below 351 000, so the card's 80% applies:
        // 300 000 × 80% = 240 000; min(80 000, 90 000) × 80% = 64 000
        assertThat(response.tongcong()).isEqualByComparingTo("580000");
        assertThat(response.insuranceAmount()).isEqualByComparingTo("304000");
        assertThat(response.patientAmount()).isEqualByComparingTo("276000");
        assertThat(response.details())
                .extracting(d -> d.bhytChitra().longValue(), d -> d.benhnhanphaitra().longValue())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(240000L, 260000L),
                        org.assertj.core.groups.Tuple.tuple(64000L, 16000L));

        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(payment.capture());
        assertThat(payment.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getValue().getItems())
                .extracting(item -> item.getPriceType(), item -> item.getCoverageRate())
                .containsOnly(org.assertj.core.groups.Tuple.tuple(PriceType.INSURANCE, (short) 80));
        // BR-26: released to the paraclinical rooms now; the patient amount is collected after the conclusion.
        verify(paymentCompletionService).releaseOrder(any(ParaclinicalOrder.class));
        assertThat(visit.getStatus()).isEqualTo(VisitStatus.WAITING_PARACLINICAL);
    }

    @Test
    void coversInFullWhileTheVisitStaysBelowTheExemptionThreshold() {
        Visit visit = insuredVisit((short) 80);
        MedicalService ultrasound = service("SA-OB", "Siêu âm ổ bụng", MedicalServiceType.IMAGING, 200000, 150000L);
        stubOrder(visit, List.of(ultrasound));

        var response = service.createOrder(DOCTOR_ID, request(ultrasound.getId()));

        // 150 000 within scope is below 351 000: 100% of it is covered; the patient pays only the difference.
        assertThat(response.insuranceAmount()).isEqualByComparingTo("150000");
        assertThat(response.patientAmount()).isEqualByComparingTo("50000");
    }

    @Test
    void repricesTheExaminationFeeWhenTheVisitCrossesTheExemptionThreshold() {
        Visit visit = insuredVisit((short) 80);
        MedicalService exam = MedicalService.create(UUID.randomUUID(), NOW, "KB", "Khám bệnh",
                MedicalServiceType.EXAMINATION, null, null, BigDecimal.valueOf(150000), BigDecimal.valueOf(100000),
                null);
        // Registered alone, the examination was below the threshold and covered at 100%.
        Payment examFee = Payment.createExaminationFee(UUID.randomUUID(), NOW, "HD0000000001", VISIT_ID,
                BigDecimal.valueOf(150000), BigDecimal.valueOf(100000), BigDecimal.valueOf(50000));
        examFee.addItem(PaymentItem.createInsuranceItem(UUID.randomUUID(), NOW, examFee, exam, (short) 100));
        List<Payment> visitPayments = new ArrayList<>(List.of(examFee));
        MedicalService ultrasound = service("SA-OB", "Siêu âm ổ bụng", MedicalServiceType.IMAGING, 300000, 260000L);
        stubOrder(visit, List.of(ultrasound));
        when(paymentRepository.findByVisitId(VISIT_ID)).thenAnswer(i -> List.copyOf(visitPayments));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(i -> {
            visitPayments.add(i.getArgument(0));
            return i.getArgument(0);
        });

        var response = service.createOrder(DOCTOR_ID, request(ultrasound.getId()));

        // 100 000 + 260 000 = 360 000 within scope is not below 351 000: the card's 80% now applies to both.
        assertThat(response.insuranceAmount()).isEqualByComparingTo("208000");
        assertThat(response.patientAmount()).isEqualByComparingTo("92000");
        assertThat(examFee.getInsuranceTotalAmount()).isEqualByComparingTo("80000");
        assertThat(examFee.getPatientTotalAmount()).isEqualByComparingTo("70000");
        assertThat(examFee.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getCoverageRate()).isEqualTo((short) 80));
        verify(auditLogService).recordSystemChange(
                eq(AuditLogAction.UPDATE), eq("payments"), eq(examFee.getId()), any(), any(), eq(NOW));
    }

    @Test
    void chargesAServiceOutsideInsuranceCoverageAtTheClinicPrice() {
        Visit visit = insuredVisit((short) 100);
        MedicalService ultrasound = service("SA-OB", "Siêu âm ổ bụng", MedicalServiceType.IMAGING, 200000);
        stubOrder(visit, List.of(ultrasound));

        var response = service.createOrder(DOCTOR_ID, request(ultrasound.getId()));

        assertThat(response.insuranceAmount()).isEqualByComparingTo("0");
        assertThat(response.patientAmount()).isEqualByComparingTo("200000");
        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(payment.capture());
        assertThat(payment.getValue().getItems())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getPriceType()).isEqualTo(PriceType.CLINIC);
                    assertThat(item.getBenhnhanphaitra()).isEqualByComparingTo("200000");
                });
        verify(paymentCompletionService).releaseOrder(any(ParaclinicalOrder.class));
    }

    @Test
    void releasesAtOnceWhenThePatientPaysNothing() {
        Visit visit = insuredVisit((short) 100);
        MedicalService ultrasound = service("SA-OB", "Siêu âm ổ bụng", MedicalServiceType.IMAGING, 200000, 200000L);
        stubOrder(visit, List.of(ultrasound));

        var response = service.createOrder(DOCTOR_ID, request(ultrasound.getId()));

        assertThat(response.patientAmount()).isEqualByComparingTo("0");
        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(payment.capture());
        assertThat(payment.getValue().getStatus()).isEqualTo(PaymentStatus.FEE_EXEMPT);
        verify(paymentCompletionService).releaseOrder(any(ParaclinicalOrder.class));
    }

    @Test
    void billsASelfPaidVisitAtTheClinicPriceEvenForAnInsuredService() {
        Visit visit = visit(VisitStatus.IN_EXAMINATION, DOCTOR_ID);
        MedicalService ultrasound = service("SA-OB", "Siêu âm ổ bụng", MedicalServiceType.IMAGING, 200000, 150000L);
        stubOrder(visit, List.of(ultrasound));

        var response = service.createOrder(DOCTOR_ID, request(ultrasound.getId()));

        assertThat(response.insuranceAmount()).isEqualByComparingTo("0");
        assertThat(response.patientAmount()).isEqualByComparingTo("200000");
        verify(paymentCompletionService, never()).releaseOrder(any());
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

    // ---- I1-D47, BR-50-01: the Doctor chooses the performing room ----

    @Test
    void storesTheRoomTheDoctorChoseOnTheOrderLine() {
        Visit visit = visit(VisitStatus.IN_EXAMINATION, DOCTOR_ID);
        MedicalService ultrasound = service("SA-OB", "Siêu âm ổ bụng", MedicalServiceType.IMAGING, 200000);
        Room room = paraclinicalRoom(ultrasound.getServiceGroupId(), ActivationStatus.ACTIVE);
        stubOrder(visit, List.of(ultrasound));
        // lenient: the ordering room of the visit is looked up through the same repository
        lenient().when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));

        var response = service.createOrder(DOCTOR_ID, requestWithRoom(ultrasound.getId(), room.getId()));

        assertThat(response.details()).singleElement()
                .satisfies(detail -> assertThat(detail.roomId()).isEqualTo(room.getId()));
        // A self-paid order still waits for the cashier; the queue number is taken at release (BR-50-02).
        verify(paymentCompletionService, never()).releaseOrder(any());
    }

    @Test
    void preselectsTheLeastBusyRoomWhenTheDoctorChoseNone() {
        Visit visit = visit(VisitStatus.IN_EXAMINATION, DOCTOR_ID);
        MedicalService ultrasound = service("SA-OB", "Siêu âm ổ bụng", MedicalServiceType.IMAGING, 200000);
        Room room = paraclinicalRoom(ultrasound.getServiceGroupId(), ActivationStatus.ACTIVE);
        stubOrder(visit, List.of(ultrasound));
        when(paymentCompletionService.leastBusyRoom(ultrasound.getServiceGroupId())).thenReturn(Optional.of(room));

        var response = service.createOrder(DOCTOR_ID, request(ultrasound.getId()));

        assertThat(response.details()).singleElement()
                .satisfies(detail -> assertThat(detail.roomId()).isEqualTo(room.getId()));
    }

    @Test
    void refusesARoomOfAnotherServiceGroup() {
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.of(visit(VisitStatus.IN_EXAMINATION, DOCTOR_ID)));
        MedicalService ultrasound = service("SA-OB", "Siêu âm ổ bụng", MedicalServiceType.IMAGING, 200000);
        when(medicalServiceRepository.findAllById(List.of(ultrasound.getId()))).thenReturn(List.of(ultrasound));
        Room labRoom = paraclinicalRoom(UUID.randomUUID(), ActivationStatus.ACTIVE);
        when(roomRepository.findById(labRoom.getId())).thenReturn(Optional.of(labRoom));

        assertThatThrownBy(() -> service.createOrder(DOCTOR_ID, requestWithRoom(ultrasound.getId(), labRoom.getId())))
                .isInstanceOfSatisfying(AppException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARACLINICAL_ROOM_NOT_ELIGIBLE);
                    assertThat(e.getDetails()).containsEntry("roomName", "Phòng siêu âm 1");
                });
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesAnInactiveRoom() {
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.of(visit(VisitStatus.IN_EXAMINATION, DOCTOR_ID)));
        MedicalService ultrasound = service("SA-OB", "Siêu âm ổ bụng", MedicalServiceType.IMAGING, 200000);
        when(medicalServiceRepository.findAllById(List.of(ultrasound.getId()))).thenReturn(List.of(ultrasound));
        Room closed = paraclinicalRoom(ultrasound.getServiceGroupId(), ActivationStatus.INACTIVE);
        when(roomRepository.findById(closed.getId())).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.createOrder(DOCTOR_ID, requestWithRoom(ultrasound.getId(), closed.getId())))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARACLINICAL_ROOM_NOT_ELIGIBLE));
    }

    @Test
    void listsTheActiveRoomsOfEachServiceWithTheLeastBusyPreselected() {
        MedicalService ultrasound = service("SA-OB", "Siêu âm ổ bụng", MedicalServiceType.IMAGING, 200000);
        when(medicalServiceRepository.findAllByStatusAndTypeInOrderByTenchidinhAsc(
                ActivationStatus.ACTIVE,
                List.of(MedicalServiceType.LAB, MedicalServiceType.IMAGING, MedicalServiceType.FUNCTIONAL)))
                .thenReturn(List.of(ultrasound));
        Room busy = new Room(UUID.randomUUID(), NOW, UUID.randomUUID(), ultrasound.getServiceGroupId(), "SA1",
                "Phòng siêu âm 1", "Tầng 2", RoomType.PARACLINICAL, ActivationStatus.ACTIVE);
        Room free = new Room(UUID.randomUUID(), NOW, UUID.randomUUID(), ultrasound.getServiceGroupId(), "SA2",
                "Phòng siêu âm 2", "Tầng 3", RoomType.PARACLINICAL, ActivationStatus.ACTIVE);
        when(roomRepository.findAllByServiceGroupIdAndStatusOrderByRoomCodeAsc(
                ultrasound.getServiceGroupId(), ActivationStatus.ACTIVE)).thenReturn(List.of(busy, free));
        when(paraclinicalOrderDetailRepository.countByRoomIdAndStatus(busy.getId(), ParaclinicalOrderDetailStatus.WAITING))
                .thenReturn(4L);
        when(paraclinicalOrderDetailRepository.countByRoomIdAndStatus(free.getId(), ParaclinicalOrderDetailStatus.WAITING))
                .thenReturn(1L);

        assertThat(service.listOrderableServices())
                .singleElement()
                .satisfies(option -> {
                    assertThat(option.rooms())
                            .extracting(r -> r.roomName(), r -> r.location(), r -> r.waitingCount())
                            .containsExactly(
                                    org.assertj.core.groups.Tuple.tuple("Phòng siêu âm 1", "Tầng 2", 4L),
                                    org.assertj.core.groups.Tuple.tuple("Phòng siêu âm 2", "Tầng 3", 1L));
                    assertThat(option.suggestedRoomId()).isEqualTo(free.getId());
                });
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

    // ---- BR-25, BR-41: the Doctor cancels a service the patient refused ----

    @Test
    void cancelsOneServiceOfAnUnpaidSelfPaidReceiptAndRecalculatesIt() {
        MedicalService ultrasound = service("SA-OB", "Siêu âm ổ bụng", MedicalServiceType.IMAGING, 200000);
        MedicalService blood = service("XN-CTM", "Tổng phân tích máu", MedicalServiceType.LAB, 80000);
        Ordered ordered = orderFor(visit(VisitStatus.IN_EXAMINATION, DOCTOR_ID), ultrasound, blood);
        ParaclinicalOrderDetail refused = ordered.detail(0);

        var result = service.cancelService(DOCTOR_ID, refused.getId(), cancel("Bệnh nhân không đồng ý chụp"));

        assertThat(refused.getStatus()).isEqualTo(ParaclinicalOrderDetailStatus.CANCELLED);
        assertThat(refused.getCancelReason()).isEqualTo("Bệnh nhân không đồng ý chụp");
        assertThat(refused.getCancelledByAccountId()).isEqualTo(DOCTOR_ID);
        assertThat(ordered.payment().getTotalAmount()).isEqualByComparingTo("80000");
        assertThat(ordered.payment().getPatientTotalAmount()).isEqualByComparingTo("80000");
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.visitStatus()).isEqualTo(VisitStatus.WAITING_PARACLINICAL);
        verify(auditLogService).recordBusinessChange(
                eq(DOCTOR_ID), eq(AuditLogAction.CANCEL), eq("paraclinical_order_details"), eq(refused.getId()),
                any(), any(), eq(NOW));
    }

    @Test
    void cancellingTheLastServiceCancelsTheReceiptAndSendsTheVisitToConclusion() {
        MedicalService ultrasound = service("SA-OB", "Siêu âm ổ bụng", MedicalServiceType.IMAGING, 200000);
        Ordered ordered = orderFor(visit(VisitStatus.IN_EXAMINATION, DOCTOR_ID), ultrasound);

        var result = service.cancelService(DOCTOR_ID, ordered.detail(0).getId(), cancel("Bệnh nhân từ chối"));

        assertThat(ordered.payment().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(ordered.order().getStatus()).isEqualTo(ParaclinicalOrderStatus.CANCELLED);
        assertThat(result.visitStatus()).isEqualTo(VisitStatus.WAITING_CONCLUSION);
        assertThat(ordered.visit().getStatus()).isEqualTo(VisitStatus.WAITING_CONCLUSION);
    }

    @Test
    void refusesToCancelAServiceAlreadyPaidFor() {
        MedicalService ultrasound = service("SA-OB", "Siêu âm ổ bụng", MedicalServiceType.IMAGING, 200000);
        Ordered ordered = orderFor(visit(VisitStatus.IN_EXAMINATION, DOCTOR_ID), ultrasound);
        ordered.payment().markAsPaid(clinic.backend.enums.PaymentMethod.CASH, UUID.randomUUID(), "PT1");

        assertThatThrownBy(() -> service.cancelService(DOCTOR_ID, ordered.detail(0).getId(), cancel("Từ chối")))
                .isInstanceOfSatisfying(AppException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARACLINICAL_SERVICE_NOT_CANCELLABLE));
        assertThat(ordered.detail(0).getStatus()).isEqualTo(ParaclinicalOrderDetailStatus.PENDING_PAYMENT);
    }

    @Test
    void refusesToCancelAServiceATechnicianHasAccepted() {
        MedicalService ultrasound = service("SA-OB", "Siêu âm ổ bụng", MedicalServiceType.IMAGING, 200000, 150000L);
        Ordered ordered = orderFor(insuredVisit((short) 80), ultrasound);
        forceField(ordered.detail(0), "status", ParaclinicalOrderDetailStatus.IN_PROGRESS);

        assertThatThrownBy(() -> service.cancelService(DOCTOR_ID, ordered.detail(0).getId(), cancel("Từ chối")))
                .isInstanceOfSatisfying(AppException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARACLINICAL_SERVICE_NOT_CANCELLABLE));
    }

    @Test
    void cancellingAnInsuredServiceBringsTheVisitBackUnderTheExemptionThreshold() {
        // 300 000 + 100 000 within scope is above 351 000: both are covered at the card's 80%.
        MedicalService mri = service("MRI", "Chụp MRI", MedicalServiceType.IMAGING, 300000, 300000L);
        MedicalService blood = service("XN-CTM", "Tổng phân tích máu", MedicalServiceType.LAB, 100000, 100000L);
        Ordered ordered = orderFor(insuredVisit((short) 80), mri, blood);
        assertThat(ordered.payment().getPatientTotalAmount()).isEqualByComparingTo("80000");

        service.cancelService(DOCTOR_ID, ordered.detail(0).getId(), cancel("Bệnh nhân không chụp MRI"));

        // 100 000 left is below 351 000: covered at 100%, the patient owes nothing.
        assertThat(ordered.payment().getInsuranceTotalAmount()).isEqualByComparingTo("100000");
        assertThat(ordered.payment().getPatientTotalAmount()).isEqualByComparingTo("0");
        assertThat(ordered.payment().getStatus()).isEqualTo(PaymentStatus.FEE_EXEMPT);
    }

    @Test
    void onlyTheDoctorOfTheVisitCancelsItsServices() {
        MedicalService ultrasound = service("SA-OB", "Siêu âm ổ bụng", MedicalServiceType.IMAGING, 200000);
        Ordered ordered = orderFor(visit(VisitStatus.IN_EXAMINATION, DOCTOR_ID), ultrasound);

        assertThatThrownBy(() -> service.cancelService(UUID.randomUUID(), ordered.detail(0).getId(), cancel("Từ chối")))
                .isInstanceOfSatisfying(AppException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    void listsWhichServicesTheDoctorCanStillCancel() {
        MedicalService ultrasound = service("SA-OB", "Siêu âm ổ bụng", MedicalServiceType.IMAGING, 200000);
        MedicalService blood = service("XN-CTM", "Tổng phân tích máu", MedicalServiceType.LAB, 80000);
        Ordered ordered = orderFor(visit(VisitStatus.IN_EXAMINATION, DOCTOR_ID), ultrasound, blood);
        service.cancelService(DOCTOR_ID, ordered.detail(0).getId(), cancel("Bệnh nhân từ chối"));

        var details = service.listOrdersByVisit(DOCTOR_ID, VISIT_ID).getFirst().details();

        assertThat(details)
                .extracting(d -> d.cancellable(), d -> d.cancelReason())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(false, "Bệnh nhân từ chối"),
                        org.assertj.core.groups.Tuple.tuple(true, null));
    }

    /** An order created through UC-49, with every lookup the cancellation needs. */
    private Ordered orderFor(Visit visit, MedicalService... services) {
        stubOrder(visit, List.of(services));
        List<Payment> payments = new ArrayList<>();
        lenient().when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(i -> {
            payments.add(i.getArgument(0));
            return i.getArgument(0);
        });
        lenient().when(paymentRepository.findByVisitId(VISIT_ID)).thenAnswer(i -> List.copyOf(payments));
        lenient().when(paymentCompletionService.releaseOrder(any())).thenReturn(List.of());

        service.createOrder(DOCTOR_ID, request(java.util.Arrays.stream(services).map(MedicalService::getId)
                .toArray(UUID[]::new)));

        ArgumentCaptor<ParaclinicalOrder> orderCaptor = ArgumentCaptor.forClass(ParaclinicalOrder.class);
        verify(paraclinicalOrderRepository).saveAndFlush(orderCaptor.capture());
        ArgumentCaptor<MedicalRecord> recordCaptor = ArgumentCaptor.forClass(MedicalRecord.class);
        verify(medicalRecordRepository).save(recordCaptor.capture());
        ParaclinicalOrder order = orderCaptor.getValue();
        MedicalRecord record = recordCaptor.getValue();
        Payment payment = payments.getFirst();

        for (ParaclinicalOrderDetail detail : order.getDetails()) {
            lenient().when(paraclinicalOrderDetailRepository.findById(detail.getId())).thenReturn(Optional.of(detail));
        }
        lenient().when(medicalRecordRepository.findById(record.getId())).thenReturn(Optional.of(record));
        lenient().when(medicalRecordRepository.findByVisitId(VISIT_ID)).thenReturn(Optional.of(record));
        lenient().when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        lenient().when(paraclinicalOrderRepository.findAllByMedicalRecordId(record.getId())).thenReturn(List.of(order));
        lenient().when(paraclinicalOrderRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(order));
        return new Ordered(visit, order, payment);
    }

    private record Ordered(Visit visit, ParaclinicalOrder order, Payment payment) {
        ParaclinicalOrderDetail detail(int index) {
            return order.getDetails().get(index);
        }
    }

    private static CancelParaclinicalServiceRequest cancel(String reason) {
        return new CancelParaclinicalServiceRequest(reason);
    }

    private static void forceField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
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
        return new CreateParaclinicalOrderRequest(VISIT_ID, "Theo dõi đau bụng", null,
                java.util.Arrays.stream(serviceIds)
                        .map(id -> new CreateParaclinicalOrderRequest.Item(id, null))
                        .toList());
    }

    private static CreateParaclinicalOrderRequest requestWithRoom(UUID serviceId, UUID roomId) {
        return new CreateParaclinicalOrderRequest(VISIT_ID, "Theo dõi đau bụng", null,
                List.of(new CreateParaclinicalOrderRequest.Item(serviceId, roomId)));
    }

    private static Room paraclinicalRoom(UUID serviceGroupId, ActivationStatus status) {
        return new Room(UUID.randomUUID(), NOW, UUID.randomUUID(), serviceGroupId, "SA1", "Phòng siêu âm 1",
                "Tầng 2, khu B", RoomType.PARACLINICAL, status);
    }

    private static MedicalService service(String code, String name, MedicalServiceType type, long price) {
        return service(code, name, type, price, null);
    }

    private static MedicalService service(
            String code, String name, MedicalServiceType type, long price, Long insurancePrice) {
        MedicalService service = MedicalService.create(
                UUID.randomUUID(), NOW, code, name, type, UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.valueOf(price), insurancePrice == null ? null : BigDecimal.valueOf(insurancePrice), null);
        service.changeStatus(ActivationStatus.ACTIVE, NOW);
        return service;
    }

    private static Visit visit(VisitStatus status, UUID doctorId) {
        VisitStatus initialStatus = doctorId == null ? status : VisitStatus.WAITING;
        Visit visit = new Visit(VISIT_ID, NOW, "DK0000000001", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), NOW, VisitDoituongbnLoai.DV, null, null, null, "Đau bụng", false, null,
                LocalDate.of(2026, 11, 3), 1, initialStatus, VisitInsuranceVerifyStatus.NOT_APPLICABLE);
        if (doctorId != null) {
            visit.startExamination(doctorId, NOW);
            if (status != VisitStatus.IN_EXAMINATION) {
                forceStatus(visit, status);
            }
        }
        return visit;
    }

    /** An insurance visit with a valid card, being examined by the doctor. */
    private static Visit insuredVisit(short coverageRate) {
        Visit visit = new Visit(VISIT_ID, NOW, "DK0000000001", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), NOW, VisitDoituongbnLoai.BHYT, "DN4797000000001", "7000000001",
                LocalDate.of(2026, 1, 1), LocalDate.of(2030, 12, 31), "79014", "Bệnh viện Đa khoa Sài Gòn",
                null, coverageRate, null, null, "Đau bụng", false, null, LocalDate.of(2026, 11, 3), 1,
                VisitStatus.WAITING, VisitInsuranceVerifyStatus.VALID);
        visit.startExamination(DOCTOR_ID, NOW);
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
