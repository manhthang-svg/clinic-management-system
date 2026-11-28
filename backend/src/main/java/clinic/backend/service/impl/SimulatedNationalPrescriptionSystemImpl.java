package clinic.backend.service.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import clinic.backend.config.properties.NationalPrescriptionSimulatorProperties;
import clinic.backend.config.properties.NationalPrescriptionSimulatorProperties.FailureMode;
import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.service.NationalPrescriptionSystem;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Simulated national prescription system (BR-59-12, BR-59-17), answering as QĐ 808/QĐ-BYT III–VII describe:
 * <ul>
 *   <li>III, VI: a sign-in with the configured password (any non-blank one when none is configured, ASSUMPTION)
 *       returns a random bearer token valid for 7 days; VI refuses a Doctor the facility has not added, with the
 *       message of VI.4.</li>
 *   <li>IV, V: add or remove a Doctor with the facility's token; both are idempotent (ASSUMPTION). Removing a Doctor
 *       ends the Doctor's tokens (ASSUMPTION).</li>
 *   <li>VII: with a valid Doctor's token, every prescription is accepted (team decision 27/11/2026); the clinic's
 *       own checks before sending (BR-59-07, BR-59-11) are the only validation. With failure mode {@code REJECT}
 *       the first send of each prescription code is rejected with a sample error and a resend is accepted, so the
 *       team can demonstrate UC-91 and UC-92 (ASSUMPTION).</li>
 * </ul>
 * The real system keeps the facility's Doctors; this one is in memory, so after a restart it reloads them from the
 * clinic's own record of who was added ({@code staff_accounts.lienthong_added_at}), standing in for the remote side.
 * These rules are configuration for demonstrations, not the real system's validation. Payloads, passwords and tokens
 * are never logged (docs/security.md).
 */
@Service
@RequiredArgsConstructor
public class SimulatedNationalPrescriptionSystemImpl implements NationalPrescriptionSystem {

    private static final Duration TOKEN_LIFETIME = Duration.ofDays(7);
    private static final String NOT_ADDED = "Bác sĩ chưa được thêm vào cơ sở khám chữa bệnh.";
    private static final String WRONG_CREDENTIALS = "Mã liên thông hoặc mật khẩu không đúng.";
    /** {@code REJECT} mode only (ASSUMPTION wording); marked as simulated so it is not read as a real BYT rule. */
    static final String SAMPLE_REJECTION = "Thuốc thứ 1: cách dùng chưa ghi rõ đường dùng, liều mỗi lần, số lần "
            + "trong ngày và số ngày dùng. (Lỗi giả lập để minh họa sửa và gửi lại đơn thuốc.)";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final NationalPrescriptionSimulatorProperties properties;
    private final StaffAccountRepository staffAccountRepository;
    private final ClinicFacilityRepository clinicFacilityRepository;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Session> tokens = new ConcurrentHashMap<>();
    /** {@code REJECT} mode: the codes already rejected once; in memory, so a restart starts over. */
    private final Set<String> rejectedOnce = ConcurrentHashMap.newKeySet();
    private volatile Set<String> addedPrescribers;

    @Override
    public SignIn signInFacility(String facilityLinkCode, char[] password) {
        waitForAnswer();
        if (blank(facilityLinkCode) || !passwordMatches(password, properties.facilityPassword())) {
            return new SignIn(null, List.of(WRONG_CREDENTIALS));
        }
        return new SignIn(issue(Kind.FACILITY, facilityLinkCode, null), List.of());
    }

    @Override
    public Answer addPrescriber(String facilityToken, String prescriberLinkCode) {
        waitForAnswer();
        Session session = session(facilityToken, Kind.FACILITY);
        if (session == null) {
            return Answer.invalidToken();
        }
        if (blank(prescriberLinkCode)) {
            return Answer.rejected("Dữ liệu không hợp lệ", List.of("Thiếu mã liên thông bác sĩ."));
        }
        added().add(key(session.facilityLinkCode(), prescriberLinkCode));
        return Answer.accepted("Bạn đã thêm bác sĩ thành công");
    }

    @Override
    public Answer removePrescriber(String facilityToken, String prescriberLinkCode) {
        waitForAnswer();
        Session session = session(facilityToken, Kind.FACILITY);
        if (session == null) {
            return Answer.invalidToken();
        }
        added().remove(key(session.facilityLinkCode(), prescriberLinkCode));
        tokens.values().removeIf(other -> other.kind() == Kind.PRESCRIBER
                && other.facilityLinkCode().equals(session.facilityLinkCode())
                && prescriberLinkCode.equals(other.prescriberLinkCode()));
        return Answer.accepted("Bạn đã xóa bác sĩ khỏi cơ sở khám chữa bệnh thành công");
    }

    @Override
    public SignIn signInPrescriber(String prescriberLinkCode, String facilityLinkCode, char[] password) {
        waitForAnswer();
        if (blank(prescriberLinkCode) || blank(facilityLinkCode)
                || !passwordMatches(password, properties.prescriberPassword())) {
            return new SignIn(null, List.of(WRONG_CREDENTIALS));
        }
        if (!added().contains(key(facilityLinkCode, prescriberLinkCode))) {
            return new SignIn(null, List.of(NOT_ADDED));
        }
        return new SignIn(issue(Kind.PRESCRIBER, facilityLinkCode, prescriberLinkCode), List.of());
    }

    @Override
    public Answer submit(String prescriberToken, byte[] prescriptionJson) {
        waitForAnswer();
        Session session = session(prescriberToken, Kind.PRESCRIBER);
        if (session == null) {
            return Answer.invalidToken();
        }
        if (properties.failureMode() == FailureMode.REJECT && rejectedOnce.add(prescriptionCode(prescriptionJson))) {
            return Answer.rejected("Đơn thuốc không hợp lệ", List.of(SAMPLE_REJECTION));
        }
        return Answer.accepted("Gửi đơn thuốc thành công");
    }

    /** The prescription code, or the whole payload's text when it cannot be read (still rejected once). */
    private String prescriptionCode(byte[] prescriptionJson) {
        try {
            String code = json.readTree(prescriptionJson).path("ma_don_thuoc").asText("");
            if (!code.isBlank()) {
                return code;
            }
        } catch (IOException exception) {
            // not JSON: fall through
        }
        return new String(prescriptionJson, StandardCharsets.UTF_8);
    }

    private String issue(Kind kind, String facilityLinkCode, String prescriberLinkCode) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.put(token, new Session(kind, facilityLinkCode, prescriberLinkCode, clock.instant().plus(TOKEN_LIFETIME)));
        return token;
    }

    private Session session(String token, Kind kind) {
        Session session = token == null ? null : tokens.get(token);
        if (session == null || session.kind() != kind) {
            return null;
        }
        if (!clock.instant().isBefore(session.expiresAt())) {
            tokens.remove(token);
            return null;
        }
        return session;
    }

    /** The Doctors added to each facility, loaded once from the clinic's record (see the class comment). */
    private Set<String> added() {
        Set<String> current = addedPrescribers;
        if (current == null) {
            synchronized (this) {
                current = addedPrescribers;
                if (current == null) {
                    current = ConcurrentHashMap.newKeySet();
                    String facilityLinkCode = clinicFacilityRepository.findCurrent()
                            .map(ClinicFacility::getMaLienthongDonthuoc).map(String::strip).orElse(null);
                    if (facilityLinkCode != null) {
                        for (StaffAccount doctor : staffAccountRepository.findAllByLienthongAddedAtIsNotNull()) {
                            if (doctor.getMaLienthongBacsi() != null) {
                                current.add(key(facilityLinkCode, doctor.getMaLienthongBacsi().strip()));
                            }
                        }
                    }
                    addedPrescribers = current;
                }
            }
        }
        return current;
    }

    private static boolean passwordMatches(char[] given, String expected) {
        if (given == null || new String(given).isBlank()) {
            return false;
        }
        if (expected.isEmpty()) {
            return true;
        }
        return MessageDigest.isEqual(new String(given).getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
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

    private static String key(String facilityLinkCode, String prescriberLinkCode) {
        return facilityLinkCode + "|" + prescriberLinkCode;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private enum Kind {
        FACILITY,
        PRESCRIBER
    }

    private record Session(Kind kind, String facilityLinkCode, String prescriberLinkCode, Instant expiresAt) {
    }
}
