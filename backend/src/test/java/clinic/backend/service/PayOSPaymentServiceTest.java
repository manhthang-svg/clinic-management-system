package clinic.backend.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.ConfirmPayOSResultRequest;
import clinic.backend.entity.Payment;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayOSPaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T03:00:00Z");
    private static final long ORDER_CODE = 1793704131724L;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private PayOS payOS;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentCompletionService paymentCompletionService;

    private PayOSPaymentService service;
    private Payment payment;

    @BeforeEach
    void setUp() {
        service = new PayOSPaymentService(payOS, paymentRepository, paymentCompletionService, "http://r", "http://c");
        payment = Payment.createExaminationFee(UUID.randomUUID(), NOW, "HD1", UUID.randomUUID(),
                BigDecimal.valueOf(100000), BigDecimal.ZERO, BigDecimal.valueOf(100000));
    }

    private void stubReceiptLookup() {
        when(paymentRepository.findByPayosOrderCode(ORDER_CODE)).thenReturn(Optional.of(payment));
    }

    @Test
    void ignoresASuccessCodeSentByTheBrowserWhenPayOSHasNotBeenPaid() throws Exception {
        stubReceiptLookup();
        PaymentLink link = mock(PaymentLink.class);
        when(link.getStatus()).thenReturn(PaymentLinkStatus.PENDING);
        when(link.getAmountPaid()).thenReturn(0L);
        when(payOS.paymentRequests().get(ORDER_CODE)).thenReturn(link);

        service.confirmPayOSResult(new ConfirmPayOSResultRequest(ORDER_CODE, "00", "PAID", false));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentCompletionService, never()).completePaidReceipt(any(), any());
    }

    @Test
    void doesNotMarkPaidWhenPayOSCannotBeReached() throws Exception {
        stubReceiptLookup();
        when(payOS.paymentRequests().get(ORDER_CODE)).thenThrow(new RuntimeException("timeout"));

        service.confirmPayOSResult(new ConfirmPayOSResultRequest(ORDER_CODE, "00", "PAID", false));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentCompletionService, never()).completePaidReceipt(any(), any());
    }

    @Test
    void completesTheReceiptWhenPayOSConfirmsThePayment() throws Exception {
        stubReceiptLookup();
        PaymentLink link = mock(PaymentLink.class);
        when(link.getStatus()).thenReturn(PaymentLinkStatus.PAID);
        when(payOS.paymentRequests().get(ORDER_CODE)).thenReturn(link);

        service.confirmPayOSResult(new ConfirmPayOSResultRequest(ORDER_CODE, null, null, false));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentCompletionService).completePaidReceipt(payment, null);
    }

    @Test
    void endOfDayNeedsNoPayOSCallForAReceiptWithoutALink() {
        assertThat(service.closeLinkBeforeCancel(payment)).isTrue();
    }

    @Test
    void endOfDayCancelsTheOpenPayOSLink() throws Exception {
        attachLink();
        PaymentLink cancelled = link(PaymentLinkStatus.CANCELLED);
        when(payOS.paymentRequests().cancel(ORDER_CODE, "Hết ngày khám")).thenReturn(cancelled);

        assertThat(service.closeLinkBeforeCancel(payment)).isTrue();
    }

    @Test
    void endOfDayAcceptsALinkThatAlreadyExpired() throws Exception {
        attachLink();
        when(payOS.paymentRequests().cancel(ORDER_CODE, "Hết ngày khám")).thenThrow(new RuntimeException("closed"));
        PaymentLink expired = link(PaymentLinkStatus.EXPIRED);
        when(payOS.paymentRequests().get(ORDER_CODE)).thenReturn(expired);

        assertThat(service.closeLinkBeforeCancel(payment)).isTrue();
    }

    @Test
    void endOfDayKeepsAReceiptWhoseLinkWasPaid() throws Exception {
        attachLink();
        when(payOS.paymentRequests().cancel(ORDER_CODE, "Hết ngày khám")).thenThrow(new RuntimeException("paid"));
        PaymentLink paid = link(PaymentLinkStatus.PAID);
        when(payOS.paymentRequests().get(ORDER_CODE)).thenReturn(paid);

        assertThat(service.closeLinkBeforeCancel(payment)).isFalse();
    }

    @Test
    void endOfDayKeepsAReceiptWhenPayOSCannotBeReached() throws Exception {
        attachLink();
        when(payOS.paymentRequests().cancel(ORDER_CODE, "Hết ngày khám")).thenThrow(new RuntimeException("timeout"));
        when(payOS.paymentRequests().get(ORDER_CODE)).thenThrow(new RuntimeException("timeout"));

        assertThat(service.closeLinkBeforeCancel(payment)).isFalse();
    }

    private void attachLink() {
        payment.attachPayOSPayment(ORDER_CODE, "https://pay.example/checkout", "qr", "link-1", java.time.Instant.now().plusSeconds(900));
    }

    private static PaymentLink link(PaymentLinkStatus status) {
        PaymentLink link = mock(PaymentLink.class);
        when(link.getStatus()).thenReturn(status);
        return link;
    }

    @Test
    void theResultCarriesNoPatientData() {
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        var result = service.getResult(payment.getId());

        assertThat(result.paymentCode()).isEqualTo("HD1");
        assertThat(result.getClass().getRecordComponents())
                .extracting(c -> c.getName())
                .doesNotContain("patientName", "patientCode", "patientPhone", "items");
    }
}
