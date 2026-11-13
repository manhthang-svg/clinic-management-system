package clinic.backend.service.impl;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import clinic.backend.dto.request.IssueSimulatedCertificateRequest;
import clinic.backend.dto.request.SigningSimulatorCredentialsRequest;
import clinic.backend.dto.response.SimulatedCertificateResponse;
import clinic.backend.dto.response.SimulatedSigningRequestResponse;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.StaffSigningCertificate;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.Role;
import clinic.backend.enums.SigningCertificateStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffSigningCertificateRepository;
import clinic.backend.service.SigningSimulatorConsoleService;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev profile only: the simulated CA app and certificate console. Registering the issued certificate stands in
 * for the Admin use case of BR-59-16, which is TBD.
 */
@Service
@RequiredArgsConstructor
public class SigningSimulatorConsoleServiceImpl implements SigningSimulatorConsoleService {

    /** ASSUMPTION: the simulated CA app asks for a 6-digit PIN. */
    private static final String PIN_FORMAT = "[0-9]{6}";

    private final SimulatedSigningAuthority authority;
    private final StaffAccountRepository staffAccountRepository;
    private final StaffSigningCertificateRepository certificateRepository;
    private final UuidV7Generator ids;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<SimulatedCertificateResponse> certificates() {
        return authority.subscribers().stream()
                .map(subscriber -> new SimulatedCertificateResponse(
                        subscriber.serial(), subscriber.userId(), subscriber.subject(), subscriber.validTo(),
                        subscriber.revokedAt(), isRegistered(subscriber.userId(), subscriber.serial())))
                .toList();
    }

    @Override
    @Transactional
    public SimulatedCertificateResponse issue(IssueSimulatedCertificateRequest request) {
        String username = request.username() == null ? "" : request.username().strip();
        StaffAccount doctor = staffAccountRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (doctor.getRole() != Role.DOCTOR) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("username", "NOT_A_DOCTOR"));
        }
        if (request.pin() == null || !request.pin().matches(PIN_FORMAT)) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("pin", "SIX_DIGITS"));
        }
        SimulatedSigningAuthority.Issued issued = authority.issue(doctor.getFullName(), username, request.pin());
        register(doctor, issued);
        X509Certificate certificate = issued.certificate();
        return new SimulatedCertificateResponse(issued.serial(), username,
                certificate.getSubjectX500Principal().getName(), certificate.getNotAfter().toInstant(), null, true);
    }

    @Override
    public void revoke(String serial) {
        authority.revoke(serial);
    }

    @Override
    public List<SimulatedSigningRequestResponse> pendingRequests(SigningSimulatorCredentialsRequest credentials) {
        return authority.pendingRequests(credentials.serial(), credentials.pin()).stream()
                .map(pending -> new SimulatedSigningRequestResponse(
                        pending.id(), pending.title(), pending.documentSha256(), pending.requestedAt(),
                        pending.expiresAt()))
                .toList();
    }

    @Override
    public void approve(String requestId, SigningSimulatorCredentialsRequest credentials) {
        authority.approve(requestId, credentials.serial(), credentials.pin());
    }

    @Override
    public void decline(String requestId, SigningSimulatorCredentialsRequest credentials) {
        authority.decline(requestId, credentials.serial(), credentials.pin());
    }

    /**
     * BR-59-16: the account's single active certificate; a previous one is removed. The subject must name the
     * Doctor (CN = full name, ASSUMPTION until the identity attribute of real certificates is decided).
     */
    private void register(StaffAccount doctor, SimulatedSigningAuthority.Issued issued) {
        X509Certificate certificate = issued.certificate();
        String subject = certificate.getSubjectX500Principal().getName();
        if (!subject.contains("CN=" + doctor.getFullName())) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("certificate", "SUBJECT_MISMATCH"));
        }
        Instant now = clock.instant();
        certificateRepository.findByStaffAccountIdAndStatus(doctor.getId(), SigningCertificateStatus.ACTIVE)
                .ifPresent(previous -> {
                    previous.remove(now);
                    certificateRepository.saveAndFlush(previous);
                });
        try {
            certificateRepository.save(new StaffSigningCertificate(
                    ids.next(), now, doctor.getId(), issued.providerCode(), issued.providerUserId(), issued.serial(),
                    certificate.getIssuerX500Principal().getName(), subject,
                    certificate.getNotBefore().toInstant(), certificate.getNotAfter().toInstant(),
                    certificate.getEncoded()));
        } catch (CertificateEncodingException exception) {
            throw new IllegalStateException("The issued certificate cannot be encoded", exception);
        }
    }

    private boolean isRegistered(String username, String serial) {
        Optional<StaffAccount> account = staffAccountRepository.findByUsername(username);
        return account.flatMap(value -> certificateRepository.findByStaffAccountIdAndStatus(
                        value.getId(), SigningCertificateStatus.ACTIVE))
                .map(certificate -> certificate.getSerialNumber().equals(serial))
                .orElse(false);
    }
}
