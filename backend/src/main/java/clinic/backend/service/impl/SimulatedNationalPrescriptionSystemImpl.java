package clinic.backend.service.impl;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import clinic.backend.config.properties.NationalPrescriptionSimulatorProperties;
import clinic.backend.config.properties.NationalPrescriptionSimulatorProperties.FailureMode;
import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import clinic.backend.service.NationalPrescriptionSystem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Simulated national prescription system (BR-59-11, BR-59-12). It issues a 7-day token per facility and Doctor
 * without checking a password (ASSUMPTION), accepts a resend of a code it already accepted (ASSUMPTION), and rejects
 * an unknown prescriber link code, a malformed prescription code or a missing required field of QĐ 808 VII.3, so the
 * team can demonstrate UC-92. These rules are configuration for demonstrations, not the real system's validation.
 * Payloads are never logged (docs/security.md).
 */
@Service
@RequiredArgsConstructor
public class SimulatedNationalPrescriptionSystemImpl implements NationalPrescriptionSystem {

    private static final Duration TOKEN_LIFETIME = Duration.ofDays(7);
    /** QĐ 808 VII.3 row 2: facility code (5) + 7 letters or digits + "-" + type. */
    private static final Pattern CODE = Pattern.compile("[0-9]{5}[0-9A-Za-z]{7}-[chny]");
    private static final List<String> REQUIRED = List.of(
            "loai_don_thuoc", "ma_don_thuoc", "ho_ten_benh_nhan", "ngay_sinh_benh_nhan", "gioi_tinh", "dia_chi",
            "hinh_thuc_dieu_tri", "signature");
    private static final List<String> REQUIRED_PER_MEDICINE = List.of(
            "ma_thuoc", "biet_duoc", "ten_thuoc", "don_vi_tinh", "so_luong", "cach_dung");

    private final NationalPrescriptionSimulatorProperties properties;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Instant> tokens = new ConcurrentHashMap<>();
    private final Set<String> accepted = ConcurrentHashMap.newKeySet();

    @Override
    public Answer submit(Credentials credentials, byte[] prescriptionJson) {
        waitForAnswer();
        // QĐ 808 III, VI: sign in once per 7 days with the facility's and the Doctor's link codes.
        List<String> loginErrors = new ArrayList<>();
        if (!properties.registeredPrescriberCodes().isEmpty()
                && !properties.registeredPrescriberCodes().contains(credentials.prescriberLinkCode())) {
            loginErrors.add("Bác sĩ chưa được thêm vào cơ sở khám chữa bệnh.");
        }
        if (!loginErrors.isEmpty()) {
            return new Answer(false, "Đăng nhập liên thông không thành công", loginErrors);
        }
        String tokenKey = credentials.facilityLinkCode() + "|" + credentials.prescriberLinkCode();
        Instant now = clock.instant();
        tokens.compute(tokenKey, (key, expiry) -> expiry == null || !expiry.isAfter(now)
                ? now.plus(TOKEN_LIFETIME) : expiry);

        JsonNode prescription;
        try {
            prescription = json.readTree(prescriptionJson);
        } catch (IOException exception) {
            return new Answer(false, "Dữ liệu không hợp lệ", List.of("Dữ liệu đơn thuốc không đúng định dạng JSON."));
        }
        String code = prescription.path("ma_don_thuoc").asText("");
        if (accepted.contains(code)) {
            return new Answer(true, "Gửi đơn thuốc thành công", List.of());
        }
        List<String> errors = new ArrayList<>();
        REQUIRED.forEach(field -> requireField(prescription, field, errors));
        if (!code.isEmpty() && !CODE.matcher(code).matches()) {
            errors.add("Mã đơn thuốc không đúng định dạng.");
        }
        if (!code.isEmpty() && !code.startsWith(credentials.facilityCode())) {
            errors.add("Mã đơn thuốc không bắt đầu bằng mã cơ sở khám chữa bệnh.");
        }
        if (!prescription.path("chan_doan").isArray() || prescription.path("chan_doan").isEmpty()) {
            errors.add("Thiếu thông tin chan_doan.");
        }
        JsonNode medicines = prescription.path("thong_tin_don_thuoc");
        if (!medicines.isArray() || medicines.isEmpty()) {
            errors.add("Thiếu thông tin thong_tin_don_thuoc.");
        } else {
            for (int index = 0; index < medicines.size(); index++) {
                JsonNode medicine = medicines.get(index);
                for (String field : REQUIRED_PER_MEDICINE) {
                    if (isMissing(medicine.path(field))) {
                        errors.add("Thuốc thứ %d thiếu thông tin %s.".formatted(index + 1, field));
                    }
                }
            }
        }
        if (!errors.isEmpty()) {
            return new Answer(false, "Đơn thuốc không hợp lệ", errors);
        }
        accepted.add(code);
        return new Answer(true, "Gửi đơn thuốc thành công", List.of());
    }

    private void waitForAnswer() {
        Duration timeout = properties.timeout();
        boolean noAnswer = properties.failureMode() == FailureMode.NO_RESPONSE
                || properties.responseDelay().compareTo(timeout) >= 0;
        Duration wait = noAnswer ? min(properties.responseDelay(), timeout) : properties.responseDelay();
        if (!wait.isZero() && !wait.isNegative()) {
            try {
                Thread.sleep(wait.toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AppException(ErrorCode.NATIONAL_PRESCRIPTION_SYSTEM_UNAVAILABLE);
            }
        }
        if (noAnswer) {
            throw new AppException(ErrorCode.NATIONAL_PRESCRIPTION_SYSTEM_UNAVAILABLE);
        }
    }

    private static Duration min(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private static void requireField(JsonNode prescription, String field, List<String> errors) {
        if (isMissing(prescription.path(field))) {
            errors.add("Thiếu thông tin " + field + ".");
        }
    }

    private static boolean isMissing(JsonNode node) {
        return node.isMissingNode() || node.isNull() || (node.isTextual() && node.asText().isBlank());
    }
}
