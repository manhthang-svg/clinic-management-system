package clinic.backend.service.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.ess.ESSCertIDv2;
import org.bouncycastle.asn1.ess.SigningCertificateV2;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
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

    /** Detached CMS SignedData at CAdES baseline level T (BR-59-10 steps 4, 6; TT 15/2025/TT-BKHCN Phụ lục I). */
    static final String SIGNATURE_FORMAT = "CAdES-T";
    /** Prefix of the personal identification number in a certificate's UID attribute (ASSUMPTION). */
    static final String CITIZEN_ID_PREFIX = "CCCD:";
    /** BouncyCastle for RSASSA-PSS verification, passed to each builder; not registered as a JVM-wide provider. */
    private static final BouncyCastleProvider BC = new BouncyCastleProvider();

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

    /**
     * BR-59-16 (NĐ 23/2025 Điều 16 khoản 1 điểm a): the personal identification number a certificate names, from the
     * subject's UID attribute written as "CCCD:" + 12 digits, or 12 digits alone (ASSUMPTION: the form Vietnamese CAs
     * commonly use; to confirm against a real certificate). Empty when the subject has no such number.
     */
    public Optional<String> citizenId(X509Certificate certificate) {
        X500Name subject = X500Name.getInstance(certificate.getSubjectX500Principal().getEncoded());
        for (RDN rdn : subject.getRDNs(BCStyle.UID)) {
            String value = IETFUtils.valueToString(rdn.getFirst().getValue()).strip();
            if (value.regionMatches(true, 0, CITIZEN_ID_PREFIX, 0, CITIZEN_ID_PREFIX.length())) {
                value = value.substring(CITIZEN_ID_PREFIX.length()).strip();
            }
            if (value.matches("[0-9]{12}")) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
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
        // CAdES baseline (TT 15/2025/TT-BKHCN Phụ lục I mục 1.7): the signed attributes bind the signer certificate.
        if (!bindsSigningCertificate(signer, signerCertificate)) {
            return Verification.failed("SIGNING_CERTIFICATE_ATTRIBUTE_INVALID");
        }
        try {
            if (!signer.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider(BC).build(signerCertificate))) {
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
            token.validate(new JcaSimpleSignerInfoVerifierBuilder().setProvider(BC).build(tsaCertificate.get()));
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
        return new Verification(null, withSignatureTimeStamp(signedData, signer, tokenBytes), signerCertificate,
                signedAt, tokenBytes, pkiPath(List.of(signerCertificate, issuer.get())),
                evidence(signedAt, Map.of("signer", signerStatus, "issuer", issuerStatus),
                        List.of(signerCertificate, issuer.get())));
    }

    /** The signing-certificate-v2 attribute names this certificate by its SHA-256 (ETSI EN 319 122-1). */
    private static boolean bindsSigningCertificate(SignerInformation signer, X509Certificate certificate) {
        AttributeTable signed = signer.getSignedAttributes();
        Attribute attribute = signed == null ? null : signed.get(PKCSObjectIdentifiers.id_aa_signingCertificateV2);
        if (attribute == null || attribute.getAttrValues().size() != 1) {
            return false;
        }
        try {
            ESSCertIDv2[] ids = SigningCertificateV2.getInstance(attribute.getAttrValues().getObjectAt(0)).getCerts();
            if (ids.length == 0) {
                return false;
            }
            DigestCalculator digest = new JcaDigestCalculatorProviderBuilder().build().get(ids[0].getHashAlgorithm());
            try (OutputStream output = digest.getOutputStream()) {
                output.write(certificate.getEncoded());
            }
            return MessageDigest.isEqual(ids[0].getCertHash(), digest.getDigest());
        } catch (GeneralSecurityException | OperatorCreationException | IOException | IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * CAdES-T (BR-59-10 step 6): the time-stamp token over the signature value becomes the signature-time-stamp
     * unsigned attribute, so the stored and sent signature carries its own proof of time.
     */
    private static byte[] withSignatureTimeStamp(byte[] signedData, SignerInformation signer, byte[] tokenBytes) {
        try {
            CMSSignedData cms = new CMSSignedData(signedData);
            AttributeTable unsigned = new AttributeTable(new Attribute(
                    PKCSObjectIdentifiers.id_aa_signatureTimeStampToken,
                    new DERSet(ASN1Primitive.fromByteArray(tokenBytes))));
            SignerInformation stamped = SignerInformation.replaceUnsignedAttributes(signer, unsigned);
            return CMSSignedData.replaceSigners(cms, new SignerInformationStore(stamped)).getEncoded();
        } catch (CMSException | IOException exception) {
            throw new IllegalStateException("The time stamp cannot be added to the signature", exception);
        }
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
     * @param signedData       the verified CMS with the time stamp added as an unsigned attribute (CAdES-T); this is
     *                         what is stored and sent
     * @param signedAt         the time-stamp time
     * @param certificateChain signer certificate and its CA (PkiPath)
     * @param evidence         status answers at the signing time, as JSON
     */
    public record Verification(
            String failureReason, byte[] signedData, X509Certificate signerCertificate, Instant signedAt,
            byte[] timestampToken, byte[] certificateChain, String evidence) {

        static Verification failed(String reason) {
            return new Verification(reason, null, null, null, null, null, null);
        }

        public boolean isValid() {
            return failureReason == null;
        }
    }
}
