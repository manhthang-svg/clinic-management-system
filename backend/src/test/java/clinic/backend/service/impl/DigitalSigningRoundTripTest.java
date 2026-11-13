package clinic.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import clinic.backend.config.properties.SigningProperties;
import clinic.backend.config.properties.SigningSimulatorProperties;
import clinic.backend.config.properties.SigningSimulatorProperties.FailureMode;
import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import clinic.backend.service.RemoteSigningGateway.SigningResult;
import clinic.backend.service.RemoteSigningGateway.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * BR-59-10: a signature made by the simulated remote-signing CA on the hash only verifies against the clinic's
 * payload with the real verification code, and every refusal reason of EF-11 and EF-16 is reported.
 */
class DigitalSigningRoundTripTest {

    private static final String PIN = "123456";
    private static final byte[] PAYLOAD =
            "{\"loai_don_thuoc\":\"c\",\"ma_don_thuoc\":\"12345abcdefg-c\"}".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    private final Clock clock = Clock.systemUTC();

    @Test
    void approvedSignatureVerifiesAgainstThePayload() throws Exception {
        Fixture fixture = fixture(FailureMode.NONE);
        SimulatedSigningAuthority.Issued issued = fixture.authority.issue("Nguyễn Văn An", "bs.an", PIN);
        fixture.verifier.preCheck(issued.certificate());

        byte[] signed = sign(fixture, issued, PAYLOAD);
        DigitalSignatureVerifier.Verification verification =
                fixture.verifier.verify(signed, PAYLOAD, issued.certificate());

        assertThat(verification.isValid()).isTrue();
        assertThat(verification.signedAt()).isBeforeOrEqualTo(clock.instant());
        assertThat(verification.timestampToken()).isNotEmpty();
        assertThat(verification.evidence()).contains("GOOD").contains(SimulatedSigningAuthority.CA_RESPONDER);
        List<? extends java.security.cert.Certificate> chain = CertificateFactory.getInstance("X.509")
                .generateCertPath(new java.io.ByteArrayInputStream(verification.certificateChain()), "PkiPath")
                .getCertificates();
        assertThat(chain).first().isEqualTo(issued.certificate());
    }

    @Test
    void changedPayloadIsRefused() {
        Fixture fixture = fixture(FailureMode.NONE);
        SimulatedSigningAuthority.Issued issued = fixture.authority.issue("Nguyễn Văn An", "bs.an", PIN);
        byte[] signed = sign(fixture, issued, PAYLOAD);

        byte[] changed = "{\"loai_don_thuoc\":\"c\",\"ma_don_thuoc\":\"12345abcdefg-n\"}"
                .getBytes(StandardCharsets.UTF_8);

        assertThat(fixture.verifier.verify(signed, changed, issued.certificate()).failureReason())
                .isEqualTo("SIGNATURE_MISMATCH");
    }

    @Test
    void signatureWithAnotherKeyIsRefused() {
        Fixture fixture = fixture(FailureMode.BAD_SIGNATURE);
        SimulatedSigningAuthority.Issued issued = fixture.authority.issue("Nguyễn Văn An", "bs.an", PIN);

        byte[] signed = sign(fixture, issued, PAYLOAD);

        assertThat(fixture.verifier.verify(signed, PAYLOAD, issued.certificate()).failureReason())
                .isEqualTo("SIGNATURE_MISMATCH");
    }

    @Test
    void signatureOfAnotherCertificateIsRefused() {
        Fixture fixture = fixture(FailureMode.NONE);
        SimulatedSigningAuthority.Issued doctor = fixture.authority.issue("Nguyễn Văn An", "bs.an", PIN);
        SimulatedSigningAuthority.Issued other = fixture.authority.issue("Trần Thị Bình", "bs.binh", PIN);

        byte[] signed = sign(fixture, other, PAYLOAD);

        assertThat(fixture.verifier.verify(signed, PAYLOAD, doctor.certificate()).failureReason())
                .isEqualTo("CERTIFICATE_NOT_REGISTERED_FOR_SIGNER");
    }

    @Test
    void revokedCertificateFailsThePreCheck() {
        Fixture fixture = fixture(FailureMode.NONE);
        SimulatedSigningAuthority.Issued issued = fixture.authority.issue("Nguyễn Văn An", "bs.an", PIN);

        fixture.authority.revoke(issued.serial());

        assertThatThrownBy(() -> fixture.verifier.preCheck(issued.certificate()))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SIGNING_CERTIFICATE_INVALID);
                    assertThat(exception.getDetails()).containsEntry("reason", "CERTIFICATE_REVOKED");
                });
    }

    @Test
    void untrustedCaFailsThePreCheck() {
        Fixture fixture = fixture(FailureMode.NONE);
        SimulatedSigningAuthority.Issued issued = fixture.authority.issue("Nguyễn Văn An", "bs.an", PIN);
        DigitalSignatureVerifier trustingNothing = new DigitalSignatureVerifier(
                new SigningProperties(Duration.ofMinutes(5), Duration.ofSeconds(10), List.of()),
                new SimulatedCertificateStatusServiceImpl(fixture.authority),
                new SimulatedTimestampAuthorityImpl(fixture.authority), clock);

        assertThatThrownBy(() -> trustingNothing.preCheck(issued.certificate()))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getDetails()).containsEntry("reason", "CA_NOT_TRUSTED"));
    }

    @Test
    void declinedAndCancelledRequestsAreNotSigned() {
        Fixture fixture = fixture(FailureMode.NONE);
        SimulatedSigningAuthority.Issued issued = fixture.authority.issue("Nguyễn Văn An", "bs.an", PIN);
        String declined = request(fixture, issued, PAYLOAD);
        String cancelled = request(fixture, issued, PAYLOAD);

        fixture.authority.decline(declined, issued.serial(), PIN);
        fixture.gateway.cancel(cancelled);

        assertThat(fixture.gateway.result(declined).status()).isEqualTo(Status.DECLINED);
        assertThat(fixture.gateway.result(cancelled).status()).isEqualTo(Status.EXPIRED);
        assertThat(fixture.gateway.result("unknown").status()).isEqualTo(Status.EXPIRED);
    }

    @Test
    void wrongPinIsRefusedByTheCaApp() {
        Fixture fixture = fixture(FailureMode.NONE);
        SimulatedSigningAuthority.Issued issued = fixture.authority.issue("Nguyễn Văn An", "bs.an", PIN);
        String transaction = request(fixture, issued, PAYLOAD);

        assertThatThrownBy(() -> fixture.authority.approve(transaction, issued.serial(), "000000"))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SIGNING_SIMULATOR_CREDENTIALS_INVALID));
        assertThat(fixture.gateway.result(transaction).status()).isEqualTo(Status.PENDING);
    }

    @Test
    void keysSurviveARestart() {
        Fixture first = fixture(FailureMode.NONE);
        SimulatedSigningAuthority.Issued issued = first.authority.issue("Nguyễn Văn An", "bs.an", PIN);

        Fixture second = fixture(FailureMode.NONE);
        byte[] signed = sign(second, issued, PAYLOAD);

        assertThat(second.verifier.verify(signed, PAYLOAD, issued.certificate()).isValid()).isTrue();
    }

    @Test
    void unavailableSimulatorAnswersAsUnavailable() {
        Fixture working = fixture(FailureMode.NONE);
        SimulatedSigningAuthority.Issued issued = working.authority.issue("Nguyễn Văn An", "bs.an", PIN);
        Fixture down = fixture(FailureMode.UNAVAILABLE);

        assertThatThrownBy(() -> down.verifier.preCheck(issued.certificate()))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SIGNING_SERVICE_UNAVAILABLE));
    }

    @Test
    void missingKeystorePasswordAnswersAsUnavailable() {
        SimulatedSigningAuthority authority = new SimulatedSigningAuthority(
                new SigningSimulatorProperties(directory.resolve("simulator.p12").toString(), "", Duration.ZERO,
                        FailureMode.NONE, false),
                clock, new SecureRandom());

        assertThatThrownBy(() -> authority.issue("Nguyễn Văn An", "bs.an", PIN))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SIGNING_SERVICE_UNAVAILABLE));
    }

    private byte[] sign(Fixture fixture, SimulatedSigningAuthority.Issued issued, byte[] payload) {
        String transaction = request(fixture, issued, payload);
        assertThat(fixture.authority.pendingRequests(issued.serial(), PIN))
                .extracting(SimulatedSigningAuthority.PendingRequest::id).contains(transaction);
        fixture.authority.approve(transaction, issued.serial(), PIN);
        SigningResult result = fixture.gateway.result(transaction);
        assertThat(result.status()).isEqualTo(Status.SIGNED);
        return result.signedData();
    }

    private String request(Fixture fixture, SimulatedSigningAuthority.Issued issued, byte[] payload) {
        return fixture.gateway.requestSignature(issued.providerUserId(), issued.serial(),
                DigitalSignatureVerifier.sha256(payload), "Đơn thuốc thử", Instant.now().plusSeconds(300));
    }

    private Fixture fixture(FailureMode failureMode) {
        Path keystore = directory.resolve("simulator.p12");
        SimulatedSigningAuthority authority = new SimulatedSigningAuthority(
                new SigningSimulatorProperties(keystore.toString(), "test-only", Duration.ZERO, failureMode, false),
                clock, new SecureRandom());
        DigitalSignatureVerifier verifier = new DigitalSignatureVerifier(
                new SigningProperties(Duration.ofMinutes(5), Duration.ofSeconds(10),
                        List.of(directory.resolve("trusted-ca.pem").toString())),
                new SimulatedCertificateStatusServiceImpl(authority), new SimulatedTimestampAuthorityImpl(authority),
                clock);
        return new Fixture(authority, new SimulatedRemoteSigningGatewayImpl(authority), verifier);
    }

    private record Fixture(
            SimulatedSigningAuthority authority, SimulatedRemoteSigningGatewayImpl gateway,
            DigitalSignatureVerifier verifier) {
    }
}
