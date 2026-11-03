package clinic.backend.service;

import clinic.backend.dto.request.ConfirmPaymentRequest;
import clinic.backend.dto.response.PaymentHistoryResponse;
import clinic.backend.dto.response.PaymentResponse;
import clinic.backend.enums.PaymentMethod;
import clinic.backend.dto.response.ReceiptPrintResponse;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.PaymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface PaymentService {

    Page<PaymentResponse> getPayments(
            PaymentStatus status,
            PaymentType type,
            String search,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    );

    PaymentHistoryResponse getHistory(
            UUID cashierAccountId,
            PaymentStatus status,
            PaymentType type,
            PaymentMethod method,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    );

    Page<PaymentResponse> getPendingExaminationPayments(
            Pageable pageable
    );

    PaymentResponse getPaymentById(UUID paymentId);

    ReceiptPrintResponse prepareReceiptPrint(UUID cashierAccountId, UUID paymentId);

    PaymentResponse confirmPayment(
            UUID cashierAccountId,
            UUID paymentId,
            ConfirmPaymentRequest request
    );
}
