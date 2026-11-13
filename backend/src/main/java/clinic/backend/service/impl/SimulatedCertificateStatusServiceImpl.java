package clinic.backend.service.impl;

import java.security.cert.X509Certificate;
import java.time.Instant;

import clinic.backend.service.CertificateStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Simulated OCSP of the CA and national trust service (NĐ 23/2025 Điều 15, 16). Replace with real OCSP and
 * Trung tâm Chứng thực điện tử quốc gia lookups to go live.
 */
@Service
@RequiredArgsConstructor
public class SimulatedCertificateStatusServiceImpl implements CertificateStatusService {

    private final SimulatedSigningAuthority authority;

    @Override
    public CertificateStatus status(X509Certificate certificate, Instant at) {
        return authority.status(certificate, at);
    }
}
