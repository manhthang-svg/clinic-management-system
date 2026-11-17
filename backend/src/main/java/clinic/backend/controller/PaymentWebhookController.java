
package clinic.backend.controller;

import clinic.backend.service.PayOSPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.payos.model.webhooks.Webhook;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Webhook thanh toán", description = "Nhận kết quả thanh toán từ PayOS")
public class PaymentWebhookController {

    private final PayOSPaymentService payOSPaymentService;

    // PayOS goi ve khi thanh toan xong
    @PostMapping("/payos-webhook")
    @Operation(summary = "Nhận webhook thanh toán từ PayOS")
    public ResponseEntity<String> handleWebhook(
            @RequestBody Webhook webhook
    ) {
        payOSPaymentService.handleWebhook(webhook);
        return ResponseEntity.ok("OK");
    }
}
