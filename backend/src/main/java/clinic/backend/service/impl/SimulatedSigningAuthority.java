package clinic.backend.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.KeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import clinic.backend.config.properties.SigningSimulatorProperties;
import clinic.backend.config.properties.SigningSimulatorProperties.FailureMode;
import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import clinic.backend.service.CertificateStatusService.CertificateStatus;
import clinic.backend.service.CertificateStatusService.State;
import clinic.backend.service.RemoteSigningGateway.SigningResult;
import clinic.backend.service.RemoteSigningGateway.Status;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSAbsentContent;
import org.bouncycastle.cms.CMSAttributeTableGenerator;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TSPException;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampTokenGenerator;
import org.springframework.stereotype.Component;

/**
 * The simulated trust services behind {@link SimulatedRemoteSigningGatewayImpl},
 * {@link SimulatedCertificateStatusServiceImpl} and {@link SimulatedTimestampAuthorityImpl}: a national root
 * ("Trung tâm Chứng thực điện tử quốc gia (giả lập)"), a remote-signing CA under it, a time-stamp unit of that CA and
 * the subscribers' keys, as the CA would keep them (NĐ 23/2025 Điều 37 khoản 4).
 *
 * <p>The keys live in a PKCS#12 file outside the database and the repository, created on first use, with a JSON
 * file beside it for the subscribers' PIN hashes and revocations. The clinic application reaches them only through
 * the three service interfaces and the dev console; it never reads a private key. Signing transactions are kept in
 * memory and lost on restart, which the clinic side sees as an expired request. The CA certificate is written to
 * {@code trusted-ca.pem} beside the keystore so the dev profile can trust it. Test keys only: no legal value
 * (Luật GDĐT 2023 Điều 28 khoản 3). RSA 2048 with SHA-256 (ASSUMPTION).
 */
@Slf4j
@Component
public class SimulatedSigningAuthority {

    static final String PROVIDER_CODE = "SIMULATED_CA";
    static final String NATIONAL_RESPONDER = "Trung tâm Chứng thực điện tử quốc gia (giả lập)";
    static final String CA_RESPONDER = "Dịch vụ OCSP của CA ký số từ xa (giả lập)";

    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String ROOT_ALIAS = "root";
    private static final String CA_ALIAS = "ca";
    private static final String TSA_ALIAS = "tsa";
    private static final String SUBSCRIBER_PREFIX = "subscriber-";
    private static final Duration AUTHORITY_VALIDITY = Duration.ofDays(3650);
    private static final Duration SUBSCRIBER_VALIDITY = Duration.ofDays(365);
    private static final int PIN_ITERATIONS = 120_000;
    /** A made-up time-stamp policy for the simulator. */
    private static final ASN1ObjectIdentifier TSA_POLICY = new ASN1ObjectIdentifier("1.3.6.1.4.1.99999.1.1");

    private final SigningSimulatorProperties properties;
    private final Clock clock;
    private final SecureRandom random;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final Map<String, Transaction> transactions = new ConcurrentHashMap<>();
    private volatile Keys keys;

    public SimulatedSigningAuthority(SigningSimulatorProperties properties, Clock clock, SecureRandom random) {
        this.properties = properties;
        this.clock = clock;
        this.random = random;
    }

    // ---- Remote-signing CA (gateway) -------------------------------------------------------------------------

    String requestSignature(
            String providerUserId, String certificateSerial, byte[] documentSha256, String title, Instant expiresAt) {
        enterService();
        Subscriber subscriber = keys().subscribers.get(certificateSerial);
        if (subscriber == null || !subscriber.userId().equals(providerUserId) || subscriber.revokedAt() != null) {
            throw new AppException(ErrorCode.SIGNING_CERTIFICATE_INVALID, Map.of("reason", "UNKNOWN_TO_CA"));
        }
        String id = UUID.randomUUID().toString();
        transactions.put(id, new Transaction(
                id, certificateSerial, documentSha256.clone(), title, clock.instant(), expiresAt));
        return id;
    }

    SigningResult result(String transactionId) {
        enterService();
        Transaction transaction = transactions.get(transactionId);
        if (transaction == null) {
            // Lost on restart, or never existed: the CA no longer waits for it.
            return SigningResult.of(Status.EXPIRED);
        }
        synchronized (transaction) {
            if (transaction.status == Status.PENDING && !clock.instant().isBefore(transaction.expiresAt)) {
                transaction.status = Status.EXPIRED;
            }
            return new SigningResult(transaction.status,
                    transaction.signedData == null ? null : transaction.signedData.clone());
        }
    }

    void cancel(String transactionId) {
        enterService();
        Transaction transaction = transactions.get(transactionId);
        if (transaction != null) {
            synchronized (transaction) {
                if (transaction.status == Status.PENDING) {
                    transaction.status = Status.EXPIRED;
                }
            }
        }
    }

    // ---- The CA's app, used by the dev console ----------------------------------------------------------------

    /** The requests waiting for this subscriber's approval, oldest first. */
    List<PendingRequest> pendingRequests(String certificateSerial, String pin) {
        requirePin(certificateSerial, pin);
        Instant now = clock.instant();
        return transactions.values().stream()
                .filter(transaction -> transaction.certificateSerial.equals(certificateSerial))
                .filter(transaction -> transaction.status == Status.PENDING && now.isBefore(transaction.expiresAt))
                .sorted(Comparator.comparing(transaction -> transaction.requestedAt))
                .map(transaction -> new PendingRequest(transaction.id, transaction.title,
                        HexFormat.of().formatHex(transaction.documentSha256), transaction.requestedAt,
                        transaction.expiresAt))
                .toList();
    }

    /** The subscriber approves: the CA signs the hash with the subscriber's key. */
    void approve(String transactionId, String certificateSerial, String pin) {
        Subscriber subscriber = requirePin(certificateSerial, pin);
        Transaction transaction = pendingTransaction(transactionId, certificateSerial);
        synchronized (transaction) {
            if (transaction.status != Status.PENDING) {
                throw new AppException(ErrorCode.SIGNING_REQUEST_NOT_FOUND);
            }
            transaction.signedData = signHash(subscriber, transaction.documentSha256);
            transaction.status = Status.SIGNED;
        }
    }

    void decline(String transactionId, String certificateSerial, String pin) {
        requirePin(certificateSerial, pin);
        Transaction transaction = pendingTransaction(transactionId, certificateSerial);
        synchronized (transaction) {
            if (transaction.status == Status.PENDING) {
                transaction.status = Status.DECLINED;
            }
        }
    }

    // ---- Certificate status (OCSP of the CA, national trust service) -------------------------------------------

    CertificateStatus status(X509Certificate certificate, Instant at) {
        enterService();
        Keys current = keys();
        Instant checkedAt = clock.instant();
        if (certificate.equals(current.ca.certificate()) || certificate.equals(current.root.certificate())) {
            return new CertificateStatus(State.GOOD, NATIONAL_RESPONDER, checkedAt, null);
        }
        if (certificate.equals(current.tsa.certificate())) {
            return new CertificateStatus(State.GOOD, CA_RESPONDER, checkedAt, null);
        }
        Subscriber subscriber = current.subscribers.get(serialOf(certificate));
        if (subscriber == null || !subscriber.certificate().equals(certificate)) {
            return new CertificateStatus(State.UNKNOWN, CA_RESPONDER, checkedAt, null);
        }
        Instant revokedAt = subscriber.revokedAt();
        boolean revoked = revokedAt != null && !revokedAt.isAfter(at);
        return new CertificateStatus(revoked ? State.REVOKED : State.GOOD, CA_RESPONDER, checkedAt, revokedAt);
    }

    // ---- Time-stamp unit -------------------------------------------------------------------------------------

    byte[] timestamp(byte[] data) {
        enterService();
        Keys current = keys();
        try {
            DigestCalculatorProvider digests = new JcaDigestCalculatorProviderBuilder().build();
            TimeStampTokenGenerator generator = new TimeStampTokenGenerator(
                    new JcaSimpleSignerInfoGeneratorBuilder()
                            .build(SIGNATURE_ALGORITHM, current.tsa.key(), current.tsa.certificate()),
                    digests.get(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256)),
                    TSA_POLICY);
            generator.addCertificates(new JcaCertStore(List.of(current.tsa.certificate(), current.ca.certificate())));
            TimeStampRequestGenerator requests = new TimeStampRequestGenerator();
            requests.setCertReq(true);
            TimeStampRequest request = requests.generate(
                    TSPAlgorithms.SHA256, MessageDigest.getInstance("SHA-256").digest(data),
                    new BigInteger(64, random));
            TimeStampResponse response = new TimeStampResponseGenerator(generator, TSPAlgorithms.ALLOWED)
                    .generate(request, new BigInteger(64, random), Date.from(clock.instant()));
            return response.getTimeStampToken().getEncoded();
        } catch (GeneralSecurityException | OperatorCreationException | TSPException | IOException exception) {
            throw new IllegalStateException("The simulated time-stamp unit failed", exception);
        }
    }

    // ---- Certificate administration, used by the dev console -------------------------------------------------

    /**
     * Issues a subscriber certificate for a staff member: subject CN = full name, UID = the account's user name
     * (ASSUMPTION: a real certificate names the person's identity number). The subscriber approves signatures with
     * the PIN, which the simulated CA keeps as a hash.
     */
    synchronized Issued issue(String fullName, String userId, String pin) {
        requireText(pin, "pin");
        Keys current = keys();
        try {
            KeyPair pair = newKeyPair();
            Instant now = clock.instant();
            X500Name subject = new X500NameBuilder(BCStyle.INSTANCE)
                    .addRDN(BCStyle.C, "VN")
                    .addRDN(BCStyle.O, "Phòng khám (giả lập)")
                    .addRDN(BCStyle.UID, userId)
                    .addRDN(BCStyle.CN, fullName)
                    .build();
            X509Certificate certificate = certificate(
                    subject, pair, current.ca, now, now.plus(SUBSCRIBER_VALIDITY), CertificateKind.SUBSCRIBER);
            String serial = serialOf(certificate);
            byte[] salt = new byte[16];
            random.nextBytes(salt);
            Subscriber subscriber = new Subscriber(serial, userId, pair.getPrivate(), certificate,
                    Base64.getEncoder().encodeToString(salt), hashPin(pin, salt), null);
            Map<String, Subscriber> subscribers = new LinkedHashMap<>(current.subscribers);
            subscribers.put(serial, subscriber);
            Keys next = new Keys(current.root, current.ca, current.tsa, subscribers);
            save(next);
            keys = next;
            return new Issued(serial, certificate, PROVIDER_CODE, userId);
        } catch (GeneralSecurityException | OperatorCreationException | IOException exception) {
            throw new IllegalStateException("The simulated CA could not issue a certificate", exception);
        }
    }

    /** Revokes a subscriber certificate from now on, to demonstrate UC-90 EF-11. */
    synchronized void revoke(String serial) {
        Keys current = keys();
        Subscriber subscriber = current.subscribers.get(serial);
        if (subscriber == null) {
            throw new AppException(ErrorCode.NOT_FOUND);
        }
        if (subscriber.revokedAt() != null) {
            return;
        }
        Map<String, Subscriber> subscribers = new LinkedHashMap<>(current.subscribers);
        subscribers.put(serial, subscriber.revoked(clock.instant()));
        Keys next = new Keys(current.root, current.ca, current.tsa, subscribers);
        try {
            save(next);
        } catch (GeneralSecurityException | IOException exception) {
            throw new IllegalStateException("The simulated CA could not revoke the certificate", exception);
        }
        keys = next;
    }

    List<SubscriberInfo> subscribers() {
        return keys().subscribers.values().stream()
                .map(subscriber -> new SubscriberInfo(subscriber.serial(), subscriber.userId(),
                        subscriber.certificate().getSubjectX500Principal().getName(),
                        subscriber.certificate().getNotAfter().toInstant(), subscriber.revokedAt()))
                .toList();
    }

    X509Certificate caCertificate() {
        return keys().ca.certificate();
    }

    static String serialOf(X509Certificate certificate) {
        return certificate.getSerialNumber().toString(16);
    }

    // ---- Internals ---------------------------------------------------------------------------------------------

    /** The configured delay and failure mode of every simulated service call. */
    private void enterService() {
        long delay = properties.responseDelay().toMillis();
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AppException(ErrorCode.SIGNING_SERVICE_UNAVAILABLE);
            }
        }
        if (properties.failureMode() == FailureMode.UNAVAILABLE) {
            throw new AppException(ErrorCode.SIGNING_SERVICE_UNAVAILABLE);
        }
    }

    private byte[] signHash(Subscriber subscriber, byte[] documentSha256) {
        try {
            // UC-90 EF-16 demonstration: a signature made with another key does not match the certificate.
            PrivateKey key = properties.failureMode() == FailureMode.BAD_SIGNATURE
                    ? newKeyPair().getPrivate() : subscriber.key();
            ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(key);
            // The CA receives only the hash: the messageDigest attribute is set to it, and the signature covers the
            // signed attributes, so the result verifies against the clinic's payload (detached CMS).
            CMSAttributeTableGenerator attributes = new CMSAttributeTableGenerator() {
                private final DefaultSignedAttributeTableGenerator defaults = new DefaultSignedAttributeTableGenerator();

                @Override
                @SuppressWarnings({"rawtypes", "unchecked"})
                public AttributeTable getAttributes(Map parameters) {
                    Map<Object, Object> withHash = new HashMap<>(parameters);
                    withHash.put(CMSAttributeTableGenerator.DIGEST, documentSha256.clone());
                    return defaults.getAttributes(withHash);
                }
            };
            CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
            generator.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                    new JcaDigestCalculatorProviderBuilder().build())
                    .setSignedAttributeGenerator(attributes)
                    .build(signer, subscriber.certificate()));
            generator.addCertificates(new JcaCertStore(List.of(subscriber.certificate(), keys().ca.certificate())));
            return generator.generate(new CMSAbsentContent(), false).getEncoded();
        } catch (GeneralSecurityException | OperatorCreationException | IOException
                 | org.bouncycastle.cms.CMSException exception) {
            throw new IllegalStateException("The simulated CA could not sign", exception);
        }
    }

    private Subscriber requirePin(String certificateSerial, String pin) {
        Subscriber subscriber = certificateSerial == null ? null : keys().subscribers.get(certificateSerial.strip());
        if (subscriber == null || pin == null
                || !MessageDigest.isEqual(
                        Base64.getDecoder().decode(subscriber.pinHash()),
                        Base64.getDecoder().decode(hashPin(pin, Base64.getDecoder().decode(subscriber.pinSalt()))))) {
            throw new AppException(ErrorCode.SIGNING_SIMULATOR_CREDENTIALS_INVALID);
        }
        return subscriber;
    }

    private Transaction pendingTransaction(String transactionId, String certificateSerial) {
        Transaction transaction = transactionId == null ? null : transactions.get(transactionId);
        if (transaction == null || !transaction.certificateSerial.equals(certificateSerial.strip())) {
            throw new AppException(ErrorCode.SIGNING_REQUEST_NOT_FOUND);
        }
        return transaction;
    }

    private String hashPin(String pin, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, PIN_ITERATIONS, 256);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("PBKDF2 is not available", exception);
        }
    }

    private Keys keys() {
        Keys current = keys;
        if (current == null) {
            synchronized (this) {
                current = keys;
                if (current == null) {
                    current = loadOrCreate();
                    keys = current;
                }
            }
        }
        return current;
    }

    private char[] password() {
        String password = properties.keystorePassword();
        if (password == null || password.isBlank()) {
            log.warn("The signing simulator has no keystore password (SIGNING_SIMULATOR_KEYSTORE_PASSWORD); "
                    + "it answers as unavailable");
            throw new AppException(ErrorCode.SIGNING_SERVICE_UNAVAILABLE);
        }
        return password.toCharArray();
    }

    private Keys loadOrCreate() {
        char[] password = password();
        Path store = Path.of(properties.keystore()).toAbsolutePath();
        try {
            if (Files.exists(store)) {
                return load(store, password);
            }
            Keys created = create();
            save(created);
            log.info("Created the signing simulator keystore at {}", store);
            return created;
        } catch (GeneralSecurityException | OperatorCreationException | IOException exception) {
            throw new IllegalStateException("The signing simulator keystore at " + store + " cannot be used",
                    exception);
        }
    }

    private Keys create() throws GeneralSecurityException, OperatorCreationException, IOException {
        Instant now = clock.instant();
        Instant until = now.plus(AUTHORITY_VALIDITY);
        KeyPair rootPair = newKeyPair();
        X500Name rootName = name("Trung tâm Chứng thực điện tử quốc gia (giả lập)");
        Identity root = new Identity(rootPair.getPrivate(),
                certificate(rootName, rootPair, null, now, until, CertificateKind.AUTHORITY));
        KeyPair caPair = newKeyPair();
        Identity ca = new Identity(caPair.getPrivate(),
                certificate(name("CA ký số từ xa (giả lập)"), caPair, root, now, until, CertificateKind.AUTHORITY));
        KeyPair tsaPair = newKeyPair();
        Identity tsa = new Identity(tsaPair.getPrivate(),
                certificate(name("Dịch vụ cấp dấu thời gian (giả lập)"), tsaPair, ca, now, until,
                        CertificateKind.TIME_STAMPING));
        return new Keys(root, ca, tsa, new LinkedHashMap<>());
    }

    private static X500Name name(String commonName) {
        return new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.C, "VN")
                .addRDN(BCStyle.O, "Mô phỏng dịch vụ tin cậy - không có giá trị pháp lý")
                .addRDN(BCStyle.CN, commonName)
                .build();
    }

    private KeyPair newKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, random);
        return generator.generateKeyPair();
    }

    private X509Certificate certificate(
            X500Name subject, KeyPair pair, Identity issuer, Instant from, Instant until, CertificateKind kind)
            throws GeneralSecurityException, OperatorCreationException, IOException {
        X500Name issuerName = issuer == null ? subject
                : X500Name.getInstance(issuer.certificate().getSubjectX500Principal().getEncoded());
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuerName, new BigInteger(127, random).add(BigInteger.ONE), Date.from(from), Date.from(until),
                subject, pair.getPublic());
        JcaX509ExtensionUtils extensions = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                extensions.createSubjectKeyIdentifier(pair.getPublic()));
        if (issuer != null) {
            builder.addExtension(Extension.authorityKeyIdentifier, false,
                    extensions.createAuthorityKeyIdentifier(issuer.certificate()));
        }
        switch (kind) {
            case AUTHORITY -> {
                builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
                builder.addExtension(Extension.keyUsage, true,
                        new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign | KeyUsage.digitalSignature));
            }
            case SUBSCRIBER -> {
                builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
                builder.addExtension(Extension.keyUsage, true,
                        new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));
            }
            case TIME_STAMPING -> {
                builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
                builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
                builder.addExtension(Extension.extendedKeyUsage, true,
                        new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
            }
        }
        PrivateKey signingKey = issuer == null ? pair.getPrivate() : issuer.key();
        return new JcaX509CertificateConverter()
                .getCertificate(builder.build(new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(signingKey)));
    }

    private Keys load(Path store, char[] password) throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(store)) {
            keyStore.load(input, password);
        }
        Map<String, StoredSubscriber> metadata = Files.exists(metadataPath(store))
                ? json.readValue(metadataPath(store).toFile(), new TypeReference<>() { })
                : Map.of();
        Map<String, Subscriber> subscribers = new LinkedHashMap<>();
        for (Map.Entry<String, StoredSubscriber> entry : metadata.entrySet()) {
            String serial = entry.getKey();
            StoredSubscriber stored = entry.getValue();
            Identity identity = identity(keyStore, SUBSCRIBER_PREFIX + serial, password);
            subscribers.put(serial, new Subscriber(serial, stored.userId(), identity.key(), identity.certificate(),
                    stored.pinSalt(), stored.pinHash(), stored.revokedAt()));
        }
        return new Keys(identity(keyStore, ROOT_ALIAS, password), identity(keyStore, CA_ALIAS, password),
                identity(keyStore, TSA_ALIAS, password), subscribers);
    }

    private static Identity identity(KeyStore keyStore, String alias, char[] password)
            throws GeneralSecurityException {
        PrivateKey key = (PrivateKey) keyStore.getKey(alias, password);
        X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
        if (key == null || certificate == null) {
            throw new GeneralSecurityException("Missing entry " + alias);
        }
        return new Identity(key, certificate);
    }

    private void save(Keys value) throws GeneralSecurityException, IOException {
        char[] password = password();
        Path store = Path.of(properties.keystore()).toAbsolutePath();
        Files.createDirectories(store.getParent());
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, password);
        keyStore.setKeyEntry(ROOT_ALIAS, value.root.key(), password, new Certificate[] {value.root.certificate()});
        keyStore.setKeyEntry(CA_ALIAS, value.ca.key(), password,
                new Certificate[] {value.ca.certificate(), value.root.certificate()});
        keyStore.setKeyEntry(TSA_ALIAS, value.tsa.key(), password,
                new Certificate[] {value.tsa.certificate(), value.ca.certificate(), value.root.certificate()});
        Map<String, StoredSubscriber> metadata = new LinkedHashMap<>();
        for (Subscriber subscriber : value.subscribers.values()) {
            keyStore.setKeyEntry(SUBSCRIBER_PREFIX + subscriber.serial(), subscriber.key(), password,
                    new Certificate[] {subscriber.certificate(), value.ca.certificate(), value.root.certificate()});
            metadata.put(subscriber.serial(), new StoredSubscriber(
                    subscriber.userId(), subscriber.pinSalt(), subscriber.pinHash(), subscriber.revokedAt()));
        }
        Path temporary = store.resolveSibling(store.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            keyStore.store(output, password);
        }
        Files.move(temporary, store, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        json.writerWithDefaultPrettyPrinter().writeValue(metadataPath(store).toFile(), metadata);
        Files.writeString(store.resolveSibling("trusted-ca.pem"), pem(value.ca.certificate()), StandardCharsets.UTF_8);
    }

    private static Path metadataPath(Path store) {
        return store.resolveSibling(store.getFileName() + ".subscribers.json");
    }

    private static String pem(X509Certificate certificate) throws IOException {
        StringWriter writer = new StringWriter();
        try (JcaPEMWriter pem = new JcaPEMWriter(writer)) {
            pem.writeObject(certificate);
        }
        return writer.toString();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private enum CertificateKind { AUTHORITY, SUBSCRIBER, TIME_STAMPING }

    private record Identity(PrivateKey key, X509Certificate certificate) {
    }

    private record Keys(Identity root, Identity ca, Identity tsa, Map<String, Subscriber> subscribers) {
    }

    private record Subscriber(
            String serial, String userId, PrivateKey key, X509Certificate certificate, String pinSalt,
            String pinHash, Instant revokedAt) {

        Subscriber revoked(Instant at) {
            return new Subscriber(serial, userId, key, certificate, pinSalt, pinHash, at);
        }
    }

    /** What the JSON file beside the keystore keeps per subscriber. */
    record StoredSubscriber(String userId, String pinSalt, String pinHash, Instant revokedAt) {
    }

    private static final class Transaction {
        private final String id;
        private final String certificateSerial;
        private final byte[] documentSha256;
        private final String title;
        private final Instant requestedAt;
        private final Instant expiresAt;
        private Status status = Status.PENDING;
        private byte[] signedData;

        private Transaction(
                String id, String certificateSerial, byte[] documentSha256, String title, Instant requestedAt,
                Instant expiresAt) {
            this.id = id;
            this.certificateSerial = Objects.requireNonNull(certificateSerial);
            this.documentSha256 = documentSha256;
            this.title = title;
            this.requestedAt = requestedAt;
            this.expiresAt = expiresAt;
        }
    }

    /** A certificate issued by the simulated CA. */
    record Issued(String serial, X509Certificate certificate, String providerCode, String providerUserId) {
    }

    record PendingRequest(String id, String title, String documentSha256, Instant requestedAt, Instant expiresAt) {
    }

    record SubscriberInfo(String serial, String userId, String subject, Instant validTo, Instant revokedAt) {
    }
}
