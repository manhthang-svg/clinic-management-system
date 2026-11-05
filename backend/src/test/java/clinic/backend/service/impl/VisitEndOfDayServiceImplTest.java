package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Payment;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.ParaclinicalOrderStatus;
import clinic.backend.enums.PaymentMethod;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.ParaclinicalOrderRepository;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.PayOSPaymentService;
import clinic.backend.service.VisitEndOfDayService.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitEndOfDayServiceImplTest {

    /** 00:05 on 06/11/2026 in clinic time, when the job runs. */
    private static final Instant NOW = Instant.parse("2026-11-05T17:05:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final UUID SERVICE_ID = UUID.randomUUID();

    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private VisitRepository visitRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private MedicalRecordRepository medicalRecordRepository;
    @Mock
    private ParaclinicalOrderRepository paraclinicalOrderRepository;
    @Mock
    private PayOSPaymentService payOSPaymentService;
    @Mock
    private AuditLogService auditLogService;

    private VisitEndOfDayServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VisitEndOfDayServiceImpl(
                Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")), transactionTemplate, visitRepository,
                paymentRepository, medicalRecordRepository, paraclinicalOrderRepository, payOSPaymentService,
                auditLogService);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(
                call -> call.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    @Test
    void looksOnlyAtVisitsOfEarlierDaysInClinicTime() {
        when(visitRepository.findUnfinishedIdsBefore(TODAY, VisitRepository.FINISHED_STATUSES)).thenReturn(List.of());

        Result result = service.closeUnfinishedVisits();

        assertThat(result).isEqualTo(new Result(0, 0, 0));
    }

    @Test
    void cancelsAVisitStillWaitingForTheFeeTogetherWithItsUnpaidReceiptAndRecord() {
        Visit visit = visit(VisitStatus.WAITING_FEE_PAYMENT);
        Payment fee = examinationFee(visit);
        MedicalRecord record = record(visit);
        stubVisit(visit, List.of(fee), record, List.of());

        Result result = service.closeUnfinishedVisits();

        assertThat(result).isEqualTo(new Result(1, 0, 0));
        assertThat(visit.getStatus()).isEqualTo(VisitStatus.CANCELLED);
        assertThat(fee.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(fee.getCancelledAt()).isEqualTo(NOW);
        assertThat(record.getStatus()).isEqualTo(MedicalRecordStatus.LOCKED);
        assertThat(record.getClosedAt()).isEqualTo(NOW);
        assertThat(record.getClosedByAccountId()).isNull();
        verify(auditLogService).recordSystemChange(eq(AuditLogAction.CANCEL), eq("visits"), eq(visit.getId()),
                eq(visit.getSovaovien()), eq(Map.of(
                        "fromStatus", "WAITING_FEE_PAYMENT", "status", "CANCELLED", "reason", "HET_NGAY")),
                eq(NOW));
        verify(auditLogService).recordSystemChange(eq(AuditLogAction.CANCEL), eq("payments"), eq(fee.getId()),
                anyString(), anyMap(), eq(NOW));
        verify(auditLogService).recordSystemChange(eq(AuditLogAction.LOCK), eq("medical_records"),
                eq(record.getId()), anyString(), anyMap(), eq(NOW));
    }

    @Test
    void abandonsAVisitWaitingForResultsWithoutRefundingWhatWasPaid() {
        Visit visit = visit(VisitStatus.WAITING_PARACLINICAL);
        Payment fee = paid(examinationFee(visit));
        Payment paraclinicalFee = paid(paraclinicalFee(visit));
        MedicalRecord record = record(visit);
        ParaclinicalOrder order = order(record, paraclinicalFee, ParaclinicalOrderStatus.RELEASED);
        ParaclinicalOrderDetail performed = detail(order, "CD1-01", ParaclinicalOrderDetailStatus.COMPLETED);
        ParaclinicalOrderDetail waiting = detail(order, "CD1-02", ParaclinicalOrderDetailStatus.WAITING);
        ParaclinicalOrderDetail inProgress = detail(order, "CD1-03", ParaclinicalOrderDetailStatus.IN_PROGRESS);
        stubVisit(visit, List.of(fee, paraclinicalFee), record, List.of(order));

        Result result = service.closeUnfinishedVisits();

        assertThat(result).isEqualTo(new Result(0, 1, 0));
        assertThat(visit.getStatus()).isEqualTo(VisitStatus.ABANDONED);
        assertThat(fee.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paraclinicalFee.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(performed.getStatus()).isEqualTo(ParaclinicalOrderDetailStatus.COMPLETED);
        assertThat(List.of(waiting, inProgress)).allSatisfy(detail -> {
            assertThat(detail.getStatus()).isEqualTo(ParaclinicalOrderDetailStatus.ABANDONED);
            assertThat(detail.getCancelReason()).isEqualTo("BO_KHAM");
            assertThat(detail.getCancelledAt()).isNull();
            assertThat(detail.getCancelledByAccountId()).isNull();
        });
        assertThat(order.getStatus()).isEqualTo(ParaclinicalOrderStatus.CANCELLED);
        assertThat(record.getStatus()).isEqualTo(MedicalRecordStatus.LOCKED);
        verify(auditLogService, never()).recordSystemChange(any(), eq("payments"), any(), any(), any(), any());
    }

    @Test
    void cancelsTheUnpaidParaclinicalReceiptOfAnAbandonedVisit() {
        Visit visit = visit(VisitStatus.WAITING_PARACLINICAL);
        Payment fee = paid(examinationFee(visit));
        Payment paraclinicalFee = paraclinicalFee(visit);
        MedicalRecord record = record(visit);
        ParaclinicalOrder order = order(record, paraclinicalFee, ParaclinicalOrderStatus.PENDING_PAYMENT);
        ParaclinicalOrderDetail unpaid = detail(order, "CD1-01", ParaclinicalOrderDetailStatus.PENDING_PAYMENT);
        stubVisit(visit, List.of(fee, paraclinicalFee), record, List.of(order));

        service.closeUnfinishedVisits();

        assertThat(fee.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paraclinicalFee.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(unpaid.getStatus()).isEqualTo(ParaclinicalOrderDetailStatus.ABANDONED);
        assertThat(order.getStatus()).isEqualTo(ParaclinicalOrderStatus.CANCELLED);
    }

    @Test
    void keepsAnOrderWhoseServicesWereAllPerformed() {
        Visit visit = visit(VisitStatus.WAITING_CONCLUSION);
        Payment paraclinicalFee = paid(paraclinicalFee(visit));
        MedicalRecord record = record(visit);
        ParaclinicalOrder order = order(record, paraclinicalFee, ParaclinicalOrderStatus.RELEASED);
        detail(order, "CD1-01", ParaclinicalOrderDetailStatus.COMPLETED);
        stubVisit(visit, List.of(paraclinicalFee), record, List.of(order));

        service.closeUnfinishedVisits();

        assertThat(visit.getStatus()).isEqualTo(VisitStatus.ABANDONED);
        assertThat(order.getStatus()).isEqualTo(ParaclinicalOrderStatus.RELEASED);
    }

    @Test
    void abandonsASkippedVisitAndKeepsTheSkipAsHistory() {
        Visit visit = visit(VisitStatus.WAITING);
        visit.markSkipped(NOW.minusSeconds(3600));
        stubVisit(visit, List.of(), null, List.of());

        Result result = service.closeUnfinishedVisits();

        assertThat(result).isEqualTo(new Result(0, 1, 0));
        assertThat(visit.getStatus()).isEqualTo(VisitStatus.ABANDONED);
        assertThat(visit.isSkipped()).isTrue();
    }

    @Test
    void closesThePayOSLinkBeforeCancellingTheReceipt() {
        Visit visit = visit(VisitStatus.WAITING_FEE_PAYMENT);
        Payment fee = withPayOSLink(examinationFee(visit));
        stubVisit(visit, List.of(fee), null, List.of());
        when(payOSPaymentService.closeLinkBeforeCancel(fee)).thenReturn(true);

        Result result = service.closeUnfinishedVisits();

        assertThat(result).isEqualTo(new Result(1, 0, 0));
        assertThat(fee.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void defersAVisitWhosePayOSLinkMayStillBePaid() {
        Visit visit = visit(VisitStatus.WAITING_FEE_PAYMENT);
        Payment fee = withPayOSLink(examinationFee(visit));
        when(visitRepository.findUnfinishedIdsBefore(TODAY, VisitRepository.FINISHED_STATUSES))
                .thenReturn(List.of(visit.getId()));
        when(paymentRepository.findByVisitId(visit.getId())).thenReturn(List.of(fee));
        when(payOSPaymentService.closeLinkBeforeCancel(fee)).thenReturn(false);

        Result result = service.closeUnfinishedVisits();

        assertThat(result).isEqualTo(new Result(0, 0, 1));
        assertThat(visit.getStatus()).isEqualTo(VisitStatus.WAITING_FEE_PAYMENT);
        assertThat(fee.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    void keepsClosingOtherVisitsWhenOneIsChangedConcurrently() {
        Visit busy = visit(VisitStatus.IN_EXAMINATION);
        Visit idle = visit(VisitStatus.WAITING);
        when(visitRepository.findUnfinishedIdsBefore(TODAY, VisitRepository.FINISHED_STATUSES))
                .thenReturn(List.of(busy.getId(), idle.getId()));
        when(paymentRepository.findByVisitId(any())).thenReturn(List.of());
        when(visitRepository.findById(busy.getId()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Visit.class, busy.getId()));
        when(visitRepository.findById(idle.getId())).thenReturn(Optional.of(idle));
        when(medicalRecordRepository.findByVisitId(idle.getId())).thenReturn(Optional.empty());

        Result result = service.closeUnfinishedVisits();

        assertThat(result).isEqualTo(new Result(0, 1, 1));
        assertThat(idle.getStatus()).isEqualTo(VisitStatus.ABANDONED);
    }

    @Test
    void skipsAVisitFinishedSinceItWasListed() {
        Visit visit = visit(VisitStatus.COMPLETED);
        when(visitRepository.findUnfinishedIdsBefore(TODAY, VisitRepository.FINISHED_STATUSES))
                .thenReturn(List.of(visit.getId()));
        when(paymentRepository.findByVisitId(visit.getId())).thenReturn(List.of());
        when(visitRepository.findById(visit.getId())).thenReturn(Optional.of(visit));

        Result result = service.closeUnfinishedVisits();

        assertThat(result).isEqualTo(new Result(0, 0, 0));
        verify(auditLogService, never()).recordSystemChange(any(), any(), any(), any(), any(), any());
    }

    private void stubVisit(Visit visit, List<Payment> payments, MedicalRecord record, List<ParaclinicalOrder> orders) {
        when(visitRepository.findUnfinishedIdsBefore(TODAY, VisitRepository.FINISHED_STATUSES))
                .thenReturn(List.of(visit.getId()));
        when(visitRepository.findById(visit.getId())).thenReturn(Optional.of(visit));
        when(paymentRepository.findByVisitId(visit.getId())).thenReturn(payments);
        when(medicalRecordRepository.findByVisitId(visit.getId())).thenReturn(Optional.ofNullable(record));
        if (record != null) {
            when(paraclinicalOrderRepository.findAllByMedicalRecordId(record.getId())).thenReturn(orders);
        }
    }

    private static Visit visit(VisitStatus status) {
        return new Visit(UUID.randomUUID(), NOW, "VV" + UUID.randomUUID().toString().substring(0, 8),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW, VisitDoituongbnLoai.DV, null, null,
                null, "Khám bệnh", false, null, YESTERDAY, 1, status, VisitInsuranceVerifyStatus.NOT_APPLICABLE);
    }

    private static Payment examinationFee(Visit visit) {
        return Payment.createExaminationFee(UUID.randomUUID(), NOW, "HD1", visit.getId(),
                BigDecimal.valueOf(150000), BigDecimal.ZERO, BigDecimal.valueOf(150000));
    }

    private static Payment paraclinicalFee(Visit visit) {
        return Payment.createParaclinicalFee(UUID.randomUUID(), NOW, "HD2", visit.getId(),
                BigDecimal.valueOf(200000), BigDecimal.ZERO, BigDecimal.valueOf(200000));
    }

    private static Payment paid(Payment payment) {
        payment.markAsPaid(PaymentMethod.CASH, UUID.randomUUID(), "PT-" + payment.getPaymentCode());
        return payment;
    }

    private static Payment withPayOSLink(Payment payment) {
        payment.attachPayOSPayment(1793704131724L, "https://pay.example/checkout", "qr", "link-1");
        return payment;
    }

    private static MedicalRecord record(Visit visit) {
        return new MedicalRecord(UUID.randomUUID(), NOW, visit.getId(), "BA1", "NGOAITRU",
                MedicalRecordStatus.OPEN, NOW, UUID.randomUUID());
    }

    private static ParaclinicalOrder order(MedicalRecord record, Payment payment, ParaclinicalOrderStatus status) {
        return new ParaclinicalOrder(UUID.randomUUID(), NOW, "CD1", record.getId(), UUID.randomUUID(),
                payment.getId(), null, (short) 2, null, "Phòng khám 1", BigDecimal.valueOf(200000), status);
    }

    private static ParaclinicalOrderDetail detail(
            ParaclinicalOrder order, String code, ParaclinicalOrderDetailStatus status) {
        ParaclinicalOrderDetail detail = new ParaclinicalOrderDetail(UUID.randomUUID(), NOW, order, SERVICE_ID,
                code, NOW, "Siêu âm ổ bụng", null, null, BigDecimal.ONE, BigDecimal.valueOf(200000),
                BigDecimal.valueOf(200000), BigDecimal.ZERO, BigDecimal.valueOf(200000), null, status);
        order.addDetail(detail);
        return detail;
    }
}
