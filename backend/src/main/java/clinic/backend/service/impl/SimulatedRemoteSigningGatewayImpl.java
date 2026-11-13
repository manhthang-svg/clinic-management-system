package clinic.backend.service.impl;

import java.time.Instant;

import clinic.backend.service.RemoteSigningGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Simulated signing gateway and remote-signing CA (docs/uc-59-e-prescription.md, Known limitations). Replace with a
 * client of the real Cổng kết nối dịch vụ chứng thực chữ ký số công cộng (NĐ 23/2025 Điều 44) to go live.
 */
@Service
@RequiredArgsConstructor
public class SimulatedRemoteSigningGatewayImpl implements RemoteSigningGateway {

    private final SimulatedSigningAuthority authority;

    @Override
    public String providerCode() {
        return SimulatedSigningAuthority.PROVIDER_CODE;
    }

    @Override
    public String requestSignature(
            String providerUserId, String certificateSerial, byte[] documentSha256, String documentTitle,
            Instant expiresAt) {
        return authority.requestSignature(providerUserId, certificateSerial, documentSha256, documentTitle, expiresAt);
    }

    @Override
    public SigningResult result(String transactionId) {
        return authority.result(transactionId);
    }

    @Override
    public void cancel(String transactionId) {
        authority.cancel(transactionId);
    }
}
