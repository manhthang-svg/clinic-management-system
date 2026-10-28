package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.ConfirmPaymentRequest;
import clinic.backend.entity.Payment;
import clinic.backend.entity.Visit;
import clinic.backend.enums.PaymentMethod;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.PaymentType;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.ParaclinicalOrderRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.VisitRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-28T01:00:00Z");
    private static final UUID VISIT_ID = UUID.randomUUID();

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private VisitRepository visitRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ParaclinicalOrderRepository paraclinicalOrderRepository;

    @Mock
    private ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;

    private PaymentServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(
                paymentRepository,
                visitRepository,
                patientRepository,
                roomRepository,
                paraclinicalOrderRepository,
                paraclinicalOrderDetailRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void trimsSearchBeforeDelegatingPaymentQuery() {
        Payment payment = Payment.createExaminationFee(
                UUID.randomUUID(), NOW, "P-001", VISIT_ID,
                BigDecimal.valueOf(100000), BigDecimal.ZERO, BigDecimal.valueOf(100000));
        when(paymentRepository.search(
                null, null, "P-001", PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(payment)));
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.empty());

        var response = service.getPayments(
                null, null, "  P-001  ", PageRequest.of(0, 10));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst().paymentCode()).isEqualTo("P-001");
        verify(paymentRepository).search(null, null, "P-001", PageRequest.of(0, 10));
    }

    @Test
    void refusesToPrintPendingPayment() {
        Payment payment = Payment.createExaminationFee(
                UUID.randomUUID(), NOW, "P-002", VISIT_ID,
                BigDecimal.valueOf(100000), BigDecimal.ZERO, BigDecimal.valueOf(100000));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.getPrintablePayment(payment.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("đã thanh toán hoặc miễn phí");
    }

    @Test
    void confirmsExaminationPaymentAndMovesVisitToWaiting() {
        UUID cashierId = UUID.randomUUID();
        Payment payment = Payment.createExaminationFee(
                UUID.randomUUID(), NOW, "P-003", VISIT_ID,
                BigDecimal.valueOf(100000), BigDecimal.ZERO, BigDecimal.valueOf(100000));
        Visit visit = visit(VisitStatus.WAITING_FEE_PAYMENT);
        authenticateAs(cashierId);
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.of(visit));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(visitRepository.save(any(Visit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.confirmPayment(
                payment.getId(),
                new ConfirmPaymentRequest(PaymentMethod.CASH, null));

        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(visit.getStatus()).isEqualTo(VisitStatus.WAITING);
        assertThat(payment.getReceiptNumber()).startsWith("PT");
        assertThat(payment.getCashierAccountId()).isEqualTo(cashierId);
        verify(paymentRepository).save(payment);
        verify(visitRepository).save(visit);
        verify(paraclinicalOrderRepository, never()).findByPaymentId(any());
    }

    private void authenticateAs(UUID accountId) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(accountId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private Visit visit(VisitStatus status) {
        return new Visit(
                VISIT_ID,
                NOW,
                "VV202610280000001",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                NOW,
                VisitDoituongbnLoai.DV,
                null,
                null,
                null,
                "Khám bệnh",
                false,
                null,
                LocalDate.of(2026, 10, 28),
                1,
                status,
                VisitInsuranceVerifyStatus.NOT_APPLICABLE);
    }
}
