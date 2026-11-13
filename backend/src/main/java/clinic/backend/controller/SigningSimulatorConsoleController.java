package clinic.backend.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import clinic.backend.dto.request.IssueSimulatedCertificateRequest;
import clinic.backend.dto.request.SigningSimulatorCredentialsRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.SimulatedCertificateResponse;
import clinic.backend.dto.response.SimulatedSigningRequestResponse;
import clinic.backend.service.SigningSimulatorConsoleService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev profile only: the simulated CA app ("Ứng dụng ký số (giả lập)") in which the Doctor approves or declines a
 * signing request, and the simulated CA's certificate console. It stands in for the CA's mobile app and is not part
 * of any use case; it signs in with the certificate serial and the simulated CA's PIN, not with a clinic account.
 */
@Hidden
@RestController
@RequestMapping("/dev/ca-sim")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "clinic.signing-simulator", name = "console-enabled", havingValue = "true")
public class SigningSimulatorConsoleController {

    private static final String PAGE = "signing-simulator/index.html";

    private final SigningSimulatorConsoleService consoleService;

    @GetMapping({"", "/"})
    public ResponseEntity<String> page() throws IOException {
        try (InputStream input = new ClassPathResource(PAGE).getInputStream()) {
            return ResponseEntity.ok()
                    .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                    .cacheControl(CacheControl.noStore())
                    .body(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @GetMapping("/api/certificates")
    public ApiResponse<List<SimulatedCertificateResponse>> certificates() {
        return ApiResponse.success(consoleService.certificates());
    }

    @PostMapping("/api/certificates")
    public ApiResponse<SimulatedCertificateResponse> issue(@RequestBody IssueSimulatedCertificateRequest request) {
        return ApiResponse.success(consoleService.issue(request));
    }

    @PostMapping("/api/certificates/{serial}/revoke")
    public ResponseEntity<Void> revoke(@PathVariable String serial) {
        consoleService.revoke(serial);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/requests/pending")
    public ApiResponse<List<SimulatedSigningRequestResponse>> pending(
            @RequestBody SigningSimulatorCredentialsRequest credentials) {
        return ApiResponse.success(consoleService.pendingRequests(credentials));
    }

    @PostMapping("/api/requests/{requestId}/approve")
    public ResponseEntity<Void> approve(
            @PathVariable String requestId, @RequestBody SigningSimulatorCredentialsRequest credentials) {
        consoleService.approve(requestId, credentials);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/requests/{requestId}/decline")
    public ResponseEntity<Void> decline(
            @PathVariable String requestId, @RequestBody SigningSimulatorCredentialsRequest credentials) {
        consoleService.decline(requestId, credentials);
        return ResponseEntity.noContent().build();
    }
}
