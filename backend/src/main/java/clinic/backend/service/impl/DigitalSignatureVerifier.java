package clinic.backend.service.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import clinic.backend.config.properties.SigningProperties;
import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import clinic.backend.service.CertificateStatusService;
import clinic.backend.service.CertificateStatusService.CertificateStatus;
import clinic.backend.service.TimestampAuthority;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.tsp.TSPException;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.util.Selector;
import org.springframework.stereotype.Component;

/**
 * Checks of a digital signature that do not depend on who provides the signing service (BR-59-10 steps 1, 5, 6;
 * NĐ 23/2025 Điều 15, 16, 17 khoản 3). Certificate statuses come from {@link CertificateStatusService}, the time stamp
 * from {@link TimestampAuthority}; the trusted CA certificates are the PEM files of {@code clinic.signing
 * .trusted-ca-files} (BR-59-16), read on every check so a changed list applies at once.
 */
@Slf4j
@Component
public class DigitalSignatureVerifier {

    /** CMS SignedData, detached (BR-59-10 step 4). */
    static final String SIGNATURE_FORMAT = "CMS";

    private final SigningProperties properties;
    private final CertificateStatusService statusService;
    private final TimestampAuthority timestampAuthority;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public DigitalSignatureVerifier(
            SigningProperties properties, CertificateStatusService statusService,
            TimestampAuthority timestampAuthority, Clock clock) {
        this.properties = properties;
        this.statusService = statusService;
        this.timestampAuthority = timestampAuthority;
        this.clock = clock;
    }

    /** Reads a DER certificate as stored in {@code staff_signing_certificates}. */
    public X509Certificate certificate(byte[] der) {
        try {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(der));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("A stored certificate cannot be read", exception);
        }
    }

    /**
     * BR-59-10 step 1 (NĐ 23 Điều 15): before a signature is requested, the Doctor's certificate is within its
     * validity, issued by a trusted CA and not revoked, and the CA certificate is valid and not revoked. Throws
     * {@code SIGNING_CERTIFICATE_INVALID} with the reason otherwise.
     */
    public void preCheck(X509Certificate certificate) {
        Instant now = clock.instant();
        String reason = validityProblem(certificate, now);
        if (reason != null) {
            throw invalidCertificate(reason);
        }
        X509Certificate issuer = trustedIssuer(certificate)
                .orElseThrow(() -> invalidCertificate("CA_NOT_TRUSTED"));
        if (validityProblem(issuer, now) != null) {
            throw invalidCertificate("CA_CERTIFICATE_INVALID");
        }
        if (!statusService.status(certificate, now).isGood()) {
            throw invalidCertificate("CERTIFICATE_REVOKED");
        }
        if (!statusService.status(issuer, now).isGood()) {
            throw invalidCertificate("CA_CERTIFICATE_INVALID");
        }
    }

    /**
     * BR-59-10 steps 5-6 (NĐ 23 Điều 16, Điều 17 khoản 3): the returned CMS matches the payload and the public key of
     * the registered certificate, the time stamp covers the signature, the certificate chains to a trusted CA and
     * was valid and not revoked at the time-stamp time, and so was the CA certificate. A failed check is returned,
     * not thrown, so the caller can record it; a service that does not answer throws
     * {@code SIGNING_SERVICE_UNAVAILABLE}.
     */
    public Verification verify(byte[] signedData, byte[] payload, X509Certificate registered) {
        SignerInformation signer;
        X509Certificate signerCertificate;
        try {
            CMSSignedData cms = new CMSSignedData(new CMSProcessableByteArray(payload), signedData);
            Collection<SignerInformation> signers = cms.getSignerInfos().getSigners();
            if (signers.size() != 1) {
                return Verification.failed("MALFORMED_SIGNATURE");
            }
            signer = signers.iterator().next();
            @SuppressWarnings("unchecked")
            Collection<X509CertificateHolder> matches = cms.getCertificates()
                    .getMatches((Selector<X509CertificateHolder>) (Selector<?>) signer.getSID());
            if (matches.isEmpty()) {
                return Verification.failed("SIGNER_CERTIFICATE_MISSING");
            }
            signerCertificate = new JcaX509CertificateConverter().getCertificate(matches.iterator().next());
        } catch (CMSException | GeneralSecurityException | RuntimeException exception) {
            return Verification.failed("MALFORMED_SIGNATURE");
        }
        if (!signerCertificate.equals(registered)) {
            return Verification.failed("CERTIFICATE_NOT_REGISTERED_FOR_SIGNER");
        }
        try {
            if (!signer.verify(new JcaSimpleSignerInfoVerifierBuilder().build(signerCertificate))) {
                return Verification.failed("SIGNATURE_MISMATCH");
            }
        } catch (CMSException | OperatorCreationException exception) {
            return Verification.failed("SIGNATURE_MISMATCH");
        }

        // BR-59-10 step 6: the time stamp over the signature value fixes the signing time.
        byte[] tokenBytes = timestampAuthority.timestamp(signer.getSignature());
        Instant signedAt;
        try {
            TimeStampToken token = new TimeStampToken(new CMSSignedData(tokenBytes));
            Optional<X509Certificate> tsaCertificate = timeStampCertificate(token);
            if (tsaCertificate.isEmpty()
                    || trustedIssuer(tsaCertificate.get()).isEmpty()
                    || !MessageDigest.isEqual(token.getTimeStampInfo().getMessageImprintDigest(),
                            sha256(signer.getSignature()))) {
                return Verification.failed("TIMESTAMP_INVALID");
            }
            token.validate(new JcaSimpleSignerInfoVerifierBuilder().build(tsaCertificate.get()));
            signedAt = token.getTimeStampInfo().getGenTime().toInstant();
        } catch (CMSException | TSPException | IOException | OperatorCreationException exception) {
            return Verification.failed("TIMESTAMP_INVALID");
        }

        Optional<X509Certificate> issuer = trustedIssuer(signerCertificate);
        if (issuer.isEmpty() || !validPath(signerCertificate, issuer.get(), signedAt)) {
            return Verification.failed("CHAIN_NOT_TRUSTED");
        }
        if (validityProblem(signerCertificate, signedAt) != null) {
            return Verification.failed("CERTIFICATE_NOT_VALID_AT_SIGNING_TIME");
        }
        if (validityProblem(issuer.get(), signedAt) != null) {
            return Verification.failed("CA_CERTIFICATE_INVALID");
        }
        CertificateStatus signerStatus = statusService.status(signerCertificate, signedAt);
        CertificateStatus issuerStatus = statusService.status(issuer.get(), signedAt);
        if (!signerStatus.isGood()) {
            return Verification.failed("CERTIFICATE_REVOKED");
        }
        if (!issuerStatus.isGood()) {
            return Verification.failed("CA_CERTIFICATE_INVALID");
        }
        return new Verification(null, signerCertificate, signedAt, tokenBytes,
                pkiPath(List.of(signerCertificate, issuer.get())),
                evidence(signedAt, Map.of("signer", signerStatus, "issuer", issuerStatus),
                        List.of(signerCertificate, issuer.get())));
    }

    /** {@code SIGNING_CERTIFICATE_INVALID} with the reason of MSG118. */
    private static AppException invalidCertificate(String reason) {
        return new AppException(ErrorCode.SIGNING_CERTIFICATE_INVALID, Map.of("reason", reason));
    }

    private static String validityProblem(X509Certificate certificate, Instant at) {
        if (at.isBefore(certificate.getNotBefore().toInstant())) {
            return "CERTIFICATE_NOT_YET_VALID";
        }
        if (at.isAfter(certificate.getNotAfter().toInstant())) {
            return "CERTIFICATE_EXPIRED";
        }
        return null;
    }

    /** The trusted CA certificate that issued and signed this certificate, if any. */
    private Optional<X509Certificate> trustedIssuer(X509Certificate certificate) {
        for (X509Certificate candidate : trustedCertificates()) {
            if (!candidate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal())) {
                continue;
            }
            try {
                certificate.verify(candidate.getPublicKey());
                return Optional.of(candidate);
            } catch (GeneralSecurityException exception) {
                // Same name, another key: keep looking.
            }
        }
        return Optional.empty();
    }

    /** PKIX path from the certificate to the trusted CA at the signing time; revocation is checked separately. */
    private static boolean validPath(X509Certificate certificate, X509Certificate issuer, Instant at) {
        try {
            CertPath path = CertificateFactory.getInstance("X.509").generateCertPath(List.of(certificate));
            PKIXParameters parameters = new PKIXParameters(Set.of(new TrustAnchor(issuer, null)));
            parameters.setRevocationEnabled(false);
            parameters.setDate(Date.from(at));
            CertPathValidator.getInstance("PKIX").validate(path, parameters);
            return true;
        } catch (CertPathValidatorException exception) {
            return false;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("PKIX validation is not available", exception);
        }
    }

    private List<X509Certificate> trustedCertificates() {
        List<X509Certificate> certificates = new ArrayList<>();
        for (String file : properties.trustedCaFiles()) {
            if (file == null || file.isBlank()) {
                continue;
            }
            Path path = Path.of(file.strip());
            if (!Files.isReadable(path)) {
                log.warn("Trusted CA file {} is missing", path.toAbsolutePath());
                continue;
            }
            try (InputStream input = Files.newInputStream(path)) {
                CertificateFactory.getInstance("X.509").generateCertificates(input).stream()
                        .map(X509Certificate.class::cast)
                        .forEach(certificates::add);
            } catch (IOException | GeneralSecurityException exception) {
                log.warn("Trusted CA file {} cannot be read", path.toAbsolutePath());
            }
        }
        return certificates;
    }

    private static Optional<X509Certificate> timeStampCertificate(TimeStampToken token) {
        @SuppressWarnings("unchecked")
        Collection<X509CertificateHolder> matches = token.getCertificates()
                .getMatches((Selector<X509CertificateHolder>) (Selector<?>) token.getSID());
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new JcaX509CertificateConverter().getCertificate(matches.iterator().next()));
        } catch (GeneralSecurityException exception) {
            return Optional.empty();
        }
    }

    private static byte[] pkiPath(List<X509Certificate> chain) {
        try {
            return CertificateFactory.getInstance("X.509").generateCertPath(chain).getEncoded("PkiPath");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("The certificate chain cannot be encoded", exception);
        }
    }

    private String evidence(Instant signedAt, Map<String, CertificateStatus> statuses, List<X509Certificate> chain) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("signedAt", signedAt.toString());
        evidence.put("verifiedAt", clock.instant().toString());
        evidence.put("statuses", statuses.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    X509Certificate certificate = entry.getKey().equals("signer") ? chain.get(0) : chain.get(1);
                    Map<String, Object> status = new LinkedHashMap<>();
                    status.put("subject", certificate.getSubjectX500Principal().getName());
                    status.put("serial", certificate.getSerialNumber().toString(16));
                    status.put("state", entry.getValue().state().name());
                    status.put("responder", entry.getValue().responder());
                    status.put("checkedAt", entry.getValue().checkedAt().toString());
                    return status;
                },
                (first, second) -> first,
                LinkedHashMap::new)));
        try {
            return json.writeValueAsString(evidence);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("The verification evidence cannot be written", exception);
        }
    }

    static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    static String sha256Hex(byte[] data) {
        return HexFormat.of().formatHex(sha256(data));
    }

    /**
     * The outcome of {@link #verify}: {@code failureReason} is null when the signature is valid.
     *
     * @param signedAt         the time-stamp time
     * @param certificateChain signer certificate and its CA (PkiPath)
     * @param evidence         status answers at the signing time, as JSON
     */
    public record Verification(
            String failureReason, X509Certificate signerCertificate, Instant signedAt, byte[] timestampToken,
            byte[] certificateChain, String evidence) {

        static Verification failed(String reason) {
            return new Verification(reason, null, null, null, null, null);
        }

        public boolean isValid() {
            return failureReason == null;
        }
    }
}
