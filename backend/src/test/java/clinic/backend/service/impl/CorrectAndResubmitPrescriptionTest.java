package clinic.backend.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import clinic.backend.config.properties.NationalPrescriptionSimulatorProperties;
import clinic.backend.config.properties.NationalPrescriptionSimulatorProperties.FailureMode;
import clinic.backend.entity.Prescription;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.PrescriptionLienthongStatus;
import clinic.backend.enums.PrescriptionSubmissionResult;
import clinic.backend.exceptions.AppException;
import clinic.backend.service.NationalPrescriptionSystem.Answer;
import clinic.backend.service.NationalPrescriptionSystem.Credentials;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-92 Correct and Resubmit Prescription: a prescription rejected by the national prescription system stays
 * editable and can be sent again; the simulated system rejects with the errors the Doctor corrects.
 */
class CorrectAndResubmitPrescriptionTest {

    private static final Instant NOW = Instant.parse("2026-11-25T03:00:00Z");
    private static final Credentials CREDENTIALS = new Credentials("79001", "CSKCB-79001", "BS-0001");
    private static final String VALID_CODE = "79001ABC1234-c";

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

    // Rejection reasons of the simulated national prescription system

    @Test
    void acceptsACompletePrescription() {
        Answer answer = simulator(List.of()).submit(CREDENTIALS, json(validPrescription(VALID_CODE)));

        assertThat(answer.accepted()).isTrue();
        assertThat(answer.errors()).isEmpty();
    }

    @Test
    void rejectsAMalformedPrescriptionCode() {
        Answer answer = simulator(List.of()).submit(CREDENTIALS, json(validPrescription("79001ABC-c")));

        assertThat(answer.accepted()).isFalse();
        assertThat(answer.errors()).contains("Mã đơn thuốc không đúng định dạng.");
    }

    @Test
    void rejectsACodeOfAnotherFacility() {
        Answer answer = simulator(List.of()).submit(CREDENTIALS, json(validPrescription("79002ABC1234-c")));

        assertThat(answer.accepted()).isFalse();
        assertThat(answer.errors()).contains("Mã đơn thuốc không bắt đầu bằng mã cơ sở khám chữa bệnh.");
    }

    @Test
    void listsEveryMissingRequiredField() {
        String payload = validPrescription(VALID_CODE).replace("\"dia_chi\":\"Q1\",", "")
                .replace("\"chan_doan\":[{\"ma\":\"J20\"}]", "\"chan_doan\":[]");

        Answer answer = simulator(List.of()).submit(CREDENTIALS, json(payload));

        assertThat(answer.accepted()).isFalse();
        assertThat(answer.errors()).contains("Thiếu thông tin dia_chi.", "Thiếu thông tin chan_doan.");
    }

    @Test
    void namesTheMedicineLineThatMissesAField() {
        String payload = validPrescription(VALID_CODE).replace("\"cach_dung\":\"Uống sau ăn\"", "\"cach_dung\":\" \"");

        Answer answer = simulator(List.of()).submit(CREDENTIALS, json(payload));

        assertThat(answer.errors()).containsExactly("Thuốc thứ 1 thiếu thông tin cach_dung.");
    }

    @Test
    void correctedPrescriptionIsAcceptedOnResubmit() {
        SimulatedNationalPrescriptionSystemImpl system = simulator(List.of());
        String rejected = validPrescription(VALID_CODE).replace("\"dia_chi\":\"Q1\",", "");

        Answer first = system.submit(CREDENTIALS, json(rejected));
        Answer second = system.submit(CREDENTIALS, json(validPrescription(VALID_CODE)));

        assertThat(first.accepted()).isFalse();
        assertThat(second.accepted()).isTrue();
    }

    @Test
    void resendOfAnAcceptedCodeIsAcceptedAgain() {
        SimulatedNationalPrescriptionSystemImpl system = simulator(List.of());
        system.submit(CREDENTIALS, json(validPrescription(VALID_CODE)));

        Answer again = system.submit(CREDENTIALS, json(validPrescription(VALID_CODE)));

        assertThat(again.accepted()).isTrue();
    }

    @Test
    void rejectsADoctorUnknownToTheNationalSystem() {
        Answer answer = simulator(List.of("BS-9999")).submit(CREDENTIALS, json(validPrescription(VALID_CODE)));

        assertThat(answer.accepted()).isFalse();
        assertThat(answer.errors()).containsExactly("Bác sĩ chưa được thêm vào cơ sở khám chữa bệnh.");
    }

    @Test
    void rejectsDataThatIsNotJson() {
        Answer answer = simulator(List.of()).submit(CREDENTIALS, json("not json {"));

        assertThat(answer.accepted()).isFalse();
        assertThat(answer.errors()).containsExactly("Dữ liệu đơn thuốc không đúng định dạng JSON.");
    }

    @Test
    void noAnswerModeReportsTheSystemUnavailable() {
        SimulatedNationalPrescriptionSystemImpl system = new SimulatedNationalPrescriptionSystemImpl(
                new NationalPrescriptionSimulatorProperties(Duration.ZERO, Duration.ofSeconds(10),
                        FailureMode.NO_RESPONSE, List.of()),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> system.submit(CREDENTIALS, json(validPrescription(VALID_CODE))))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.NATIONAL_PRESCRIPTION_SYSTEM_UNAVAILABLE));
    }

    private static Prescription prescription() {
        return new Prescription(UUID.randomUUID(), NOW, "DT0001", VALID_CODE, UUID.randomUUID());
    }

    private static SimulatedNationalPrescriptionSystemImpl simulator(List<String> registeredPrescribers) {
        return new SimulatedNationalPrescriptionSystemImpl(
                new NationalPrescriptionSimulatorProperties(Duration.ZERO, Duration.ofSeconds(10), FailureMode.NONE,
                        registeredPrescribers),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static byte[] json(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String validPrescription(String code) {
        return "{\"loai_don_thuoc\":\"c\",\"ma_don_thuoc\":\"" + code + "\",\"ho_ten_benh_nhan\":\"Nguyễn Văn A\","
                + "\"ngay_sinh_benh_nhan\":\"1990-01-01\",\"gioi_tinh\":1,\"dia_chi\":\"Q1\","
                + "\"hinh_thuc_dieu_tri\":1,\"signature\":\"abc\",\"chan_doan\":[{\"ma\":\"J20\"}],"
                + "\"thong_tin_don_thuoc\":[{\"ma_thuoc\":\"PARA500\",\"biet_duoc\":\"Panadol\","
                + "\"ten_thuoc\":\"Paracetamol\",\"don_vi_tinh\":\"Viên\",\"so_luong\":10,"
                + "\"cach_dung\":\"Uống sau ăn\"}]}";
    }
}
