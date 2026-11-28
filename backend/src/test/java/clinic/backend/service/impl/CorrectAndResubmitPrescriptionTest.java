package clinic.backend.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.config.properties.NationalPrescriptionSimulatorProperties;
import clinic.backend.config.properties.NationalPrescriptionSimulatorProperties.FailureMode;
import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.Prescription;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.PrescriptionLienthongStatus;
import clinic.backend.enums.PrescriptionSubmissionResult;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.service.NationalPrescriptionSystem.Answer;
import clinic.backend.service.NationalPrescriptionSystem.SignIn;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UC-92 Correct and Resubmit Prescription: a prescription rejected by the national prescription system stays
 * editable and can be sent again. The simulated system (BR-59-12, BR-59-17) answers as QĐ 808 III–VII describe:
 * sign-ins with a password and a 7-day token and adding and removing a Doctor; with a valid token every send is
 * accepted (27/11/2026), except the first send of each code in {@code REJECT} mode, used to demonstrate UC-92.
 */
class CorrectAndResubmitPrescriptionTest {

    private static final Instant NOW = Instant.parse("2026-11-25T03:00:00Z");
    private static final String FACILITY = "CSKCB-79001";
    private static final String DOCTOR = "BS-0001";
    private static final String VALID_CODE = "79001abc1234-c";

    private final AccountTestFixture.MutableClock clock = new AccountTestFixture.MutableClock(NOW);
    private final StaffAccountRepository staffAccounts = mock(StaffAccountRepository.class);
    private final ClinicFacilityRepository facilities = mock(ClinicFacilityRepository.class);

    // Prescription states (P04-P07)

    @Test
    void rejectedPrescriptionStaysEditableAndCanBeSentAgain() {
        Prescription prescription = prescription();

        prescription.recordSubmission(PrescriptionSubmissionResult.REJECTED, NOW);

        assertThat(prescription.getLienthongStatus()).isEqualTo(PrescriptionLienthongStatus.REJECTED);
        assertThat(prescription.isSent()).isFalse();
        assertThat(prescription.canBeSubmitted()).isTrue();
        assertThat(prescription.isPrintable()).isFalse();
        assertThat(prescription.getLienthongSentAt()).isNull();
    }

    @Test
    void rejectedPrescriptionIsCorrectedNotDeleted() {
        Prescription prescription = prescription();

        prescription.recordSubmission(PrescriptionSubmissionResult.REJECTED, NOW);

        assertThat(prescription.isDeletable()).isFalse();
    }

    @Test
    void resubmittedCorrectedPrescriptionIsAcceptedAndLocked() {
        Prescription prescription = prescription();
        prescription.recordSubmission(PrescriptionSubmissionResult.REJECTED, NOW);

        prescription.recordSubmission(PrescriptionSubmissionResult.ACCEPTED, NOW.plusSeconds(600));

        assertThat(prescription.getLienthongStatus()).isEqualTo(PrescriptionLienthongStatus.ACCEPTED);
        assertThat(prescription.getLienthongSentAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(prescription.isPrintable()).isTrue();
        assertThat(prescription.canBeSubmitted()).isFalse();
    }

    @Test
    void rejectedAgainAfterCorrectionCanStillBeResubmitted() {
        Prescription prescription = prescription();
        prescription.recordSubmission(PrescriptionSubmissionResult.REJECTED, NOW);

        prescription.recordSubmission(PrescriptionSubmissionResult.REJECTED, NOW.plusSeconds(60));

        assertThat(prescription.getLienthongStatus()).isEqualTo(PrescriptionLienthongStatus.REJECTED);
        assertThat(prescription.canBeSubmitted()).isTrue();
    }

    @Test
    void acceptedPrescriptionCannotBeResubmitted() {
        Prescription prescription = prescription();
        prescription.recordSubmission(PrescriptionSubmissionResult.ACCEPTED, NOW);

        assertThatThrownBy(() -> prescription.recordSubmission(PrescriptionSubmissionResult.ACCEPTED, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sendWithoutAnswerIsLockedForEditingButCanBeSentAgain() {
        Prescription prescription = prescription();

        prescription.recordSubmission(PrescriptionSubmissionResult.NO_RESPONSE, NOW);

        assertThat(prescription.getLienthongStatus()).isEqualTo(PrescriptionLienthongStatus.SENT);
        assertThat(prescription.isSent()).isTrue();
        assertThat(prescription.canBeSubmitted()).isTrue();
    }

    // Sign-ins and Doctors of the facility (QĐ 808 III-VI)

    @Test
    void facilitySignInNeedsTheConfiguredPassword() {
        SimulatedNationalPrescriptionSystemImpl system = simulator("co-so-123", "");

        SignIn wrong = system.signInFacility(FACILITY, "sai".toCharArray());
        SignIn right = system.signInFacility(FACILITY, "co-so-123".toCharArray());

        assertThat(wrong.succeeded()).isFalse();
        assertThat(wrong.errors()).containsExactly("Mã liên thông hoặc mật khẩu không đúng.");
        assertThat(right.succeeded()).isTrue();
        assertThat(right.token()).hasSizeGreaterThanOrEqualTo(40);
    }

    @Test
    void withoutAConfiguredPasswordAnyNonBlankPasswordSignsIn() {
        SimulatedNationalPrescriptionSystemImpl system = simulator("", "");

        assertThat(system.signInFacility(FACILITY, "bat-ky".toCharArray()).succeeded()).isTrue();
        assertThat(system.signInFacility(FACILITY, "  ".toCharArray()).succeeded()).isFalse();
    }

    @Test
    void aDoctorNotAddedToTheFacilityCannotSignIn() {
        SimulatedNationalPrescriptionSystemImpl system = simulator("", "");

        SignIn signIn = system.signInPrescriber(DOCTOR, FACILITY, "bac-si".toCharArray());

        assertThat(signIn.succeeded()).isFalse();
        assertThat(signIn.errors()).containsExactly("Bác sĩ chưa được thêm vào cơ sở khám chữa bệnh.");
    }

    @Test
    void anAddedDoctorSignsInWithTheDoctorsPassword() {
        SimulatedNationalPrescriptionSystemImpl system = simulator("", "bac-si-123");
        addDoctor(system);

        assertThat(system.signInPrescriber(DOCTOR, FACILITY, "sai".toCharArray()).succeeded()).isFalse();
        assertThat(system.signInPrescriber(DOCTOR, FACILITY, "bac-si-123".toCharArray()).succeeded()).isTrue();
    }

    @Test
    void addingADoctorNeedsTheFacilityToken() {
        SimulatedNationalPrescriptionSystemImpl system = simulator("", "");
        String prescriberToken = prescriberToken(system);

        Answer answer = system.addPrescriber(prescriberToken, "BS-0002");

        assertThat(answer.accepted()).isFalse();
        assertThat(answer.tokenInvalid()).isTrue();
    }

    @Test
    void removingADoctorEndsTheDoctorsTokensAndSignIn() {
        SimulatedNationalPrescriptionSystemImpl system = simulator("", "");
        String prescriberToken = prescriberToken(system);
        String facilityToken = system.signInFacility(FACILITY, "co-so".toCharArray()).token();

        Answer removed = system.removePrescriber(facilityToken, DOCTOR);

        assertThat(removed.accepted()).isTrue();
        assertThat(system.submit(prescriberToken, json(validPrescription(VALID_CODE))).tokenInvalid()).isTrue();
        assertThat(system.signInPrescriber(DOCTOR, FACILITY, "bac-si".toCharArray()).succeeded()).isFalse();
    }

    @Test
    void aTokenEndsAfterSevenDays() {
        SimulatedNationalPrescriptionSystemImpl system = simulator("", "");
        String token = prescriberToken(system);

        clock.advance(Duration.ofDays(7));

        assertThat(system.submit(token, json(validPrescription(VALID_CODE))).tokenInvalid()).isTrue();
    }

    @Test
    void aFacilityTokenCannotSendAPrescription() {
        SimulatedNationalPrescriptionSystemImpl system = simulator("", "");
        String facilityToken = system.signInFacility(FACILITY, "co-so".toCharArray()).token();

        assertThat(system.submit(facilityToken, json(validPrescription(VALID_CODE))).tokenInvalid()).isTrue();
    }

    @Test
    void afterARestartTheDoctorsTheClinicRecordedAsAddedCanSignIn() {
        StaffAccount doctor = mock(StaffAccount.class);
        when(doctor.getMaLienthongBacsi()).thenReturn(DOCTOR);
        when(staffAccounts.findAllByLienthongAddedAtIsNotNull()).thenReturn(List.of(doctor));
        SimulatedNationalPrescriptionSystemImpl system = simulator("", "");

        assertThat(system.signInPrescriber(DOCTOR, FACILITY, "bac-si".toCharArray()).succeeded()).isTrue();
    }

    // Answers of the simulated national prescription system (QĐ 808 VII): always accepted

    @Test
    void acceptsAPrescriptionSentWithAValidDoctorToken() {
        SimulatedNationalPrescriptionSystemImpl system = simulator("", "");

        Answer answer = system.submit(prescriberToken(system), json(validPrescription(VALID_CODE)));

        assertThat(answer.accepted()).isTrue();
        assertThat(answer.errors()).isEmpty();
    }

    @Test
    void neverRejectsWhateverTheContent() {
        SimulatedNationalPrescriptionSystemImpl system = simulator("", "");
        String token = prescriberToken(system);

        assertThat(system.submit(token, json(validPrescription("79002ABC-c").replace("\"dia_chi\":\"Q1\",", "")))
                .accepted()).isTrue();
        assertThat(system.submit(token, json("not json {")).accepted()).isTrue();
        assertThat(system.submit(token, json(validPrescription(VALID_CODE))).accepted()).isTrue();
    }

    // REJECT mode: demonstrates UC-91 and UC-92

    @Test
    void rejectModeRejectsTheFirstSendOfACodeAndAcceptsTheResend() {
        SimulatedNationalPrescriptionSystemImpl system = simulator(FailureMode.REJECT);
        String token = prescriberToken(system);

        Answer first = system.submit(token, json(validPrescription(VALID_CODE)));
        Answer resend = system.submit(token, json(validPrescription(VALID_CODE)));

        assertThat(first.accepted()).isFalse();
        assertThat(first.tokenInvalid()).isFalse();
        assertThat(first.errors()).containsExactly(SimulatedNationalPrescriptionSystemImpl.SAMPLE_REJECTION);
        assertThat(resend.accepted()).isTrue();
    }

    @Test
    void rejectModeRejectsEachCodeOnce() {
        SimulatedNationalPrescriptionSystemImpl system = simulator(FailureMode.REJECT);
        String token = prescriberToken(system);
        system.submit(token, json(validPrescription(VALID_CODE)));

        assertThat(system.submit(token, json(validPrescription("79001xyz9876-c"))).accepted()).isFalse();
        assertThat(system.submit(token, json(validPrescription(VALID_CODE))).accepted()).isTrue();
    }

    @Test
    void rejectModeStillSignsInAndAddsDoctorsAndChecksTheToken() {
        SimulatedNationalPrescriptionSystemImpl system = simulator(FailureMode.REJECT);

        String token = prescriberToken(system);

        assertThat(token).isNotBlank();
        assertThat(system.submit("khong-hop-le", json(validPrescription(VALID_CODE))).tokenInvalid()).isTrue();
    }

    @Test
    void noAnswerModeReportsTheSystemUnavailable() {
        SimulatedNationalPrescriptionSystemImpl system = new SimulatedNationalPrescriptionSystemImpl(
                new NationalPrescriptionSimulatorProperties(Duration.ZERO, Duration.ofSeconds(10),
                        FailureMode.NO_RESPONSE, "", ""),
                staffAccounts, facilities, clock);

        assertThatThrownBy(() -> system.signInFacility(FACILITY, "co-so".toCharArray()))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.NATIONAL_PRESCRIPTION_SYSTEM_UNAVAILABLE));
    }

    private static Prescription prescription() {
        return new Prescription(UUID.randomUUID(), NOW, "DT0001", VALID_CODE, UUID.randomUUID());
    }

    private SimulatedNationalPrescriptionSystemImpl simulator(String facilityPassword, String prescriberPassword) {
        return simulator(FailureMode.NONE, facilityPassword, prescriberPassword);
    }

    private SimulatedNationalPrescriptionSystemImpl simulator(FailureMode mode) {
        return simulator(mode, "", "");
    }

    private SimulatedNationalPrescriptionSystemImpl simulator(
            FailureMode mode, String facilityPassword, String prescriberPassword) {
        ClinicFacility facility = mock(ClinicFacility.class);
        when(facility.getMaCskcb()).thenReturn("79001");
        when(facility.getMaLienthongDonthuoc()).thenReturn(FACILITY);
        when(facilities.findCurrent()).thenReturn(Optional.of(facility));
        return new SimulatedNationalPrescriptionSystemImpl(
                new NationalPrescriptionSimulatorProperties(Duration.ZERO, Duration.ofSeconds(10), mode,
                        facilityPassword, prescriberPassword),
                staffAccounts, facilities, clock);
    }

    private static void addDoctor(SimulatedNationalPrescriptionSystemImpl system) {
        String facilityToken = system.signInFacility(FACILITY, "co-so".toCharArray()).token();
        assertThat(system.addPrescriber(facilityToken, DOCTOR).accepted()).isTrue();
    }

    private static String prescriberToken(SimulatedNationalPrescriptionSystemImpl system) {
        addDoctor(system);
        return system.signInPrescriber(DOCTOR, FACILITY, "bac-si".toCharArray()).token();
    }

    private static byte[] json(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String validPrescription(String code) {
        return "{\"loai_don_thuoc\":\"c\",\"ma_don_thuoc\":\"" + code + "\",\"ho_ten_benh_nhan\":\"Nguyễn Văn A\","
                + "\"ngay_sinh_benh_nhan\":\"01/01/1990\",\"gioi_tinh\":2,\"dia_chi\":\"Q1\","
                + "\"chan_doan\":[{\"ma_benh\":\"J20\"}],\"hinh_thuc_dieu_tri\":1,"
                + "\"thong_tin_don_thuoc\":[{\"ma_thuoc\":\"VD-12345-20\",\"biet_duoc\":\"Paracetamol\","
                + "\"ten_thuoc\":\"Panadol 500mg\",\"don_vi_tinh\":\"Viên\",\"so_luong\":10,"
                + "\"cach_dung\":\"Uống sau ăn\"}]}";
    }
}
