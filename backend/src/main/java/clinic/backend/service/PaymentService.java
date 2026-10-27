package clinic.backend.service;

import clinic.backend.dto.request.ConfirmPaymentRequest;
import clinic.backend.dto.response.PaymentResponse;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.PaymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PaymentService {

    Page<PaymentResponse> getPayments(
            PaymentStatus status,
            PaymentType type,
            String search,
            Pageable pageable
    );

    Page<PaymentResponse> getPendingExaminationPayments(
            Pageable pageable
    );

    PaymentResponse getPaymentById(UUID paymentId);

    PaymentResponse getPrintablePayment(UUID paymentId);

    PaymentResponse confirmPayment(
            UUID paymentId,
            ConfirmPaymentRequest request
    );
}
